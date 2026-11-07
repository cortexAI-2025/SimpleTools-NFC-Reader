package com.simpletools.nfcreader.core.license

/** Achat PRO : identifiant du produit Google Play (produit non consommable). */
const val PRO_PRODUCT_ID = "simpletools_nfc_pro_lifetime"

data class ProductInfo(val formattedPrice: String, val title: String)

sealed interface PurchaseEvent {
    /** Achat accepté par Google Play, à acquitter puis vérifier. */
    data class Purchased(val token: String, val acknowledged: Boolean) : PurchaseEvent
    data class Pending(val token: String) : PurchaseEvent
    data object Cancelled : PurchaseEvent
    data class Error(val reason: String) : PurchaseEvent
}

/**
 * Accès à Google Play Billing. L'implémentation réelle est dans PlayBillingGateway ; les tests utilisent un faux.
 * Aucune licence n'est jamais accordée par ce code sans réponse de Google Play.
 */
interface BillingGateway {
    /** Prix et titre réels renvoyés par Google Play, null si indisponible. */
    suspend fun productInfo(): ProductInfo?
    /** Interroge Google Play pour les achats existants du compte. Lève [BillingUnavailable] si injoignable. */
    suspend fun checkOwned(): PlayCheck
    suspend fun acknowledge(token: String): Boolean
}

class BillingUnavailable(message: String) : Exception(message)
