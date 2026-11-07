package com.simpletools.nfcreader.feature.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.simpletools.nfcreader.AppContainer
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.core.database.TemplateEntity
import com.simpletools.nfcreader.core.templates.Template
import com.simpletools.nfcreader.core.templates.TemplateRepository
import com.simpletools.nfcreader.feature.write.PayloadForm
import com.simpletools.nfcreader.feature.write.PayloadState
import com.simpletools.nfcreader.feature.write.buildPayload
import com.simpletools.nfcreader.ui.components.AppScaffold
import com.simpletools.nfcreader.ui.components.ProLockedCard
import com.simpletools.nfcreader.ui.components.ScreenColumn
import com.simpletools.nfcreader.ui.components.SectionCard
import com.simpletools.nfcreader.core.write.WritePayload
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/** Bibliothèque de modèles (PRO) : liste par catégorie, utilisation, duplication, suppression. */
@Composable
fun TemplatesScreen(
    container: AppContainer,
    isPro: Boolean,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
    onEdit: (Long?) -> Unit,
    onUse: (Long) -> Unit,
) {
    val list by container.templates.observe().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var toDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    AppScaffold(stringResource(R.string.templates_title), onBack) {
        if (!isPro) {
            ScreenColumn { ProLockedCard(stringResource(R.string.templates_title), onUnlock) }
            return@AppScaffold
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onEdit(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.template_new)) }
            if (list.isEmpty()) Text(stringResource(R.string.templates_empty), style = MaterialTheme.typography.bodyMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { t ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp).clickable { onEdit(t.id) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(t.category + " · " + stringResource(kindName(t.kind)), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { onUse(t.id) }) { Text(stringResource(R.string.template_use)) }
                            OutlinedButton(onClick = { scope.launch { container.templates.duplicate(t.id) } }) { Text(stringResource(R.string.template_duplicate)) }
                            TextButton(onClick = { toDelete = t.id }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.template_delete_title)) },
            text = { Text(stringResource(R.string.delete_reading_body)) },
            confirmButton = { TextButton(onClick = { toDelete = null; scope.launch { container.templates.delete(id) } }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Création ou modification d'un modèle (PRO). Le mot de passe Wi-Fi n'est jamais enregistré. */
@Composable
fun TemplateEditorScreen(container: AppContainer, templateId: Long?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val form = remember { PayloadState() }
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(templateId == null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val invalidMsg = stringResource(R.string.issue_invalid)

    LaunchedEffect(templateId) {
        if (!loaded && templateId != null) {
            container.templates.get(templateId)?.let { t ->
                name = t.name; category = t.category; form.kind = t.kind
                form.fields.clear(); form.fields.putAll(t.fields)
            }
            loaded = true
        }
    }

    AppScaffold(stringResource(R.string.template_editor_title), onBack) {
        ScreenColumn {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.template_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(category, { category = it.take(40) }, label = { Text(stringResource(R.string.template_category)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PayloadForm(form)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                // Le modèle est valable si son contenu s'encode ; le mot de passe n'est jamais requis ni stocké.
                val payload: WritePayload? = buildPayload(form.kind, form.fields.toMap(), "")
                val issues = payload?.let { WritePayload.validate(it) }.orEmpty()
                val isWifi = form.kind == TemplateRepository.KIND_WIFI
                val ok = payload != null && (issues.isEmpty() || (isWifi && issues.all { it == com.simpletools.nfcreader.core.write.WriteIssue.INVALID_PASSWORD }))
                if (!ok) {
                    error = invalidMsg
                } else {
                    error = null
                    scope.launch {
                        container.templates.save(templateId, name, category, form.fields.toMap(), form.kind)
                        onBack()
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        }
    }
}

fun kindName(kind: String): Int = when (kind) {
    TemplateRepository.KIND_URL -> R.string.kind_url_write
    TemplateRepository.KIND_CONTACT -> R.string.kind_contact
    TemplateRepository.KIND_WIFI -> R.string.kind_wifi
    else -> R.string.kind_text_write
}
