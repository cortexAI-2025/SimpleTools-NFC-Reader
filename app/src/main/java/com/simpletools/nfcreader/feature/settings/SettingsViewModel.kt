package com.simpletools.nfcreader.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpletools.nfcreader.AppContainer
import com.simpletools.nfcreader.core.settings.AppSettings
import com.simpletools.nfcreader.core.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { container.settings.setTheme(mode) } }
    fun setMask(mask: Boolean) { viewModelScope.launch { container.settings.setMaskIdentifiers(mask) } }
    fun wipeAll(onDone: () -> Unit) { viewModelScope.launch { container.wipeAllLocalData(); onDone() } }
    fun clearHistoryOnly(onDone: () -> Unit) { viewModelScope.launch { container.history.deleteAll(); container.exporter.clearExports(); onDone() } }
}
