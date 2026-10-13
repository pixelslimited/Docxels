package com.docscan.app.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.AppContainer
import com.docscan.app.core.Geometry
import com.docscan.app.data.HomeItem
import com.docscan.app.data.ImageIO
import com.docscan.app.imaging.EdgeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val items: StateFlow<List<HomeItem>> = container.repository.observeHome()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** Scan sessions that were abandoned before any page was captured are removed. */
    fun cleanUpEmptyDocuments() {
        viewModelScope.launch { container.repository.deleteEmptyDocuments() }
    }

    fun startScan(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(container.repository.createDocument()) }
    }

    fun delete(id: String) {
        viewModelScope.launch { container.repository.deleteDocument(id) }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { container.repository.renameDocument(id, name) }
    }

    /** Imports photos from the gallery into a new document, detecting page edges for each. */
    fun importImages(context: android.content.Context, uris: List<Uri>, onDone: (String) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            val docId = container.repository.createDocument()
            var added = 0
            for (uri in uris) {
                val ok = runCatching {
                    val bitmap = withContext(Dispatchers.IO) { ImageIO.decodeUri(context, uri, 3000) }
                        ?: error("Unreadable image")
                    val corners = withContext(Dispatchers.Default) { EdgeDetector.detect(bitmap) } ?: Geometry.DefaultCorners
                    container.repository.addPage(docId, bitmap, corners)
                    bitmap.recycle()
                }.isSuccess
                if (ok) added++
            }
            _busy.value = false
            if (added == 0) {
                container.repository.deleteDocument(docId)
                _message.value = "Could not read the selected images."
            } else {
                if (added < uris.size) _message.value = "${uris.size - added} image(s) could not be imported."
                onDone(docId)
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
