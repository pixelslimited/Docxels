package com.docscan.app.ui.home

import android.provider.Settings
import androidx.compose.animation.core.EaseInOutSine
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Three paper sheets that float and turn in perspective. Pure Compose: each sheet is rotated in 3D with a camera
 * distance, so it needs no extra libraries and costs almost nothing. Stays still when the phone has animations
 * switched off (Settings > Accessibility > Remove animations).
 */
@Composable
fun FloatingSheets(modifier: Modifier = Modifier) {
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
    Box(modifier.size(width = 104.dp, height = 88.dp), contentAlignment = Alignment.Center) {
        for (i in 0 until 3) {
            Column(
                Modifier
                    .size(width = 48.dp, height = 64.dp)
                    .graphicsLayer {
                        cameraDistance = 14f * density
                        rotationY = if (still) -20f else turn * (1f + i * 0.3f) - 8f
                        rotationX = if (still) 0f else lean
                        translationX = (i - 1) * 18.dp.toPx()
                        translationY = if (still) 0f else bob * (1f + i * 0.4f)
                        alpha = 0.6f + 0.2f * i
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(8.dp),
            ) {
                Box(Modifier.fillMaxWidth(0.6f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF4F46E5)))
                Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFB9B4F4)))
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFB9B4F4)))
                Box(Modifier.padding(top = 4.dp).fillMaxWidth(0.7f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFB9B4F4)))
            }
        }
    }
}
