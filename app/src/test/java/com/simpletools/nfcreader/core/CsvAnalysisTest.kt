package com.simpletools.nfcreader.core

import com.simpletools.nfcreader.Fixtures
import com.simpletools.nfcreader.core.analysis.Csv
import com.simpletools.nfcreader.core.analysis.HexView
import com.simpletools.nfcreader.core.analysis.ReadingComparator
import com.simpletools.nfcreader.core.nfc.TagInterpreter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvAnalysisTest {
    @Test fun `csv - guillemets, virgules et sauts de ligne echappes`() {
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
        assertEquals("\"l1\nl2\"", Csv.escape("l1\nl2"))
    }

    @Test fun `csv - protection contre l'injection de formules`() {
        assertEquals("'=1+1", Csv.escape("=1+1"))
        assertEquals("'+33 6", Csv.escape("+33 6"))
        assertEquals("'@cmd", Csv.escape("@cmd"))
        assertEquals("texte normal", Csv.escape("texte normal"))
    }

    @Test fun `csv - document avec entete et fins de ligne CRLF`() {
        val doc = Csv.document(listOf("a", "b"), listOf(listOf("1", "2")))
        assertEquals("a,b\r\n1,2\r\n", doc)
    }

    @Test fun `hexadecimal - lignes de 16 octets avec ASCII`() {
        val dump = HexView.dump("Hello, NFC!".toByteArray())
        assertTrue(dump.startsWith("0000  48 65 6C 6C 6F 2C 20 4E 46 43 21"))
        assertTrue(dump.trimEnd().endsWith("Hello, NFC!"))
    }

    @Test fun `hexadecimal - donnees tronquees indiquees`() {
        val dump = HexView.dump(ByteArray(5000), maxBytes = 100)
        assertTrue(dump.contains("octets non affichés"))
    }

    @Test fun `comparaison - champs identiques et differents`() {
        val ti = TagInterpreter(clock = { 1L })
        val a = ti.interpret(Fixtures.ndefTag(Fixtures.textRecord("un")))
        val b = ti.interpret(Fixtures.ndefTag(Fixtures.textRecord("deux")))
        val diffs = ReadingComparator.compare(a, b)
        assertTrue(diffs.first { it.field == "UID" }.same)
        assertFalse(diffs.first { it.field == "Enregistrements NDEF" }.same)
    }

    @Test fun `comparaison - types MIME listes`() {
        val s = TagInterpreter(clock = { 1L }).interpret(Fixtures.ndefTag(Fixtures.mimeRecord("text/plain", "x".toByteArray())))
        assertEquals(listOf("text/plain"), ReadingComparator.mimeTypes(s))
    }
}
