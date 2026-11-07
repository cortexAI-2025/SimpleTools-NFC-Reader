package com.simpletools.nfcreader.write

import com.simpletools.nfcreader.core.nfc.RawNdefRecord
import com.simpletools.nfcreader.core.write.NdefEncoder
import com.simpletools.nfcreader.core.nfc.NdefParser
import com.simpletools.nfcreader.core.write.TagWriteInfo
import com.simpletools.nfcreader.core.write.WriteErrorKind
import com.simpletools.nfcreader.core.write.WriteEngine
import com.simpletools.nfcreader.core.write.WriteFailure
import com.simpletools.nfcreader.core.write.WriteOps
import com.simpletools.nfcreader.core.write.WriteOutcome
import com.simpletools.nfcreader.core.write.WritePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Faux tag : simulation de tests unitaires, pas une validation matérielle. */
class FakeWriteOps(
    var info: TagWriteInfo = TagWriteInfo(ndefSupported = true, formatable = false, writable = true, maxSize = 504, hasContent = false),
    var existing: List<RawNdefRecord>? = null,
    var failOnWrite: WriteErrorKind? = null,
    var readBackOverride: List<RawNdefRecord>? = null,
    var failOnRead: WriteErrorKind? = null,
) : WriteOps {
    var stored: List<RawNdefRecord>? = existing
    var writes = 0
    var formats = 0
    override fun inspect(): TagWriteInfo = info
    override fun write(encoded: ByteArray) {
        failOnWrite?.let { throw WriteFailure(it) }
        writes++
        stored = NdefParser.parseMessageBytes(encoded)
    }
    override fun format(encoded: ByteArray) {
        formats++
        stored = NdefParser.parseMessageBytes(encoded)
    }
    override fun readRecords(): List<RawNdefRecord>? {
        failOnRead?.let { throw WriteFailure(it) }
        return readBackOverride ?: stored
    }
}

class WriteEngineTest {
    private val engine = WriteEngine()
    private val text = WritePayload.Text("Bonjour")

    @Test fun `ecriture reussie et verifiee par relecture`() {
        val ops = FakeWriteOps()
        val out = engine.write(ops, text, overwriteExisting = false)
        assertTrue(out is WriteOutcome.Success && out.verified)
        assertEquals(1, ops.writes)
        assertTrue(NdefEncoder.sameContent(ops.stored!!, text.toRecords()))
    }

    @Test fun `tag verrouille refuse sans ecriture`() {
        val ops = FakeWriteOps(info = TagWriteInfo(true, false, writable = false, maxSize = 504, hasContent = true))
        assertEquals(WriteOutcome.Refused(WriteErrorKind.LOCKED), engine.write(ops, text, true))
        assertEquals(0, ops.writes)
    }

    @Test fun `capacite insuffisante refusee sans ecriture`() {
        val ops = FakeWriteOps(info = TagWriteInfo(true, false, writable = true, maxSize = 10, hasContent = false))
        assertEquals(WriteOutcome.Refused(WriteErrorKind.TOO_LARGE), engine.write(ops, text, false))
        assertEquals(0, ops.writes)
    }

    @Test fun `tag non NDEF et non formatable refuse`() {
        val ops = FakeWriteOps(info = TagWriteInfo(false, false, false, 0, false))
        assertEquals(WriteOutcome.Refused(WriteErrorKind.NOT_NDEF), engine.write(ops, text, false))
    }

    @Test fun `contenu existant - refus sans accord explicite`() {
        val ops = FakeWriteOps(info = TagWriteInfo(true, false, true, 504, hasContent = true), existing = text.toRecords())
        assertEquals(WriteOutcome.Refused(WriteErrorKind.EXISTING_CONTENT), engine.write(ops, WritePayload.Text("Nouveau"), overwriteExisting = false))
        assertEquals(0, ops.writes)
    }

    @Test fun `contenu existant - remplacement autorise apres accord`() {
        val ops = FakeWriteOps(info = TagWriteInfo(true, false, true, 504, hasContent = true), existing = text.toRecords())
        val out = engine.write(ops, WritePayload.Text("Nouveau"), overwriteExisting = true)
        assertTrue(out is WriteOutcome.Success)
        assertEquals("Nouveau", NdefParser.parse(ops.stored!!).single().text)
    }

    @Test fun `tag vierge formatable - formatage puis verification`() {
        val ops = FakeWriteOps(info = TagWriteInfo(false, true, true, 0, false), existing = null)
        val out = engine.write(ops, text, false)
        assertTrue(out is WriteOutcome.Success)
        assertEquals(1, ops.formats)
    }

    @Test fun `tag retire pendant l'ecriture - echec signale, pas de succes`() {
        val ops = FakeWriteOps(failOnWrite = WriteErrorKind.TAG_LOST)
        val out = engine.write(ops, text, false)
        assertTrue(out is WriteOutcome.Failed && out.kind == WriteErrorKind.TAG_LOST)
    }

    @Test fun `erreur de communication pendant l'ecriture`() {
        val out = engine.write(FakeWriteOps(failOnWrite = WriteErrorKind.IO), text, false)
        assertTrue(out is WriteOutcome.Failed && out.kind == WriteErrorKind.IO)
    }

    @Test fun `verification - relecture differente signalee comme echec`() {
        val other = WritePayload.Text("Autre").toRecords()
        val out = engine.write(FakeWriteOps(readBackOverride = other), text, false)
        assertTrue(out is WriteOutcome.Failed && out.kind == WriteErrorKind.VERIFY_FAILED)
    }

    @Test fun `verification - erreur de relecture signalee`() {
        val out = engine.write(FakeWriteOps(failOnRead = WriteErrorKind.TAG_LOST), text, false)
        assertTrue(out is WriteOutcome.Failed && out.kind == WriteErrorKind.VERIFY_FAILED)
    }

    @Test fun `contenu invalide refuse avant toute operation`() {
        val ops = FakeWriteOps()
        assertEquals(WriteOutcome.Refused(WriteErrorKind.INVALID_PAYLOAD), engine.write(ops, WritePayload.Url("ftp://x"), false))
        assertEquals(0, ops.writes)
        assertEquals(0, ops.formats)
    }
}
