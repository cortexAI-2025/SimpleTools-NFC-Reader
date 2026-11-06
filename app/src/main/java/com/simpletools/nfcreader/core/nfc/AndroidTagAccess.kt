package com.simpletools.nfcreader.core.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import java.io.IOException

/** Implémentation Android, en lecture seule : aucun appel write/format/makeReadOnly. */
class AndroidTagAccess(private val tag: Tag) : TagAccess {

    override val id: ByteArray? get() = tag.id
    override val techList: List<String> get() = tag.techList.toList()

    override fun technologyInfo(): TechnologyInfo {
        val techs = techList.map { it.substringAfterLast('.') }
        val nfcA = if ("NfcA" in techs) NfcA.get(tag) else null
        val isoDep = if ("IsoDep" in techs) IsoDep.get(tag) else null
        val classic = if ("MifareClassic" in techs) MifareClassic.get(tag) else null
        val ultralight = if ("MifareUltralight" in techs) MifareUltralight.get(tag) else null
        val nfcB = if ("NfcB" in techs) NfcB.get(tag) else null
        val nfcF = if ("NfcF" in techs) NfcF.get(tag) else null
        val nfcV = if ("NfcV" in techs) NfcV.get(tag) else null
        return TechnologyInfo(
            atqa = nfcA?.atqa,
            sak = nfcA?.sak?.toInt(),
            historicalBytes = isoDep?.historicalBytes,
            hiLayerResponse = isoDep?.hiLayerResponse,
            classicKind = classic?.let {
                when (it.type) {
                    MifareClassic.TYPE_CLASSIC -> ClassicKind.CLASSIC
                    MifareClassic.TYPE_PLUS -> ClassicKind.PLUS
                    MifareClassic.TYPE_PRO -> ClassicKind.PRO
                    else -> ClassicKind.UNKNOWN
                }
            },
            classicSizeBytes = classic?.size,
            classicSectors = classic?.sectorCount,
            classicBlocks = classic?.blockCount,
            ultralightKind = ultralight?.let {
                when (it.type) {
                    MifareUltralight.TYPE_ULTRALIGHT -> UltralightKind.ULTRALIGHT
                    MifareUltralight.TYPE_ULTRALIGHT_C -> UltralightKind.ULTRALIGHT_C
                    else -> UltralightKind.UNKNOWN
                }
            },
            nfcBApplicationData = nfcB?.applicationData,
            nfcBProtocolInfo = nfcB?.protocolInfo,
            nfcFSystemCode = nfcF?.systemCode,
            nfcFManufacturer = nfcF?.manufacturer,
            nfcVDsfId = nfcV?.dsfId?.toInt(),
            nfcVResponseFlags = nfcV?.responseFlags?.toInt(),
        )
    }

    override fun openNdef(): NdefSession? {
        val ndef = Ndef.get(tag) ?: return null
        return AndroidNdefSession(ndef)
    }

    private class AndroidNdefSession(private val ndef: Ndef) : NdefSession {
        override var messageSize: Int? = null
            private set
        override val typeName: String? get() = ndef.type
        override val maxSize: Int get() = ndef.maxSize
        override val isWritable: Boolean get() = ndef.isWritable
        override fun canMakeReadOnly(): Boolean = ndef.canMakeReadOnly()

        override fun readMessage(): List<RawNdefRecord>? {
            try {
                if (!ndef.isConnected) ndef.connect()
                val message: NdefMessage? = ndef.ndefMessage
                messageSize = message?.byteArrayLength
                return message?.records?.map { RawNdefRecord(it.tnf.toInt(), it.type, it.id, it.payload) }
            } catch (e: TagLostException) {
                throw TagAccessException(AccessError.TAG_LOST, cause = e)
            } catch (e: FormatException) {
                throw TagAccessException(AccessError.FORMAT, cause = e)
            } catch (e: IOException) {
                throw TagAccessException(AccessError.IO, cause = e)
            } catch (e: SecurityException) {
                // Android lève SecurityException si la puce a disparu / n'est plus valide.
                throw TagAccessException(AccessError.TAG_LOST, cause = e)
            }
        }

        override fun close() {
            try { ndef.close() } catch (_: IOException) { }
        }
    }
}
