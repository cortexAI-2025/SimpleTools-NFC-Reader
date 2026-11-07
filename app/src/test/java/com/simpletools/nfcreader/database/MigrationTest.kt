package com.simpletools.nfcreader.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simpletools.nfcreader.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration V1 -> V2 : les lectures enregistrées par la version 1.0 doivent survivre à la mise à jour.
 * La base V1 est créée à la main avec le même schéma que la version 1.0 (app/schemas/.../1.json).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @Test fun `les lectures V1 sont conservees apres migration vers V2`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.getDatabasePath("simpletools-nfc-migration.db")
        file.parentFile?.mkdirs(); file.delete()

        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.execSQL("CREATE TABLE IF NOT EXISTS `readings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `scannedAt` INTEGER NOT NULL, `savedAt` INTEGER NOT NULL, `name` TEXT NOT NULL, `chipType` TEXT NOT NULL, `uid` TEXT, `technologies` TEXT NOT NULL, `snapshotJson` TEXT NOT NULL, `notes` TEXT NOT NULL)")
        raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        raw.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${V1_IDENTITY_HASH}')")
        raw.insert("readings", null, ContentValues().apply {
            put("scannedAt", 1L); put("savedAt", 2L); put("name", "Ancienne lecture"); put("chipType", "NTAG")
            put("uid", "04:A3"); put("technologies", "NfcA,Ndef"); put("snapshotJson", "{}"); put("notes", "garde")
        })
        raw.version = 1
        raw.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.name)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val readings = db.readingDao().all()
            assertEquals(1, readings.size)
            assertEquals("Ancienne lecture", readings.single().name)
            assertEquals("garde", readings.single().notes)
            // Les nouvelles tables existent et sont vides.
            assertEquals(0, db.templateDao().all().size)
            assertEquals(0, db.inventoryDao().collections().size)
        } finally {
            db.close()
            file.delete()
        }
    }

    private companion object {
        /** Identité du schéma V1 telle qu'exportée par Room (app/schemas/.../1.json). */
        val V1_IDENTITY_HASH: String = "0a3c041f61f7f32a851d870f5e6e8d8a"
    }
}
