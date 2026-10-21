package com.docscan.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.room.Room
import com.docscan.app.data.AppDatabase
import com.docscan.app.data.DocumentRepository
import com.docscan.app.data.FileStore
import com.docscan.app.export.ExportManager
import com.docscan.app.imaging.PageRenderer
import com.docscan.app.ocr.OcrEngine
import com.docscan.app.ocr.PageArranger
import com.docscan.app.security.FileCipher
import com.docscan.app.security.KeyVault
import com.docscan.app.security.SecureFiles
import com.docscan.app.security.SecurityPrefs
import java.io.File
import kotlin.concurrent.thread
import org.opencv.android.OpenCVLoader

class DocScanApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (!OpenCVLoader.initLocal()) {
            Log.e("DocScan", "OpenCV native libraries failed to load; scanning features will not work")
        }
        com.docscan.app.ui.write.WordFonts.init(this)
        com.docscan.app.imaging.BackgroundRemover.init(this)
        enableEncryption()
        container = AppContainer(this)
        thread(name = "docxels-housekeeping") {
            // Encrypt pages saved by older versions, and remove temp files an interrupted session left behind.
            SecureFiles.migrate(File(filesDir, "docs"))
            cacheDir.listFiles()?.filter { it.isFile && it.name.startsWith("capture") }?.forEach { SecureFiles.wipe(it) }
            container.exporter.clearCache()
            // Unlocked/working copies of PDFs and old downloaded updates must not outlive the session.
            listOf("pdfwork", "updates", "pdfview").forEach { n -> File(cacheDir, n).listFiles()?.forEach { SecureFiles.wipe(it) } }
        }
    }

    /** Pages are encrypted with a key kept in the Android Keystore. If the Keystore is unusable, files stay unencrypted rather than lost. */
    private fun enableEncryption() {
        runCatching { SecureFiles.cipher = FileCipher(KeyVault(this).dataKey()) }
            .onFailure { Log.e("DocScan", "Encryption unavailable on this device; files will be stored unencrypted") }
    }
}

/** Hand-wired dependencies. A DI framework would add weight without benefit at this app's size. */
class AppContainer(context: Context) {
    val fileStore = FileStore(context)
    private val database = Room.databaseBuilder(context, AppDatabase::class.java, "docscan.db").build()
    val repository = DocumentRepository(database, fileStore)
    val renderer = PageRenderer(fileStore)
    val ocr = OcrEngine()
    val arranger = PageArranger(renderer, ocr)
    val exporter = ExportManager(context, renderer, ocr)
    val security = SecurityPrefs(context)
    val ui = com.docscan.app.security.UiPrefs(context)
    val words = com.docscan.app.data.WordStore(context)
    val searchIndex = com.docscan.app.data.SearchIndex(context)
    val pdfs = com.docscan.app.data.PdfStore(context)
    val libraryMeta = com.docscan.app.data.LibraryMeta(context)
    val scanToWord = com.docscan.app.ocr.ScanToWord(renderer, ocr)
    /** A scan that has been read into Word text and is waiting for the person to check it (see ScanReviewScreen). */
    var pendingReview: com.docscan.app.ui.write.ScanReview? = null
    val pdfToWord = com.docscan.app.pdf.PdfToWord(context, scanToWord)
}
