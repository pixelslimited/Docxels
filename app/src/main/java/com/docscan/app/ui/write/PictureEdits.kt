package com.docscan.app.ui.write

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.docscan.app.core.word.ImageBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The size in twips left after cutting the given fractions (0 to 1) off each side. */
fun cropTwips(widthTwips: Int, heightTwips: Int, left: Float, top: Float, right: Float, bottom: Float): Pair<Int, Int> =
    Math.round(widthTwips * (1f - left - right)).coerceAtLeast(120) to Math.round(heightTwips * (1f - top - bottom)).coerceAtLeast(120)

private fun decodeLimited(bytes: ByteArray, maxSide: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    var sample = 1
    while (maxOf(o.outWidth, o.outHeight) / sample > maxSide) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Cuts the given fractions off each side of the picture itself; the shown size shrinks to match. Null if the picture can't be read. */
fun cropImage(img: ImageBlock, left: Float, top: Float, right: Float, bottom: Float): ImageBlock? {
    val src = decodeLimited(img.bytes, 3000) ?: return null
    val x = (src.width * left).toInt().coerceIn(0, src.width - 1)
    val y = (src.height * top).toInt().coerceIn(0, src.height - 1)
    val w = (src.width * (1f - left - right)).toInt().coerceIn(1, src.width - x)
    val h = (src.height * (1f - top - bottom)).toInt().coerceIn(1, src.height - y)
    val cut = Bitmap.createBitmap(src, x, y, w, h)
    val png = img.extension != "jpg"
    val out = java.io.ByteArrayOutputStream()
    cut.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, out)
    val (tw, th) = cropTwips(img.widthTwips, img.heightTwips, left, top, right, bottom)
    return img.copy(bytes = out.toByteArray(), widthTwips = tw, heightTwips = th)
}

/** Makes the plain background transparent (works best on a clear subject against a plain background). Null on failure. */
fun removePictureBackground(img: ImageBlock, strict: Boolean = false): ImageBlock? = runCatching {
    val bmp = decodeLimited(img.bytes, 1600) ?: error("decode")
    val cut = com.docscan.app.imaging.BackgroundRemover.remove(bmp, strict)
    val out = java.io.ByteArrayOutputStream()
    cut.compress(Bitmap.CompressFormat.PNG, 100, out)
    img.copy(bytes = out.toByteArray(), extension = "png")
}.getOrNull()

/** Crop a picture with four sliders; the part that will be cut off is shaded on the preview. */
@Composable
internal fun CropDialog(img: ImageBlock, onDone: (ImageBlock) -> Unit, onDismiss: () -> Unit) {
    val preview = remember(img) { decodeLimited(img.bytes, 1000) }
    var l by remember { mutableFloatStateOf(0f) }
    var t by remember { mutableFloatStateOf(0f) }
    var r by remember { mutableFloatStateOf(0f) }
    var b by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crop") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (preview != null) Box(Modifier.fillMaxWidth().aspectRatio(preview.width.toFloat() / preview.height)) {
                    Image(preview.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
                    Canvas(Modifier.matchParentSize()) {
                        val shade = Color(0x99000000)
                        val cl = size.width * l
                        val cr = size.width * r
                        val ct = size.height * t
                        val cb = size.height * b
                        drawRect(shade, Offset(0f, 0f), Size(size.width, ct))
                        drawRect(shade, Offset(0f, size.height - cb), Size(size.width, cb))
                        drawRect(shade, Offset(0f, ct), Size(cl, size.height - ct - cb))
                        drawRect(shade, Offset(size.width - cr, ct), Size(cr, size.height - ct - cb))
                    }
                }
                Text("Left", style = MaterialTheme.typography.labelMedium)
                Slider(l, { l = it }, valueRange = 0f..0.45f)
                Text("Top", style = MaterialTheme.typography.labelMedium)
                Slider(t, { t = it }, valueRange = 0f..0.45f)
                Text("Right", style = MaterialTheme.typography.labelMedium)
                Slider(r, { r = it }, valueRange = 0f..0.45f)
                Text("Bottom", style = MaterialTheme.typography.labelMedium)
                Slider(b, { b = it }, valueRange = 0f..0.45f, modifier = Modifier.padding(bottom = 4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = busy == 0f && (l + t + r + b) > 0f,
                onClick = {
                    busy = 1f
                    scope.launch {
                        val out = withContext(Dispatchers.Default) { runCatching { cropImage(img, l, t, r, b) }.getOrNull() }
                        if (out != null) onDone(out) else onDismiss()
                    }
                },
            ) { Text(if (busy == 0f) "Crop" else "Working…") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
