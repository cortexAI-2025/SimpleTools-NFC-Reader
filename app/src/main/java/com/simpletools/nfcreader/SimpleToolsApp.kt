package com.simpletools.nfcreader

import android.app.Application
import android.content.Context
import com.simpletools.nfcreader.core.backup.BackupService
import com.simpletools.nfcreader.core.database.AppDatabase
import com.simpletools.nfcreader.core.database.HistoryRepository
import com.simpletools.nfcreader.core.export.ExportService
import com.simpletools.nfcreader.core.export.StringProvider
import com.simpletools.nfcreader.core.inventory.InventoryRepository
import com.simpletools.nfcreader.core.license.LicenseManager
import com.simpletools.nfcreader.core.license.LicenseStore
import com.simpletools.nfcreader.core.license.PlayBillingGateway
import com.simpletools.nfcreader.core.license.PurchaseEvent
import com.simpletools.nfcreader.core.models.TagSnapshot
import com.simpletools.nfcreader.core.nfc.NfcReaderController
import com.simpletools.nfcreader.core.settings.SettingsRepository
import com.simpletools.nfcreader.core.templates.TemplateRepository
import com.simpletools.nfcreader.core.write.WriteEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Dernière lecture en mémoire (jamais persistée automatiquement). */
class CurrentScanHolder {
    val snapshot = MutableStateFlow<TagSnapshot?>(null)
}

/** Injection de dépendances manuelle : simple et sans surcoût pour une application de cette taille. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val history: HistoryRepository by lazy { HistoryRepository(database.readingDao()) }
    val templates: TemplateRepository by lazy { TemplateRepository(database.templateDao()) }
    val inventory: InventoryRepository by lazy { InventoryRepository(database.inventoryDao()) }
    val backup: BackupService by lazy { BackupService(database) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val nfc: NfcReaderController by lazy { NfcReaderController(appContext) }
    val currentScan = CurrentScanHolder()
    val strings = StringProvider { id, args -> appContext.getString(id, *args) }
    val exporter: ExportService by lazy { ExportService(appContext, strings) }
    val writeEngine = WriteEngine()

    /** Achats Google Play. Les événements d'achat sont transmis au gestionnaire de licence. */
    val billing: PlayBillingGateway by lazy {
        PlayBillingGateway(appContext) { event -> appScope.launch { license.onPurchaseEvent(event) } }
    }
    val license: LicenseManager by lazy { LicenseManager(billing, LicenseStore(appContext)) }

    /** Efface historique, modèles, inventaire, exports, lecture en mémoire et préférences.
     *  La licence n'est PAS effacée : elle appartient au compte Google et est revérifiée. */
    suspend fun wipeAllLocalData() {
        history.deleteAll()
        database.templateDao().deleteAll()
        database.inventoryDao().deleteAllItems()
        database.inventoryDao().deleteAllCollections()
        exporter.clearExports()
        currentScan.snapshot.value = null
        settings.reset()
    }
}

class SimpleToolsApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
