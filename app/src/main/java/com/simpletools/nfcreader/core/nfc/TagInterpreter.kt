package com.simpletools.nfcreader.core.nfc

import com.simpletools.nfcreader.core.models.NdefDetails
import com.simpletools.nfcreader.core.models.ReadIssue
import com.simpletools.nfcreader.core.models.TagFamily
import com.simpletools.nfcreader.core.models.TagSnapshot
import com.simpletools.nfcreader.core.models.TechDetail
import com.simpletools.nfcreader.core.models.UidNote

/**
 * Transforme un [TagAccess] en [TagSnapshot]. Ne lève jamais : toute erreur de lecture devient
 * un [ReadIssue] et le résultat partiel est conservé. Ne déduit que ce que les API permettent.
 */
class TagInterpreter(private val clock: () -> Long = System::currentTimeMillis) {

    fun interpret(tag: TagAccess): TagSnapshot {
        val issues = linkedSetOf<ReadIssue>()
        val shortTechs = tag.techList.map { it.substringAfterLast('.') }
        val family = familyOf(shortTechs)

        val rawId = try { tag.id } catch (e: RuntimeException) { issues += ReadIssue.UNEXPECTED; null }
        val uid = rawId?.takeIf { it.isNotEmpty() }
        val uidNote = when {
            uid == null -> UidNote.ABSENT
            uid.all { it == 0.toByte() } -> UidNote.ALL_ZERO
            // ISO/IEC 14443-3 : un UID de 4 octets commençant par 0x08 est un identifiant aléatoire.
            uid.size == 4 && uid[0] == 0x08.toByte() -> UidNote.RANDOM_LIKELY
            else -> null
        }

        val info = try {
            tag.technologyInfo()
        } catch (e: TagAccessException) {
            issues += if (e.error == AccessError.TAG_LOST) ReadIssue.TAG_LOST else ReadIssue.IO_ERROR
            TechnologyInfo()
        } catch (e: RuntimeException) {
            issues += ReadIssue.UNEXPECTED
            TechnologyInfo()
        }

        var ndef: NdefDetails? = null
        if (shortTechs.contains("Ndef")) {
            ndef = readNdef(tag, issues)
        }

        val nfcType = ndef?.typeName?.let(::friendlyNdefType) ?: nfcTypeFromTech(family, info)
        val details = buildDetails(info)
        val memory = info.classicSizeBytes

        return TagSnapshot(
            scannedAt = clock(),
            family = family,
            technologies = shortTechs,
            uidHex = uid?.toHex(),
            uidDecimal = uid?.takeIf { it.size in 4..8 }?.toUnsignedDecimal(),
            uidDecimalReversed = uid?.takeIf { it.size in 4..8 }?.reversedArray()?.toUnsignedDecimal(),
            uidLength = uid?.size ?: 0,
            uidNote = uidNote,
            nfcType = nfcType,
            isoStandards = isoStandards(shortTechs),
            memoryBytes = memory,
            manufacturer = if (uidNote == null) Manufacturers.fromUid(uid, family) else null,
            techDetails = details,
            ndefFormatable = shortTechs.contains("NdefFormatable"),
            ndef = ndef,
            issues = issues.toList(),
        )
    }

    private fun readNdef(tag: TagAccess, issues: MutableSet<ReadIssue>): NdefDetails? {
        val session = try {
            tag.openNdef()
        } catch (e: TagAccessException) {
            issues += if (e.error == AccessError.TAG_LOST) ReadIssue.TAG_LOST else ReadIssue.IO_ERROR
            return null
        } catch (e: RuntimeException) {
            issues += ReadIssue.UNEXPECTED
            return null
        } ?: return null

        try {
            val raw = try {
                session.readMessage()
            } catch (e: TagAccessException) {
                issues += when (e.error) {
                    AccessError.TAG_LOST -> ReadIssue.TAG_LOST
                    AccessError.FORMAT -> ReadIssue.NDEF_MALFORMED
                    AccessError.IO -> ReadIssue.IO_ERROR
                }
                null
            }
            val records = raw?.let { NdefParser.parse(it) }.orEmpty()
            return NdefDetails(
                typeName = session.typeName,
                maxSize = session.maxSize.takeIf { it > 0 },
                usedSize = session.messageSize,
                isWritable = session.isWritable,
                canMakeReadOnly = runCatching { session.canMakeReadOnly() }.getOrNull(),
                records = records,
            )
        } catch (e: RuntimeException) {
            issues += ReadIssue.UNEXPECTED
            return null
        } finally {
            runCatching { session.close() }
        }
    }

