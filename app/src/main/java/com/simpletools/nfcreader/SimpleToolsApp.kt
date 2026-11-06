package com.simpletools.nfcreader

import android.app.Application
import android.content.Context
import com.simpletools.nfcreader.core.database.AppDatabase
import com.simpletools.nfcreader.core.database.HistoryRepository
import com.simpletools.nfcreader.core.export.ExportService
import com.simpletools.nfcreader.core.export.StringProvider
import com.simpletools.nfcreader.core.models.TagSnapshot
import com.simpletools.nfcreader.core.nfc.NfcReaderController
import com.simpletools.nfcreader.core.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** Dernière lecture en mémoire (jamais persistée automatiquement). */
class CurrentScanHolder {
    val snapshot = MutableStateFlow<TagSnapshot?>(null)
}

/** Injection de dépendances manuelle : simple et sans surcoût pour une application de cette taille. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val history: HistoryRepository by lazy { HistoryRepository(database.readingDao()) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val nfc: NfcReaderController by lazy { NfcReaderController(appContext) }
    val currentScan = CurrentScanHolder()
    val strings = StringProvider { id, args -> appContext.getString(id, *args) }
    val exporter: ExportService by lazy { ExportService(appContext, strings) }

    /** Efface historique, exports temporaires, lecture en mémoire et préférences. */
    suspend fun wipeAllLocalData() {
        history.deleteAll()
        exporter.clearExports()
        currentScan.snapshot.value = null
        settings.reset()
    }
}

class SimpleToolsApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
