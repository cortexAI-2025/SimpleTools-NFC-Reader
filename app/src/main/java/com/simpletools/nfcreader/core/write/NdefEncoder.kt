package com.simpletools.nfcreader.core.write

import com.simpletools.nfcreader.core.nfc.RawNdefRecord

/** Sérialise des enregistrements NDEF (spécification NFC Forum, sans dépendance Android). */
object NdefEncoder {
    fun encode(records: List<RawNdefRecord>): ByteArray {
        require(records.isNotEmpty()) { "Un message NDEF doit contenir au moins un enregistrement." }
        val out = java.io.ByteArrayOutputStream()
        records.forEachIndexed { i, r ->
            val shortRecord = r.payload.size < 256
            var header = r.tnf and 0x07
            if (i == 0) header = header or 0x80   // MB
            if (i == records.lastIndex) header = header or 0x40   // ME
            if (shortRecord) header = header or 0x10                // SR
            if (r.id.isNotEmpty()) header = header or 0x08           // IL
            out.write(header)
            out.write(r.type.size)
            if (shortRecord) out.write(r.payload.size) else {
                val n = r.payload.size
                out.write(n ushr 24); out.write(n ushr 16); out.write(n ushr 8); out.write(n)
            }
            if (r.id.isNotEmpty()) out.write(r.id.size)
            out.write(r.type)
            if (r.id.isNotEmpty()) out.write(r.id)
            out.write(r.payload)
        }
        return out.toByteArray()
    }

    /** Compare deux messages sur le contenu (TNF, type, charge utile) et non sur les octets : Android peut réencoder. */
    fun sameContent(a: List<RawNdefRecord>, b: List<RawNdefRecord>): Boolean =
        a.size == b.size && a.indices.all { i ->
            a[i].tnf == b[i].tnf && a[i].type.contentEquals(b[i].type) && a[i].payload.contentEquals(b[i].payload)
        }
}
