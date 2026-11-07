package com.simpletools.nfcreader.core.write

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.simpletools.nfcreader.core.nfc.NdefParser
import com.simpletools.nfcreader.core.nfc.RawNdefRecord
import java.io.IOException

/** Écriture réelle sur un tag Android. Toute opération passe par connect/close. */
class AndroidWriteOps(private val tag: Tag) : WriteOps {

    override fun inspect(): TagWriteInfo {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return withConnection(ndef) {
                val msg = try { ndef.ndefMessage } catch (e: FormatException) { null }
                TagWriteInfo(ndefSupported = true, formatable = false, writable = ndef.isWritable,
                    maxSize = ndef.maxSize, hasContent = msg != null)
            }
        }
        val formatable = NdefFormatable.get(tag)
        if (formatable != null) return TagWriteInfo(ndefSupported = false, formatable = true, writable = true, maxSize = 0, hasContent = false)
        return TagWriteInfo(ndefSupported = false, formatable = false, writable = false, maxSize = 0, hasContent = false)
    }

    override fun write(encoded: ByteArray) {
        val ndef = Ndef.get(tag) ?: throw WriteFailure(WriteErrorKind.NOT_NDEF)
        withConnectionOrThrow(ndef) {
            if (!ndef.isWritable) throw WriteFailure(WriteErrorKind.LOCKED)
            if (encoded.size > ndef.maxSize) throw WriteFailure(WriteErrorKind.TOO_LARGE)
            try {
                ndef.writeNdefMessage(NdefMessage(encoded))
            } catch (e: TagLostException) { throw WriteFailure(WriteErrorKind.TAG_LOST, cause = e) }
              catch (e: FormatException) { throw WriteFailure(WriteErrorKind.IO, cause = e) }
              catch (e: IOException) { throw WriteFailure(WriteErrorKind.IO, cause = e) }
        }
    }

    override fun format(encoded: ByteArray) {
        val formatable = NdefFormatable.get(tag) ?: throw WriteFailure(WriteErrorKind.NOT_NDEF)
        try {
            formatable.connect()
            formatable.format(NdefMessage(encoded))
        } catch (e: TagLostException) {
            throw WriteFailure(WriteErrorKind.TAG_LOST, cause = e)
        } catch (e: IOException) {
            throw WriteFailure(WriteErrorKind.FORMAT_FAILED, cause = e)
        } catch (e: FormatException) {
            throw WriteFailure(WriteErrorKind.FORMAT_FAILED, cause = e)
        } finally {
            runCatching { formatable.close() }
        }
    }

    override fun readRecords(): List<RawNdefRecord>? {
        val ndef = Ndef.get(tag) ?: throw WriteFailure(WriteErrorKind.NOT_NDEF)
        return withConnectionOrThrow(ndef) {
            val m = try { ndef.ndefMessage } catch (e: TagLostException) { throw WriteFailure(WriteErrorKind.TAG_LOST, cause = e) }
              catch (e: FormatException) { throw WriteFailure(WriteErrorKind.VERIFY_FAILED, cause = e) }
              catch (e: IOException) { throw WriteFailure(WriteErrorKind.IO, cause = e) }
            m?.records?.map { RawNdefRecord(it.tnf.toInt(), it.type, it.id, it.payload) }
        }
    }

    private inline fun <T> withConnection(ndef: Ndef, block: () -> T): T {
        try {
            if (!ndef.isConnected) ndef.connect()
            return block()
        } finally {
            runCatching { ndef.close() }
        }
    }

    private inline fun <T> withConnectionOrThrow(ndef: Ndef, block: () -> T): T {
        try {
            if (!ndef.isConnected) ndef.connect()
            return block()
        } catch (e: TagLostException) {
            throw WriteFailure(WriteErrorKind.TAG_LOST, cause = e)
        } catch (e: IOException) {
            throw WriteFailure(WriteErrorKind.IO, cause = e)
        } finally {
            runCatching { ndef.close() }
        }
    }
}
