package com.docscan.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docscan.app.LocalContainer
import com.docscan.app.data.LibraryItem
import kotlinx.coroutines.delay

/** How many pages the glance loads (a very long document is shown only as far as this). */
private const val GLANCE_PAGES = 40
private const val GLANCE_SIDE = 1100
private const val SECONDS_PER_PAGE = 2200L

/**
 * A quick look through the last document without opening it: starts on the page the person reached and turns through the
 * rest by itself. Touching the pages pauses it (swipe by hand, or press play to carry on); "Open" goes into the document.
 */
@Composable
fun QuickGlance(item: LibraryItem, onOpen: () -> Unit, onClose: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val pages by produceState(emptyList<android.graphics.Bitmap>(), item.id, item.modified) {
        loadPages(context, container, item, max = GLANCE_PAGES, side = GLANCE_SIDE) { value = it }
    }
    val target = (item.meta.lastPage - 1).coerceAtLeast(0)
    val pager = rememberPagerState { pages.size.coerceAtLeast(1) }
    var jumped by remember { mutableStateOf(target == 0) }
    LaunchedEffect(pages.size) {
        if (!jumped && pages.size > target) { pager.scrollToPage(target); jumped = true }
    }
    var playing by remember { mutableStateOf(true) }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) playing = false }
    LaunchedEffect(playing, jumped, pages.size) {
        while (playing && jumped && pages.size > 1) {
            delay(SECONDS_PER_PAGE)
            val next = pager.currentPage + 1
            if (next < pager.pageCount) pager.animateScrollToPage(next) else playing = false
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF2101418))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            if (pages.isEmpty()) "Loading pages…" else "Page ${pager.currentPage + 1} of ${if (item.meta.pageTotal > pages.size) item.meta.pageTotal else pages.size}",
                            color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    if (pages.isEmpty()) CircularProgressIndicator(color = Color.White)
                    else HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 16.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp)) { index ->
                        Image(
                            pages[index.coerceAtMost(pages.lastIndex)].asImageBitmap(),
                            contentDescription = "Page ${index + 1} of ${item.title}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color.White)
                                .pointerInput(Unit) { detectTapGestures(onTap = { playing = !playing }) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = { playing = !playing }, enabled = pages.size > 1) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Row(
                        Modifier.weight(1f).height(56.dp).clip(CircleShape).background(Color.White).clickable(onClick = onOpen),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Open document", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp).size(20.dp))
                    }
                }
            }
        }
    }
}
