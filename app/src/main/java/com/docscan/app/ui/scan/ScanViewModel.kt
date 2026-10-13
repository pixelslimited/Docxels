package com.docscan.app.ui.scan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.AppContainer
import com.docscan.app.core.Geometry
import com.docscan.app.data.ImageIO
import com.docscan.app.imaging.EdgeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScanViewModel(private val container: AppContainer, val docId: String) : ViewModel() {
    val pageCount: StateFlow<Int> = container.repository.observePages(docId)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** A freshly captured photo: normalise it, find the page edges and add it to the document. */
    fun addCapture(file: File) {
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                val bitmap = withContext(Dispatchers.IO) { ImageIO.decodeFile(file, MAX_SIDE) } ?: error("Unreadable photo")
                val corners = withContext(Dispatchers.Default) { EdgeDetector.detect(bitmap) } ?: Geometry.DefaultCorners
                container.repository.addPage(docId, bitmap, corners)
                bitmap.recycle()
            }.onFailure { _message.value = "Could not save the photo. Please try again." }
            file.delete()
            _busy.value = false
        }
    }

    fun addFromGallery(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            var failed = 0
            for (uri in uris) {
                runCatching {
                    val bitmap = withContext(Dispatchers.IO) { ImageIO.decodeUri(context, uri, MAX_SIDE) } ?: error("Unreadable image")
                    val corners = withContext(Dispatchers.Default) { EdgeDetector.detect(bitmap) } ?: Geometry.DefaultCorners
                    container.repository.addPage(docId, bitmap, corners)
                    bitmap.recycle()
                }.onFailure { failed++ }
            }
            if (failed > 0) _message.value = "$failed image(s) could not be imported."
            _busy.value = false
        }
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun messageShown() {
        _message.value = null
    }

    private companion object {
        const val MAX_SIDE = 3000
    }
}
