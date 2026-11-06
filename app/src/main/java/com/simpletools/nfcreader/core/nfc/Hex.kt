package com.simpletools.nfcreader.core.nfc

import java.math.BigInteger

fun ByteArray.toHex(separator: String = ":"): String =
    joinToString(separator) { "%02X".format(it.toInt() and 0xFF) }

/** Version compacte, tronquée à [maxBytes] octets (suffixe « … » si coupée). */
fun ByteArray.toHexPreview(maxBytes: Int = 64): String {
    val head = if (size > maxBytes) copyOf(maxBytes) else this
    return head.toHex(" ") + if (size > maxBytes) " …" else ""
}

/** Valeur décimale non signée, octets lus en gros-boutiste. */
fun ByteArray.toUnsignedDecimal(): String = BigInteger(1, this).toString()

/** Nettoie un texte venant d'une puce : supprime les caractères de contrôle (hors \n et \t). */
fun String.sanitizeUntrusted(maxLength: Int = 2000): String {
    val cleaned = filter { it == '\n' || it == '\t' || !it.isISOControl() }
    return if (cleaned.length > maxLength) cleaned.take(maxLength) + "…" else cleaned
}

object Mask {
    /** Masque un UID hexadécimal « 04:A3:… » en conservant le premier et le dernier octet. */
    fun uid(hex: String): String {
        val parts = hex.split(":")
        if (parts.size <= 2) return parts.joinToString(":") { "••" }
        return parts.mapIndexed { i, p -> if (i == 0 || i == parts.lastIndex) p else "••" }.joinToString(":")
    }
}
