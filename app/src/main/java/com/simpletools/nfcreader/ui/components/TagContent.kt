package com.simpletools.nfcreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.core.export.ReportBuilder
import com.simpletools.nfcreader.core.models.NdefRecordInfo
import com.simpletools.nfcreader.core.models.NdefRecordKind
import com.simpletools.nfcreader.core.models.TagSnapshot
import com.simpletools.nfcreader.core.nfc.Mask
import com.simpletools.nfcreader.core.nfc.UrlSafety
import java.text.DateFormat
import java.util.Date

/** Affichage technique complet d'une puce. Tout le contenu provient de la puce : texte brut uniquement. */
@Composable
fun TagContent(snapshot: TagSnapshot, maskIds: Boolean, onOpenUrl: (String) -> Unit, isPro: Boolean = false, onUnlock: () -> Unit = {}) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val hide = maskIds && !revealed
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (snapshot.issues.isNotEmpty()) {
            val issueTexts = snapshot.issues.map { stringResource(ReportBuilder.issueRes(it)) }
            NoticeCard(
                title = stringResource(R.string.partial_read_title),
                text = issueTexts.joinToString("\n"),
                isError = true,
            )
        }
        SectionCard(stringResource(R.string.section_general)) {
            InfoRow(stringResource(R.string.report_technology), snapshot.family.label ?: stringResource(R.string.unknown_technology))
            val hex = snapshot.uidHex
            if (hex != null) {
                InfoRow(stringResource(R.string.uid_hex), if (hide) Mask.uid(hex) else hex, mono = true)
                snapshot.uidDecimal?.let { InfoRow(stringResource(R.string.uid_decimal), if (hide) "••••" else it, mono = true) }
                snapshot.uidDecimalReversed?.let { InfoRow(stringResource(R.string.uid_decimal_reversed), if (hide) "••••" else it, mono = true) }
                if (maskIds) {
                    TextButton(onClick = { revealed = !revealed }) {
                        Text(stringResource(if (revealed) R.string.mask_uid else R.string.reveal_uid))
                    }
                }
            } else {
                InfoRow(stringResource(R.string.uid_hex), stringResource(R.string.uid_absent))
            }
            snapshot.uidNote?.let { Text(stringResource(ReportBuilder.uidNoteRes(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            InfoRow(stringResource(R.string.android_technologies), snapshot.technologies.joinToString(", ").ifEmpty { "—" })
            InfoRow(stringResource(R.string.scan_date), DateFormat.getDateTimeInstance().format(Date(snapshot.scannedAt)))
        }
        SectionCard(stringResource(R.string.section_technical)) {
            snapshot.nfcType?.let { InfoRow(stringResource(R.string.nfc_type), it) }
            if (snapshot.isoStandards.isNotEmpty()) InfoRow(stringResource(R.string.iso_standards), snapshot.isoStandards.joinToString("\n"))
            snapshot.memoryBytes?.let { InfoRow(stringResource(R.string.memory), stringResource(R.string.bytes_fmt, it)) }
            snapshot.ndef?.let { n ->
                n.maxSize?.let { InfoRow(stringResource(R.string.ndef_capacity), stringResource(R.string.bytes_fmt, it)) }
                n.usedSize?.let { InfoRow(stringResource(R.string.ndef_size), stringResource(R.string.bytes_fmt, it)) }
                n.isWritable?.let { InfoRow(stringResource(R.string.read_only_state), stringResource(if (it) R.string.writable else R.string.read_only)) }
            }
            snapshot.manufacturer?.let { InfoRow(stringResource(R.string.manufacturer_hint), it) }
            if (snapshot.ndefFormatable) InfoRow(stringResource(R.string.ndef_formatable_label), stringResource(R.string.ndef_formatable_value))
            snapshot.techDetails.forEach { InfoRow(it.label, it.value, mono = true) }
            if (snapshot.nfcType == null && snapshot.isoStandards.isEmpty() && snapshot.techDetails.isEmpty()) {
                Text(stringResource(R.string.no_technical_info), style = MaterialTheme.typography.bodyMedium)
            }
        }
        SectionCard(stringResource(R.string.section_hex)) {
            if (!isPro) {
                Text(stringResource(R.string.pro_locked_body), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onUnlock) { Text(stringResource(R.string.unlock_pro)) }
            } else {
                val flat = flattenRecords(snapshot.ndefRecords)
                if (flat.isEmpty()) Text(stringResource(R.string.no_ndef_content), style = MaterialTheme.typography.bodyMedium)
                flat.forEachIndexed { i, r ->
                    Text("#${i + 1} · ${r.typeName} · ${stringResource(R.string.bytes_fmt, r.payloadSize)}", style = MaterialTheme.typography.labelLarge)
                    SelectionContainer {
                        Text(r.rawHex ?: "—", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
                if (flat.any { it.rawHex?.endsWith("…") == true }) Text(stringResource(R.string.hex_truncated), style = MaterialTheme.typography.bodySmall)
            }
        }
        SectionCard(stringResource(R.string.section_content)) {
            val records = snapshot.ndefRecords
            if (records.isEmpty()) {
                Text(
                    stringResource(if (snapshot.hasNdefTechnology) R.string.no_ndef_content else R.string.no_ndef_tech),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                records.forEachIndexed { i, r -> RecordView(i + 1, r, onOpenUrl) }
            }
        }
    }
}

private fun flattenRecords(list: List<NdefRecordInfo>): List<NdefRecordInfo> = list.flatMap { listOf(it) + flattenRecords(it.children) }

@Composable
private fun RecordView(index: Int, r: NdefRecordInfo, onOpenUrl: (String) -> Unit) {
    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val kind = when (r.kind) {
            NdefRecordKind.TEXT -> R.string.kind_text
            NdefRecordKind.URI -> R.string.kind_url
            NdefRecordKind.MIME -> R.string.kind_mime
            NdefRecordKind.SMART_POSTER -> R.string.kind_smart_poster
            NdefRecordKind.EXTERNAL -> R.string.kind_external
            NdefRecordKind.EMPTY -> R.string.kind_empty
            else -> R.string.kind_other
        }
        Text("#$index · " + stringResource(kind) + " · " + stringResource(R.string.bytes_fmt, r.payloadSize), style = MaterialTheme.typography.labelLarge)
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                r.language?.takeIf { it.isNotEmpty() }?.let { Text("[$it]", style = MaterialTheme.typography.bodySmall) }
                r.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                r.uri?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                r.mimeType?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (r.kind != NdefRecordKind.TEXT && r.kind != NdefRecordKind.URI && r.mimeType == null && r.typeName.isNotEmpty()) {
                    Text(r.typeName, style = MaterialTheme.typography.bodyMedium)
                }
                r.payloadHex?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
            }
        }
        r.uri?.let { uri ->
            if (UrlSafety.parse(uri) != null) {
                OutlinedButton(onClick = { onOpenUrl(uri) }) { Text(stringResource(R.string.open_link_button)) }
            } else {
                Text(stringResource(R.string.link_not_openable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        r.children.forEachIndexed { i, c ->
            Column(Modifier.padding(start = 12.dp)) { RecordView(i + 1, c, onOpenUrl) }
        }
    }
}
