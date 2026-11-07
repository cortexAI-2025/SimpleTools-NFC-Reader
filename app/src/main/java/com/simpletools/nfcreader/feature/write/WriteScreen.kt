package com.simpletools.nfcreader.feature.write

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.simpletools.nfcreader.AppContainer
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.core.nfc.NfcAvailability
import com.simpletools.nfcreader.feature.write.WriteSessionViewModel
import com.simpletools.nfcreader.core.write.WriteErrorKind
import com.simpletools.nfcreader.core.write.WriteOutcome
import com.simpletools.nfcreader.core.write.WritePayload
import com.simpletools.nfcreader.core.write.WriteIssue
import com.simpletools.nfcreader.ui.components.AppScaffold
import com.simpletools.nfcreader.ui.components.InfoRow
import com.simpletools.nfcreader.ui.components.NfcListener
import com.simpletools.nfcreader.ui.components.NoticeCard
import com.simpletools.nfcreader.ui.components.ProLockedCard
import com.simpletools.nfcreader.ui.components.ScreenColumn
import com.simpletools.nfcreader.ui.components.SectionCard

/**
 * Écran d'écriture PRO. Deux modes : contenu unique, ou série (liste de contenus, un tag à la fois).
 * Les modèles peuvent être chargés via [initialKind]/[initialFields] (route « Utiliser ce modèle »).
 */
@Composable
fun WriteScreen(
    container: AppContainer,
    isPro: Boolean,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
    initialTemplateId: Long? = null,
) {
    AppScaffold(stringResource(R.string.write_title), onBack) {
        ScreenColumn {
            if (!isPro) {
                ProLockedCard(stringResource(R.string.write_title), onUnlock)
                return@ScreenColumn
            }
            WriteContent(container, onUnlock, initialTemplateId)
        }
    }
}

@Composable
private fun WriteContent(container: AppContainer, onUnlock: () -> Unit, initialTemplateId: Long?) {
    val form = remember { PayloadState() }
    val queue = remember { mutableStateListOf<WritePayload>() }
    var sessionId by rememberSaveable { mutableStateOf(0) }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var templateLoaded by rememberSaveable { mutableStateOf(false) }
    val nfcStatus = container.nfc.status()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(initialTemplateId) {
        if (initialTemplateId != null && !templateLoaded) {
            container.templates.get(initialTemplateId)?.let { t ->
                form.kind = t.kind
                form.fields.clear(); form.fields.putAll(t.fields)
            }
            templateLoaded = true
        }
    }

    if (sessionId == 0) {
        // Édition du contenu
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (nfcStatus != NfcAvailability.ENABLED) {
                NoticeCard(
                    title = stringResource(if (nfcStatus == NfcAvailability.ABSENT) R.string.nfc_absent_title else R.string.nfc_disabled_title),
                    text = stringResource(if (nfcStatus == NfcAvailability.ABSENT) R.string.nfc_absent_body else R.string.nfc_disabled_body),
                    isError = true,
                )
            }
            SectionCard(stringResource(R.string.write_content_title)) {
                PayloadForm(form)
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val p = buildPayload(form.kind, form.fields.toMap(), form.wifiPassword)
                        val issues = p?.let { WritePayload.validate(it) }.orEmpty()
                        if (p == null || issues.isNotEmpty()) {
                            formError = issues.joinToString(" ") { context.getString(issueRes(it)) }.ifEmpty { context.getString(R.string.issue_invalid) }
                        } else {
                            formError = null
                            queue.clear(); queue.add(p); sessionId++
                        }
                    }, enabled = nfcStatus == NfcAvailability.ENABLED, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.write_now))
                    }
                    OutlinedButton(onClick = {
                        val p = buildPayload(form.kind, form.fields.toMap(), form.wifiPassword)
                        val issues = p?.let { WritePayload.validate(it) }.orEmpty()
                        if (p == null || issues.isNotEmpty()) {
                            formError = issues.joinToString(" ") { context.getString(issueRes(it)) }.ifEmpty { context.getString(R.string.issue_invalid) }
                        } else {
                            formError = null
                            queue.add(p)
                        }
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.add_to_batch)) }
                }
            }
            if (queue.isNotEmpty()) {
                SectionCard(stringResource(R.string.batch_title, queue.size)) {
                    queue.forEachIndexed { i, p ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("${i + 1}. " + WritePayload.summary(p), modifier = Modifier.weight(1f))
                            TextButton(onClick = { queue.removeAt(i) }) { Text(stringResource(R.string.remove)) }
                        }
                    }
                    Button(onClick = { sessionId++ }, enabled = nfcStatus == NfcAvailability.ENABLED && queue.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.start_batch))
                    }
                }
            }
            SectionCard(stringResource(R.string.write_limits_title)) {
                Text(stringResource(R.string.write_limits_body))
            }
        }
    } else {
        val items = remember(sessionId) { queue.toList() }
        val vm: WriteSessionViewModel = viewModel(key = "write-$sessionId", factory = viewModelFactory {
            initializer { WriteSessionViewModel(items, container.writeEngine, isPro = { container.license.state.value.isPro }) }
        })
        WriteSession(container, vm, onUnlock, onFinish = { sessionId = 0; queue.clear() })
    }
}

