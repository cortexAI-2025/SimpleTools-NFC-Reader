package com.simpletools.nfcreader.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "readings")
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scannedAt: Long,
    val savedAt: Long,
    val name: String,
    val chipType: String,
    /** UID hexadécimal, uniquement si l'utilisateur a choisi de l'enregistrer. */
    val uid: String?,
    val technologies: String,
    /** Instantané JSON (SnapshotJson). */
    val snapshotJson: String,
    val notes: String,
)

@Dao
interface ReadingDao {
    @Query("SELECT * FROM readings ORDER BY scannedAt DESC")
    fun observeAll(): Flow<List<ReadingEntity>>

    @Query(
        """SELECT * FROM readings WHERE
           name LIKE :q ESCAPE '\' OR chipType LIKE :q ESCAPE '\' OR notes LIKE :q ESCAPE '\'
           OR technologies LIKE :q ESCAPE '\'
           OR uid LIKE :q ESCAPE '\' OR REPLACE(uid, ':', '') LIKE :qCompact ESCAPE '\'
           ORDER BY scannedAt DESC""",
    )
    fun search(q: String, qCompact: String): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM readings WHERE id = :id")
    suspend fun get(id: Long): ReadingEntity?

    @Insert
    suspend fun insert(entity: ReadingEntity): Long

    @Query("UPDATE readings SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE readings SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String)

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM readings")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM readings")
    suspend fun count(): Int
}

@Database(entities = [ReadingEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "simpletools-nfc.db").build()
    }
}
