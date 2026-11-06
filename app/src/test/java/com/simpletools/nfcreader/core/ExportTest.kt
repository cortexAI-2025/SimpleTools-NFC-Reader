package com.simpletools.nfcreader.core

import com.simpletools.nfcreader.Fixtures
import com.simpletools.nfcreader.core.export.ReportBuilder
import com.simpletools.nfcreader.core.export.SnapshotJson
import com.simpletools.nfcreader.core.export.StringProvider
import com.simpletools.nfcreader.core.models.TagSnapshot
import com.simpletools.nfcreader.core.nfc.TagInterpreter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {
    private fun snapshot(): TagSnapshot = TagInterpreter(clock = { 1_700_000_000_000 }).interpret(
        Fixtures.ndefTag(Fixtures.textRecord("Bonjour", "fr"), Fixtures.uriRecord("code.moncler.com/x")),
    )

    @Test fun `export JSON avec UID - champs et contenu`() {
        val json = JSONObject(SnapshotJson.encode(snapshot(), includeUid = true, name = "Veste", notes = "boutique"))
        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("Veste", json.getString("name"))
        assertEquals("04:A3:5B:12:7C:80:5C", json.getString("uidHex"))
        assertTrue(json.getBoolean("uidIncluded"))
        val records = json.getJSONObject("ndef").getJSONArray("records")
        assertEquals("Bonjour", records.getJSONObject(0).getString("text"))
        assertEquals("https://code.moncler.com/x", records.getJSONObject(1).getString("uri"))
        assertTrue(json.getString("disclaimer").contains("does not prove"))
    }

    @Test fun `export JSON sans UID - aucun identifiant dans le fichier`() {
        val text = SnapshotJson.encode(snapshot(), includeUid = false)
        val json = JSONObject(text)
        assertFalse(json.getBoolean("uidIncluded"))
        assertFalse(json.has("uidHex")); assertFalse(json.has("uidDecimal")); assertFalse(json.has("uidDecimalReversed"))
        assertFalse(text.contains("04:A3:5B"))
    }

    @Test fun `JSON aller-retour fidele`() {
        val original = snapshot()
        val back = SnapshotJson.decode(SnapshotJson.encode(original, includeUid = true))
        assertEquals(original, back)
    }

    @Test fun `JSON aller-retour sans UID`() {
        val back = SnapshotJson.decode(SnapshotJson.encode(snapshot(), includeUid = false))
        assertEquals(null, back.uidHex)
        assertEquals("Bonjour", back.ndefRecords[0].text)
    }

    @Test fun `JSON invalide leve une exception geree par le depot`() {
        try { SnapshotJson.decode("pas du json"); throw AssertionError("attendu") } catch (e: org.json.JSONException) { /* ok */ }
    }

    private val strings = StringProvider { id, args -> "s$id" + if (args.isNotEmpty()) args.joinToString(prefix = "(", postfix = ")") else "" }

    @Test fun `rapport sans UID n'inclut pas l'identifiant`() {
        val doc = ReportBuilder(strings).build(snapshot(), "Veste", null, includeUid = false)
        val all = doc.sections.flatMap { it.rows }.joinToString { it.value + it.label }
        assertFalse(all.contains("04:A3"))
        assertTrue(doc.disclaimer.isNotBlank())
    }

    @Test fun `rapport avec UID inclut l'identifiant et le contenu public`() {
        val doc = ReportBuilder(strings).build(snapshot(), "Veste", "n", includeUid = true)
        val values = doc.sections.flatMap { it.rows }.map { it.value }
        assertTrue(values.any { it.contains("04:A3:5B:12:7C:80:5C") })
        assertTrue(values.any { it.contains("Bonjour") })
        assertTrue(values.any { it.contains("https://code.moncler.com/x") })
    }
}
