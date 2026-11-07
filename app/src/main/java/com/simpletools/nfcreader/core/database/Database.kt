package com.simpletools.nfcreader.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

/** Modèle réutilisable (PRO). [fieldsJson] contient les champs saisis, sans secret persistant. */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    /** Type de contenu : text, url, contact, wifi. */
    val kind: String,
    val fieldsJson: String,
    val updatedAt: Long,
)

/** Collection d'inventaire (PRO). */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** Tag inventorié (PRO), rattaché à une collection. */
@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionId: Long,
    val name: String,
    val category: String,
    val notes: String,
    val uid: String?,
    val chipType: String,
    /** Lecture d'origine (SnapshotJson) si disponible. */
    val snapshotJson: String?,
    val addedAt: Long,
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

    @Query("SELECT * FROM readings ORDER BY scannedAt DESC")
    suspend fun all(): List<ReadingEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
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

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY category COLLATE NOCASE, name COLLATE NOCASE")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates ORDER BY category COLLATE NOCASE, name COLLATE NOCASE")
    suspend fun all(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: Long): TemplateEntity?

    @Insert suspend fun insert(entity: TemplateEntity): Long
    @Update suspend fun update(entity: TemplateEntity)
    @Query("DELETE FROM templates WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM templates") suspend fun deleteAll()
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    suspend fun collections(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun collection(id: Long): CollectionEntity?

    @Insert suspend fun insertCollection(entity: CollectionEntity): Long
    @Query("DELETE FROM collections WHERE id = :id") suspend fun deleteCollection(id: Long)
    @Query("DELETE FROM inventory_items WHERE collectionId = :id") suspend fun deleteItemsOf(id: Long)

    @Query("SELECT * FROM inventory_items WHERE collectionId = :collectionId ORDER BY name COLLATE NOCASE")
    fun observeItems(collectionId: Long): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items ORDER BY collectionId, name COLLATE NOCASE")
    suspend fun allItems(): List<InventoryItemEntity>

    @Insert suspend fun insertItem(entity: InventoryItemEntity): Long
    @Update suspend fun updateItem(entity: InventoryItemEntity)
    @Query("DELETE FROM inventory_items WHERE id = :id") suspend fun deleteItem(id: Long)
    @Query("DELETE FROM inventory_items") suspend fun deleteAllItems()
    @Query("DELETE FROM collections") suspend fun deleteAllCollections()
}

@Database(
    entities = [ReadingEntity::class, TemplateEntity::class, CollectionEntity::class, InventoryItemEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao
    abstract fun templateDao(): TemplateDao
    abstract fun inventoryDao(): InventoryDao

    companion object {
        /** V1 -> V2 : ajoute modèles, collections et inventaire. Les lectures existantes sont conservées. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `kind` TEXT NOT NULL, `fieldsJson` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `inventory_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `collectionId` INTEGER NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `notes` TEXT NOT NULL, `uid` TEXT, `chipType` TEXT NOT NULL, `snapshotJson` TEXT, `addedAt` INTEGER NOT NULL)")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "simpletools-nfc.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
