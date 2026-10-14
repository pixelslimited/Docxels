package com.docscan.app.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.docscan.app.core.word.PageImage

/** Decodes header/footer pictures at a sensible size, so a big letterhead scan cannot exhaust memory. */
object PageImageBitmaps {
    fun decode(img: PageImage, maxSide: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
