package com.docscan.app.ui.home

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue


/** The sheets' own turning, driven by touch: drag to turn them, flick to spin them, tap to flip them over. */
/** One paper thrown off the pile: where it ends up (dp from where it started), how far it turns, and its colour. */
class Flung(val dx: Float, val dy: Float, val turn: Float, val hue: Float, val ms: Int, val w: Float) {
    val progress = Animatable(0f)
}

class SheetsState(private val scope: CoroutineScope, private val reduceMotion: Boolean = false) {
    val flung = mutableStateListOf<Flung>()

    /** A tap: a handful of papers are flung away from the pile, slow down as they travel, and fade out. */
    fun fling() {
        if (reduceMotion) return
        val r = kotlin.random.Random
        repeat(5) {
            // Mostly to the left and upward (the pile sits at the right end of the card), a few the other way.
            val angle = Math.toRadians((if (r.nextFloat() < 0.8f) r.nextDouble(150.0, 255.0) else r.nextDouble(-40.0, 40.0)))
            val dist = r.nextFloat() * 120f + 110f
            val f = Flung(
                dx = (kotlin.math.cos(angle) * dist).toFloat(),
                dy = (-kotlin.math.sin(angle) * dist).toFloat() * 0.6f,
                turn = (r.nextFloat() * 400f + 160f) * (if (r.nextBoolean()) 1 else -1),
                hue = r.nextFloat(),
                ms = r.nextInt(750, 1150),
                w = r.nextFloat() * 0.3f + 0.7f,
            )
            flung.add(f)
            scope.launch {
                f.progress.animateTo(1f, tween(f.ms, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                flung.remove(f)
            }
        }
    }

    val spin = Animatable(0f)
    val tilt = Animatable(0f)
    private var lastDx = 0f

    fun drag(dx: Float, dy: Float) {
        lastDx = dx
        scope.launch {
            spin.stop(); tilt.stop()
            spin.snapTo(spin.value + dx * 0.55f)
            tilt.snapTo((tilt.value - dy * 0.35f).coerceIn(-45f, 45f))
        }
    }

    /** Finger lifted: carry on turning with the flick's speed, then settle back to face front. */
    fun release() {
        val v = lastDx * 55f
        lastDx = 0f
        scope.launch {
            launch { tilt.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
            if (kotlin.math.abs(v) > 120f) spin.animateDecay(v, exponentialDecay(frictionMultiplier = 1.1f))
            val target = Math.round(spin.value / 360f) * 360f
            spin.animateTo(target, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
            spin.snapTo(0f)
        }
    }

    fun flip() {
        scope.launch {
            spin.stop()
            spin.animateTo(spin.value + 360f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
            spin.snapTo(spin.value % 360f)
        }
    }
}

@Composable
fun rememberSheetsState(): SheetsState {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember {
        val off = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
        SheetsState(scope, off)
    }
}

/** Makes a region turn the sheets: drag or flick to spin them, tap to fling some papers away. Buttons inside the region keep working. */
fun Modifier.sheetsTouch(state: SheetsState): Modifier = this
    .pointerInput(state) { detectTapGestures(onTap = { state.fling() }) }
    .pointerInput(state) {
        detectDragGestures(
            onDragEnd = { state.release() },
            onDragCancel = { state.release() },
        ) { change, drag ->
            change.consume()
            state.drag(drag.x, drag.y)
        }
    }

/**
 * Three paper sheets that float and turn in perspective. Pure Compose: each sheet is rotated in 3D with a camera
 * distance, so it needs no extra libraries and costs almost nothing. Stays still when the phone has animations
 * switched off (Settings > Accessibility > Remove animations).
 */
@Composable
fun FloatingSheets(state: SheetsState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val still = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val t = rememberInfiniteTransition(label = "sheets")
    val turn by t.animateFloat(
        initialValue = -24f, targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(3800, easing = EaseInOutSine), RepeatMode.Reverse), label = "turn",
    )
    val bob by t.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(2600, easing = EaseInOutSine), RepeatMode.Reverse), label = "bob",
    )
    val lean by t.animateFloat(
        initialValue = 7f, targetValue = -7f,
        animationSpec = infiniteRepeatable(tween(4400, easing = EaseInOutSine), RepeatMode.Reverse), label = "lean",
    )
    // The colours drift round the whole rainbow and meet themselves again, so the change never jumps.
    val hue by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "hue",
    )
    Box(modifier.size(width = 104.dp, height = 88.dp), contentAlignment = Alignment.Center) {
        for (i in 0 until 3) {
            val h = if (still) 0.68f else (hue + i * 0.12f) % 1f
            val paper = Color.hsv(h * 360f, 0.22f, 1f)
            val ink = Color.hsv(h * 360f, 0.75f, 0.85f)
            val line = Color.hsv(h * 360f, 0.35f, 0.95f)
            Column(
                Modifier
                    .size(width = 48.dp, height = 64.dp)
                    .graphicsLayer {
                        cameraDistance = 14f * density
                        rotationY = (if (still) -20f else turn * (1f + i * 0.3f) - 8f) + state.spin.value * (1f + i * 0.12f)
                        rotationX = (if (still) 0f else lean) + state.tilt.value
                        translationX = (i - 1) * 18.dp.toPx()
                        translationY = if (still) 0f else bob * (1f + i * 0.4f)
                        alpha = 0.6f + 0.2f * i
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(paper)
                    .padding(8.dp),
            ) {
                Box(Modifier.fillMaxWidth(0.6f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ink))
                Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(line))
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(line))
                Box(Modifier.padding(top = 4.dp).fillMaxWidth(0.7f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(line))
            }
        }
        // Papers thrown off by a tap: they leave the pile fast, slow down, turn and fade away.
        state.flung.forEach { f ->
            val p = f.progress.value
            Column(
                Modifier
                    .size(width = (34 * f.w).dp, height = (46 * f.w).dp)
                    .graphicsLayer {
                        translationX = f.dx * p * density
                        translationY = f.dy * p * density
                        rotationZ = f.turn * p
                        rotationY = f.turn * 0.4f * p
                        cameraDistance = 14f * density
                        val grow = 1f - 0.25f * p
                        scaleX = grow; scaleY = grow
                        alpha = (1f - p * p).coerceIn(0f, 1f) * 0.9f
                    }
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.hsv(f.hue * 360f, 0.22f, 1f))
                    .padding(6.dp),
            ) {
                val ink = Color.hsv(f.hue * 360f, 0.75f, 0.85f)
                Box(Modifier.fillMaxWidth(0.6f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(ink))
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(2.dp).background(ink.copy(alpha = 0.4f)))
                Box(Modifier.padding(top = 3.dp).fillMaxWidth(0.7f).height(2.dp).background(ink.copy(alpha = 0.4f)))
            }
        }
    }
}
