package com.docscan.app.ui.home

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/** A real page of the document, thrown off the deck by a tap: where it lands (dp from the deck), how far it turns, how long it flies. */
class ThrownPage(val bmp: Bitmap, val dx: Float, val dy: Float, val turn: Float, val ms: Int, val size: Float) {
    val progress = Animatable(0f)
}

/**
 * The motion of the Continue card's pages: they turn over by themselves, revolve in any direction when dragged (and settle
 * upright again), and fly away when tapped. Stays still when the phone has animations switched off.
 */
class DeckState(private val scope: CoroutineScope, val still: Boolean) {
    val spinY = Animatable(0f)
    val spinX = Animatable(0f)
    val flip = Animatable(0f)
    val punch = Animatable(1f)
    var index by mutableIntStateOf(0)
    val thrown = mutableStateListOf<ThrownPage>()
    /** When the finger last touched the deck, so the pages do not turn by themselves under it. */
    var touchedAt = 0L
        private set
    private var lastDx = 0f
    private var lastDy = 0f

    /** Turns the front page over to show the next one. */
    suspend fun turnNext(count: Int) {
        flip.animateTo(180f, tween(900, easing = FastOutSlowInEasing))
        index = (index + 1) % count
        flip.snapTo(0f)
    }

    fun drag(dx: Float, dy: Float) {
        if (still) return
        touchedAt = SystemClock.uptimeMillis()
        lastDx = dx; lastDy = dy
        scope.launch {
            spinY.stop(); spinX.stop()
            spinY.snapTo(spinY.value + dx * 0.8f)
            spinX.snapTo(spinX.value - dy * 0.8f)
        }
    }

    /** Finger lifted: carry on revolving with the flick's speed, then settle upright (a whole number of turns). */
    fun release() {
        if (still) return
        touchedAt = SystemClock.uptimeMillis()
        val vy = lastDx * 55f
        val vx = -lastDy * 55f
        lastDx = 0f; lastDy = 0f
        scope.launch { settle(spinY, vy) }
        scope.launch { settle(spinX, vx) }
    }

    private suspend fun settle(a: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, velocity: Float) {
        if (abs(velocity) > 120f) a.animateDecay(velocity, exponentialDecay(frictionMultiplier = 1.1f))
        val target = (a.value / 360f).roundToInt() * 360f
        a.animateTo(target, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
        a.snapTo(0f)
    }

    /** A tap: the document's real pages fly off (mostly upward, well beyond the card), slow down, turn and fade; the deck pops back. */
    fun fling(pages: List<Bitmap>) {
        if (still || pages.isEmpty()) return
        touchedAt = SystemClock.uptimeMillis()
        val r = kotlin.random.Random
        repeat(7) { i ->
            // Mostly upward, and far: the pages leave the card and travel over the rest of the screen before they fade.
            val angle = Math.toRadians(if (i % 3 == 2) r.nextDouble(0.0, 360.0) else r.nextDouble(15.0, 165.0))
            val dist = r.nextFloat() * 220f + 190f
            val t = ThrownPage(
                bmp = pages[i % pages.size],
                dx = (cos(angle) * dist).toFloat(), dy = (-kotlin.math.sin(angle) * dist).toFloat(),
                turn = (r.nextFloat() * 420f + 140f) * (if (r.nextBoolean()) 1 else -1),
                ms = r.nextInt(1300, 1900), size = r.nextFloat() * 0.25f + 0.55f,
            )
            thrown.add(t)
            scope.launch {
                t.progress.animateTo(1f, tween(t.ms, easing = FastOutSlowInEasing))
                thrown.remove(t)
            }
        }
        scope.launch {
            punch.snapTo(0.78f)
            punch.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow))
        }
    }
}

