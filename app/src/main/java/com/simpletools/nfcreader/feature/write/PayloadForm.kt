package com.simpletools.nfcreader.feature.write

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.core.templates.Template
import com.simpletools.nfcreader.core.templates.TemplateRepository
import com.simpletools.nfcreader.core.write.WritePayload

val PAYLOAD_KINDS = listOf(
    TemplateRepository.KIND_TEXT, TemplateRepository.KIND_URL, TemplateRepository.KIND_CONTACT, TemplateRepository.KIND_WIFI,
)

fun kindLabelRes(kind: String): Int = when (kind) {
    TemplateRepository.KIND_URL -> R.string.kind_url_write
    TemplateRepository.KIND_CONTACT -> R.string.kind_contact
    TemplateRepository.KIND_WIFI -> R.string.kind_wifi
    else -> R.string.kind_text_write
}

/** Construit le contenu écrivable à partir de la saisie. Le mot de passe Wi-Fi n'est jamais stocké. */
fun buildPayload(kind: String, fields: Map<String, String>, wifiPassword: String): WritePayload? =
    TemplateRepository.toPayload(Template(0, "", "", kind, fields), wifiPassword)

class PayloadState {
    var kind by mutableStateOf(TemplateRepository.KIND_TEXT)
    val fields: SnapshotStateMap<String, String> = mutableStateMapOf()
    var wifiPassword by mutableStateOf("")
}

@Composable
fun PayloadForm(state: PayloadState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PAYLOAD_KINDS.forEach { k ->
                FilterChip(selected = state.kind == k, onClick = { state.kind = k },
                    label = { Text(stringResource(kindLabelRes(k))) })
            }
        }
        @Composable fun field(key: String, label: Int, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true) {
            OutlinedTextField(
                value = state.fields[key].orEmpty(),
                onValueChange = { state.fields[key] = it },
                label = { Text(stringResource(label)) },
                singleLine = singleLine,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when (state.kind) {
            TemplateRepository.KIND_TEXT -> {
                field("text", R.string.field_text, singleLine = false)
            }
            TemplateRepository.KIND_URL -> field("url", R.string.field_url, KeyboardType.Uri)
            TemplateRepository.KIND_CONTACT -> {
                field("firstName", R.string.field_first_name)
                field("lastName", R.string.field_last_name)
                field("phone", R.string.field_phone, KeyboardType.Phone)
                field("email", R.string.field_email, KeyboardType.Email)
                field("organization", R.string.field_organization)
            }
            TemplateRepository.KIND_WIFI -> {
                field("ssid", R.string.field_ssid)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = state.fields["open"] == "true", onCheckedChange = { state.fields["open"] = it.toString() })
                    Text(stringResource(R.string.field_open_network), modifier = Modifier.padding(start = 8.dp))
                }
                if (state.fields["open"] != "true") {
                    OutlinedTextField(
                        value = state.wifiPassword,
                        onValueChange = { state.wifiPassword = it },
                        label = { Text(stringResource(R.string.field_wifi_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(stringResource(R.string.wifi_limits), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
