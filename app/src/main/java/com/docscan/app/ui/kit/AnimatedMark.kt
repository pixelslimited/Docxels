package com.docscan.app.ui.kit

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.docscan.app.R
import com.docscan.app.ui.theme.DocxelsTheme

/**
 * The pixel D with no background, whose colour drifts slowly round the spectrum. The colours wrap back to where they
 * began, so the change never jumps. Stays in the theme colour when the phone has animations switched off.
 */
@Composable
fun AnimatedMark(height: Dp, modifier: Modifier = Modifier, description: String? = null) {
    val context = LocalContext.current
    val still = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val t = rememberInfiniteTransition(label = "mark")
    val hue by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(45000, easing = LinearEasing), RepeatMode.Restart), label = "markHue",
    )
    val tint: Color = if (still) DocxelsTheme.brand.a else Color.hsv(hue * 360f, 0.72f, 0.78f)
    Image(
        painterResource(R.drawable.docxels_d), contentDescription = description,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.height(height),
    )
}
