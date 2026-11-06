package com.simpletools.nfcreader.core.database

import com.simpletools.nfcreader.core.export.SnapshotJson
import com.simpletools.nfcreader.core.models.TagSnapshot
import kotlinx.coroutines.flow.Flow

data class SavedReading(val entity: ReadingEntity, val snapshot: TagSnapshot)

/** Historique local. Toute écriture est déclenchée par une action explicite de l'utilisateur. */
class HistoryRepository(private val dao: ReadingDao, private val clock: () -> Long = System::currentTimeMillis) {

    fun observe(query: String): Flow<List<ReadingEntity>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return dao.observeAll()
        val like = "%" + escapeLike(trimmed) + "%"
        val compact = "%" + escapeLike(trimmed.replace(":", "").replace(" ", "")) + "%"
        return dao.search(like, compact)
    }

    suspend fun save(snapshot: TagSnapshot, name: String, notes: String, includeUid: Boolean): Long {
        val stored = if (includeUid) snapshot else snapshot.withoutUid()
        return dao.insert(
            ReadingEntity(
                scannedAt = snapshot.scannedAt,
                savedAt = clock(),
                name = name.trim(),
                chipType = snapshot.family.label ?: "?",
                uid = stored.uidHex,
                technologies = snapshot.technologies.joinToString(","),
                snapshotJson = SnapshotJson.encode(stored, includeUid = includeUid),
                notes = notes.trim(),
            ),
        )
    }

    suspend fun get(id: Long): SavedReading? {
        val e = dao.get(id) ?: return null
        return runCatching { SavedReading(e, SnapshotJson.decode(e.snapshotJson)) }.getOrNull()
    }

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())
    suspend fun updateNotes(id: Long, notes: String) = dao.updateNotes(id, notes.trim())
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun count(): Int = dao.count()

    companion object {
        fun escapeLike(s: String): String = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
