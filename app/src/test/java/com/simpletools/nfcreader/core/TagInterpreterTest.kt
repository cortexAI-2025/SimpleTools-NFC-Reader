package com.simpletools.nfcreader.core

import com.simpletools.nfcreader.Fixtures
import com.simpletools.nfcreader.Fixtures.FakeNdef
import com.simpletools.nfcreader.Fixtures.FakeTag
import com.simpletools.nfcreader.core.models.NdefRecordKind
import com.simpletools.nfcreader.core.models.ReadIssue
import com.simpletools.nfcreader.core.models.TagFamily
import com.simpletools.nfcreader.core.models.UidNote
import com.simpletools.nfcreader.core.nfc.AccessError
import com.simpletools.nfcreader.core.nfc.TagInterpreter
import com.simpletools.nfcreader.core.nfc.TechnologyInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagInterpreterTest {
    private val interpreter = TagInterpreter(clock = { 1_700_000_000_000 })

    @Test fun `tag NDEF texte - texte et langue decodes`() {
        val s = interpreter.interpret(Fixtures.ndefTag(Fixtures.textRecord("Bonjour NFC", "fr")))
        assertEquals(TagFamily.NFC_A, s.family)
        val r = s.ndefRecords.single()
        assertEquals(NdefRecordKind.TEXT, r.kind)
        assertEquals("Bonjour NFC", r.text)
        assertEquals("fr", r.language)
        assertTrue(s.issues.isEmpty())
        assertEquals(1_700_000_000_000, s.scannedAt)
    }

    @Test fun `tag NDEF URL - prefixe https reconstitue`() {
        val s = interpreter.interpret(Fixtures.ndefTag(Fixtures.uriRecord("example.com/p?id=1")))
        val r = s.ndefRecords.single()
        assertEquals(NdefRecordKind.URI, r.kind)
        assertEquals("https://example.com/p?id=1", r.uri)
    }

    @Test fun `UID hexadecimal decimal et fabricant pour UID de 7 octets NXP`() {
        val s = interpreter.interpret(Fixtures.ndefTag(Fixtures.textRecord("x")))
        assertEquals("04:A3:5B:12:7C:80:5C", s.uidHex)
        assertEquals(7, s.uidLength)
        assertEquals("NXP Semiconductors", s.manufacturer)
        assertNull(s.uidNote)
        assertEquals("1305511454343260", s.uidDecimal)
        assertEquals("26036968229675780", s.uidDecimalReversed)
    }

    @Test fun `tag sans NDEF - MIFARE Classic 1K sans contenu ni erreur`() {
        val tag = FakeTag(
            id = byteArrayOf(0x12, 0x34, 0x56, 0x78),
            techList = listOf(Fixtures.NFCA, Fixtures.CLASSIC),
            info = TechnologyInfo(atqa = byteArrayOf(0x04, 0x00), sak = 0x08, classicSizeBytes = 1024, classicSectors = 16, classicBlocks = 64),
            ndef = null,
        )
        val s = interpreter.interpret(tag)
        assertEquals(TagFamily.MIFARE_CLASSIC, s.family)
        assertEquals("MIFARE Classic 1K", s.nfcType)
        assertEquals(1024, s.memoryBytes)
        assertNull(s.ndef)
        assertTrue(s.ndefRecords.isEmpty())
        assertTrue(s.issues.isEmpty())
        assertNull(s.manufacturer) // UID de 4 octets : fabricant non fiable
    }

    @Test fun `puce vierge NdefFormatable - capacite signalee sans ecriture`() {
        val s = interpreter.interpret(FakeTag(techList = listOf(Fixtures.NFCA, Fixtures.ULTRALIGHT, Fixtures.NDEF_FORMATABLE), ndef = null))
        assertTrue(s.ndefFormatable)
        assertEquals(TagFamily.MIFARE_ULTRALIGHT, s.family)
        assertNull(s.memoryBytes) // pas de capacité inventée pour NTAG/Ultralight
    }

    @Test fun `tag inconnu - famille UNKNOWN et aucune invention`() {
        val s = interpreter.interpret(FakeTag(id = null, techList = emptyList(), ndef = null))
        assertEquals(TagFamily.UNKNOWN, s.family)
        assertNull(s.nfcType)
        assertTrue(s.isoStandards.isEmpty())
        assertNull(s.memoryBytes)
        assertNull(s.manufacturer)
    }

    @Test fun `erreur de lecture IO - resultat partiel conserve avec issue IO_ERROR`() {
        val tag = FakeTag(ndef = FakeNdef(failure = AccessError.IO))
        val s = interpreter.interpret(tag)
        assertEquals(listOf(ReadIssue.IO_ERROR), s.issues)
        assertEquals("04:A3:5B:12:7C:80:5C", s.uidHex) // l'UID reste affiché
        assertTrue(s.ndefRecords.isEmpty())
        assertTrue("la session NDEF doit toujours être fermée", tag.ndef!!.closed)
    }

    @Test fun `tag retire pendant la lecture - issue TAG_LOST sans crash`() {
        val tag = FakeTag(ndef = FakeNdef(failure = AccessError.TAG_LOST))
        val s = interpreter.interpret(tag)
        assertEquals(listOf(ReadIssue.TAG_LOST), s.issues)
        assertTrue(s.isPartial)
        assertTrue(tag.ndef!!.closed)
    }

    @Test fun `tag retire pendant la lecture des infos techniques`() {
        val s = interpreter.interpret(FakeTag(infoFailure = AccessError.TAG_LOST, ndef = null))
        assertEquals(listOf(ReadIssue.TAG_LOST), s.issues)
    }

    @Test fun `NDEF mal forme - issue NDEF_MALFORMED`() {
        val s = interpreter.interpret(FakeTag(ndef = FakeNdef(failure = AccessError.FORMAT)))
        assertEquals(listOf(ReadIssue.NDEF_MALFORMED), s.issues)
    }

    @Test fun `UID absent - note ABSENT et aucun identifiant invente`() {
        val s = interpreter.interpret(FakeTag(id = null))
        assertNull(s.uidHex); assertNull(s.uidDecimal); assertEquals(0, s.uidLength)
        assertEquals(UidNote.ABSENT, s.uidNote)
    }

    @Test fun `UID vide traite comme absent`() {
        assertEquals(UidNote.ABSENT, interpreter.interpret(FakeTag(id = ByteArray(0))).uidNote)
    }

    @Test fun `UID aleatoire 08xxxxxx - signale comme non stable`() {
        val s = interpreter.interpret(FakeTag(id = byteArrayOf(0x08, 0x11, 0x22, 0x33)))
        assertEquals(UidNote.RANDOM_LIKELY, s.uidNote)
        assertNull(s.manufacturer)
    }

    @Test fun `UID tout a zero - signale non fiable`() {
        assertEquals(UidNote.ALL_ZERO, interpreter.interpret(FakeTag(id = ByteArray(4))).uidNote)
    }

    @Test fun `exception inattendue - ne fait jamais planter l'interpreteur`() {
        val tag = object : com.simpletools.nfcreader.core.nfc.TagAccess {
            override val id: ByteArray? get() = throw IllegalStateException("boom")
            override val techList: List<String> = listOf(Fixtures.NFCA)
            override fun technologyInfo(): TechnologyInfo = throw IllegalStateException("boom")
            override fun openNdef() = throw IllegalStateException("boom")
        }
        val s = interpreter.interpret(tag)
        assertTrue(ReadIssue.UNEXPECTED in s.issues)
        assertFalse(s.hasNdefTechnology)
    }
}
