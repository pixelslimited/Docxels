package com.docscan.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.docscan.app.core.Geometry
import com.docscan.app.core.Pt
import com.docscan.app.imaging.DocMode
import com.docscan.app.imaging.EnhanceSettings
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class PageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val position: Int,
    /** File name of the normalised original photo inside the document folder. */
    val sourcePath: String,
    /** Document corners in the original photo, normalised 0..1, TL;TR;BR;BL. */
    val corners: String,
    /** Extra clockwise rotation in degrees (0, 90, 180, 270). */
    val rotation: Int = 0,
    val mode: String = DocMode.COLOR.name,
    val brightness: Int = 0,
    val contrast: Float = 1f,
    val removeShadows: Boolean = true,
    val autoLevels: Boolean = true,
    /** File name of the hand-removed, already straightened image, if the user cleaned this page. */
    val cleanedPath: String? = null,
    /** Smooths grain from dim light (database version 2). */
    val denoise: Boolean = false,
    /** 0..100 sharpening that reduces mild blur (database version 2). */
    val sharpen: Int = 0,
    /** Fills small blown-out highlights from a flash or glossy paper (database version 2). */
    val glare: Boolean = false,
    /** Flips the page left to right (database version 2). */
    val mirror: Boolean = false,
)

fun PageEntity.cornerList(): List<Pt> = Geometry.decode(corners) ?: Geometry.DefaultCorners

fun PageEntity.settings(): EnhanceSettings = EnhanceSettings(
    mode = runCatching { DocMode.valueOf(mode) }.getOrDefault(DocMode.COLOR),
    brightness = brightness,
    contrast = contrast,
    removeShadows = removeShadows,
    autoLevels = autoLevels,
    denoise = denoise,
    sharpen = sharpen,
    glare = glare,
    mirror = mirror,
)

fun PageEntity.withSettings(s: EnhanceSettings): PageEntity = copy(
    mode = s.mode.name,
    brightness = s.brightness,
    contrast = s.contrast,
    removeShadows = s.removeShadows,
    autoLevels = s.autoLevels,
    denoise = s.denoise,
    sharpen = s.sharpen,
    glare = s.glare,
    mirror = s.mirror,
)

data class PageCount(val documentId: String, val pageCount: Int)

enum class DocKind { SCAN, DOCX, PDF }

/** One row of the unified library: a scan or a Word document. */
data class LibraryItem(
    val kind: DocKind,
    val id: String,
    val title: String,
    val modified: Long,
    val detail: String,
    val cover: PageEntity?,
    val meta: ItemMeta = ItemMeta(),
)

data class HomeItem(val document: DocumentEntity, val pageCount: Int, val cover: PageEntity?)

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM pages p WHERE p.position = (SELECT MIN(position) FROM pages WHERE documentId = p.documentId)")
    fun observeCoverPages(): Flow<List<PageEntity>>

    @Query("SELECT documentId, COUNT(*) AS pageCount FROM pages GROUP BY documentId")
    fun observePageCounts(): Flow<List<PageCount>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeDocument(id: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position")
    fun observePages(documentId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE id = :id")
    fun observePage(id: String): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position")
    suspend fun getPages(documentId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPage(id: String): PageEntity?

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocument(id: String): DocumentEntity?

    @Query("SELECT id FROM documents WHERE id NOT IN (SELECT DISTINCT documentId FROM pages)")
    suspend fun emptyDocumentIds(): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) FROM pages WHERE documentId = :documentId")
    suspend fun maxPosition(documentId: String): Int

    @Insert
    suspend fun insertDocument(document: DocumentEntity)

    @Insert
    suspend fun insertPage(page: PageEntity)

    @Update
    suspend fun updatePage(page: PageEntity)

    @Delete
    suspend fun deletePage(page: PageEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("UPDATE documents SET name = :name, updatedAt = :time WHERE id = :id")
    suspend fun renameDocument(id: String, name: String, time: Long)

    @Query("UPDATE documents SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: String, time: Long)

    @Query("UPDATE pages SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)
}

@Database(entities = [DocumentEntity::class, PageEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}

/** Version 1 to 2: four more per-page picture options. Existing pages keep every setting and get the options switched off. */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pages ADD COLUMN denoise INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pages ADD COLUMN sharpen INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pages ADD COLUMN glare INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pages ADD COLUMN mirror INTEGER NOT NULL DEFAULT 0")
    }
}
