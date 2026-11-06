package com.simpletools.nfcreader.core.models

/** Famille principale d'une puce, déduite uniquement de la liste de technologies Android. */
enum class TagFamily(val label: String?) {
    MIFARE_CLASSIC("MIFARE Classic"),
    MIFARE_ULTRALIGHT("MIFARE Ultralight / NTAG"),
    ISO_DEP("ISO-DEP (ISO/IEC 14443-4)"),
    NFC_V("NFC-V (ISO/IEC 15693)"),
    NFC_F("NFC-F (FeliCa)"),
    NFC_B("NFC-B (ISO/IEC 14443-3B)"),
    NFC_A("NFC-A (ISO/IEC 14443-3A)"),
    UNKNOWN(null),
}

enum class NdefRecordKind { EMPTY, TEXT, URI, MIME, SMART_POSTER, EXTERNAL, WELL_KNOWN_OTHER, UNKNOWN }

/** Enregistrement NDEF décodé. Toutes les valeurs proviennent de la puce : entrée non fiable. */
data class NdefRecordInfo(
    val kind: NdefRecordKind,
    val typeName: String,
    val payloadSize: Int,
    val text: String? = null,
    val language: String? = null,
    val uri: String? = null,
    val mimeType: String? = null,
    val payloadHex: String? = null,
    val children: List<NdefRecordInfo> = emptyList(),
)

data class NdefDetails(
    val typeName: String?,
    val maxSize: Int?,
    val usedSize: Int?,
    val isWritable: Boolean?,
    val canMakeReadOnly: Boolean?,
    val records: List<NdefRecordInfo>,
)

/** Problèmes rencontrés pendant la lecture. Une lecture partielle reste affichée. */
enum class ReadIssue { TAG_LOST, IO_ERROR, NDEF_MALFORMED, UNEXPECTED }

enum class UidNote { ABSENT, RANDOM_LIKELY, ALL_ZERO }

data class TechDetail(val label: String, val value: String)

data class TagSnapshot(
    val scannedAt: Long,
    val family: TagFamily,
    val technologies: List<String>,
    val uidHex: String?,
    val uidDecimal: String?,
    val uidDecimalReversed: String?,
    val uidLength: Int,
    val uidNote: UidNote?,
    val nfcType: String?,
    val isoStandards: List<String>,
    val memoryBytes: Int?,
    val manufacturer: String?,
    val techDetails: List<TechDetail>,
    val ndefFormatable: Boolean,
    val ndef: NdefDetails?,
    val issues: List<ReadIssue>,
) {
    val hasNdefTechnology: Boolean get() = technologies.contains("Ndef")
    val ndefRecords: List<NdefRecordInfo> get() = ndef?.records.orEmpty()
    val isPartial: Boolean get() = issues.isNotEmpty()

    /** Copie sans aucun identifiant (UID) : pour export/enregistrement sans identifiant. */
    fun withoutUid(): TagSnapshot = copy(
        uidHex = null,
        uidDecimal = null,
        uidDecimalReversed = null,
    )
}
