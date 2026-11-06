package com.simpletools.nfcreader.core

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simpletools.nfcreader.Fixtures
import com.simpletools.nfcreader.core.database.AppDatabase
import com.simpletools.nfcreader.core.database.HistoryRepository
import com.simpletools.nfcreader.core.export.ExportService
import com.simpletools.nfcreader.core.export.StringProvider
import com.simpletools.nfcreader.core.nfc.TagInterpreter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomHistoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: HistoryRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val interpreter = TagInterpreter(clock = { 1_700_000_000_000 })

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = HistoryRepository(db.readingDao(), clock = { 42L })
    }
    @After fun tearDown() { db.close() }

    private fun snap(text: String = "Bonjour") = interpreter.interpret(Fixtures.ndefTag(Fixtures.textRecord(text)))

    @Test fun `aucune lecture n'est enregistree automatiquement`() = runBlocking {
        interpreter.interpret(Fixtures.ndefTag(Fixtures.textRecord("scan")))
        assertEquals(0, repo.count())
    }

    @Test fun `historique - enregistrer puis relire a l'identique`() = runBlocking {
        val s = snap()
        val id = repo.save(s, "  Veste  ", " note ", includeUid = true)
        val saved = repo.get(id)!!
        assertEquals("Veste", saved.entity.name)
        assertEquals("note", saved.entity.notes)
        assertEquals("04:A3:5B:12:7C:80:5C", saved.entity.uid)
        assertEquals(s, saved.snapshot)
    }

    @Test fun `historique - enregistrement sans UID ne stocke aucun identifiant`() = runBlocking {
        val id = repo.save(snap(), "Anonyme", "", includeUid = false)
        val saved = repo.get(id)!!
        assertNull(saved.entity.uid)
        assertNull(saved.snapshot.uidHex)
        assertFalse(saved.entity.snapshotJson.contains("04:A3:5B"))
    }

    @Test fun `historique - renommer et notes`() = runBlocking {
        val id = repo.save(snap(), "A", "", true)
        repo.rename(id, " B "); repo.updateNotes(id, "n2")
        val e = repo.get(id)!!.entity
        assertEquals("B", e.name); assertEquals("n2", e.notes)
    }

    @Test fun `historique - recherche par nom, UID avec ou sans deux-points, type et notes`() = runBlocking {
        repo.save(snap("a"), "Veste Moncler", "boutique Paris", true)
        repo.save(interpreter.interpret(Fixtures.FakeTag(id = byteArrayOf(1, 2, 3, 4), techList = listOf(Fixtures.NFCA, Fixtures.CLASSIC), ndef = null)), "Badge bureau", "", true)
        suspend fun q(s: String) = repo.observe(s).first().map { it.name }
        assertEquals(2, q("").size)
        assertEquals(listOf("Veste Moncler"), q("moncler"))
        assertEquals(listOf("Veste Moncler"), q("04:A3"))
        assertEquals(listOf("Veste Moncler"), q("04A35B"))
        assertEquals(listOf("Badge bureau"), q("classic"))
        assertEquals(listOf("Veste Moncler"), q("paris"))
        assertEquals(emptyList<String>(), q("introuvable"))
    }

    @Test fun `recherche - caracteres LIKE echappes`() = runBlocking {
        repo.save(snap(), "100% coton", "", true)
        repo.save(snap(), "autre", "", true)
        assertEquals(listOf("100% coton"), repo.observe("%").first().map { it.name })
        assertEquals(emptyList<String>(), repo.observe("_").first().map { it.name })
    }

    @Test fun `suppression d'une lecture et tout effacer`() = runBlocking {
        val a = repo.save(snap(), "A", "", true); repo.save(snap(), "B", "", true)
        repo.delete(a)
        assertNull(repo.get(a)); assertEquals(1, repo.count())
        repo.deleteAll()
        assertEquals(0, repo.count())
        assertTrue(repo.observe("").first().isEmpty())
    }

    @Test fun `ExportService - fichier JSON dans le cache prive, texte presse-papiers, nettoyage`() {
        val svc = ExportService(context, StringProvider { id, args -> context.getString(id, *args) })
        val s = snap()
        val json = svc.createJsonFile(s, "Veste", null, includeUid = false)
        assertTrue(json.exists() && json.length() > 0 && json.parentFile!!.name == "exports")
        assertTrue(json.readText().contains("Bonjour"))
        assertTrue(svc.clipboardText(s, "Veste", null, true).contains("04:A3:5B:12:7C:80:5C"))
        svc.clearExports()
        assertFalse(json.exists())
    }
}
