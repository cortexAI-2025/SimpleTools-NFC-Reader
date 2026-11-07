package com.simpletools.nfcreader.feature.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simpletools.nfcreader.AppContainer
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.ui.components.ProLockedCard
import com.simpletools.nfcreader.ui.components.SectionCard
import kotlinx.coroutines.launch

/**
 * Sauvegarde et restauration locales (PRO). Le fichier est choisi par l'utilisateur via le
 * Storage Access Framework : aucune permission de stockage n'est demandée, aucun envoi réseau.
 */
@Composable
fun BackupSection(container: AppContainer, isPro: Boolean, onUnlock: () -> Unit, onMessage: (String) -> Unit) {
    if (!isPro) {
        ProLockedCard(stringResource(R.string.backup_title), onUnlock)
        return
    }
    val scope = rememberCoroutineScope()
    val context: Context = LocalContext.current
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    val savedMsg = stringResource(R.string.backup_saved)
    val restoredMsg = stringResource(R.string.backup_restored)
    val failedMsg = stringResource(R.string.backup_failed)

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val json = container.backup.export()
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null
            }.getOrDefault(false)
            onMessage(if (ok) savedMsg else failedMsg)
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            if (text != null) pendingRestore = text else onMessage(failedMsg)
        }
    }

    SectionCard(stringResource(R.string.backup_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.backup_body))
            Button(onClick = { createLauncher.launch("simpletools-nfc-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_export))
            }
            OutlinedButton(onClick = { openLauncher.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_restore))
            }
        }
    }

    pendingRestore?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val result = runCatching { container.backup.restore(text) }
                        onMessage(if (result.isSuccess) restoredMsg else failedMsg)
                    }
                }) { Text(stringResource(R.string.restore_confirm_button)) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
