package com.docscan.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

private const val PREVIEW_PAGES = 40
private const val PREVIEW_SIDE = 1100
private const val DEAD_ZONE_DP = 14f
private const val AUTO_DP_PER_SECOND = 130f
private const val PAGE_GAP_DP = 14

private object CenterInWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset((windowSize.width - popupContentSize.width) / 2, (windowSize.height - popupContentSize.height) / 2)
}

/**
 * Shown for as long as the Continue card's pages are held: a wide window in the middle of the screen with the document's
 * pages running through it, top to bottom, from the page the person reached. Sliding the finger up runs them back towards the
 * start, sliding it down runs them on towards the end, faster the further the finger is from where it began; with the finger
 * near that spot they roll on slowly by themselves. It is a popup that takes no touches, so letting go removes it.
 */
@Composable
fun HoldPreview(item: LibraryItem, scrub: () -> Float) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val pages by produceState(emptyList<android.graphics.Bitmap>(), item.id, item.modified) {
        loadPages(context, container, item, max = PREVIEW_PAGES, side = PREVIEW_SIDE) { value = it }
    }
    val list = rememberLazyListState()
    val target = (item.meta.lastPage - 1).coerceAtLeast(0)
    var jumped by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(target == 0) }
    LaunchedEffect(pages.size) {
        if (!jumped && pages.size > target) { list.scrollToItem(target); jumped = true }
    }
    LaunchedEffect(jumped, pages.isNotEmpty()) {
        if (!jumped || pages.isEmpty()) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            val offset = scrub()
            val dp = if (abs(offset) < DEAD_ZONE_DP) AUTO_DP_PER_SECOND * dt
            else sign(offset) * (abs(offset) / 40f).pow(1.6f) * 700f * dt // about 1.4 pages a second at 40 dp of slide
            list.scrollBy(dp * density)
            // At the end of what is loaded, the slow roll starts over from the first page.
            if (abs(offset) < DEAD_ZONE_DP && !list.canScrollForward) list.scrollToItem(0)
        }
    }
    val screen = LocalConfiguration.current
    Popup(popupPositionProvider = CenterInWindow, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Column(
            Modifier.size(width = (screen.screenWidthDp * 0.92f).dp, height = (screen.screenHeightDp * 0.5f).dp)
                .clip(RoundedCornerShape(24.dp)).background(Color(0xFFDDE2EC)).padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(item.title, color = Color(0xFF1B1B1F), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                if (pages.isEmpty()) CircularProgressIndicator()
                else LazyColumn(Modifier.fillMaxSize(), state = list, userScrollEnabled = false, verticalArrangement = Arrangement.spacedBy(PAGE_GAP_DP.dp)) {
                    itemsIndexed(pages) { i, bmp ->
                        Image(
                            bmp.asImageBitmap(), contentDescription = "Page ${i + 1} of ${item.title}", contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().shadow(2.dp).aspectRatio(bmp.width.toFloat() / bmp.height).background(Color.White),
                        )
                    }
                }
            }
            Text(
                if (pages.isEmpty()) "Loading pages…" else "Page ${list.firstVisibleItemIndex + 1} of ${maxOf(item.meta.pageTotal, pages.size)}",
                color = Color(0xFF3A3F4B), style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Keep holding and slide up for the start, down for the end",
                color = Color(0xFF5B6270), style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
