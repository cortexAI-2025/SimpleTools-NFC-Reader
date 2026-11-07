package com.simpletools.nfcreader.core.write

import com.simpletools.nfcreader.core.nfc.NdefParser
import com.simpletools.nfcreader.core.nfc.RawNdefRecord

/** Opérations d'écriture bas niveau. L'implémentation Android est dans AndroidWriteOps. */
interface WriteOps {
    /** Le tag porte-t-il déjà un message NDEF ? (null = pas de NDEF, true/false = présent) */
    @Throws(WriteFailure::class) fun inspect(): TagWriteInfo
    /** Écrit le message NDEF encodé. */
    @Throws(WriteFailure::class) fun write(encoded: ByteArray)
    /** Formate un tag vierge NDEF-formatable et y écrit le message. */
    @Throws(WriteFailure::class) fun format(encoded: ByteArray)
    /** Relit les enregistrements présents sur le tag. */
    @Throws(WriteFailure::class) fun readRecords(): List<RawNdefRecord>?
}

data class TagWriteInfo(
    val ndefSupported: Boolean,
    val formatable: Boolean,
    val writable: Boolean,
    val maxSize: Int,
    val hasContent: Boolean,
)

enum class WriteErrorKind { TAG_LOST, IO, LOCKED, NOT_NDEF, TOO_LARGE, VERIFY_FAILED, FORMAT_FAILED, INVALID_PAYLOAD, EXISTING_CONTENT }

class WriteFailure(val kind: WriteErrorKind, message: String? = null, cause: Throwable? = null) : Exception(message, cause)

sealed interface WriteOutcome {
    data class Success(val bytesWritten: Int, val verified: Boolean) : WriteOutcome
    data class Refused(val kind: WriteErrorKind, val detail: String = "") : WriteOutcome
    /** Le message a peut-être été partiellement écrit : l'utilisateur doit recommencer. */
    data class Failed(val kind: WriteErrorKind, val detail: String = "") : WriteOutcome
}

/**
 * Écriture contrôlée : vérifie le verrouillage, la capacité et la compatibilité AVANT toute écriture,
 * puis relit le tag pour vérifier le résultat. L'appelant doit avoir obtenu la confirmation explicite.
 */
class WriteEngine {
    fun write(ops: WriteOps, payload: WritePayload, overwriteExisting: Boolean): WriteOutcome {
        if (WritePayload.validate(payload).isNotEmpty()) return WriteOutcome.Refused(WriteErrorKind.INVALID_PAYLOAD)
        val records = WritePayload.records(payload)
        if (!WritePayload.selfCheck(records)) return WriteOutcome.Refused(WriteErrorKind.INVALID_PAYLOAD)
        val encoded = NdefEncoder.encode(records)

        val info = try { ops.inspect() } catch (e: WriteFailure) { return WriteOutcome.Refused(e.kind, e.message.orEmpty()) }
        if (!info.ndefSupported && !info.formatable) return WriteOutcome.Refused(WriteErrorKind.NOT_NDEF)
        if (info.ndefSupported && !info.writable) return WriteOutcome.Refused(WriteErrorKind.LOCKED)
        if (info.ndefSupported && info.hasContent && !overwriteExisting) return WriteOutcome.Refused(WriteErrorKind.EXISTING_CONTENT)
        if (info.ndefSupported && encoded.size > info.maxSize) return WriteOutcome.Refused(WriteErrorKind.TOO_LARGE)
        
        try {
            if (info.ndefSupported) ops.write(encoded) else ops.format(encoded)
        } catch (e: WriteFailure) {
            return WriteOutcome.Failed(e.kind, e.message.orEmpty())
        }

        return try {
            val back = ops.readRecords().orEmpty()
            if (NdefEncoder.sameContent(back, records)) WriteOutcome.Success(encoded.size, verified = true)
            else WriteOutcome.Failed(WriteErrorKind.VERIFY_FAILED, "readback-mismatch")
        } catch (e: WriteFailure) {
            WriteOutcome.Failed(WriteErrorKind.VERIFY_FAILED, e.kind.name)
        }
    }

    companion object {
        /** Contenu déjà présent, sous forme lisible (pour la confirmation de remplacement). */
        fun describeExisting(records: List<RawNdefRecord>?): String =
            NdefParser.parse(records.orEmpty()).joinToString(" · ") { r -> r.text ?: r.uri ?: r.mimeType ?: r.typeName }
    }
}