    private fun familyOf(techs: List<String>): TagFamily = when {
        "MifareClassic" in techs -> TagFamily.MIFARE_CLASSIC
        "MifareUltralight" in techs -> TagFamily.MIFARE_ULTRALIGHT
        "IsoDep" in techs -> TagFamily.ISO_DEP
        "NfcV" in techs -> TagFamily.NFC_V
        "NfcF" in techs -> TagFamily.NFC_F
        "NfcB" in techs -> TagFamily.NFC_B
        "NfcA" in techs -> TagFamily.NFC_A
        else -> TagFamily.UNKNOWN
    }

    private fun isoStandards(techs: List<String>): List<String> = buildList {
        if ("NfcA" in techs) add("ISO/IEC 14443-3A")
        if ("NfcB" in techs) add("ISO/IEC 14443-3B")
        if ("IsoDep" in techs) add("ISO/IEC 14443-4 (ISO-DEP)")
        if ("NfcF" in techs) add("JIS X 6319-4 (FeliCa)")
        if ("NfcV" in techs) add("ISO/IEC 15693")
    }

    private fun friendlyNdefType(t: String): String = when (t) {
        "org.nfcforum.ndef.type1" -> "NFC Forum Type 1"
        "org.nfcforum.ndef.type2" -> "NFC Forum Type 2"
        "org.nfcforum.ndef.type3" -> "NFC Forum Type 3"
        "org.nfcforum.ndef.type4" -> "NFC Forum Type 4"
        "com.nxp.ndef.mifareclassic" -> "MIFARE Classic (NDEF)"
        else -> t
    }

    private fun nfcTypeFromTech(family: TagFamily, info: TechnologyInfo): String? = when (family) {
        TagFamily.MIFARE_CLASSIC -> when (info.classicSizeBytes) {
            320 -> "MIFARE Classic Mini"
            1024 -> "MIFARE Classic 1K"
            2048 -> "MIFARE Classic 2K"
            4096 -> "MIFARE Classic 4K"
            else -> "MIFARE Classic"
        }
        TagFamily.MIFARE_ULTRALIGHT -> when (info.ultralightKind) {
            UltralightKind.ULTRALIGHT_C -> "MIFARE Ultralight C"
            else -> "MIFARE Ultralight / NTAG (modèle exact non déterminable)"
        }
        else -> null
    }

    private fun buildDetails(i: TechnologyInfo): List<TechDetail> = buildList {
        i.atqa?.let { add(TechDetail("ATQA", it.toHex(" "))) }
        i.sak?.let { add(TechDetail("SAK", "0x%02X".format(it and 0xFF))) }
        i.historicalBytes?.takeIf { it.isNotEmpty() }?.let { add(TechDetail("Historical bytes", it.toHexPreview())) }
        i.hiLayerResponse?.takeIf { it.isNotEmpty() }?.let { add(TechDetail("Hi-layer response", it.toHexPreview())) }
        if (i.classicSectors != null) add(TechDetail("MIFARE Classic", "${i.classicSectors} secteurs / ${i.classicBlocks} blocs"))
        i.nfcBApplicationData?.let { add(TechDetail("NFC-B application data", it.toHex(" "))) }
        i.nfcBProtocolInfo?.let { add(TechDetail("NFC-B protocol info", it.toHex(" "))) }
        i.nfcFSystemCode?.let { add(TechDetail("FeliCa system code", it.toHex(" "))) }
        i.nfcFManufacturer?.let { add(TechDetail("FeliCa manufacturer (PMm)", it.toHex(" "))) }
        i.nfcVDsfId?.let { add(TechDetail("NFC-V DSFID", "0x%02X".format(it and 0xFF))) }
        i.nfcVResponseFlags?.let { add(TechDetail("NFC-V response flags", "0x%02X".format(it and 0xFF))) }
    }
}

/** Fabricant déduit du premier octet d'un UID de 7 octets (ISO/IEC 7816-6) : indicatif, jamais une preuve. */
object Manufacturers {
    private val table = mapOf(
        0x02 to "STMicroelectronics",
        0x04 to "NXP Semiconductors",
        0x05 to "Infineon Technologies",
        0x07 to "Texas Instruments",
        0x16 to "EM Microelectronic-Marin",
    )

    fun fromUid(uid: ByteArray?, family: TagFamily): String? {
        if (uid == null || uid.size != 7) return null
        if (family == TagFamily.NFC_V || family == TagFamily.NFC_F || family == TagFamily.UNKNOWN) return null
        return table[uid[0].toInt() and 0xFF]
    }
}
