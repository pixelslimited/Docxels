package com.docscan.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.docscan.app.AppContainer
import com.docscan.app.ui.document.DocumentScreen
import com.docscan.app.ui.document.DocumentViewModel
import com.docscan.app.ui.edit.EditScreen
import com.docscan.app.ui.edit.EditViewModel
import com.docscan.app.ui.pdf.PdfMode
import com.docscan.app.ui.pdf.PdfScreen
import com.docscan.app.ui.pdf.PdfViewModel
import com.docscan.app.ui.scan.ScanScreen
import com.docscan.app.ui.scan.ScanViewModel
import com.docscan.app.ui.shell.MainShell
import com.docscan.app.ui.write.WordEditorScreen
import com.docscan.app.ui.write.WordViewModel

private const val HOME = "home"
private const val SCAN = "scan/{docId}"
private const val DOC = "doc/{docId}"
private const val EDIT = "edit/{docId}/{pageId}"
private const val WRITE = "write/{id}"
private const val PDF = "pdf/{mode}"

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = HOME) {
        composable(HOME) {
            MainShell(
                container = container,
                onScanDoc = { docId -> nav.navigate("scan/$docId") },
                onOpenScan = { docId -> nav.navigate("doc/$docId") },
                onOpenWord = { id -> nav.navigate("write/$id") },
                onPdfTool = { mode -> nav.navigate("pdf/$mode") },
            )
        }
        composable(PDF) { entry ->
            val mode = PdfMode.from(entry.arguments?.getString("mode"))
            val context = androidx.compose.ui.platform.LocalContext.current
            val vm: PdfViewModel = viewModel(
                key = "pdf-${mode.route}",
                factory = viewModelFactory { initializer { PdfViewModel(container, context.applicationContext) } },
            )
            PdfScreen(vm = vm, mode = mode, onBack = { nav.popBackStack() })
        }
        composable(WRITE) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val vm: WordViewModel = viewModel(
                key = "write-$id",
                factory = viewModelFactory { initializer { WordViewModel(container, id) } },
            )
            WordEditorScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(SCAN) { entry ->
            val docId = entry.arguments?.getString("docId") ?: return@composable
            val vm: ScanViewModel = viewModel(
                key = "scan-$docId",
                factory = viewModelFactory { initializer { ScanViewModel(container, docId) } },
            )
            ScanScreen(
                vm = vm,
                onDone = {
                    if (nav.previousBackStackEntry?.destination?.route == DOC) {
                        nav.popBackStack()
                    } else {
                        nav.navigate("doc/$docId") { popUpTo(SCAN) { inclusive = true } }
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(DOC) { entry ->
            val docId = entry.arguments?.getString("docId") ?: return@composable
            val vm: DocumentViewModel = viewModel(
                key = "doc-$docId",
                factory = viewModelFactory { initializer { DocumentViewModel(container, docId) } },
            )
            DocumentScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onAddPages = { nav.navigate("scan/$docId") },
                onEditPage = { pageId -> nav.navigate("edit/$docId/$pageId") },
                onOpenWord = { id -> nav.navigate("write/$id") },
            )
        }
        composable(EDIT) { entry ->
            val docId = entry.arguments?.getString("docId") ?: return@composable
            val pageId = entry.arguments?.getString("pageId") ?: return@composable
            val vm: EditViewModel = viewModel(
                key = "edit-$pageId",
                factory = viewModelFactory { initializer { EditViewModel(container, docId, pageId) } },
            )
            EditScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
