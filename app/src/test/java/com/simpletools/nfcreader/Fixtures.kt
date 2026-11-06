package com.simpletools.nfcreader

import com.simpletools.nfcreader.core.nfc.AccessError
import com.simpletools.nfcreader.core.nfc.NdefSession
import com.simpletools.nfcreader.core.nfc.RawNdefRecord
import com.simpletools.nfcreader.core.nfc.TagAccess
import com.simpletools.nfcreader.core.nfc.TagAccessException
import com.simpletools.nfcreader.core.nfc.TechnologyInfo

/** Fixtures de TESTS UNITAIRES : ce sont des simulations, pas une validation matérielle. */
object Fixtures {
    const val NFCA = "android.nfc.tech.NfcA"
    const val NDEF = "android.nfc.tech.Ndef"
    const val ULTRALIGHT = "android.nfc.tech.MifareUltralight"
    const val CLASSIC = "android.nfc.tech.MifareClassic"
    const val NDEF_FORMATABLE = "android.nfc.tech.NdefFormatable"

    val uid7 = byteArrayOf(0x04, 0xA3.toByte(), 0x5B, 0x12, 0x7C, 0x80.toByte(), 0x5C)

    fun textRecord(text: String, lang: String = "fr"): RawNdefRecord {
        val l = lang.toByteArray(Charsets.US_ASCII)
        return RawNdefRecord(RawNdefRecord.TNF_WELL_KNOWN, "T".toByteArray(), ByteArray(0), byteArrayOf(l.size.toByte()) + l + text.toByteArray())
    }

    /** prefixCode 0x04 = « https:// ». */
    fun uriRecord(rest: String, prefixCode: Int = 0x04) =
        RawNdefRecord(RawNdefRecord.TNF_WELL_KNOWN, "U".toByteArray(), ByteArray(0), byteArrayOf(prefixCode.toByte()) + rest.toByteArray())

    fun mimeRecord(mime: String, payload: ByteArray) =
        RawNdefRecord(RawNdefRecord.TNF_MIME_MEDIA, mime.toByteArray(), ByteArray(0), payload)

    class FakeNdef(
        private val records: List<RawNdefRecord>? = emptyList(),
        private val failure: AccessError? = null,
        override val maxSize: Int = 504,
        override val isWritable: Boolean = true,
        override val typeName: String? = "org.nfcforum.ndef.type2",
    ) : NdefSession {
        var closed = false
        override val messageSize: Int? get() = records?.sumOf { it.payload.size + it.type.size + 3 }
        override fun canMakeReadOnly() = true
        override fun readMessage(): List<RawNdefRecord>? {
            failure?.let { throw TagAccessException(it) }
            return records
        }
        override fun close() { closed = true }
    }

    class FakeTag(
        override val id: ByteArray? = uid7,
        override val techList: List<String> = listOf(NFCA, NDEF),
        private val info: TechnologyInfo = TechnologyInfo(atqa = byteArrayOf(0x00, 0x44), sak = 0x00),
        private val infoFailure: AccessError? = null,
        val ndef: FakeNdef? = FakeNdef(),
    ) : TagAccess {
        override fun technologyInfo(): TechnologyInfo {
            infoFailure?.let { throw TagAccessException(it) }
            return info
        }
        override fun openNdef(): NdefSession? = ndef
    }

    fun ndefTag(vararg records: RawNdefRecord) = FakeTag(ndef = FakeNdef(records.toList()))
}
