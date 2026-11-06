package com.simpletools.nfcreader

import androidx.compose.runtime.Composable
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
import com.simpletools.nfcreader.feature.history.HistoryScreen
import com.simpletools.nfcreader.feature.history.HistoryViewModel
import com.simpletools.nfcreader.feature.home.HomeScreen
import com.simpletools.nfcreader.feature.info.InfoScreen
import com.simpletools.nfcreader.feature.scanner.ScannerScreen
import com.simpletools.nfcreader.feature.scanner.ScannerViewModel
import com.simpletools.nfcreader.feature.settings.SettingsScreen
import com.simpletools.nfcreader.feature.settings.SettingsViewModel
import com.simpletools.nfcreader.feature.tagdetails.DetailsSource
import com.simpletools.nfcreader.feature.tagdetails.TagDetailsScreen
import com.simpletools.nfcreader.feature.tagdetails.TagDetailsViewModel

object Routes {
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val AUTH = "auth"
    const val HISTORY = "history"
    const val INFO = "info"
    const val SETTINGS = "settings"
    const val DETAILS = "details/{source}"
    fun details(source: String) = "details/$source"
}

@Composable
fun AppNavigation(container: AppContainer, settings: AppSettings) {
    val nav = rememberNavController()
    // Le ViewModel du scanner vit au niveau de l'activité : l'état survit à la navigation et aux rotations.
    val scannerVm: ScannerViewModel = viewModel(factory = viewModelFactory {
        initializer { ScannerViewModel(container.nfc, container.currentScan) }
    })

    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onScanner = { nav.navigate(Routes.SCANNER) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onAuth = { nav.navigate(Routes.AUTH) },
                onInfo = { nav.navigate(Routes.INFO) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SCANNER) {
            ScannerScreen(scannerVm, container.nfc, settings.maskIdentifiers, onBack = { nav.popBackStack() },
                onDetails = { nav.navigate(Routes.details("current")) })
        }
        composable(Routes.AUTH) {
            AuthenticationScreen(scannerVm, container.nfc, onBack = { nav.popBackStack() },
                onDetails = { nav.navigate(Routes.details("current")) })
        }
        composable(Routes.HISTORY) {
            val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(container.history) } })
            HistoryScreen(vm, settings.maskIdentifiers, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.details(it.toString())) })
        }
        composable(Routes.INFO) { InfoScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container) } })
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.DETAILS, arguments = listOf(navArgument("source") { type = NavType.StringType })) { entry ->
            val raw = entry.arguments?.getString("source").orEmpty()
            val source = raw.toLongOrNull()?.let { DetailsSource.Saved(it) } ?: DetailsSource.Current
            val vm: TagDetailsViewModel = viewModel(key = "details-$raw", factory = viewModelFactory {
                initializer { TagDetailsViewModel(source, container.currentScan, container.history, container.exporter) }
            })
            TagDetailsScreen(vm, settings.maskIdentifiers, onBack = { nav.popBackStack() })
        }
    }
}
