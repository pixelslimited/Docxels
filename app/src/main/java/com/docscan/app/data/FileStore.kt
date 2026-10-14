package com.docscan.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.docscan.app.security.SecureFiles
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** Private, app-internal storage for page images (encrypted at rest). Nothing here is visible to other apps. */
class FileStore(context: Context) {
    private val root = File(context.filesDir, "docs").apply { mkdirs() }

    fun file(documentId: String, name: String): File = File(File(root, documentId).apply { mkdirs() }, name)

    fun deleteDocument(documentId: String) {
        SecureFiles.wipeDirectory(File(root, documentId))
    }
}

/** Bitmap decoding that honours EXIF orientation and never loads more pixels than needed. */
object ImageIO {
    fun decodeFile(file: File, maxSide: Int): Bitmap? {
        val bytes = SecureFiles.read(file) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions(bounds, maxSide)) ?: return null
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return finish(bitmap, orientation, maxSide)
    }

    fun decodeUri(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions(bounds, maxSide))
        } ?: return null
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return finish(bitmap, orientation, maxSide)
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 92) {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        SecureFiles.write(file, out.toByteArray())
    }

    private fun decodeOptions(bounds: BitmapFactory.Options, maxSide: Int): BitmapFactory.Options {
        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    }

    private fun finish(decoded: Bitmap, orientation: Int, maxSide: Int): Bitmap {
        var bitmap = rotateByExif(decoded, orientation)
        val longest = max(bitmap.width, bitmap.height)
        if (longest > maxSide) {
            val scale = maxSide.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * scale).toInt()),
                max(1, (bitmap.height * scale).toInt()),
                true,
            )
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }
        return bitmap
    }

    private fun rotateByExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
