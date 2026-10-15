package com.docscan.app.ui.kit

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.docscan.app.R
import com.docscan.app.ui.theme.DocxelsTheme

/**
 * The Docxels mark with a background whose colours drift smoothly round the spectrum. The colours wrap back to where
 * they began, so the change never jumps. Stays in the theme colour when the phone has animations switched off.
 */
@Composable
fun AnimatedMark(size: Dp, modifier: Modifier = Modifier, description: String? = null) {
    val context = LocalContext.current
    val still = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val t = rememberInfiniteTransition(label = "mark")
    val hue by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart), label = "markHue",
    )
    val brush: Brush = if (still) DocxelsTheme.brand.gradient else Brush.linearGradient(
        listOf(Color.hsv(hue * 360f, 0.72f, 0.82f), Color.hsv(((hue + 0.16f) % 1f) * 360f, 0.78f, 0.72f)),
    )
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(brush)) {
        Image(painterResource(R.drawable.ic_docxels_mark_fg), contentDescription = description, modifier = Modifier.size(size))
    }
}
