package com.simpletools.nfcreader.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simpletools.nfcreader.Fixtures
import com.simpletools.nfcreader.core.backup.BackupService
import com.simpletools.nfcreader.core.database.AppDatabase
import com.simpletools.nfcreader.core.database.HistoryRepository
import com.simpletools.nfcreader.core.database.InventoryItemEntity
import com.simpletools.nfcreader.core.export.SnapshotJson
import com.simpletools.nfcreader.core.inventory.InventoryRepository
import com.simpletools.nfcreader.core.nfc.TagInterpreter
import com.simpletools.nfcreader.core.templates.TemplateRepository
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
class DatabaseTest {
    private lateinit var db: AppDatabase
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val interpreter = TagInterpreter(clock = { 1_700_000_000_000 })

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun tearDown() { db.close() }

    @Test fun `modele - enregistrer, dupliquer, modifier, supprimer`() = runBlocking {
        val repo = TemplateRepository(db.templateDao(), clock = { 5L })
        val id = repo.save(null, "  Accueil ", "", mapOf("url" to "https://code.moncler.com/"), TemplateRepository.KIND_URL)
        val t = repo.get(id)!!
        assertEquals("Accueil", t.name)
        assertEquals("Général", t.category)
        val copy = repo.duplicate(id)!!
        assertEquals("Accueil (copie)", repo.get(copy)!!.name)
        repo.save(id, "Renommé", "Boutique", t.fields, t.kind)
        assertEquals("Renommé", repo.get(id)!!.name)
        repo.delete(id)
        assertNull(repo.get(id))
    }

    @Test fun `modele - le mot de passe Wi-Fi n'est jamais persiste`() = runBlocking {
        val repo = TemplateRepository(db.templateDao())
        val id = repo.save(null, "Wifi bureau", "Réseaux", mapOf("ssid" to "Bureau", "password" to "secret1234", "open" to "false"), TemplateRepository.KIND_WIFI)
        val stored = db.templateDao().get(id)!!.fieldsJson
        assertFalse(stored.contains("secret1234"))
        assertFalse(repo.get(id)!!.fields.containsKey("password"))
    }

    @Test fun `modele - transformation en contenu ecrivable`() {
        val t = TemplateRepository.toPayload(
            com.simpletools.nfcreader.core.templates.Template(1, "n", "c", TemplateRepository.KIND_WIFI, mapOf("ssid" to "Bureau", "open" to "false")),
            wifiPassword = "motdepasse",
        )
        assertTrue(t is com.simpletools.nfcreader.core.write.WritePayload.Wifi)
        assertEquals("Bureau", (t as com.simpletools.nfcreader.core.write.WritePayload.Wifi).ssid)
    }

    @Test fun `inventaire - collections, elements, recherche et CSV sans UID`() = runBlocking {
        val inv = InventoryRepository(db.inventoryDao(), clock = { 9L })
        val c = inv.createCollection("Boutique Paris")
        inv.addItem(c, "Veste noire", "Vêtements", "taille M", "04:A3:5B:12:7C:80:5C", "NTAG", null)
        inv.addItem(c, "Badge", "Accès", "", null, "MIFARE", null)
        val items = db.inventoryDao().observeItems(c).first()
        assertEquals(2, items.size)
        assertTrue(items.any { InventoryRepository.matches(it, "veste", null) })
        assertTrue(items.any { InventoryRepository.matches(it, "04A35B", null) })
        assertEquals(1, items.count { InventoryRepository.matches(it, "", "Accès") })
        val csv = inv.exportCsv(includeUid = false)
        assertTrue(csv.startsWith("collection,nom,categorie,type_puce,uid,notes"))
        assertTrue(csv.contains("Veste noire"))
        assertFalse(csv.contains("04:A3:5B"))
        assertTrue(inv.exportCsv(includeUid = true).contains("04:A3:5B:12:7C:80:5C"))
    }

    @Test fun `inventaire - suppression d'une collection supprime ses elements`() = runBlocking {
        val inv = InventoryRepository(db.inventoryDao())
        val c = inv.createCollection("X")
        inv.addItem(c, "a", "b", "", null, "NTAG", null)
        inv.deleteCollection(c)
        assertTrue(db.inventoryDao().allItems().isEmpty())
    }

    @Test fun `sauvegarde - export puis restauration complete avec remappage des collections`() = runBlocking {
        val history = HistoryRepository(db.readingDao())
        val snap = interpreter.interpret(Fixtures.ndefTag(Fixtures.textRecord("Sauvegardé")))
        history.save(snap, "Lecture", "note", includeUid = true)
        TemplateRepository(db.templateDao()).save(null, "Modèle", "Cat", mapOf("text" to "bonjour"), TemplateRepository.KIND_TEXT)
        val inv = InventoryRepository(db.inventoryDao())
        val c = inv.createCollection("Col")
        inv.addItem(c, "Item", "Cat", "n", null, "NTAG", SnapshotJson.encode(snap, includeUid = false))

        val backup = BackupService(db)
        val json = backup.export()

        // Restauration dans une base vide : les données doivent être identiques.
        val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val summary = BackupService(target).restore(json)
            assertEquals(1, summary.readings)
            assertEquals(1, summary.templates)
            assertEquals(1, summary.collections)
            assertEquals(1, summary.items)
            val restoredItem: InventoryItemEntity = target.inventoryDao().allItems().single()
            val restoredCollection = target.inventoryDao().collections().single()
            assertEquals(restoredCollection.id, restoredItem.collectionId)
            assertEquals("Sauvegardé", HistoryRepository(target.readingDao()).get(target.readingDao().all().single().id)!!.snapshot.ndefRecords.single().text)
        } finally {
            target.close()
        }
    }

    @Test fun `sauvegarde - fichier non reconnu refuse sans modifier les donnees`() = runBlocking {
        HistoryRepository(db.readingDao()).save(interpreter.interpret(Fixtures.ndefTag(Fixtures.textRecord("Gardé"))), "A", "", true)
        val result = runCatching { BackupService(db).restore("{\"format\":\"autre\"}") }
        assertTrue(result.isFailure)
        assertEquals(1, db.readingDao().count())
    }
}