@Composable
fun rememberDeckState(): DeckState {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember {
        val off = runCatching { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
        DeckState(scope, off)
    }
}

/**
 * The document's first pages as a living deck: the front page keeps turning over to show the next one, drag it any way
 * to revolve it (down or sideways, upside down included), tap to send the real pages flying far across the screen, hold to preview the whole document until the finger lifts. Pure Compose, no extra libraries.
 */
@Composable
fun PageDeck(pages: List<Bitmap>, description: String, modifier: Modifier = Modifier, state: DeckState = rememberDeckState(), onHold: (Boolean) -> Unit = {}) {
    val density = LocalDensity.current.density
    val n = pages.size
    val current by rememberUpdatedState(pages)
    val holdNow by rememberUpdatedState(onHold)
    val fan = remember { Animatable(if (state.still) 1f else 0f) }
    LaunchedEffect(Unit) { fan.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
    LaunchedEffect(n, state.still) {
        if (state.still || n < 2) return@LaunchedEffect
        while (true) {
            delay(2600)
            if (SystemClock.uptimeMillis() - state.touchedAt > 3500) state.turnNext(n)
        }
    }
    val t = rememberInfiniteTransition(label = "deck")
    val sway by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(3600, easing = EaseInOutSine), RepeatMode.Reverse), label = "sway")
    val bob by t.animateFloat(-3f, 3f, infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse), label = "bob")

    Box(
        modifier
            .size(width = 100.dp, height = 124.dp)
            .zIndex(1f)
            .pointerInput(state) {
                // Holding the deck shows a preview of the document for as long as the finger stays down; a quick tap sends the pages flying.
                detectTapGestures(
                    onPress = {
                        var holding = false
                        kotlinx.coroutines.coroutineScope {
                            val timer = launch { delay(viewConfiguration.longPressTimeoutMillis); holding = true; holdNow(true) }
                            tryAwaitRelease()
                            timer.cancel()
                            if (holding) holdNow(false)
                        }
                    },
                    onLongPress = {},
                    onTap = { state.fling(current) },
                )
            }
            .pointerInput(state) {
                detectDragGestures(onDragEnd = { state.release() }, onDragCancel = { state.release() }) { change, drag ->
                    change.consume()
                    state.drag(drag.x, drag.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Pages waiting behind, fanned to either side: the next two in the document.
        for (i in 0 until 2) {
            val dir = if (i == 0) -1f else 1f
            val behind = if (n > 1) pages[(state.index + 1 + i) % n] else null
            Box(
                Modifier
                    .size(width = 78.dp, height = 104.dp)
                    .graphicsLayer {
                        cameraDistance = 14f * density
                        rotationZ = dir * 9f * fan.value
                        translationX = dir * 10.dp.toPx() * fan.value
                        rotationY = state.spinY.value * 0.5f + (if (state.still) 0f else sway * 0.5f * -dir)
                        rotationX = state.spinX.value * 0.5f
                        alpha = 0.6f + 0.1f * i
                    }
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White),
            ) {
                if (behind != null) Image(behind.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
            }
        }
        // The front page. Halfway through a turn the next page takes over, so it looks like the sheet flipped.
        val a = state.flip.value
        val showNext = a > 90f && n > 1
        val face = if (n == 0) null else pages[(state.index + (if (showNext) 1 else 0)) % n]
        val ry = (if (showNext) a - 180f else a) + state.spinY.value + (if (state.still) 0f else sway)
        val rx = state.spinX.value
        val facing = cos(Math.toRadians(ry.toDouble())) * cos(Math.toRadians(rx.toDouble())) >= 0.0
        Box(
            Modifier
                .size(width = 84.dp, height = 112.dp)
                .graphicsLayer {
                    cameraDistance = 14f * density
                    rotationY = ry
                    rotationX = rx
                    translationY = if (state.still) 0f else bob.dp.toPx()
                    val pop = (0.6f + 0.4f * fan.value) * state.punch.value
                    scaleX = pop; scaleY = pop
                    shadowElevation = 10.dp.toPx()
                    shape = RoundedCornerShape(10.dp)
                    clip = true
                }
                .background(if (facing) Color.White else Color(0xFFEDEDF3)),
        ) {
            // Seen from behind, the paper shows the page faintly through, mirrored by the turn itself.
            if (face != null) Image(
                face.asImageBitmap(), contentDescription = description, contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter, alpha = if (facing) 1f else 0.18f, modifier = Modifier.fillMaxSize(),
            )
        }
        // Real pages thrown off by a tap.
        state.thrown.forEach { p ->
            val pr = p.progress.value
            Box(
                Modifier
                    .size(width = (84 * p.size).dp, height = (112 * p.size).dp)
                    .graphicsLayer {
                        translationX = p.dx * pr * density
                        translationY = p.dy * pr * density
                        rotationZ = p.turn * pr
                        rotationY = p.turn * 0.4f * pr
                        cameraDistance = 14f * density
                        val shrink = 1f - 0.3f * pr
                        scaleX = shrink; scaleY = shrink
                        alpha = (1f - pr * pr).coerceIn(0f, 1f) * 0.95f
                        shadowElevation = 6.dp.toPx()
                        shape = RoundedCornerShape(6.dp)
                        clip = true
                    }
                    .background(Color.White),
            ) {
                Image(p.bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
