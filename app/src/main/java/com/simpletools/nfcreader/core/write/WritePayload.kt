package com.simpletools.nfcreader.core.write

import com.simpletools.nfcreader.core.nfc.NdefParser
import com.simpletools.nfcreader.core.nfc.RawNdefRecord
import java.net.URI

/** Contenu à écrire. Chaque variante sait se valider et se transformer en enregistrements NDEF. */
sealed interface WritePayload {
    val kindLabelKey: String

    /** Texte NDEF (well-known « T », UTF-8). */
    data class Text(val text: String, val language: String = "fr") : WritePayload {
        override val kindLabelKey = "text"
        fun validate(): List<WriteIssue> = buildList {
            if (text.isBlank()) add(WriteIssue.EMPTY)
            if (text.length > MAX_TEXT) add(WriteIssue.TOO_LONG)
            if (language.isEmpty() || language.length > 10 || !language.all { it.isLetter() || it == '-' }) add(WriteIssue.INVALID_LANGUAGE)
        }
        fun toRecords(): List<RawNdefRecord> {
            val lang = language.toByteArray(Charsets.US_ASCII)
            val payload = byteArrayOf(lang.size.toByte()) + lang + text.toByteArray(Charsets.UTF_8)
            return listOf(RawNdefRecord(RawNdefRecord.TNF_WELL_KNOWN, "T".toByteArray(), ByteArray(0), payload))
        }
        companion object { const val MAX_TEXT = 1000 }
    }

    /** URL NDEF (well-known « U ») : seuls http et https sont acceptés. */
    data class Url(val url: String) : WritePayload {
        override val kindLabelKey = "url"
        fun validate(): List<WriteIssue> {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return listOf(WriteIssue.EMPTY)
            val uri = try { URI(trimmed) } catch (e: Exception) { return listOf(WriteIssue.INVALID_URL) }
            val scheme = uri.scheme?.lowercase()
            return buildList {
                if (scheme != "http" && scheme != "https") add(WriteIssue.INVALID_URL)
                if (uri.host.isNullOrBlank()) add(WriteIssue.INVALID_URL)
                if (trimmed.length > MAX_URL) add(WriteIssue.TOO_LONG)
            }
        }
        fun toRecords(): List<RawNdefRecord> {
            val trimmed = url.trim()
            // Préfixe le plus long possible (table NFC Forum) pour économiser la mémoire du tag.
            val prefixes = listOf("https://www." to 0x02, "http://www." to 0x01, "https://" to 0x04, "http://" to 0x03)
            val (rest, code) = prefixes.firstOrNull { trimmed.lowercase().startsWith(it.first) }
                ?.let { trimmed.substring(it.first.length) to it.second } ?: (trimmed to 0x00)
            return listOf(RawNdefRecord(RawNdefRecord.TNF_WELL_KNOWN, "U".toByteArray(), ByteArray(0),
                byteArrayOf(code.toByte()) + rest.toByteArray(Charsets.UTF_8)))
        }
        companion object { const val MAX_URL = 500 }
    }

