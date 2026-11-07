package com.simpletools.nfcreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.simpletools.nfcreader.core.settings.AppSettings
import com.simpletools.nfcreader.feature.authentication.AuthenticationScreen
import com.simpletools.nfcreader.feature.compare.CompareScreen
import com.simpletools.nfcreader.feature.history.HistoryScreen
import com.simpletools.nfcreader.feature.history.HistoryViewModel
import com.simpletools.nfcreader.feature.home.HomeScreen
import com.simpletools.nfcreader.feature.info.InfoScreen
import com.simpletools.nfcreader.feature.inventory.CollectionScreen
import com.simpletools.nfcreader.feature.inventory.InventoryScreen
import com.simpletools.nfcreader.feature.pro.ProScreen
import com.simpletools.nfcreader.feature.pro.ProViewModel
import com.simpletools.nfcreader.feature.scanner.ScannerScreen
import com.simpletools.nfcreader.feature.scanner.ScannerViewModel
import com.simpletools.nfcreader.feature.settings.SettingsScreen
import com.simpletools.nfcreader.feature.settings.SettingsViewModel
import com.simpletools.nfcreader.feature.tagdetails.DetailsSource
import com.simpletools.nfcreader.feature.tagdetails.TagDetailsScreen
import com.simpletools.nfcreader.feature.tagdetails.TagDetailsViewModel
import com.simpletools.nfcreader.feature.templates.TemplateEditorScreen
import com.simpletools.nfcreader.feature.templates.TemplatesScreen
import com.simpletools.nfcreader.feature.write.WriteScreen
import com.simpletools.nfcreader.core.export.ShareUtil

object Routes {
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val AUTH = "auth"
    const val HISTORY = "history"
    const val INFO = "info"
    const val SETTINGS = "settings"
    const val DETAILS = "details/{source}"
    const val WRITE = "write?template={template}"
    const val TEMPLATES = "templates"
    const val TEMPLATE_EDIT = "template/{id}"
    const val INVENTORY = "inventory"
    const val COLLECTION = "collection/{id}"
    const val PRO = "pro"
    const val COMPARE = "compare/{a}/{b}"
    fun details(source: String) = "details/$source"
    fun write(template: Long? = null) = if (template == null) "write" else "write?template=$template"
    fun templateEdit(id: Long?) = "template/${id ?: "new"}"
    fun collection(id: Long) = "collection/$id"
    fun compare(a: Long, b: Long) = "compare/$a/$b"
}

@Composable
fun AppNavigation(container: AppContainer, settings: AppSettings) {
    val nav = rememberNavController()
    val isPro = container.license.state.collectAsState().value.isPro

    // Le ViewModel du scanner vit au niveau de l'activité : l'état survit à la navigation et aux rotations.
    val scannerVm: ScannerViewModel = viewModel(factory = viewModelFactory {
        initializer { ScannerViewModel(container.nfc, container.currentScan) }
    })
    val shareContext = androidx.compose.ui.platform.LocalContext.current

    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                isPro = isPro,
                onScanner = { nav.navigate(Routes.SCANNER) },
                onWrite = { nav.navigate(Routes.write()) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onTemplates = { nav.navigate(Routes.TEMPLATES) },
                onInventory = { nav.navigate(Routes.INVENTORY) },
                onAuth = { nav.navigate(Routes.AUTH) },
                onInfo = { nav.navigate(Routes.INFO) },
                onPro = { nav.navigate(Routes.PRO) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SCANNER) {
            ScannerScreen(scannerVm, container.nfc, settings.maskIdentifiers, isPro = isPro,
                onUnlock = { nav.navigate(Routes.PRO) }, onBack = { nav.popBackStack() },
                onDetails = { nav.navigate(Routes.details("current")) })
        }
        composable(Routes.AUTH) {
            AuthenticationScreen(scannerVm, container.nfc, onBack = { nav.popBackStack() },
                onDetails = { nav.navigate(Routes.details("current")) })
        }
        composable(Routes.HISTORY) {
            val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(container.history) } })
            HistoryScreen(vm, settings.maskIdentifiers, isPro = isPro, onUnlock = { nav.navigate(Routes.PRO) },
                onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.details(it.toString())) },
                onCompare = { a, b -> nav.navigate(Routes.compare(a, b)) })
        }
        composable(Routes.COMPARE, arguments = listOf(navArgument("a") { type = NavType.LongType }, navArgument("b") { type = NavType.LongType })) { entry ->
            CompareScreen(container, entry.arguments!!.getLong("a"), entry.arguments!!.getLong("b"), onBack = { nav.popBackStack() })
        }
        composable(Routes.INFO) { InfoScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container) } })
            SettingsScreen(vm, container, isPro = isPro, onUnlock = { nav.navigate(Routes.PRO) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.PRO) {
            val vm: ProViewModel = viewModel(factory = viewModelFactory { initializer { ProViewModel(container.license, container.billing) } })
            ProScreen(container, vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.WRITE, arguments = listOf(navArgument("template") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            val templateId = entry.arguments?.getString("template")?.toLongOrNull()
            WriteScreen(container, isPro, onUnlock = { nav.navigate(Routes.PRO) }, onBack = { nav.popBackStack() }, initialTemplateId = templateId)
        }
        composable(Routes.TEMPLATES) {
            TemplatesScreen(container, isPro, onUnlock = { nav.navigate(Routes.PRO) }, onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.templateEdit(it)) }, onUse = { nav.navigate(Routes.write(it)) })
        }
        composable(Routes.TEMPLATE_EDIT) { entry ->
            val raw = entry.arguments?.getString("id")
            TemplateEditorScreen(container, raw?.toLongOrNull(), onBack = { nav.popBackStack() })
        }
        composable(Routes.INVENTORY) {
            InventoryScreen(container, isPro, onUnlock = { nav.navigate(Routes.PRO) }, onBack = { nav.popBackStack() },
                onOpen = { nav.navigate(Routes.collection(it)) })
        }
        composable(Routes.COLLECTION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            CollectionScreen(container, entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() },
                onExported = { csv -> ShareUtil.shareText(shareContext, container.exporter, "inventaire", csv, "text/csv") })
        }
        composable(Routes.DETAILS, arguments = listOf(navArgument("source") { type = NavType.StringType })) { entry ->
            val raw = entry.arguments?.getString("source").orEmpty()
            val source = raw.toLongOrNull()?.let { DetailsSource.Saved(it) } ?: DetailsSource.Current
            val vm: TagDetailsViewModel = viewModel(key = "details-$raw", factory = viewModelFactory {
                initializer { TagDetailsViewModel(source, container.currentScan, container.history, container.exporter, container.inventory) }
            })
            TagDetailsScreen(container, vm, settings.maskIdentifiers, isPro = isPro, onUnlock = { nav.navigate(Routes.PRO) },
                onBack = { nav.popBackStack() })
        }
    }
}
