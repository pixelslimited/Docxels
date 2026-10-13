package com.docscan.app.data

import android.graphics.Bitmap
import androidx.room.withTransaction
import com.docscan.app.core.Geometry
import com.docscan.app.core.Pt
import com.docscan.app.imaging.EnhanceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Single source of truth for documents and pages (database rows + image files). */
class DocumentRepository(
    private val database: AppDatabase,
    private val store: FileStore,
) {
    private val dao: DocumentDao = database.documentDao()

    /** Writes all positions in one transaction so observers never see a half-reordered list. */
    private suspend fun setOrder(orderedIds: List<String>) {
        database.withTransaction {
            orderedIds.forEachIndexed { index, id -> dao.setPosition(id, index) }
        }
    }

    fun observeHome(): Flow<List<HomeItem>> = combine(
        dao.observeDocuments(),
        dao.observeCoverPages(),
        dao.observePageCounts(),
    ) { docs, covers, counts ->
        val coverBy = covers.associateBy { it.documentId }
        val countBy = counts.associate { it.documentId to it.pageCount }
        docs.map { HomeItem(it, countBy[it.id] ?: 0, coverBy[it.id]) }
    }

    fun observeDocument(id: String) = dao.observeDocument(id)
    fun observePages(documentId: String) = dao.observePages(documentId)
    fun observePage(pageId: String) = dao.observePage(pageId)
    suspend fun pages(documentId: String) = dao.getPages(documentId)
    suspend fun page(pageId: String) = dao.getPage(pageId)
    suspend fun document(id: String) = dao.getDocument(id)

    suspend fun createDocument(): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val name = "Scan " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(now))
        dao.insertDocument(DocumentEntity(id, name, now, now))
        return id
    }

    /** Stores [source] (already upright) as a new last page. */
    suspend fun addPage(documentId: String, source: Bitmap, corners: List<Pt>): PageEntity {
        val id = UUID.randomUUID().toString()
        val fileName = "$id.jpg"
        withContext(Dispatchers.IO) { ImageIO.saveJpeg(source, store.file(documentId, fileName), 92) }
        val page = PageEntity(
            id = id,
            documentId = documentId,
            position = dao.maxPosition(documentId) + 1,
            sourcePath = fileName,
            corners = Geometry.encode(corners),
        )
        dao.insertPage(page)
        dao.touch(documentId, System.currentTimeMillis())
        return page
    }

    suspend fun updatePage(page: PageEntity) {
        dao.updatePage(page)
        dao.touch(page.documentId, System.currentTimeMillis())
    }

    suspend fun updateSettings(page: PageEntity, settings: EnhanceSettings) = updatePage(page.withSettings(settings))

    /** Changing the crop invalidates any hand-removed image because that was cut from the old crop. */
    suspend fun updateCorners(page: PageEntity, corners: List<Pt>) {
        deleteCleaned(page)
        updatePage(page.copy(corners = Geometry.encode(corners), cleanedPath = null))
    }

    suspend fun rotate(page: PageEntity, deltaDegrees: Int) {
        updatePage(page.copy(rotation = (((page.rotation + deltaDegrees) % 360) + 360) % 360))
    }

    /** Saves a hand-removed, straightened image for [page], or clears it when [bitmap] is null. */
    suspend fun setCleaned(page: PageEntity, bitmap: Bitmap?) {
        deleteCleaned(page)
        val name = bitmap?.let {
            // A new file name each time so cached thumbnails (keyed by page fields) never go stale.
            val n = "${page.id}_clean_${System.currentTimeMillis()}.jpg"
            withContext(Dispatchers.IO) { ImageIO.saveJpeg(it, store.file(page.documentId, n), 95) }
            n
        }
        updatePage(page.copy(cleanedPath = name))
    }

    suspend fun deletePage(page: PageEntity) {
        withContext(Dispatchers.IO) {
            store.file(page.documentId, page.sourcePath).delete()
            page.cleanedPath?.let { store.file(page.documentId, it).delete() }
        }
        dao.deletePage(page)
        setOrder(dao.getPages(page.documentId).map { it.id })
        dao.touch(page.documentId, System.currentTimeMillis())
    }

    suspend fun reorder(documentId: String, orderedIds: List<String>) {
        setOrder(orderedIds)
        dao.touch(documentId, System.currentTimeMillis())
    }

    suspend fun renameDocument(id: String, name: String) {
        val clean = name.trim().ifEmpty { return }
        dao.renameDocument(id, clean, System.currentTimeMillis())
    }

    suspend fun deleteDocument(id: String) {
        dao.deleteDocument(id)
        withContext(Dispatchers.IO) { store.deleteDocument(id) }
    }

    /** Removes documents that were created for scanning but never received a page. */
    suspend fun deleteEmptyDocuments() {
        dao.emptyDocumentIds().forEach { deleteDocument(it) }
    }

    private suspend fun deleteCleaned(page: PageEntity) {
        page.cleanedPath?.let { name -> withContext(Dispatchers.IO) { store.file(page.documentId, name).delete() } }
    }
}
