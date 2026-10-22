package com.docscan.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A soft light sweeping across a placeholder, so people see that content is on its way rather than an empty screen. */
fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val light = MaterialTheme.colorScheme.surfaceContainerHighest
    val t = rememberInfiniteTransition(label = "skeleton")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    drawBehind {
        drawRect(Brush.linearGradient(listOf(base, light, base), start = Offset(size.width * (x * 3f - 1f), 0f), end = Offset(size.width * (x * 3f), 0f)))
    }
}

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(10.dp)) {
    Box(modifier.clip(shape).shimmer())
}

@Composable
private fun SkeletonRow() {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        SkeletonBox(Modifier.size(52.dp), RoundedCornerShape(12.dp))
        Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(Modifier.fillMaxWidth(0.7f).height(16.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.4f).height(12.dp))
        }
    }
}

/** Placeholder for a list of documents while the library is being read. */
@Composable
fun ListSkeleton(rows: Int = 7, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp).semantics { contentDescription = "Loading documents" }) {
        SkeletonBox(Modifier.fillMaxWidth().height(52.dp), CircleShape)
        Box(Modifier.height(12.dp))
        repeat(rows) { SkeletonRow() }
    }
}

/** Placeholder shaped like the Home screen: greeting, search, the Continue card, quick actions and recent documents. */
@Composable
fun HomeSkeleton() {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).semantics { contentDescription = "Loading" }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.padding(top = 14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(56.dp), RoundedCornerShape(18.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(Modifier.fillMaxWidth(0.35f).height(14.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.6f).height(24.dp))
            }
            SkeletonBox(Modifier.size(width = 76.dp, height = 44.dp), CircleShape)
        }
        SkeletonBox(Modifier.fillMaxWidth().height(52.dp), CircleShape)
        SkeletonBox(Modifier.fillMaxWidth().height(190.dp), RoundedCornerShape(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(4) { SkeletonBox(Modifier.weight(1f).height(84.dp), RoundedCornerShape(20.dp)) } }
        repeat(2) { SkeletonRow() }
    }
}

/** A one-line placeholder, for a value that is being fetched (such as the plan). */
@Composable
fun SkeletonLine(width: Dp = 140.dp, height: Dp = 16.dp) = SkeletonBox(Modifier.size(width, height))
