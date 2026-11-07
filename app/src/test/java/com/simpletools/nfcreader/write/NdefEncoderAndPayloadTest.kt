package com.simpletools.nfcreader.write

import com.simpletools.nfcreader.core.nfc.NdefParser
import com.simpletools.nfcreader.core.nfc.RawNdefRecord
import com.simpletools.nfcreader.core.models.NdefRecordKind
import com.simpletools.nfcreader.core.write.NdefEncoder
import com.simpletools.nfcreader.core.write.WriteIssue
import com.simpletools.nfcreader.core.write.WritePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NdefEncoderAndPayloadTest {

    @Test fun `encodage puis analyse redonne les memes enregistrements`() {
        val records = WritePayload.Text("Bonjour", "fr").toRecords() + WritePayload.Url("https://code.moncler.com/x").toRecords()
        val back = NdefParser.parseMessageBytes(NdefEncoder.encode(records))
        assertTrue(NdefEncoder.sameContent(records, back))
    }

    @Test fun `charge utile longue (plus de 255 octets) encodee en format long`() {
        val text = "é".repeat(400)
        val records = WritePayload.Text(text).toRecords()
        val bytes = NdefEncoder.encode(records)
        val back = NdefParser.parseMessageBytes(bytes)
        assertEquals(text, NdefParser.parse(back).single().text)
    }

    @Test fun `identifiant d'enregistrement pris en compte`() {
        val r = RawNdefRecord(RawNdefRecord.TNF_WELL_KNOWN, "T".toByteArray(), byteArrayOf(1, 2), byteArrayOf(0x02, 'f'.code.toByte(), 'r'.code.toByte()))
        val back = NdefParser.parseMessageBytes(NdefEncoder.encode(listOf(r))).single()
        assertTrue(back.id.contentEquals(byteArrayOf(1, 2)))
    }

    @Test fun `sameContent detecte une difference de charge utile`() {
        val a = WritePayload.Text("a").toRecords()
        val b = WritePayload.Text("b").toRecords()
        assertFalse(NdefEncoder.sameContent(a, b))
    }

    @Test fun `texte vide et langue invalide refuses`() {
        assertEquals(listOf(WriteIssue.EMPTY), WritePayload.Text("   ").validate())
        assertTrue(WriteIssue.INVALID_LANGUAGE in WritePayload.Text("x", "fr_FR_%").validate())
    }

    @Test fun `texte trop long refuse`() {
        assertTrue(WriteIssue.TOO_LONG in WritePayload.Text("x".repeat(1001)).validate())
    }

    @Test fun `url - seuls http et https, prefixe compresse`() {
        assertEquals(emptyList<WriteIssue>(), WritePayload.Url("https://example.com/a").validate())
        assertTrue(WriteIssue.INVALID_URL in WritePayload.Url("javascript:alert(1)").validate())
        assertTrue(WriteIssue.INVALID_URL in WritePayload.Url("ftp://example.com").validate())
        assertTrue(WriteIssue.INVALID_URL in WritePayload.Url("https://").validate())
        val rec = WritePayload.Url("https://www.example.com/p").toRecords().single()
        assertEquals(0x02, rec.payload[0].toInt())
        val back = NdefParser.parse(listOf(rec)).single()
        assertEquals("https://www.example.com/p", back.uri)
    }

    @Test fun `contact - vCard 3_0 relue correctement et emails valides`() {
        val c = WritePayload.Contact("Marie", "Dupont", "+33 6 12 34 56 78", "marie@example.fr", "Acme")
        assertEquals(emptyList<WriteIssue>(), c.validate())
        val rec = c.toRecords().single()
        assertEquals("text/vcard", rec.type.toString(Charsets.US_ASCII))
        val text = String(rec.payload, Charsets.UTF_8)
        assertTrue(text.startsWith("BEGIN:VCARD\r\nVERSION:3.0"))
        assertTrue(text.contains("FN:Marie Dupont"))
        assertTrue(text.contains("EMAIL:marie@example.fr"))
        assertTrue(WriteIssue.INVALID_EMAIL in WritePayload.Contact("A", "B", email = "pas-un-mail").validate())
        assertTrue(WriteIssue.INVALID_PHONE in WritePayload.Contact("A", "B", phone = "abc").validate())
    }

    @Test fun `contact - caracteres speciaux echappes`() {
        val v = WritePayload.Contact("A;B", "C,D").toVCard()
        assertTrue(v.contains("N:C\\,D;A\\;B;;;"))
    }

    @Test fun `wifi WPA2 - format WSC decodable et mot de passe absent du resume`() {
        val w = WritePayload.Wifi("Maison", "motdepasse123")
        assertEquals(emptyList<WriteIssue>(), w.validate())
        val rec = w.toRecords().single()
        assertEquals("application/vnd.wfa.wsc", rec.type.toString(Charsets.US_ASCII))
        // Lecture TLV : 100E contient les sous-champs ; on retrouve le SSID et la clé.
        val credential = readTlv(rec.payload, 0x100E)
        assertEquals("Maison", String(readTlv(credential, 0x1045), Charsets.UTF_8))
        assertEquals("motdepasse123", String(readTlv(credential, 0x1027), Charsets.UTF_8))
        assertEquals(0x0020, readTlv(credential, 0x1003).let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) })
        assertFalse(WritePayload.summary(w).contains("motdepasse123"))
    }

    @Test fun `wifi - mot de passe de 8 a 63 caracteres, SSID max 32 octets`() {
        assertTrue(WriteIssue.INVALID_PASSWORD in WritePayload.Wifi("N", "court").validate())
        assertTrue(WriteIssue.TOO_LONG in WritePayload.Wifi("x".repeat(33), "motdepasse").validate())
        assertEquals(emptyList<WriteIssue>(), WritePayload.Wifi("Public", "", open = true).validate())
    }

    @Test fun `wifi ouvert - chiffrement aucun et cle vide`() {
        val credential = readTlv(WritePayload.Wifi("Hotel", "", open = true).toRecords().single().payload, 0x100E)
        assertEquals(0x0001, readTlv(credential, 0x100F).let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) })
        assertEquals(0, readTlv(credential, 0x1027).size)
    }

    @Test fun `resume d'un contenu - texte et url lisibles`() {
        assertEquals("https://a.fr", WritePayload.summary(WritePayload.Url(" https://a.fr ")))
        assertEquals(NdefRecordKind.TEXT, NdefParser.parse(WritePayload.Text("x").toRecords()).single().kind)
    }

    /** Lit une valeur TLV (type 16 bits, longueur 16 bits) dans un tampon. */
    private fun readTlv(buf: ByteArray, type: Int): ByteArray {
        var i = 0
        while (i + 4 <= buf.size) {
            val t = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            val len = ((buf[i + 2].toInt() and 0xFF) shl 8) or (buf[i + 3].toInt() and 0xFF)
            if (t == type) return buf.copyOfRange(i + 4, i + 4 + len)
            i += 4 + len
        }
        throw AssertionError("TLV 0x%04X absent".format(type))
    }
}