    /** Coordonnées au format vCard 3.0 (MIME text/vcard). */
    data class Contact(
        val firstName: String,
        val lastName: String,
        val phone: String = "",
        val email: String = "",
        val organization: String = "",
    ) : WritePayload {
        override val kindLabelKey = "contact"
        fun validate(): List<WriteIssue> = buildList {
            if (firstName.isBlank() && lastName.isBlank()) add(WriteIssue.EMPTY)
            if (listOf(firstName, lastName, phone, email, organization).any { it.length > 100 }) add(WriteIssue.TOO_LONG)
            if (email.isNotEmpty() && !Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""").matches(email)) add(WriteIssue.INVALID_EMAIL)
            if (phone.isNotEmpty() && !Regex("""\+?[0-9 .()-]{4,25}""").matches(phone)) add(WriteIssue.INVALID_PHONE)
        }
        fun toVCard(): String = buildString {
            append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
            append("N:${escape(lastName)};${escape(firstName)};;;\r\n")
            append("FN:${escape("$firstName $lastName".trim())}\r\n")
            if (organization.isNotBlank()) append("ORG:${escape(organization)}\r\n")
            if (phone.isNotBlank()) append("TEL;TYPE=CELL:${escape(phone)}\r\n")
            if (email.isNotBlank()) append("EMAIL:${escape(email)}\r\n")
            append("END:VCARD\r\n")
        }
        fun toRecords(): List<RawNdefRecord> = listOf(
            RawNdefRecord(RawNdefRecord.TNF_MIME_MEDIA, "text/vcard".toByteArray(), ByteArray(0), toVCard().toByteArray(Charsets.UTF_8)),
        )
        private fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
    }

    /**
     * Configuration Wi-Fi au format WFA Wi-Fi Simple Configuration (MIME application/vnd.wfa.wsc).
     * Limité aux réseaux WPA2-Personal (AES) et aux réseaux ouverts.
     */
    data class Wifi(val ssid: String, val password: String, val open: Boolean = false) : WritePayload {
        override val kindLabelKey = "wifi"
        fun validate(): List<WriteIssue> = buildList {
            if (ssid.isEmpty()) add(WriteIssue.EMPTY)
            if (ssid.toByteArray(Charsets.UTF_8).size > 32) add(WriteIssue.TOO_LONG)
            if (!open && (password.length !in 8..63)) add(WriteIssue.INVALID_PASSWORD)
        }
        fun toRecords(): List<RawNdefRecord> {
            val credential = tlv(0x1026, byteArrayOf(0x01)) +                       // index réseau
                tlv(0x1045, ssid.toByteArray(Charsets.UTF_8)) +                    // SSID
                tlv(0x1003, if (open) be16(0x0001) else be16(0x0020)) +              // auth : ouvert / WPA2-PSK
                tlv(0x100F, if (open) be16(0x0001) else be16(0x0008)) +              // chiffrement : aucun / AES
                tlv(0x1027, if (open) ByteArray(0) else password.toByteArray(Charsets.UTF_8)) +
                tlv(0x1020, ByteArray(6) { 0xFF.toByte() })                         // MAC : toutes stations
            val payload = tlv(0x100E, credential)
            return listOf(RawNdefRecord(RawNdefRecord.TNF_MIME_MEDIA, "application/vnd.wfa.wsc".toByteArray(), ByteArray(0), payload))
        }
        private fun tlv(type: Int, value: ByteArray) = be16(type) + be16(value.size) + value
        private fun be16(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())
    }

    companion object {
        /** Taille du message encodé, utile pour contrôler la capacité du tag avant écriture. */
        fun encodedSize(p: WritePayload): Int = NdefEncoder.encode(records(p)).size

        fun records(p: WritePayload): List<RawNdefRecord> = when (p) {
            is Text -> p.toRecords()
            is Url -> p.toRecords()
            is Contact -> p.toRecords()
            is Wifi -> p.toRecords()
        }

        fun validate(p: WritePayload): List<WriteIssue> = when (p) {
            is Text -> p.validate()
            is Url -> p.validate()
            is Contact -> p.validate()
            is Wifi -> p.validate()
        }

        /** Résumé lisible pour l'écran de confirmation : aucune valeur secrète (mot de passe Wi-Fi masqué). */
        fun summary(p: WritePayload): String = when (p) {
            is Text -> p.text
            is Url -> p.url.trim()
            is Contact -> listOf(p.firstName, p.lastName, p.phone, p.email).filter { it.isNotBlank() }.joinToString(" · ")
            is Wifi -> "SSID « ${p.ssid} » · " + if (p.open) "ouvert" else "WPA2 (mot de passe masqué)"
        }

        /** Vérifie que les enregistrements s'analysent comme NDEF valides (garde-fou avant écriture). */
        fun selfCheck(records: List<RawNdefRecord>): Boolean {
            val bytes = NdefEncoder.encode(records)
            return runCatching { NdefParser.parseMessageBytes(bytes) }.getOrNull()?.size == records.size
        }
    }
}

enum class WriteIssue { EMPTY, TOO_LONG, INVALID_LANGUAGE, INVALID_URL, INVALID_EMAIL, INVALID_PHONE, INVALID_PASSWORD }
