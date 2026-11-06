package com.simpletools.nfcreader.core.nfc

import com.simpletools.nfcreader.core.models.NdefRecordInfo
import com.simpletools.nfcreader.core.models.NdefRecordKind

/** Enregistrement NDEF brut (indépendant d'android.nfc pour rester testable en JVM). */
class RawNdefRecord(val tnf: Int, val type: ByteArray, val id: ByteArray, val payload: ByteArray) {
    companion object {
        const val TNF_EMPTY = 0
        const val TNF_WELL_KNOWN = 1
        const val TNF_MIME_MEDIA = 2
        const val TNF_ABSOLUTE_URI = 3
        const val TNF_EXTERNAL_TYPE = 4
        const val TNF_UNKNOWN = 5
    }
}

class NdefFormatException(message: String) : Exception(message)

/** Décodage NDEF en Kotlin pur. Ne lève jamais pour du contenu inattendu : renvoie un enregistrement UNKNOWN. */
object NdefParser {
    private const val MAX_DEPTH = 3
    private const val MAX_MIME_TEXT = 2000

    private val URI_PREFIXES = arrayOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
        "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://",
        "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:",
        "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://",
        "file://", "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
    )

    fun parse(records: List<RawNdefRecord>, depth: Int = 0): List<NdefRecordInfo> =
        records.map { parseRecord(it, depth) }

    /** Décode un message NDEF sérialisé (utilisé pour les Smart Posters imbriqués). */
    fun parseMessageBytes(bytes: ByteArray): List<RawNdefRecord> {
        val out = mutableListOf<RawNdefRecord>()
        var i = 0
        while (i < bytes.size) {
            val header = bytes[i++].toInt() and 0xFF
            val shortRecord = header and 0x10 != 0
            val hasId = header and 0x08 != 0
            val tnf = header and 0x07
            if (i >= bytes.size) throw NdefFormatException("truncated")
            val typeLen = bytes[i++].toInt() and 0xFF
            val payloadLen: Int
            if (shortRecord) {
                if (i >= bytes.size) throw NdefFormatException("truncated")
                payloadLen = bytes[i++].toInt() and 0xFF
            } else {
                if (i + 4 > bytes.size) throw NdefFormatException("truncated")
                val l = ((bytes[i].toLong() and 0xFF) shl 24) or ((bytes[i + 1].toLong() and 0xFF) shl 16) or
                    ((bytes[i + 2].toLong() and 0xFF) shl 8) or (bytes[i + 3].toLong() and 0xFF)
                i += 4
                if (l > bytes.size) throw NdefFormatException("length")
                payloadLen = l.toInt()
            }
            var idLen = 0
            if (hasId) {
                if (i >= bytes.size) throw NdefFormatException("truncated")
                idLen = bytes[i++].toInt() and 0xFF
            }
            if (i.toLong() + typeLen + idLen + payloadLen > bytes.size) throw NdefFormatException("truncated")
            val type = bytes.copyOfRange(i, i + typeLen); i += typeLen
            val id = bytes.copyOfRange(i, i + idLen); i += idLen
            val payload = bytes.copyOfRange(i, i + payloadLen); i += payloadLen
            out += RawNdefRecord(tnf, type, id, payload)
            if (header and 0x40 != 0) break // ME
        }
        return out
    }

    private fun parseRecord(r: RawNdefRecord, depth: Int): NdefRecordInfo {
        val typeStr = r.type.toString(Charsets.US_ASCII).sanitizeUntrusted(120)
        return when (r.tnf) {
            RawNdefRecord.TNF_EMPTY -> NdefRecordInfo(NdefRecordKind.EMPTY, "", 0)
            RawNdefRecord.TNF_WELL_KNOWN -> parseWellKnown(r, typeStr, depth)
            RawNdefRecord.TNF_MIME_MEDIA -> parseMime(r, typeStr)
            RawNdefRecord.TNF_ABSOLUTE_URI -> NdefRecordInfo(
                NdefRecordKind.URI, "URI", r.payload.size,
                uri = r.type.toString(Charsets.UTF_8).sanitizeUntrusted(),
            )
            RawNdefRecord.TNF_EXTERNAL_TYPE -> NdefRecordInfo(
                NdefRecordKind.EXTERNAL, typeStr, r.payload.size, payloadHex = r.payload.toHexPreview(),
            )
            else -> unknown(r, typeStr)
        }
    }

    private fun unknown(r: RawNdefRecord, typeStr: String) = NdefRecordInfo(
        NdefRecordKind.UNKNOWN, typeStr, r.payload.size, payloadHex = r.payload.toHexPreview(),
    )

    private fun parseWellKnown(r: RawNdefRecord, typeStr: String, depth: Int): NdefRecordInfo = when (typeStr) {
        "T" -> parseText(r) ?: unknown(r, typeStr)
        "U" -> parseUri(r) ?: unknown(r, typeStr)
        "Sp" -> {
            val children = if (depth >= MAX_DEPTH) emptyList() else try {
                parse(parseMessageBytes(r.payload), depth + 1)
            } catch (e: NdefFormatException) {
                emptyList()
            }
            NdefRecordInfo(NdefRecordKind.SMART_POSTER, typeStr, r.payload.size, children = children,
                payloadHex = if (children.isEmpty()) r.payload.toHexPreview() else null)
        }
        else -> NdefRecordInfo(NdefRecordKind.WELL_KNOWN_OTHER, typeStr, r.payload.size, payloadHex = r.payload.toHexPreview())
    }

    private fun parseText(r: RawNdefRecord): NdefRecordInfo? {
        val p = r.payload
        if (p.isEmpty()) return null
        val status = p[0].toInt() and 0xFF
        val utf16 = status and 0x80 != 0
        val langLen = status and 0x3F
        if (1 + langLen > p.size) return null
        val lang = String(p, 1, langLen, Charsets.US_ASCII).sanitizeUntrusted(35)
        val charset = if (utf16) Charsets.UTF_16 else Charsets.UTF_8
        val text = String(p, 1 + langLen, p.size - 1 - langLen, charset).sanitizeUntrusted()
        return NdefRecordInfo(NdefRecordKind.TEXT, "T", p.size, text = text, language = lang)
    }

    private fun parseUri(r: RawNdefRecord): NdefRecordInfo? {
        val p = r.payload
        if (p.isEmpty()) return null
        val code = p[0].toInt() and 0xFF
        val prefix = URI_PREFIXES.getOrNull(code) ?: return null
        val rest = String(p, 1, p.size - 1, Charsets.UTF_8)
        return NdefRecordInfo(NdefRecordKind.URI, "U", p.size, uri = (prefix + rest).sanitizeUntrusted())
    }

    private fun parseMime(r: RawNdefRecord, mime: String): NdefRecordInfo {
        val isText = mime.startsWith("text/") || mime == "application/json" || mime == "application/xml"
        return NdefRecordInfo(
            NdefRecordKind.MIME, mime, r.payload.size, mimeType = mime,
            text = if (isText) String(r.payload, Charsets.UTF_8).sanitizeUntrusted(MAX_MIME_TEXT) else null,
            payloadHex = if (isText) null else r.payload.toHexPreview(),
        )
    }
}
