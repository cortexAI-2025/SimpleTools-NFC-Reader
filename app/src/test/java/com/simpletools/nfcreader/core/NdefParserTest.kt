package com.simpletools.nfcreader.core

import com.simpletools.nfcreader.Fixtures
import com.simpletools.nfcreader.core.models.NdefRecordKind
import com.simpletools.nfcreader.core.nfc.NdefParser
import com.simpletools.nfcreader.core.nfc.RawNdefRecord
import com.simpletools.nfcreader.core.nfc.sanitizeUntrusted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NdefParserTest {
    @Test fun `texte UTF-16 decode`() {
        val payload = byteArrayOf(0x82.toByte(), 'e'.code.toByte(), 'n'.code.toByte()) + "Héllo".toByteArray(Charsets.UTF_16)
        val r = NdefParser.parse(listOf(RawNdefRecord(1, "T".toByteArray(), ByteArray(0), payload))).single()
        assertEquals("Héllo", r.text)
    }

    @Test fun `texte avec longueur de langue invalide - enregistrement inconnu sans crash`() {
        val r = NdefParser.parse(listOf(RawNdefRecord(1, "T".toByteArray(), ByteArray(0), byteArrayOf(0x3F, 0x65)))).single()
        assertEquals(NdefRecordKind.UNKNOWN, r.kind)
    }

    @Test fun `payload vide pour URI - inconnu sans crash`() {
        val r = NdefParser.parse(listOf(RawNdefRecord(1, "U".toByteArray(), ByteArray(0), ByteArray(0)))).single()
        assertEquals(NdefRecordKind.UNKNOWN, r.kind)
    }

    @Test fun `code de prefixe URI invalide - inconnu`() {
        val r = NdefParser.parse(listOf(Fixtures.uriRecord("x", prefixCode = 0xEE))).single()
        assertEquals(NdefRecordKind.UNKNOWN, r.kind)
        assertNull(r.uri)
    }

    @Test fun `tel et mailto reconnus`() {
        assertEquals("tel:+33123456789", NdefParser.parse(listOf(Fixtures.uriRecord("+33123456789", 0x05))).single().uri)
        assertEquals("mailto:a@b.fr", NdefParser.parse(listOf(Fixtures.uriRecord("a@b.fr", 0x06))).single().uri)
    }

    @Test fun `MIME texte et MIME binaire`() {
        val t = NdefParser.parse(listOf(Fixtures.mimeRecord("text/plain", "abc".toByteArray()))).single()
        assertEquals(NdefRecordKind.MIME, t.kind); assertEquals("abc", t.text); assertNull(t.payloadHex)
        val b = NdefParser.parse(listOf(Fixtures.mimeRecord("application/octet-stream", byteArrayOf(1, 2, 0xFF.toByte())))).single()
        assertNull(b.text); assertEquals("01 02 FF", b.payloadHex)
    }

    @Test fun `grosse charge binaire - hexa tronque`() {
        val r = NdefParser.parse(listOf(Fixtures.mimeRecord("application/x", ByteArray(500)))).single()
        assertEquals(500, r.payloadSize)
        assertTrue(r.payloadHex!!.endsWith("…"))
    }

    @Test fun `Smart Poster imbrique decode`() {
        // Message imbriqué : un enregistrement URI court (MB|ME|SR, TNF=1, type "U").
        val uri = byteArrayOf(0x04) + "example.com".toByteArray()
        val inner = byteArrayOf(0xD1.toByte(), 1, uri.size.toByte(), 'U'.code.toByte()) + uri
        val sp = RawNdefRecord(1, "Sp".toByteArray(), ByteArray(0), inner)
        val r = NdefParser.parse(listOf(sp)).single()
        assertEquals(NdefRecordKind.SMART_POSTER, r.kind)
        assertEquals("https://example.com", r.children.single().uri)
    }

    @Test fun `Smart Poster corrompu - pas de crash`() {
        val r = NdefParser.parse(listOf(RawNdefRecord(1, "Sp".toByteArray(), ByteArray(0), byteArrayOf(0xD1.toByte(), 1, 99)))).single()
        assertEquals(NdefRecordKind.SMART_POSTER, r.kind)
        assertTrue(r.children.isEmpty())
        assertNotNull(r.payloadHex)
    }

    @Test fun `enregistrement vide et type externe`() {
        assertEquals(NdefRecordKind.EMPTY, NdefParser.parse(listOf(RawNdefRecord(0, ByteArray(0), ByteArray(0), ByteArray(0)))).single().kind)
        val e = NdefParser.parse(listOf(RawNdefRecord(4, "example.com:t".toByteArray(), ByteArray(0), byteArrayOf(1)))).single()
        assertEquals(NdefRecordKind.EXTERNAL, e.kind)
    }

    @Test fun `caracteres de controle supprimes du contenu non fiable`() {
        val r = NdefParser.parse(listOf(Fixtures.textRecord("a\u0000b\u001Bc\nd"))).single()
        assertEquals("abc\nd", r.text)
        assertFalse("a\u0007".sanitizeUntrusted().contains('\u0007'))
    }

    @Test fun `texte tres long tronque`() {
        val r = NdefParser.parse(listOf(Fixtures.textRecord("x".repeat(10_000)))).single()
        assertTrue(r.text!!.length <= 2001)
    }
}
