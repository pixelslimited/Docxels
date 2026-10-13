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
import org.opencv.android.OpenCVLoader

class DocScanApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (!OpenCVLoader.initLocal()) {
            Log.e("DocScan", "OpenCV native libraries failed to load; scanning features will not work")
        }
        container = AppContainer(this)
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
}
