package com.simpletools.nfcreader.feature.pro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpletools.nfcreader.core.license.LicenseManager
import com.simpletools.nfcreader.core.license.LicenseState
import com.simpletools.nfcreader.core.license.LicensePolicy
import com.simpletools.nfcreader.core.license.PlayCheck
import com.simpletools.nfcreader.core.license.PlayBillingGateway
import com.simpletools.nfcreader.core.license.ProductInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Écran PRO : prix réel fourni par Google Play, achat, restauration, état de licence. */
class ProViewModel(
    private val license: LicenseManager,
    private val billing: PlayBillingGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val licenseState: StateFlow<LicenseState> = license.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LicenseState())

    private val _product = MutableStateFlow<ProductInfo?>(null)
    val product: StateFlow<ProductInfo?> = _product.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Indique si la dernière vérification a réussi, pour afficher « hors ligne » sans retirer l'accès. */
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    init {
        viewModelScope.launch {
            _product.value = billing.productInfo()
            refresh()
        }
    }

    suspend fun refresh() {
        val check = license.refresh()
        _offline.value = check is PlayCheck.Unavailable
    }

    fun restore() {
        viewModelScope.launch {
            _busy.value = true
            val check = license.restore()
            _busy.value = false
            _offline.value = check is PlayCheck.Unavailable
            _message.value = when (check) {
                is PlayCheck.Answered -> if (check.owned) "restore-ok" else "restore-none"
                is PlayCheck.Unavailable -> "restore-unavailable"
            }
        }
    }

    fun needsReverifyHint(state: LicenseState) = LicensePolicy.needsReverifyHint(state, clock())

    fun consumeMessage() { _message.value = null }
}
