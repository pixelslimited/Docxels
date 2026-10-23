package com.docscan.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.docscan.app.LocalContainer
import com.docscan.app.data.LibraryItem

private const val PREVIEW_PAGES = 40
private const val PREVIEW_SIDE = 1100
private const val MS_PER_PAGE = 1300L
private const val DEAD_ZONE_DP = 14f

private object CenterInWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset((windowSize.width - popupContentSize.width) / 2, (windowSize.height - popupContentSize.height) / 2)
}

/**
 * Shown for as long as the Continue card's pages are held: the document's pages in the middle of the screen, turning by
 * themselves from the page the person reached. It is a popup that takes no touches, so letting go of the deck removes it.
 */
@Composable
fun HoldPreview(item: LibraryItem, scrub: () -> Float) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val pages by produceState(emptyList<android.graphics.Bitmap>(), item.id, item.modified) {
        loadPages(context, container, item, max = PREVIEW_PAGES, side = PREVIEW_SIDE) { value = it }
    }
    var index by remember { mutableIntStateOf((item.meta.lastPage - 1).coerceAtLeast(0)) }
    LaunchedEffect(pages.size) {
        // Finger near where the hold began: the pages turn by themselves. Slid left: towards the start of the document;
        // slid right: towards its end. The further from where it began, the faster (it stops at the first and last page).
        var position = index.toFloat()
        var autoClock = 0L
        var last = withFrameNanos { it }
        while (pages.size > 1) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            val offset = scrub()
            if (abs(offset) < DEAD_ZONE_DP) {
                autoClock += (dt * 1000).toLong()
                if (autoClock >= MS_PER_PAGE) { autoClock = 0; position = if (position >= pages.lastIndex) 0f else position + 1f }
            } else {
                autoClock = 0
                val speed = (abs(offset) / 40f).pow(1.6f) * 1.5f // pages per second
                position = (position + sign(offset) * speed * dt).coerceIn(0f, pages.lastIndex.toFloat())
            }
            index = position.roundToInt().coerceIn(0, pages.lastIndex)
        }
    }
    val screen = LocalConfiguration.current
    Popup(popupPositionProvider = CenterInWindow, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Column(
            Modifier.size(width = (screen.screenWidthDp * 0.84f).dp, height = (screen.screenHeightDp * 0.72f).dp)
                .clip(RoundedCornerShape(24.dp)).background(Color(0xF2101418)).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(item.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                if (pages.isEmpty()) CircularProgressIndicator(color = Color.White)
                else AnimatedContent(
                    index.coerceAtMost(pages.lastIndex),
                    transitionSpec = {
                        // Pages slide sideways, forward from the right and backward from the left.
                        val forward = targetState > initialState
                        (slideInHorizontally(tween(140)) { if (forward) it else -it } + fadeIn(tween(140))) togetherWith
                            (slideOutHorizontally(tween(140)) { if (forward) -it else it } + fadeOut(tween(140)))
                    },
                    label = "holdPage",
                ) { i ->
                    Image(
                        pages[i].asImageBitmap(), contentDescription = "Page ${i + 1} of ${item.title}", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.White),
                    )
                }
            }
            Text(
                if (pages.isEmpty()) "Loading pages…" else "Page ${index.coerceAtMost(pages.lastIndex) + 1} of ${maxOf(item.meta.pageTotal, pages.size)}",
                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Keep holding and slide left for the start, right for the end",
                color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
