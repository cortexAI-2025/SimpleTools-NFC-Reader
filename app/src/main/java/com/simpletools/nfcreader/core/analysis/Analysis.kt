package com.simpletools.nfcreader.core.analysis

import com.simpletools.nfcreader.core.models.NdefRecordInfo
import com.simpletools.nfcreader.core.models.TagSnapshot
import com.simpletools.nfcreader.core.nfc.toHex

/** Vue hexadécimale des données accessibles (PRO). Jamais de données absentes de la lecture. */
object HexView {
    /** Affiche la charge utile par lignes de 16 octets : offset, hexa, ASCII imprimable. */
    fun dump(bytes: ByteArray, maxBytes: Int = 4096): String = buildString {
        val shown = bytes.copyOf(minOf(bytes.size, maxBytes))
        shown.toList().chunked(16).forEachIndexed { line, chunk ->
            append("%04X  ".format(line * 16))
            append(chunk.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }.padEnd(47))
            append("  ")
            append(chunk.joinToString("") { b -> val c = b.toInt() and 0xFF; if (c in 0x20..0x7E) c.toChar().toString() else "." })
            append('\n')
        }
        if (bytes.size > maxBytes) append("… ${bytes.size - maxBytes} octets non affichés")
    }

    fun recordsDump(records: List<NdefRecordInfo>): String = records.flatMapIndexed { i, r ->
        listOf("#${i + 1} ${r.typeName} (${r.payloadSize} o)") + (r.payloadHex?.let { listOf(it) } ?: emptyList())
    }.joinToString("\n")

    fun bytesOf(hex: String): ByteArray = hex.split(' ').filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()
    fun toHexString(bytes: ByteArray) = bytes.toHex(" ")
}

/** Comparaison de deux lectures : différences de champs visibles. */
data class FieldDiff(val field: String, val left: String, val right: String) {
    val same: Boolean get() = left == right
}

object ReadingComparator {
    fun compare(a: TagSnapshot, b: TagSnapshot): List<FieldDiff> = listOf(
        FieldDiff("Technologie", a.family.name, b.family.name),
        FieldDiff("Type NFC", a.nfcType.orEmpty(), b.nfcType.orEmpty()),
        FieldDiff("UID", a.uidHex.orEmpty(), b.uidHex.orEmpty()),
        FieldDiff("Technologies Android", a.technologies.joinToString(), b.technologies.joinToString()),
        FieldDiff("Capacité NDEF", a.ndef?.maxSize?.toString().orEmpty(), b.ndef?.maxSize?.toString().orEmpty()),
        FieldDiff("Lecture seule", a.ndef?.isWritable?.let { if (it) "non" else "oui" }.orEmpty(),
            b.ndef?.isWritable?.let { if (it) "non" else "oui" }.orEmpty()),
        FieldDiff("Enregistrements NDEF", a.ndefRecords.joinToString(" | ") { it.text ?: it.uri ?: it.mimeType ?: it.typeName },
            b.ndefRecords.joinToString(" | ") { it.text ?: it.uri ?: it.mimeType ?: it.typeName }),
    )

    /** Types MIME présents, pour l'analyse avancée. */
    fun mimeTypes(s: TagSnapshot): List<String> = s.ndefRecords.flatMap { listOfNotNull(it.mimeType) + it.children.mapNotNull { c -> c.mimeType } }.distinct()
}

/** Export CSV RFC 4180 : échappement des guillemets, séparateurs et retours à la ligne. */
object Csv {
    fun row(values: List<String>): String = values.joinToString(",") { escape(it) }

    fun escape(v: String): String {
        // Protège contre l'injection de formules dans les tableurs (contenu de puce non fiable).
        val safe = if (v.isNotEmpty() && v[0] in "=+-@\t\r") "'$v" else v
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun document(header: List<String>, rows: List<List<String>>): String =
        (listOf(row(header)) + rows.map { row(it) }).joinToString("\r\n", postfix = "\r\n")
}
