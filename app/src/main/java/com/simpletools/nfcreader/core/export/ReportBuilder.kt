package com.simpletools.nfcreader.core.export

import androidx.annotation.StringRes
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.core.models.NdefRecordInfo
import com.simpletools.nfcreader.core.models.NdefRecordKind
import com.simpletools.nfcreader.core.models.ReadIssue
import com.simpletools.nfcreader.core.models.TagSnapshot
import com.simpletools.nfcreader.core.models.UidNote
import com.simpletools.nfcreader.core.nfc.sanitizeUntrusted
import java.text.DateFormat
import java.util.Date

fun interface StringProvider {
    fun get(@StringRes id: Int, vararg args: Any): String
}

data class ReportRow(val label: String, val value: String)
data class ReportSection(val heading: String, val rows: List<ReportRow>)
data class ReportDocument(val title: String, val subtitle: String, val sections: List<ReportSection>, val disclaimer: String)

/** Construit le contenu textuel du rapport (indépendant de PdfDocument donc testable en JVM). */
class ReportBuilder(private val s: StringProvider) {

    fun build(snapshot: TagSnapshot, name: String?, notes: String?, includeUid: Boolean): ReportDocument {
        val snap = if (includeUid) snapshot else snapshot.withoutUid()
        val date = DateFormat.getDateTimeInstance().format(Date(snap.scannedAt))
        val sections = mutableListOf<ReportSection>()

        val general = mutableListOf(
            ReportRow(s.get(R.string.report_reading_name), name?.takeIf { it.isNotBlank() } ?: s.get(R.string.report_unnamed)),
            ReportRow(s.get(R.string.report_date), date),
            ReportRow(s.get(R.string.report_technology), snap.family.label ?: s.get(R.string.unknown_technology)),
        )
        if (includeUid) {
            general += ReportRow(s.get(R.string.uid_hex), snap.uidHex ?: s.get(R.string.uid_absent))
            snap.uidDecimal?.let { general += ReportRow(s.get(R.string.uid_decimal), it) }
        } else {
            general += ReportRow(s.get(R.string.report_identifier), s.get(R.string.report_uid_excluded))
        }
        notes?.takeIf { it.isNotBlank() }?.let { general += ReportRow(s.get(R.string.notes), it.sanitizeUntrusted()) }
        sections += ReportSection(s.get(R.string.section_general), general)

        val tech = mutableListOf<ReportRow>()
        tech += ReportRow(s.get(R.string.android_technologies), snap.technologies.joinToString(", ").ifEmpty { "—" })
        snap.nfcType?.let { tech += ReportRow(s.get(R.string.nfc_type), it) }
        if (snap.isoStandards.isNotEmpty()) tech += ReportRow(s.get(R.string.iso_standards), snap.isoStandards.joinToString(", "))
        snap.memoryBytes?.let { tech += ReportRow(s.get(R.string.memory), s.get(R.string.bytes_fmt, it)) }
        snap.ndef?.let { n ->
            n.maxSize?.let { tech += ReportRow(s.get(R.string.ndef_capacity), s.get(R.string.bytes_fmt, it)) }
            n.usedSize?.let { tech += ReportRow(s.get(R.string.ndef_size), s.get(R.string.bytes_fmt, it)) }
            n.isWritable?.let { tech += ReportRow(s.get(R.string.read_only_state), s.get(if (it) R.string.writable else R.string.read_only)) }
        }
        snap.manufacturer?.let { tech += ReportRow(s.get(R.string.manufacturer_hint), it) }
        snap.techDetails.forEach { tech += ReportRow(it.label, it.value) }
        sections += ReportSection(s.get(R.string.section_technical), tech)

        val content = mutableListOf<ReportRow>()
        flatten(snap.ndefRecords).forEachIndexed { i, r -> content += ReportRow("#${i + 1} ${kindLabel(r)}", describe(r)) }
        if (content.isEmpty()) content += ReportRow("—", s.get(R.string.no_ndef_content))
        sections += ReportSection(s.get(R.string.public_data), content)

        if (snap.issues.isNotEmpty()) {
            sections += ReportSection(
                s.get(R.string.section_issues),
                snap.issues.map { ReportRow("•", s.get(issueRes(it))) },
            )
        }
        return ReportDocument(
            title = s.get(R.string.report_title),
            subtitle = s.get(R.string.app_name),
            sections = sections,
            disclaimer = s.get(R.string.report_disclaimer),
        )
    }

    private fun flatten(records: List<NdefRecordInfo>): List<NdefRecordInfo> =
        records.flatMap { listOf(it) + flatten(it.children) }

    private fun kindLabel(r: NdefRecordInfo) = when (r.kind) {
        NdefRecordKind.TEXT -> s.get(R.string.kind_text)
        NdefRecordKind.URI -> s.get(R.string.kind_url)
        NdefRecordKind.MIME -> s.get(R.string.kind_mime)
        NdefRecordKind.SMART_POSTER -> s.get(R.string.kind_smart_poster)
        NdefRecordKind.EXTERNAL -> s.get(R.string.kind_external)
        NdefRecordKind.EMPTY -> s.get(R.string.kind_empty)
        else -> s.get(R.string.kind_other)
    }

    private fun describe(r: NdefRecordInfo): String = when (r.kind) {
        NdefRecordKind.TEXT -> (r.language?.let { "[$it] " } ?: "") + r.text.orEmpty()
        NdefRecordKind.URI -> r.uri.orEmpty()
        NdefRecordKind.MIME -> "${r.mimeType.orEmpty()} (${r.payloadSize} B)" + (r.text?.let { "\n$it" } ?: r.payloadHex?.let { "\n$it" } ?: "")
        else -> "${r.typeName} (${r.payloadSize} B)" + (r.payloadHex?.let { "\n$it" } ?: "")
    }

    companion object {
        @StringRes
        fun issueRes(i: ReadIssue): Int = when (i) {
            ReadIssue.TAG_LOST -> R.string.issue_tag_lost
            ReadIssue.IO_ERROR -> R.string.issue_io
            ReadIssue.NDEF_MALFORMED -> R.string.issue_ndef_malformed
            ReadIssue.UNEXPECTED -> R.string.issue_unexpected
        }

        @StringRes
        fun uidNoteRes(n: UidNote): Int = when (n) {
            UidNote.ABSENT -> R.string.uid_note_absent
            UidNote.RANDOM_LIKELY -> R.string.uid_note_random
            UidNote.ALL_ZERO -> R.string.uid_note_zero
        }
    }
}
