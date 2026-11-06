package com.simpletools.nfcreader.core

import com.simpletools.nfcreader.Fixtures
import com.simpletools.nfcreader.core.nfc.TagInterpreter
import com.simpletools.nfcreader.core.nfc.UrlSafety
import com.simpletools.nfcreader.core.nfc.UrlWarning
import com.simpletools.nfcreader.feature.authentication.AuthVerdict
import com.simpletools.nfcreader.feature.authentication.BrandRegistry
import com.simpletools.nfcreader.feature.authentication.ProductAuthAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSafetyAndAuthTest {
    private val interpreter = TagInterpreter()

    @Test fun `seuls http et https sont ouvrables`() {
        assertNotNull(UrlSafety.parse("https://example.com/a"))
        assertNotNull(UrlSafety.parse("http://example.com"))
        assertNull(UrlSafety.parse("javascript:alert(1)"))
        assertNull(UrlSafety.parse("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(UrlSafety.parse("tel:+33123"))
        assertNull(UrlSafety.parse("file:///sdcard/x"))
        assertNull(UrlSafety.parse("https://exa mple.com"))
        assertNull(UrlSafety.parse(""))
    }

    @Test fun `avertissements - http IP punycode userinfo port`() {
        assertTrue(UrlWarning.NOT_HTTPS in UrlSafety.parse("http://example.com")!!.warnings)
        assertTrue(UrlWarning.IP_HOST in UrlSafety.parse("https://192.168.0.1/x")!!.warnings)
        assertTrue(UrlWarning.PUNYCODE in UrlSafety.parse("https://xn--mncler-3ya.com")!!.warnings)
        assertTrue(UrlWarning.USER_INFO in UrlSafety.parse("https://code.moncler.com@evil.com/")!!.warnings)
        assertTrue(UrlWarning.NON_STANDARD_PORT in UrlSafety.parse("https://example.com:8443")!!.warnings)
        assertTrue(UrlSafety.parse("https://example.com")!!.warnings.isEmpty())
    }

    @Test fun `domaine Moncler officiel reconnu et usurpations rejetees`() {
        fun brand(u: String) = BrandRegistry.match(UrlSafety.parse(u)!!)
        assertNotNull(brand("https://code.moncler.com/"))
        assertNotNull(brand("https://www.moncler.com/x"))
        assertNull(brand("https://moncler.com.evil.example/"))
        assertNull(brand("https://evilmoncler.com/"))
        assertNull(brand("https://code.moncler.com@evil.com/"))
        assertNull(brand("http://code.moncler.com/")) // http : pas de correspondance
    }

    @Test fun `puce sans donnee - jamais de verdict d'authenticite`() {
        val s = interpreter.interpret(Fixtures.FakeTag(techList = listOf(Fixtures.NFCA), ndef = null))
        val a = ProductAuthAnalyzer.analyze(s)
        assertEquals(AuthVerdict.NO_PUBLIC_DATA, a.verdict)
        assertTrue(a.urls.isEmpty())
    }

    @Test fun `puce avec URL officielle - verdict limite au domaine reconnu`() {
        val s = interpreter.interpret(Fixtures.ndefTag(Fixtures.uriRecord("code.moncler.com/abc123")))
        val a = ProductAuthAnalyzer.analyze(s)
        assertEquals(AuthVerdict.URL_OFFICIAL_DOMAIN, a.verdict)
        assertEquals("Moncler", a.urls.single().brand!!.name)
    }

    @Test fun `URL inconnue - domaine non reconnu`() {
        val s = interpreter.interpret(Fixtures.ndefTag(Fixtures.uriRecord("shop.example.org/t/1")))
        assertEquals(AuthVerdict.URL_UNRECOGNIZED_DOMAIN, ProductAuthAnalyzer.analyze(s).verdict)
    }

    @Test fun `URL dans un texte NDEF detectee`() {
        val s = interpreter.interpret(Fixtures.ndefTag(Fixtures.textRecord("Vérifier sur https://code.moncler.com/x, merci")))
        val a = ProductAuthAnalyzer.analyze(s)
        assertEquals("https://code.moncler.com/x", a.urls.single().url)
    }

    @Test fun `donnees sans URL`() {
        val s = interpreter.interpret(Fixtures.ndefTag(Fixtures.textRecord("hello")))
        assertEquals(AuthVerdict.NO_URL, ProductAuthAnalyzer.analyze(s).verdict)
    }

    @Test fun `aucun verdict ne contient authentique`() {
        // Garde-fou : l'énumération ne doit exposer aucun état « authentique ».
        assertTrue(AuthVerdict.values().none { it.name.contains("AUTHENTIC", ignoreCase = true) })
    }
}
