package com.docscan.app.imaging

import android.graphics.Bitmap
import android.util.LruCache
import com.docscan.app.data.FileStore
import com.docscan.app.data.ImageIO
import com.docscan.app.data.PageEntity
import com.docscan.app.data.cornerList
import com.docscan.app.data.settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Mat

/**
 * Turns a stored page (original photo + crop + clean-up settings) into the final bitmap.
 * The original photo is never modified; every edit is re-applied from it, so edits are reversible.
 */
class PageRenderer(private val store: FileStore) {
    private val thumbnails = LruCache<String, Bitmap>(48)

    /** Full pipeline: crop/straighten -> (hand removal) -> rotate -> filters. */
    suspend fun render(page: PageEntity, maxSide: Int, source: Bitmap? = null): Bitmap =
        withContext(Dispatchers.Default) {
            val base = warpedBase(page, maxSide, source) ?: error("Page image is missing")
            val rotated = Warper.rotate(base, page.rotation)
            base.release()
            val result = Enhancer.apply(rotated, page.settings())
            rotated.release()
            val bitmap = result.toBitmap()
            result.release()
            bitmap
        }

    /**
     * Straightened page without rotation or filters. Uses the hand-removed image when there is one.
     * The caller owns (and must release) the returned Mat.
     */
    suspend fun warpedBase(page: PageEntity, maxSide: Int, source: Bitmap? = null): Mat? =
        withContext(Dispatchers.Default) {
            val cleaned = page.cleanedPath?.let { store.file(page.documentId, it) }
            if (cleaned != null && cleaned.exists()) {
                val bitmap = ImageIO.decodeFile(cleaned, maxSide) ?: return@withContext null
                val mat = bitmap.toRgbMat()
                bitmap.recycle()
                return@withContext mat
            }
            val original = source
                ?: ImageIO.decodeFile(store.file(page.documentId, page.sourcePath), (maxSide * 1.5).toInt().coerceAtMost(3200))
                ?: return@withContext null
            val mat = original.toRgbMat()
            if (source == null) original.recycle()
            val warped = Warper.warp(mat, page.cornerList(), maxSide)
            mat.release()
            // Un-curl wavy paper (needs several text lines), then level any remaining tilt of the text.
            val flat = Dewarp.apply(warped)
            warped.release()
            val level = Deskew.apply(flat)
            flat.release()
            level
        }

    /** Small cached rendering for lists and grids. */
    suspend fun thumbnail(page: PageEntity): Bitmap {
        // PageEntity is a data class, so any edit changes the key and invalidates the cache.
        val key = page.hashCode().toString()
        thumbnails.get(key)?.let { return it }
        return render(page, 420).also { thumbnails.put(key, it) }
    }
}
