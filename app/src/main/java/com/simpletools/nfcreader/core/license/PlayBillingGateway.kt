package com.simpletools.nfcreader.core.license

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Implémentation réelle Google Play Billing (aucune licence simulée). */
class PlayBillingGateway(
    context: android.content.Context,
    /** Reçoit les événements d'achat (achat, en attente, annulation, erreur) envoyés par Google Play. */
    private val onPurchaseEvent: (PurchaseEvent) -> Unit,
) : BillingGateway {

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private var details: ProductDetails? = null

    private suspend fun ensureConnected() {
        if (client.isReady) return
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) {
                        if (result.responseCode == BillingResponseCode.OK) cont.resume(Unit)
                        else cont.resumeWith(Result.failure(BillingUnavailable("setup:${result.responseCode}")))
                    }
                }
                override fun onBillingServiceDisconnected() { /* reconnexion à la demande */ }
            })
        }
    }

    override suspend fun productInfo(): ProductInfo? = try {
        ensureConnected()
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRO_PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()),
        ).build()
        val result = client.queryProductDetails(params)
        val d = result.productDetailsList?.firstOrNull()
        val offer = d?.oneTimePurchaseOfferDetails
        if (result.billingResult.responseCode != BillingResponseCode.OK || d == null || offer == null) {
            null
        } else {
            details = d
            ProductInfo(formattedPrice = offer.formattedPrice, title = d.title)
        }
    } catch (e: Exception) {
        null
    }

    override suspend fun checkOwned(): PlayCheck = try {
            ensureConnected()
            val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            val result = client.queryPurchasesAsync(params)
            if (result.billingResult.responseCode != BillingResponseCode.OK) {
                PlayCheck.Unavailable("query:${result.billingResult.responseCode}")
            } else {
                val mine = result.purchasesList.filter { PRO_PRODUCT_ID in it.products }
                val purchased = mine.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                val pending = mine.any { it.purchaseState == Purchase.PurchaseState.PENDING }
                PlayCheck.Answered(owned = purchased != null, pending = pending && purchased == null)
            }
        } catch (e: BillingUnavailable) {
            PlayCheck.Unavailable(e.message.orEmpty())
        } catch (e: Exception) {
            PlayCheck.Unavailable("exception")
        }

    override suspend fun acknowledge(token: String): Boolean = try {
        ensureConnected()
        val result = client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build())
        result.responseCode == BillingResponseCode.OK
    } catch (e: Exception) {
        false
    }

    /** Lance le flux d'achat Google Play. Le résultat arrive par le listener de l'activité. */
    fun launchPurchase(activity: Activity): Boolean {
        val d = details ?: return false
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()),
        ).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingResponseCode.OK
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> purchases.orEmpty().filter { PRO_PRODUCT_ID in it.products }.forEach { p ->
                when (p.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> onPurchaseEvent(PurchaseEvent.Purchased(p.purchaseToken, p.isAcknowledged))
                    Purchase.PurchaseState.PENDING -> onPurchaseEvent(PurchaseEvent.Pending(p.purchaseToken))
                    else -> onPurchaseEvent(PurchaseEvent.Error("state"))
                }
            }
            BillingResponseCode.USER_CANCELED -> onPurchaseEvent(PurchaseEvent.Cancelled)
            else -> onPurchaseEvent(PurchaseEvent.Error("billing:${result.responseCode}"))
        }
    }

    fun end() { runCatching { client.endConnection() } }
}
