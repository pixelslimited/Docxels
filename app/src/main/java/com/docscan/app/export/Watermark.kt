package com.docscan.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import com.docscan.app.R

/**
 * The small "Scanned with Docxels by Pixels" credit, with both logos, in the bottom-left corner of an exported page.
 * The text is about 12 pt when the page is printed on A4, the logos are as tall as the text, and it is drawn slightly
 * transparent so it never competes with the page.
 */
object Watermark {
    private const val TEXT_FRACTION = 0.0145f // of the page height: 12 pt on an A4 sheet
    private const val MARGIN_FRACTION = 0.014f

    /** Returns [bitmap] with the credit drawn on it (a mutable copy when [bitmap] cannot be drawn on). */
    fun stamp(context: Context, bitmap: Bitmap): Bitmap {
        val target = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true).also { bitmap.recycle() }
        val canvas = Canvas(target)
        val textSize = (target.height * TEXT_FRACTION).coerceAtLeast(10f)
        val logo = (textSize * 1.35f)
        val gap = textSize * 0.4f
        val margin = target.height * MARGIN_FRACTION
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.argb(170, 70, 70, 70)
            this.textSize = textSize
        }
        val baseline = target.height - margin - (logo - textSize) / 2f - paint.descent()
        val top = target.height - margin - logo
        var x = margin

        val docxels = BitmapFactory.decodeResource(context.resources, R.drawable.docxels_logo)
        if (docxels != null) {
            paint.alpha = 200
            canvas.drawBitmap(docxels, null, RectF(x, top, x + logo, top + logo), paint)
            docxels.recycle()
            x += logo + gap
        }
        paint.color = Color.argb(170, 70, 70, 70)
        val lead = "Scanned with Docxels by"
        canvas.drawText(lead, x, baseline, paint)
        x += paint.measureText(lead) + gap

        val pixels = ContextCompat.getDrawable(context, R.drawable.pixels_logo)
        if (pixels != null) {
            // The mark is taller than wide (96 x 112); keep its proportions inside a logo-sized box.
            val h = logo
            val w = logo * 96f / 112f
            pixels.alpha = 200
            pixels.setBounds(x.toInt(), top.toInt(), (x + w).toInt(), (top + h).toInt())
            pixels.draw(canvas)
            x += w + gap
        }
        canvas.drawText("Pixels", x, baseline, paint)
        return target
    }
}
