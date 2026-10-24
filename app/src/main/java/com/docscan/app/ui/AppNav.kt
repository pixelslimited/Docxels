package com.docscan.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue

private const val HOME = "home"
private const val SCAN = "scan/{docId}"
private const val SCANS = "scans"
private const val DOC = "doc/{docId}"
private const val EDIT = "edit/{docId}/{pageId}"
private const val WRITE = "write/{id}"
private const val REVIEW = "review"
private const val PDF = "pdf/{mode}"
private const val PDFDOC = "pdfdoc/{id}"

@Composable
fun AppNav(container: AppContainer, incoming: kotlinx.coroutines.flow.StateFlow<android.net.Uri?>, onIncomingHandled: () -> Unit) {
    val nav = rememberNavController()
    val incomingUri by incoming.collectAsState()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    // A file opened from another app waits here until the terms are accepted (the effect below runs only after this).
    var termsOk by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(container.ui.termsAccepted == TermsGate.VERSION)
    }
    if (!termsOk) {
        TermsGateScreen(onAccept = { container.ui.termsAccepted = TermsGate.VERSION; termsOk = true })
        return
    }

    // Clearing the incoming file changes the effect's key, which would cancel the import half way (the file got saved but the
    // screen never opened). The import therefore runs in a scope that outlives the key change.
    val importScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(incomingUri) {
        val uri = incomingUri ?: return@LaunchedEffect
        onIncomingHandled()
        importScope.launch {
            val route = Incoming.import(appContext, container, uri) { msg ->
                android.os.Handler(android.os.Looper.getMainLooper()).post { android.widget.Toast.makeText(appContext, msg, android.widget.Toast.LENGTH_LONG).show() }
            }
            if (route != null) nav.navigate(route)
        }
    }

    var showIntro by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(FirstRunIntro.shouldShow(appContext, container.ui.introSeen))
    }
    if (showIntro) FirstRunIntroDialog(onDone = { container.ui.introSeen = true; showIntro = false })

    NavHost(navController = nav, startDestination = HOME) {
        composable(HOME) {
            MainShell(
                container = container,
                onScanDoc = { docId -> nav.navigate("scan/$docId") },
                onOpenScan = { docId -> nav.navigate("doc/$docId") },
                onOpenWord = { id -> nav.navigate(if (id == com.docscan.app.ui.write.REVIEW_ID) REVIEW else "write/$id") },
                onPdfTool = { mode -> nav.navigate("pdf/$mode") },
                onOpenPdf = { id -> nav.navigate("pdfdoc/$id") },
                onShowScans = { nav.navigate(SCANS) },
            )
        }
        composable(SCANS) {
            val lib: com.docscan.app.ui.shell.LibraryViewModel = viewModel(factory = viewModelFactory { initializer { com.docscan.app.ui.shell.LibraryViewModel(container) } })
            com.docscan.app.ui.library.ScansScreen(
                vm = lib,
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate("doc/$id") },
                onNewScan = { id -> nav.navigate("scan/$id") { popUpTo(SCANS) { inclusive = true } } },
            )
        }
        composable(PDF) { entry ->
            val mode = PdfMode.from(entry.arguments?.getString("mode"))
            val context = androidx.compose.ui.platform.LocalContext.current
            val vm: PdfViewModel = viewModel(
                key = "pdf-${mode.route}",
                factory = viewModelFactory { initializer { PdfViewModel(container, context.applicationContext) } },
            )
            PdfScreen(vm = vm, startMode = mode, onBack = { nav.popBackStack() }, onOpenWord = { id -> nav.navigate("write/$id") })
        }
        composable(PDFDOC) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val context = androidx.compose.ui.platform.LocalContext.current
            val vm: PdfViewModel = viewModel(
                key = "pdfdoc-$id",
                factory = viewModelFactory { initializer { PdfViewModel(container, context.applicationContext) } },
            )
            androidx.compose.runtime.LaunchedEffect(id) { vm.openStored(id) }
            PdfScreen(vm = vm, startMode = PdfMode.VIEW, onBack = { nav.popBackStack() }, autoPick = false, storedId = id, onOpenWord = { wid -> nav.navigate("write/$wid") })
        }
        composable(REVIEW) {
            val review = container.pendingReview
            if (review == null) {
                androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack() }
            } else {
                com.docscan.app.ui.write.ScanReviewScreen(
                    container = container,
                    review = review,
                    onDone = { id -> container.pendingReview = null; nav.navigate("write/$id") { popUpTo(REVIEW) { inclusive = true } } },
                    onCancel = { container.pendingReview = null; nav.popBackStack() },
                )
            }
        }
        composable(WRITE) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val vm: WordViewModel = viewModel(
                key = "write-$id",
                factory = viewModelFactory { initializer { WordViewModel(container, id) } },
            )
            WordEditorScreen(vm = vm, onBack = { nav.popBackStack() }, onOpenWord = { nid -> nav.navigate("write/$nid") })
        }
        composable(SCAN) { entry ->
            val docId = entry.arguments?.getString("docId") ?: return@composable
            val vm: ScanViewModel = viewModel(
                key = "scan-$docId",
                factory = viewModelFactory { initializer { ScanViewModel(container, docId) } },
            )
            ScanScreen(
                vm = vm,
                onShowScans = { nav.navigate(SCANS) },
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
                onOpenWord = { id -> nav.navigate(if (id == com.docscan.app.ui.write.REVIEW_ID) REVIEW else "write/$id") },
            )
        }
        composable(EDIT) { entry ->
            val docId = entry.arguments?.getString("docId") ?: return@composable
            val pageId = entry.arguments?.getString("pageId") ?: return@composable
            val vm: EditViewModel = viewModel(
                key = "edit-$pageId",
                factory = viewModelFactory { initializer { EditViewModel(container, docId, pageId) } },
            )
            EditScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onGoToPage = { id -> nav.navigate("edit/$docId/$id") { popUpTo(EDIT) { inclusive = true } } },
            )
        }
    }
}
