package com.simpletools.nfcreader.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.core.database.ReadingEntity
import com.simpletools.nfcreader.core.nfc.Mask
import com.simpletools.nfcreader.ui.components.AppScaffold
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    vm: HistoryViewModel,
    maskIds: Boolean,
    isPro: Boolean,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onCompare: (Long, Long) -> Unit,
) {
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(listOf<Long>()) }
    val items by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var deleteTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var confirmAll by rememberSaveable { mutableStateOf(false) }

    AppScaffold(
        stringResource(R.string.history_title), onBack,
        actions = {
            if (!isPro) TextButton(onClick = onUnlock) { Text(stringResource(R.string.compare_locked)) }
            else TextButton(onClick = { selectMode = !selectMode; selected = emptyList() }) {
                Text(stringResource(if (selectMode) R.string.cancel else R.string.compare_action))
            }
            if (!items.isNullOrEmpty()) {
                TextButton(onClick = { confirmAll = true }) { Text(stringResource(R.string.clear_all)) }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                    }
                },
            )
            val list = items
            if (selectMode) {
                Text(stringResource(R.string.compare_pick, selected.size), style = MaterialTheme.typography.bodyMedium)
                if (selected.size == 2) {
                    Button(onClick = { onCompare(selected[0], selected[1]) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.compare_run))
                    }
                }
            }
            when {
                list == null -> Unit
                list.isEmpty() -> Text(
                    stringResource(if (query.isBlank()) R.string.history_empty else R.string.history_no_result),
                    modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge,
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { e ->
                        ReadingCard(e, maskIds, onOpen = {
                            if (selectMode) {
                                selected = if (e.id in selected) selected - e.id else (selected + e.id).takeLast(2)
                            } else onOpen(e.id)
                        },
                            onRename = { renameTarget = e.id; renameText = e.name },
                            onDelete = { deleteTarget = e.id })
                    }
                }
            }
        }
    }

    deleteTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_reading_title)) },
            text = { Text(stringResource(R.string.delete_reading_body)) },
            confirmButton = { TextButton(onClick = { vm.delete(id); deleteTarget = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    renameTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(renameText, { renameText = it.take(80) }, singleLine = true, label = { Text(stringResource(R.string.report_reading_name)) }) },
            confirmButton = { TextButton(onClick = { vm.rename(id, renameText); renameTarget = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.clear_all_title)) },
            text = { Text(stringResource(R.string.clear_all_body)) },
            confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmAll = false }) { Text(stringResource(R.string.clear_all)) } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ReadingCard(e: ReadingEntity, maskIds: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(e.name.ifBlank { stringResource(R.string.report_unnamed) }, style = MaterialTheme.typography.titleMedium)
                Text(e.chipType, style = MaterialTheme.typography.bodyMedium)
                e.uid?.let { Text(if (maskIds) Mask.uid(it) else it, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
                Text(DateFormat.getDateTimeInstance().format(Date(e.scannedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRename) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
        }
    }
}
