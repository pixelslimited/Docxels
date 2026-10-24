package com.docscan.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docscan.app.data.DocKind
import com.docscan.app.ui.shell.LibraryViewModel

/** Every scan (newest first) with a button to start a new one. Reached from Home and from the camera. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScansScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onNewScan: (String) -> Unit) {
    val all by vm.items.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val scans = all.filter { it.kind == DocKind.SCAN }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My scans") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.startScan(onNewScan) },
                icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                text = { Text("New scan") },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                scans.isEmpty() -> Text("No scans yet. Tap New scan to make your first.", Modifier.align(Alignment.Center).padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    items(scans, key = { it.id }) { item -> LibraryRow(item, onClick = { onOpen(item.id) }) }
                }
            }
        }
    }
}
