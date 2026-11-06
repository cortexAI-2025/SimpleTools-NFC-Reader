package com.simpletools.nfcreader.core.export

import android.content.Context
import com.simpletools.nfcreader.core.models.TagSnapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Génère les fichiers d'export dans le cache privé ; le partage passe par FileProvider. */
class ExportService(private val context: Context, private val strings: StringProvider) {
    private val dir: File get() = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }

    fun jsonText(snapshot: TagSnapshot, name: String?, notes: String?, includeUid: Boolean): String =
        SnapshotJson.encode(snapshot, includeUid, name?.takeIf { it.isNotBlank() }, notes)

    fun createJsonFile(snapshot: TagSnapshot, name: String?, notes: String?, includeUid: Boolean): File =
        File(dir, fileName(snapshot, "json")).also { it.writeText(jsonText(snapshot, name, notes, includeUid)) }

    fun createPdfFile(snapshot: TagSnapshot, name: String?, notes: String?, includeUid: Boolean): File {
        val doc = ReportBuilder(strings).build(snapshot, name, notes, includeUid)
        return File(dir, fileName(snapshot, "pdf")).also { f -> f.outputStream().use { PdfReportWriter.write(doc, it) } }
    }

    /** Texte brut lisible pour le presse-papiers. */
    fun clipboardText(snapshot: TagSnapshot, name: String?, notes: String?, includeUid: Boolean): String {
        val doc = ReportBuilder(strings).build(snapshot, name, notes, includeUid)
        return buildString {
            doc.sections.forEach { s ->
                appendLine(s.heading)
                s.rows.forEach { appendLine("${it.label}: ${it.value}") }
                appendLine()
            }
            append(doc.disclaimer)
        }
    }

    /** Supprime tous les fichiers d'export temporaires. */
    fun clearExports() {
        File(context.cacheDir, EXPORT_DIR).deleteRecursively()
    }

    private fun fileName(s: TagSnapshot, ext: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(s.scannedAt))
        return "simpletools-nfc-$stamp.$ext"
    }

    companion object {
        const val EXPORT_DIR = "exports"
    }
}