@Composable
private fun WriteSession(container: AppContainer, vm: WriteSessionViewModel, onUnlock: () -> Unit, onFinish: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var confirmVisible by remember { mutableStateOf(false) }
    NfcListener(active = ui.phase == SessionPhase.AWAITING_TAG || ui.phase == SessionPhase.CONFIRM,
        controller = container.nfc) { tag -> vm.onTag(tag) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (ui.phase) {
            SessionPhase.LOCKED -> ProLockedCard(stringResource(R.string.write_title), onUnlock)
            SessionPhase.AWAITING_TAG -> SectionCard(stringResource(R.string.write_step_tag, ui.index + 1, ui.total)) {
                ui.current?.let { InfoRow(stringResource(R.string.write_content_label), WritePayload.summary(it)) }
                Text(stringResource(R.string.write_approach), style = MaterialTheme.typography.bodyMedium)
                if (ui.tagLostHint) Text(stringResource(R.string.write_tag_lost), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = vm::skip) { Text(stringResource(R.string.skip_tag)) }
            }
            SessionPhase.CONFIRM -> SectionCard(stringResource(R.string.write_confirm_title)) {
                ui.current?.let { InfoRow(stringResource(R.string.write_content_label), WritePayload.summary(it)) }
                if (ui.replacesExisting) {
                    NoticeCard(title = stringResource(R.string.overwrite_title),
                        text = stringResource(R.string.overwrite_body, ui.existingDescription.ifBlank { "—" }), isError = true)
                }
                Text(stringResource(R.string.write_confirm_body), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { confirmVisible = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(if (ui.replacesExisting) R.string.overwrite_confirm else R.string.write_confirm_button))
                    }
                    OutlinedButton(onClick = vm::cancelConfirm, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                }
            }
            SessionPhase.WRITING -> Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(stringResource(R.string.write_in_progress), style = MaterialTheme.typography.bodyMedium)
            }
            SessionPhase.FINISHED -> SectionCard(stringResource(R.string.write_report_title)) {
                ui.results.forEach { r ->
                    Text("${r.index + 1}. ${r.summary} — " + resultLabel(r.outcome, r.skipped), style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
            }
        }
    }

    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { confirmVisible = false },
            title = { Text(stringResource(R.string.write_last_check_title)) },
            text = { Text(stringResource(R.string.write_last_check_body)) },
            confirmButton = { TextButton(onClick = { confirmVisible = false; vm.confirm() }) { Text(stringResource(R.string.write_confirm_button)) } },
            dismissButton = { TextButton(onClick = { confirmVisible = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun resultLabel(outcome: WriteOutcome?, skipped: Boolean): String {
    if (skipped) return stringResource(R.string.result_skipped)
    return when (outcome) {
        is WriteOutcome.Success -> stringResource(if (outcome.verified) R.string.result_ok_verified else R.string.result_ok)
        is WriteOutcome.Refused -> stringResource(errorRes(outcome.kind))
        is WriteOutcome.Failed -> stringResource(errorRes(outcome.kind))
        null -> "—"
    }
}

fun errorRes(kind: WriteErrorKind): Int = when (kind) {
    WriteErrorKind.TAG_LOST -> R.string.err_tag_lost
    WriteErrorKind.IO -> R.string.err_io
    WriteErrorKind.LOCKED -> R.string.err_locked
    WriteErrorKind.NOT_NDEF -> R.string.err_not_ndef
    WriteErrorKind.TOO_LARGE -> R.string.err_too_large
    WriteErrorKind.VERIFY_FAILED -> R.string.err_verify
    WriteErrorKind.FORMAT_FAILED -> R.string.err_format
    WriteErrorKind.INVALID_PAYLOAD -> R.string.issue_invalid
    WriteErrorKind.EXISTING_CONTENT -> R.string.overwrite_title
}

fun issueRes(i: WriteIssue): Int = when (i) {
    WriteIssue.EMPTY -> R.string.issue_empty
    WriteIssue.TOO_LONG -> R.string.issue_too_long
    WriteIssue.INVALID_LANGUAGE -> R.string.issue_language
    WriteIssue.INVALID_URL -> R.string.issue_url
    WriteIssue.INVALID_EMAIL -> R.string.issue_email
    WriteIssue.INVALID_PHONE -> R.string.issue_phone
    WriteIssue.INVALID_PASSWORD -> R.string.issue_password
}
