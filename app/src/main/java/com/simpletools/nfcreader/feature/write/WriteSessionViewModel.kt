package com.simpletools.nfcreader.feature.write

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpletools.nfcreader.core.nfc.AndroidTagAccess
import com.simpletools.nfcreader.core.nfc.TagAccess
import com.simpletools.nfcreader.core.write.AndroidWriteOps
import com.simpletools.nfcreader.core.write.WriteEngine
import com.simpletools.nfcreader.core.write.WriteOutcome
import com.simpletools.nfcreader.core.write.WritePayload
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SessionPhase { LOCKED, AWAITING_TAG, CONFIRM, WRITING, FINISHED }

data class ItemResult(val index: Int, val summary: String, val outcome: WriteOutcome?, val skipped: Boolean = false)

data class SessionUi(
    val phase: SessionPhase,
    val index: Int = 0,
    val total: Int = 0,
    val current: WritePayload? = null,
    /** Vrai si le tag porte déjà un message : l'utilisateur doit confirmer le remplacement. */
    val replacesExisting: Boolean = false,
    val existingDescription: String = "",
    val tagLostHint: Boolean = false,
    val results: List<ItemResult> = emptyList(),
)

/**
 * Écriture NFC en série assistée. UN tag à la fois : chaque écriture exige une confirmation,
 * puis une vérification par relecture. Rien n'est écrit sans confirmation explicite.
 */
class WriteSessionViewModel(
    private val items: List<WritePayload>,
    private val engine: WriteEngine,
    private val isPro: () -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        SessionUi(
            phase = if (!isPro()) SessionPhase.LOCKED else if (items.isEmpty()) SessionPhase.FINISHED else SessionPhase.AWAITING_TAG,
            total = items.size,
            current = items.firstOrNull(),
        ),
    )
    val ui: StateFlow<SessionUi> = _ui.asStateFlow()

    private var pendingTag: Tag? = null
    private var pendingExisting = false

    /** Appelé par le lecteur NFC. Ignoré hors de l'étape d'attente d'un tag. */
    fun onTag(tag: TagAccess) {
        if (_ui.value.phase != SessionPhase.AWAITING_TAG) return
        val raw = (tag as? AndroidTagAccess)?.tag ?: return
        val payload = _ui.value.current ?: return
        viewModelScope.launch {
            val inspected = withContext(ioDispatcher) {
                runCatching {
                    val ops = AndroidWriteOps(raw)
                    val info = ops.inspect()
                    val existing = if (info.hasContent) ops.readRecords() else null
                    Triple(info.hasContent, existing, info.writable)
                }
            }
            if (_ui.value.phase != SessionPhase.AWAITING_TAG) return@launch
            inspected.onSuccess { (hasContent, existing, _) ->
                pendingTag = raw
                pendingExisting = hasContent
                _ui.update {
                    it.copy(phase = SessionPhase.CONFIRM, replacesExisting = hasContent,
                        existingDescription = if (hasContent) WriteEngine.describeExisting(existing) else "",
                        tagLostHint = false)
                }
            }.onFailure {
                _ui.update { it.copy(tagLostHint = true) }
            }
        }
    }

    /** Confirmation explicite de l'utilisateur : déclenche l'écriture puis la vérification. */
    fun confirm() {
        val state = _ui.value
        val tag = pendingTag ?: return
        val payload = state.current ?: return
        if (state.phase != SessionPhase.CONFIRM) return
        if (!isPro()) { _ui.update { it.copy(phase = SessionPhase.LOCKED) }; return }
        _ui.update { it.copy(phase = SessionPhase.WRITING) }
        val overwrite = pendingExisting
        viewModelScope.launch {
            val outcome = withContext(ioDispatcher) {
                engine.write(AndroidWriteOps(tag), payload, overwriteExisting = overwrite)
            }
            pendingTag = null
            pendingExisting = false
            record(payload, outcome, skipped = false)
        }
    }

    /** Passe le tag courant sans écrire. */
    fun skip() {
        if (_ui.value.phase != SessionPhase.AWAITING_TAG && _ui.value.phase != SessionPhase.CONFIRM) return
        pendingTag = null
        pendingExisting = false
        record(_ui.value.current ?: return, null, skipped = true)
    }

    /** Annule la confirmation et revient à l'attente : aucune écriture. */
    fun cancelConfirm() {
        if (_ui.value.phase != SessionPhase.CONFIRM) return
        pendingTag = null
        pendingExisting = false
        _ui.update { it.copy(phase = SessionPhase.AWAITING_TAG, replacesExisting = false, existingDescription = "") }
    }

    private fun record(payload: WritePayload, outcome: WriteOutcome?, skipped: Boolean) {
        _ui.update { s ->
            val result = ItemResult(s.index, WritePayload.summary(payload), outcome, skipped)
            val next = s.index + 1
            if (next >= s.total) {
                s.copy(phase = SessionPhase.FINISHED, results = s.results + result, replacesExisting = false, existingDescription = "")
            } else {
                s.copy(phase = SessionPhase.AWAITING_TAG, index = next, current = items[next], results = s.results + result,
                    replacesExisting = false, existingDescription = "", tagLostHint = false)
            }
        }
    }
}
