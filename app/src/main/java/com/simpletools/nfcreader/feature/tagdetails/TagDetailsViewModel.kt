package com.simpletools.nfcreader.feature.tagdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpletools.nfcreader.CurrentScanHolder
import com.simpletools.nfcreader.core.database.HistoryRepository
import com.simpletools.nfcreader.core.export.ExportService
import com.simpletools.nfcreader.core.models.TagSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface DetailsSource {
    data object Current : DetailsSource
    data class Saved(val id: Long) : DetailsSource
}

data class DetailsUiState(
    val loading: Boolean = true,
    val snapshot: TagSnapshot? = null,
    val savedId: Long? = null,
    val name: String = "",
    val notes: String = "",
)

sealed interface DetailsEvent {
    data object Saved : DetailsEvent
    data object Deleted : DetailsEvent
    data class Share(val file: File, val mime: String) : DetailsEvent
    data object Failed : DetailsEvent
    data object InventoryAdded : DetailsEvent
}

class TagDetailsViewModel(
    private val source: DetailsSource,
    private val holder: CurrentScanHolder,
    private val history: HistoryRepository,
    private val exporter: ExportService,
    private val inventory: com.simpletools.nfcreader.core.inventory.InventoryRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()
    private val _events = Channel<DetailsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            when (source) {
                DetailsSource.Current -> _state.value = DetailsUiState(loading = false, snapshot = holder.snapshot.value)
                is DetailsSource.Saved -> {
                    val saved = history.get(source.id)
                    _state.value = if (saved == null) DetailsUiState(loading = false)
                    else DetailsUiState(false, saved.snapshot, saved.entity.id, saved.entity.name, saved.entity.notes)
                }
            }
        }
    }

    /** Enregistrement volontaire (jamais automatique). */
    fun save(name: String, notes: String, includeUid: Boolean) {
        val snap = _state.value.snapshot ?: return
        viewModelScope.launch {
            try {
                val id = history.save(snap, name, notes, includeUid)
                _state.update { it.copy(savedId = id, name = name.trim(), notes = notes.trim()) }
                _events.send(DetailsEvent.Saved)
            } catch (e: Exception) {
                _events.send(DetailsEvent.Failed)
            }
        }
    }

    fun rename(name: String) {
        val id = _state.value.savedId ?: return
        viewModelScope.launch {
            history.rename(id, name)
            _state.update { it.copy(name = name.trim()) }
        }
    }

    fun updateNotes(notes: String) {
        val id = _state.value.savedId ?: return
        viewModelScope.launch {
            history.updateNotes(id, notes)
            _state.update { it.copy(notes = notes.trim()) }
        }
    }

    fun delete() {
        val id = _state.value.savedId ?: return
        viewModelScope.launch {
            history.delete(id)
            _events.send(DetailsEvent.Deleted)
        }
    }

    fun clipboardText(includeUid: Boolean): String? {
        val s = _state.value
        val snap = s.snapshot ?: return null
        return exporter.clipboardText(snap, s.name, s.notes, includeUid)
    }

    fun exportJson(includeUid: Boolean) = export("application/json") { snap, s -> exporter.createJsonFile(snap, s.name, s.notes, includeUid) }

    /** Export CSV (PRO). */
    fun exportCsv(includeUid: Boolean) = export("text/csv") { snap, s -> exporter.createCsvFile(snap, s.name, includeUid) }

    /** Ajout à une collection d'inventaire (PRO), déclenché par l'utilisateur uniquement. */
    fun addToInventory(collectionId: Long, category: String, includeUid: Boolean) {
        val s = _state.value
        val snap = s.snapshot ?: return
        viewModelScope.launch {
            try {
                inventory.addItem(
                    collectionId = collectionId,
                    name = s.name.ifBlank { snap.family.label ?: "Tag" },
                    category = category,
                    notes = s.notes,
                    uid = if (includeUid) snap.uidHex else null,
                    chipType = snap.nfcType ?: snap.family.label ?: "?",
                    snapshotJson = com.simpletools.nfcreader.core.export.SnapshotJson.encode(snap, includeUid),
                )
                _events.send(DetailsEvent.InventoryAdded)
            } catch (e: Exception) {
                _events.send(DetailsEvent.Failed)
            }
        }
    }

    fun exportPdf(includeUid: Boolean) = export("application/pdf") { snap, s -> exporter.createPdfFile(snap, s.name, s.notes, includeUid) }

    private fun export(mime: String, block: (TagSnapshot, DetailsUiState) -> File) {
        val s = _state.value
        val snap = s.snapshot ?: return
        viewModelScope.launch {
            try {
                val file = withContext(ioDispatcher) { block(snap, s) }
                _events.send(DetailsEvent.Share(file, mime))
            } catch (e: Exception) {
                _events.send(DetailsEvent.Failed)
            }
        }
    }
}
