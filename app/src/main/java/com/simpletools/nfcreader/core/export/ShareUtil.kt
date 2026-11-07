package com.simpletools.nfcreader.core.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider

/** Partage d'un fichier généré via FileProvider (accès temporaire en lecture pour le destinataire). */
object ShareUtil {
    fun shareFile(context: Context, file: java.io.File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Écrit un texte (ex. CSV d'inventaire) et le partage. */
    fun shareText(context: Context, exporter: ExportService, baseName: String, text: String, mime: String) {
        val ext = if (mime == "text/csv") "csv" else "txt"
        shareFile(context, exporter.writeTextFile(baseName, ext, text), mime, baseName)
    }
}
