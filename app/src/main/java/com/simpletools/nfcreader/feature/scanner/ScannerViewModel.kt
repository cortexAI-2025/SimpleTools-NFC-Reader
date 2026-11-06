package com.simpletools.nfcreader.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpletools.nfcreader.CurrentScanHolder
import com.simpletools.nfcreader.core.models.TagSnapshot
import com.simpletools.nfcreader.core.nfc.NfcAvailability
import com.simpletools.nfcreader.core.nfc.NfcStatusProvider
import com.simpletools.nfcreader.core.nfc.TagAccess
import com.simpletools.nfcreader.core.nfc.TagInterpreter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScanPhase { IDLE, READING, DONE, FAILED }

data class ScannerUiState(
    val nfc: NfcAvailability,
    val phase: ScanPhase = ScanPhase.IDLE,
    val snapshot: TagSnapshot? = null,
    /** Incrémenté à chaque lecture terminée (permet à l'UI de réagir à un nouveau scan). */
    val scanCount: Int = 0,
)

/**
 * État du scanner, partagé par les écrans Scanner et Authentification. Vit au niveau de
 * l'activité : il survit aux rotations d'écran et au passage en arrière-plan.
 */
class ScannerViewModel(
    private val nfcStatus: NfcStatusProvider,
    private val holder: CurrentScanHolder,
    private val interpreter: TagInterpreter = TagInterpreter(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ScannerUiState(nfc = nfcStatus.status(), snapshot = holder.snapshot.value,
            phase = if (holder.snapshot.value != null) ScanPhase.DONE else ScanPhase.IDLE),
    )
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private var readJob: Job? = null

    /** À appeler à chaque retour au premier plan (ex. retour des réglages NFC). */
    fun refreshStatus(): NfcAvailability {
        val s = nfcStatus.status()
        _state.update { it.copy(nfc = s) }
        return s
    }

    /** Appelé depuis le thread du lecteur NFC : la lecture se fait hors du thread principal. */
    fun onTagDiscovered(tag: TagAccess) {
        readJob?.cancel()
        _state.update { it.copy(phase = ScanPhase.READING) }
        readJob = viewModelScope.launch {
            try {
                val snapshot = withContext(ioDispatcher) { interpreter.interpret(tag) }
                holder.snapshot.value = snapshot
                _state.update { it.copy(phase = ScanPhase.DONE, snapshot = snapshot, scanCount = it.scanCount + 1) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = ScanPhase.FAILED) }
            }
        }
    }

    fun clear() {
        readJob?.cancel()
        holder.snapshot.value = null
        _state.update { it.copy(phase = ScanPhase.IDLE, snapshot = null) }
    }
}
