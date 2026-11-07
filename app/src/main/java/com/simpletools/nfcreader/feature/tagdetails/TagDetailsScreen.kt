package com.simpletools.nfcreader.feature.tagdetails

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.ui.components.AppScaffold
import com.simpletools.nfcreader.ui.components.InfoRow
import com.simpletools.nfcreader.ui.components.NoticeCard
import com.simpletools.nfcreader.ui.components.ScreenColumn
import com.simpletools.nfcreader.ui.components.TagContent
import com.simpletools.nfcreader.ui.components.rememberConfirmedUrlOpener
import kotlinx.coroutines.launch
import java.io.File

private enum class PendingAction { SAVE, COPY, PDF, JSON }

/** Actions PRO sur une lecture : export CSV et ajout à une collection d'inventaire. */
@Composable
private fun ProDetailsActions(
    container: com.simpletools.nfcreader.AppContainer,
    vm: TagDetailsViewModel,
    isPro: Boolean,
    onUnlock: () -> Unit,
) {
    var showInventory by rememberSaveable { mutableStateOf(false) }
    var showCsv by rememberSaveable { mutableStateOf(false) }
    if (!isPro) {
        OutlinedButton(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pro_action_locked)) }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { showCsv = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export_csv)) }
        OutlinedButton(onClick = { showInventory = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.add_to_inventory)) }
    }
    if (showCsv) {
        var includeUid by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCsv = false },
            title = { Text(stringResource(R.string.export_csv)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeUid, { includeUid = it })
                    Text(stringResource(R.string.include_uid))
                }
            },
            confirmButton = { TextButton(onClick = { showCsv = false; vm.exportCsv(includeUid) }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { showCsv = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showInventory) {
        val collections by container.inventory.observeCollections().collectAsState(initial = emptyList())
        var category by rememberSaveable { mutableStateOf("") }
        var includeUid by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showInventory = false },
            title = { Text(stringResource(R.string.add_to_inventory)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (collections.isEmpty()) Text(stringResource(R.string.inventory_create_first))
                    collections.forEach { c ->
                        TextButton(onClick = {
                            showInventory = false
                            vm.addToInventory(c.id, category.ifBlank { "Général" }, includeUid)
                        }) { Text(c.name) }
                    }
                    OutlinedTextField(category, { category = it.take(40) }, singleLine = true,
                        label = { Text(stringResource(R.string.item_category)) })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(includeUid, { includeUid = it })
                        Text(stringResource(R.string.include_uid))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showInventory = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun TagDetailsScreen(
    container: com.simpletools.nfcreader.AppContainer,
    vm: TagDetailsViewModel,
    maskIds: Boolean,
    isPro: Boolean,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openUrl = rememberConfirmedUrlOpener()
    var pending by rememberSaveable { mutableStateOf<PendingAction?>(null) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    val savedMsg = stringResource(R.string.saved_ok)
    val failedMsg = stringResource(R.string.action_failed)
    val copiedMsg = stringResource(R.string.copied_ok)
    val shareTitle = stringResource(R.string.share_title)
    val inventoryMsg = stringResource(R.string.inventory_added)

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                DetailsEvent.Saved -> snackbar.showSnackbar(savedMsg)
                DetailsEvent.Deleted -> onBack()
                DetailsEvent.Failed -> snackbar.showSnackbar(failedMsg)
                DetailsEvent.InventoryAdded -> snackbar.showSnackbar(inventoryMsg)
                is DetailsEvent.Share -> shareFile(context, e.file, e.mime, shareTitle)
            }
        }
    }

    AppScaffold(stringResource(R.string.details_title), onBack, snackbar) {
        val snap = state.snapshot
        when {
            state.loading -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
            snap == null -> ScreenColumn { NoticeCard(stringResource(R.string.reading_not_found), isError = true) }
            else -> ScreenColumn {
                if (state.savedId != null) {
                    InfoRow(stringResource(R.string.report_reading_name), state.name.ifBlank { stringResource(R.string.report_unnamed) })
                    if (state.notes.isNotBlank()) InfoRow(stringResource(R.string.notes), state.notes)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showRename = true }) { Text(stringResource(R.string.rename)) }
                        OutlinedButton(onClick = { showDelete = true }) { Text(stringResource(R.string.delete)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { pending = PendingAction.COPY }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.copy)) }
                    if (state.savedId == null) {
                        Button(onClick = { pending = PendingAction.SAVE }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { pending = PendingAction.PDF }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export_pdf)) }
                    Button(onClick = { pending = PendingAction.JSON }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export_json)) }
                }
                ProDetailsActions(container, vm, isPro, onUnlock)
                TagContent(snap, maskIds, openUrl, isPro = isPro, onUnlock = onUnlock)
            }
        }
    }

    pending?.let { action ->
        OptionsDialog(
            action = action,
            defaultIncludeUid = !maskIds,
            onDismiss = { pending = null },
            onConfirm = { includeUid, name, notes ->
                pending = null
                when (action) {
                    PendingAction.SAVE -> vm.save(name, notes, includeUid)
                    PendingAction.COPY -> vm.clipboardText(includeUid)?.let {
                        copyToClipboard(context, it)
                        scope.launch { snackbar.showSnackbar(copiedMsg) }
                    }
                    PendingAction.PDF -> vm.exportPdf(includeUid)
                    PendingAction.JSON -> vm.exportJson(includeUid)
                }
            },
        )
    }
    if (showRename) {
        var text by rememberSaveable { mutableStateOf(state.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(text, { text = it.take(80) }, label = { Text(stringResource(R.string.report_reading_name)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(text); showRename = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_reading_title)) },
            text = { Text(stringResource(R.string.delete_reading_body)) },
            confirmButton = { TextButton(onClick = { showDelete = false; vm.delete() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun OptionsDialog(
    action: PendingAction,
    defaultIncludeUid: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (includeUid: Boolean, name: String, notes: String) -> Unit,
) {
    var includeUid by rememberSaveable { mutableStateOf(defaultIncludeUid) }
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(when (action) {
            PendingAction.SAVE -> R.string.save_title
            PendingAction.COPY -> R.string.copy_title
            PendingAction.PDF -> R.string.export_pdf
            PendingAction.JSON -> R.string.export_json
        })) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (action == PendingAction.SAVE) {
                    Text(stringResource(R.string.save_consent), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.report_reading_name)) }, singleLine = true)
                    OutlinedTextField(notes, { notes = it.take(500) }, label = { Text(stringResource(R.string.notes_optional)) }, minLines = 2)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeUid, { includeUid = it })
                    Text(stringResource(R.string.include_uid), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.uid_in_data_warning), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(includeUid, name, notes) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Copie dans le presse-papiers en marquant le contenu comme sensible (masque l'aperçu sur Android 13+). */
private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("SimpleTools NFC", text)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    }
    cm.setPrimaryClip(clip)
}

private fun shareFile(context: Context, file: File, mime: String, title: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
