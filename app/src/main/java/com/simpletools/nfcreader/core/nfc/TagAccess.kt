package com.simpletools.nfcreader.core.nfc

/**
 * Abstraction de lecture d'une puce. L'implémentation Android ([AndroidTagAccess]) enveloppe
 * android.nfc.Tag ; les tests utilisent des faux. Aucune méthode d'écriture n'existe : lecture seule.
 */
interface TagAccess {
    /** Identifiant brut tel que renvoyé par Android (peut être absent ou vide). */
    val id: ByteArray?

    /** Noms complets des classes de technologie Android (ex. « android.nfc.tech.NfcA »). */
    val techList: List<String>

    /** Informations techniques disponibles sans commande supplémentaire sur la puce. */
    fun technologyInfo(): TechnologyInfo

    /** Ouvre une session NDEF en lecture, ou null si la puce n'expose pas Ndef. */
    fun openNdef(): NdefSession?
}

interface NdefSession : AutoCloseable {
    val typeName: String?
    val maxSize: Int
    val isWritable: Boolean
    fun canMakeReadOnly(): Boolean

    /** Lit le message NDEF ; null si la puce n'a pas de message. */
    @Throws(TagAccessException::class)
    fun readMessage(): List<RawNdefRecord>?

    /** Taille en octets du message lu (si connue). */
    val messageSize: Int?
}

enum class AccessError { TAG_LOST, IO, FORMAT }

class TagAccessException(val error: AccessError, message: String? = null, cause: Throwable? = null) :
    Exception(message, cause)

enum class ClassicKind { CLASSIC, PLUS, PRO, UNKNOWN }
enum class UltralightKind { ULTRALIGHT, ULTRALIGHT_C, UNKNOWN }

class TechnologyInfo(
    val atqa: ByteArray? = null,
    val sak: Int? = null,
    val historicalBytes: ByteArray? = null,
    val hiLayerResponse: ByteArray? = null,
    val classicKind: ClassicKind? = null,
    val classicSizeBytes: Int? = null,
    val classicSectors: Int? = null,
    val classicBlocks: Int? = null,
    val ultralightKind: UltralightKind? = null,
    val nfcBApplicationData: ByteArray? = null,
    val nfcBProtocolInfo: ByteArray? = null,
    val nfcFSystemCode: ByteArray? = null,
    val nfcFManufacturer: ByteArray? = null,
    val nfcVDsfId: Int? = null,
    val nfcVResponseFlags: Int? = null,
)
