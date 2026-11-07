package com.simpletools.nfcreader.core.inventory

import com.simpletools.nfcreader.core.database.CollectionEntity
import com.simpletools.nfcreader.core.database.InventoryDao
import com.simpletools.nfcreader.core.database.InventoryItemEntity
import com.simpletools.nfcreader.core.analysis.Csv
import kotlinx.coroutines.flow.Flow

/** Inventaire de tags (PRO) : collections, éléments, notes, recherche et export. */
class InventoryRepository(private val dao: InventoryDao, private val clock: () -> Long = System::currentTimeMillis) {

    fun observeCollections(): Flow<List<CollectionEntity>> = dao.observeCollections()
    fun observeItems(collectionId: Long): Flow<List<InventoryItemEntity>> = dao.observeItems(collectionId)

    suspend fun createCollection(name: String): Long =
        dao.insertCollection(CollectionEntity(name = name.trim().take(60).ifEmpty { "Sans nom" }, createdAt = clock()))

    suspend fun deleteCollection(id: Long) {
        dao.deleteItemsOf(id)
        dao.deleteCollection(id)
    }

    suspend fun addItem(collectionId: Long, name: String, category: String, notes: String, uid: String?, chipType: String, snapshotJson: String?): Long =
        dao.insertItem(InventoryItemEntity(
            collectionId = collectionId,
            name = name.trim().take(80).ifEmpty { "Tag sans nom" },
            category = category.trim().take(40).ifEmpty { "Général" },
            notes = notes.trim().take(500),
            uid = uid,
            chipType = chipType,
            snapshotJson = snapshotJson,
            addedAt = clock(),
        ))

    suspend fun updateItem(item: InventoryItemEntity) = dao.updateItem(item)
    suspend fun deleteItem(id: Long) = dao.deleteItem(id)

    /** Export CSV de tout l'inventaire, avec le nom de la collection. Les UID ne sont inclus que si demandé. */
    suspend fun exportCsv(includeUid: Boolean): String {
        val collections = dao.collections().associateBy { it.id }
        val header = listOf("collection", "nom", "categorie", "type_puce", "uid", "notes")
        val rows = dao.allItems().map { i ->
            listOf(collections[i.collectionId]?.name.orEmpty(), i.name, i.category, i.chipType,
                if (includeUid) i.uid.orEmpty() else "", i.notes)
        }
        return Csv.document(header, rows)
    }

    companion object {
        /** Filtre local : nom, catégorie, notes, UID (avec ou sans deux-points). */
        fun matches(item: InventoryItemEntity, query: String, category: String?): Boolean {
            if (category != null && item.category != category) return false
            val q = query.trim()
            if (q.isEmpty()) return true
            val compact = q.replace(":", "").replace(" ", "")
            return item.name.contains(q, true) || item.notes.contains(q, true) || item.category.contains(q, true) ||
                (item.uid?.replace(":", "")?.contains(compact, true) == true) || (item.uid?.contains(q, true) == true)
        }
    }
}
