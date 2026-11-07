package com.simpletools.nfcreader.core.backup

import com.simpletools.nfcreader.core.database.AppDatabase
import com.simpletools.nfcreader.core.database.CollectionEntity
import com.simpletools.nfcreader.core.database.InventoryItemEntity
import com.simpletools.nfcreader.core.database.ReadingEntity
import com.simpletools.nfcreader.core.database.TemplateEntity
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sauvegarde et restauration LOCALES (PRO) : fichier JSON choisi par l'utilisateur (Storage Access Framework).
 * Aucun envoi réseau. La restauration remplace les données existantes après confirmation.
 */
class BackupService(private val db: AppDatabase) {

    suspend fun export(): String {
        val readings = db.readingDao().all()
        val templates = db.templateDao().all()
        val collections = db.inventoryDao().collections()
        val items = db.inventoryDao().allItems()
        return JSONObject().apply {
            put("format", FORMAT)
            put("schemaVersion", SCHEMA_VERSION)
            put("generator", "SimpleTools NFC Reader")
            put("readings", JSONArray(readings.map { r ->
                JSONObject().put("scannedAt", r.scannedAt).put("savedAt", r.savedAt).put("name", r.name)
                    .put("chipType", r.chipType).put("uid", r.uid ?: JSONObject.NULL).put("technologies", r.technologies)
                    .put("snapshotJson", r.snapshotJson).put("notes", r.notes)
            }))
            put("templates", JSONArray(templates.map { t ->
                JSONObject().put("name", t.name).put("category", t.category).put("kind", t.kind)
                    .put("fieldsJson", t.fieldsJson).put("updatedAt", t.updatedAt)
            }))
            put("collections", JSONArray(collections.map { c ->
                JSONObject().put("id", c.id).put("name", c.name).put("createdAt", c.createdAt)
            }))
            put("inventoryItems", JSONArray(items.map { i ->
                JSONObject().put("collectionId", i.collectionId).put("name", i.name).put("category", i.category)
                    .put("notes", i.notes).put("uid", i.uid ?: JSONObject.NULL).put("chipType", i.chipType)
                    .put("snapshotJson", i.snapshotJson ?: JSONObject.NULL).put("addedAt", i.addedAt)
            }))
        }.toString(2)
    }

    /** Restauration complète. Valide le format avant toute modification ; lève une exception sinon. */
    suspend fun restore(json: String): Summary {
        val o = JSONObject(json)
        require(o.optString("format") == FORMAT) { "Fichier de sauvegarde non reconnu" }
        require(o.optInt("schemaVersion") in 1..SCHEMA_VERSION) { "Version de sauvegarde non prise en charge" }

        val readings = o.getJSONArray("readings").let { a -> List(a.length()) { a.getJSONObject(it) } }.map { r ->
            ReadingEntity(scannedAt = r.getLong("scannedAt"), savedAt = r.getLong("savedAt"), name = r.getString("name"),
                chipType = r.getString("chipType"), uid = if (r.isNull("uid")) null else r.getString("uid"),
                technologies = r.getString("technologies"), snapshotJson = r.getString("snapshotJson"), notes = r.getString("notes"))
        }
        val templates = o.getJSONArray("templates").let { a -> List(a.length()) { a.getJSONObject(it) } }.map { t ->
            TemplateEntity(name = t.getString("name"), category = t.getString("category"), kind = t.getString("kind"),
                fieldsJson = t.getString("fieldsJson"), updatedAt = t.getLong("updatedAt"))
        }
        val collections = o.getJSONArray("collections").let { a -> List(a.length()) { a.getJSONObject(it) } }.map { c ->
            c.getLong("id") to CollectionEntity(name = c.getString("name"), createdAt = c.getLong("createdAt"))
        }
        val items = o.getJSONArray("inventoryItems").let { a -> List(a.length()) { a.getJSONObject(it) } }.map { i ->
            InventoryItemEntity(collectionId = i.getLong("collectionId"), name = i.getString("name"), category = i.getString("category"),
                notes = i.getString("notes"), uid = if (i.isNull("uid")) null else i.getString("uid"), chipType = i.getString("chipType"),
                snapshotJson = if (i.isNull("snapshotJson")) null else i.getString("snapshotJson"), addedAt = i.getLong("addedAt"))
        }

        db.withTransaction {
            db.readingDao().deleteAll()
            db.templateDao().deleteAll()
            db.inventoryDao().deleteAllItems()
            db.inventoryDao().deleteAllCollections()
            readings.forEach { db.readingDao().insert(it) }
            templates.forEach { db.templateDao().insert(it) }
            // Les identifiants de collection sont réassignés : on remappe les éléments.
            val idMap = collections.associate { (oldId, c) -> oldId to db.inventoryDao().insertCollection(c) }
            items.forEach { db.inventoryDao().insertItem(it.copy(collectionId = idMap[it.collectionId] ?: return@forEach)) }
        }
        return Summary(readings.size, templates.size, collections.size, items.size)
    }

    data class Summary(val readings: Int, val templates: Int, val collections: Int, val items: Int)

    companion object {
        const val FORMAT = "simpletools-nfc-backup"
        const val SCHEMA_VERSION = 1
    }
}
