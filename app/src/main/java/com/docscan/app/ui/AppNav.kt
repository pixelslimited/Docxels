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
import com.docscan.app.ui.home.HomeScreen
import com.docscan.app.ui.home.HomeViewModel
import com.docscan.app.ui.scan.ScanScreen
import com.docscan.app.ui.scan.ScanViewModel
import com.docscan.app.ui.settings.SettingsScreen
import com.docscan.app.ui.write.WordEditorScreen
import com.docscan.app.ui.write.WordListScreen
import com.docscan.app.ui.write.WordViewModel

private const val HOME = "home"
private const val SETTINGS = "settings"
private const val SCAN = "scan/{docId}"
private const val DOC = "doc/{docId}"
private const val EDIT = "edit/{docId}/{pageId}"
private const val WORDS = "words"
private const val WRITE = "write/{id}"

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = HOME) {
        composable(HOME) {
            val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container) } })
            HomeScreen(
                vm = vm,
                onScan = { docId -> nav.navigate("scan/$docId") },
                onOpen = { docId -> nav.navigate("doc/$docId") },
                onSettings = { nav.navigate(SETTINGS) },
                onWrite = { nav.navigate(WORDS) },
            )
        }
        composable(WORDS) {
            WordListScreen(container = container, onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate("write/$id") })
        }
        composable(WRITE) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val vm: WordViewModel = viewModel(
                key = "write-$id",
                factory = viewModelFactory { initializer { WordViewModel(container, id) } },
            )
            WordEditorScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(SETTINGS) { SettingsScreen(container = container, onBack = { nav.popBackStack() }) }
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
