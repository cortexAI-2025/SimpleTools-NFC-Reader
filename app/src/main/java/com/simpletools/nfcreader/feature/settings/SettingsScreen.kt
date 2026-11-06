package com.simpletools.nfcreader.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.core.settings.ThemeMode
import com.simpletools.nfcreader.ui.components.AppScaffold
import com.simpletools.nfcreader.ui.components.InfoRow
import com.simpletools.nfcreader.ui.components.ScreenColumn
import com.simpletools.nfcreader.ui.components.SectionCard
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    val doneMsg = stringResource(R.string.data_erased)
    val versionName = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0.0" }
    val currentLang = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')

    AppScaffold(stringResource(R.string.settings_title), onBack, snackbar) {
        ScreenColumn {
            SectionCard(stringResource(R.string.language)) {
                Column2 {
                    LangOption(R.string.language_system, currentLang.isEmpty()) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
                    LangOption(R.string.language_fr, currentLang == "fr") { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr")) }
                    LangOption(R.string.language_en, currentLang == "en") { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
                }
            }
            SectionCard(stringResource(R.string.theme)) {
                Column2 {
                    LangOption(R.string.theme_system, settings.theme == ThemeMode.SYSTEM) { vm.setTheme(ThemeMode.SYSTEM) }
                    LangOption(R.string.theme_light, settings.theme == ThemeMode.LIGHT) { vm.setTheme(ThemeMode.LIGHT) }
                    LangOption(R.string.theme_dark, settings.theme == ThemeMode.DARK) { vm.setTheme(ThemeMode.DARK) }
                }
            }
            SectionCard(stringResource(R.string.privacy)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.mask_ids_setting), Modifier.weight(1f))
                    Switch(settings.maskIdentifiers, vm::setMask)
                }
                Text(stringResource(R.string.privacy_statement), style = MaterialTheme.typography.bodyMedium)
            }
            SectionCard(stringResource(R.string.data_section)) {
                OutlinedButton(onClick = { confirm = "history" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.erase_history)) }
                Button(onClick = { confirm = "all" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.erase_all)) }
            }
            SectionCard(stringResource(R.string.about)) {
                InfoRow(stringResource(R.string.app_name), stringResource(R.string.version_fmt, versionName))
                InfoRow(stringResource(R.string.publisher), "AIWORKPAY — SimpleTools")
                Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    confirm?.let { kind ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(if (kind == "all") R.string.erase_all else R.string.erase_history)) },
            text = { Text(stringResource(if (kind == "all") R.string.erase_all_body else R.string.erase_history_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    val done = { scope.launch { snackbar.showSnackbar(doneMsg) }; Unit }
                    if (kind == "all") vm.wipeAll(done) else vm.clearHistoryOnly(done)
                }) { Text(stringResource(R.string.erase_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Column2(content: @Composable () -> Unit) {
    Column(Modifier.selectableGroup()) { content() }
}

@Composable
private fun LangOption(label: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onSelect, role = Role.RadioButton).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Text(stringResource(label), Modifier.padding(start = 12.dp))
    }
}
