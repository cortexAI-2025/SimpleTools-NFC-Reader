package com.simpletools.nfcreader.core.license

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Orchestre achat, acquittement, vérification et restauration. Source unique de l'état PRO. */
class LicenseManager(
    private val gateway: BillingGateway,
    private val store: LicenseStorage,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(LicenseState())
    val state: StateFlow<LicenseState> = _state.asStateFlow()

    /** À charger au démarrage : l'état local précédent reste valide hors ligne. */
    suspend fun load() {
        _state.value = store.state.first()
    }

    /** Vérifie auprès de Google Play. Un échec réseau ne retire jamais un accès déjà acquis. */
    suspend fun refresh(): PlayCheck {
        val check = try {
            gateway.checkOwned()
        } catch (e: BillingUnavailable) {
            PlayCheck.Unavailable(e.message.orEmpty())
        }
        val next = LicensePolicy.apply(_state.value, check, clock())
        if (next != _state.value) {
            _state.value = next
            store.save(next)
        }
        return check
    }

    /** Traite un événement d'achat issu de Google Play. */
    suspend fun onPurchaseEvent(event: PurchaseEvent): PurchaseEvent {
        when (event) {
            is PurchaseEvent.Purchased -> {
                if (!event.acknowledged && !gateway.acknowledge(event.token)) {
                    // Sans acquittement, Google Play rembourse après 3 jours : on n'accorde pas l'accès maintenant.
                    return PurchaseEvent.Error("acknowledge-failed")
                }
                commit(LicenseState(owned = true, lastVerifiedAt = clock(), pending = false))
            }
            is PurchaseEvent.Pending -> commit(_state.value.copy(pending = true))
            PurchaseEvent.Cancelled, is PurchaseEvent.Error -> Unit
        }
        return event
    }

    /** Restauration : identique à une vérification, déclenchée par l'utilisateur. */
    suspend fun restore(): PlayCheck = refresh()

    private suspend fun commit(next: LicenseState) {
        _state.value = next
        store.save(next)
    }
}
