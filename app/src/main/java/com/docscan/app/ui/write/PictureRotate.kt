package com.docscan.app.ui.write

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.docscan.app.core.word.ImageBlock

/** Turns the picture itself a quarter turn clockwise (the file is re-encoded), swapping its width and height. */
fun rotateQuarterTurn(img: ImageBlock): ImageBlock? {
    val src = BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size) ?: return null
    val turned = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(90f) }, true)
    val png = img.extension != "jpg"
    val out = java.io.ByteArrayOutputStream()
    turned.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, out)
    if (turned !== src) src.recycle()
    turned.recycle()
    return img.copy(bytes = out.toByteArray(), widthTwips = img.heightTwips, heightTwips = img.widthTwips)
}
