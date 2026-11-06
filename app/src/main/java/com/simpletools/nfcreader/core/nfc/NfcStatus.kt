package com.simpletools.nfcreader.core.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag

enum class NfcAvailability { ABSENT, DISABLED, ENABLED }

fun interface NfcStatusProvider {
    fun status(): NfcAvailability
}

/** Statut matériel réel via [NfcAdapter] + activation du mode lecture (reader mode). */
class NfcReaderController(context: Context) : NfcStatusProvider {
    private val appContext = context.applicationContext

    override fun status(): NfcAvailability {
        val adapter = NfcAdapter.getDefaultAdapter(appContext) ?: return NfcAvailability.ABSENT
        return if (adapter.isEnabled) NfcAvailability.ENABLED else NfcAvailability.DISABLED
    }

    /** Active le mode lecture. Renvoie false si impossible (NFC absent/désactivé ou erreur système). */
    fun enable(activity: Activity, onTag: (TagAccess) -> Unit): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return false
        if (!adapter.isEnabled) return false
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        return try {
            adapter.enableReaderMode(activity, NfcAdapter.ReaderCallback { tag: Tag -> onTag(AndroidTagAccess(tag)) }, flags, null)
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    fun disable(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        try { adapter.disableReaderMode(activity) } catch (_: RuntimeException) { }
    }
}
