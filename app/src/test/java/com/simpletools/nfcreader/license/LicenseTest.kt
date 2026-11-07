package com.simpletools.nfcreader.license

import com.simpletools.nfcreader.core.license.BillingGateway
import com.simpletools.nfcreader.core.license.LicenseManager
import com.simpletools.nfcreader.core.license.LicensePolicy
import com.simpletools.nfcreader.core.license.LicenseState
import com.simpletools.nfcreader.core.license.LicenseStorage
import com.simpletools.nfcreader.core.license.PlayCheck
import com.simpletools.nfcreader.core.license.ProductInfo
import com.simpletools.nfcreader.core.license.PurchaseEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Faux stockage et faux Google Play : aucun accès réseau réel, aucune licence simulée en production. */
class FakeStorage(initial: LicenseState = LicenseState()) : LicenseStorage {
    val flow = MutableStateFlow(initial)
    override val state: Flow<LicenseState> get() = flow
    override suspend fun save(state: LicenseState) { flow.value = state }
}

class FakeBilling(
    var check: () -> PlayCheck = { PlayCheck.Answered(owned = false, pending = false) },
    var ack: Boolean = true,
) : BillingGateway {
    var acknowledged = mutableListOf<String>()
    override suspend fun productInfo(): ProductInfo? = ProductInfo("2,99 €", "PRO")
    override suspend fun checkOwned(): PlayCheck = check()
    override suspend fun acknowledge(token: String): Boolean { acknowledged += token; return ack }
}

class LicenseTest {
    private val now = 1_700_000_000_000L

    @Test fun `achat non acquitte - acquittement puis acces accorde`() = runBlocking {
        val billing = FakeBilling()
        val store = FakeStorage()
        val lm = LicenseManager(billing, store, clock = { now })
        lm.onPurchaseEvent(PurchaseEvent.Purchased("tok", acknowledged = false))
        assertEquals(listOf("tok"), billing.acknowledged)
        assertTrue(lm.state.value.isPro)
        assertTrue(store.flow.value.owned)
    }

    @Test fun `echec d'acquittement - acces NON accorde`() = runBlocking {
        val lm = LicenseManager(FakeBilling(ack = false), FakeStorage(), clock = { now })
        val out = lm.onPurchaseEvent(PurchaseEvent.Purchased("tok", acknowledged = false))
        assertEquals(PurchaseEvent.Error("acknowledge-failed"), out)
        assertFalse(lm.state.value.isPro)
    }

    @Test fun `achat deja acquitte - acces accorde sans nouvel acquittement`() = runBlocking {
        val billing = FakeBilling()
        val lm = LicenseManager(billing, FakeStorage(), clock = { now })
        lm.onPurchaseEvent(PurchaseEvent.Purchased("tok", acknowledged = true))
        assertTrue(billing.acknowledged.isEmpty())
        assertTrue(lm.state.value.isPro)
    }

    @Test fun `achat en attente - pas d'acces, indication pending`() = runBlocking {
        val lm = LicenseManager(FakeBilling(), FakeStorage(), clock = { now })
        lm.onPurchaseEvent(PurchaseEvent.Pending("tok"))
        assertFalse(lm.state.value.isPro)
        assertTrue(lm.state.value.pending)
    }

    @Test fun `annulation utilisateur - etat inchange`() = runBlocking {
        val lm = LicenseManager(FakeBilling(), FakeStorage(), clock = { now })
        lm.onPurchaseEvent(PurchaseEvent.Cancelled)
        assertEquals(LicenseState(), lm.state.value)
    }

    @Test fun `hors ligne - la licence deja acquise reste active au demarrage`() = runBlocking {
        val store = FakeStorage(LicenseState(owned = true, lastVerifiedAt = now - 1000))
        val billing = FakeBilling(check = { PlayCheck.Unavailable("network") })
        val lm = LicenseManager(billing, store, clock = { now })
        lm.load()
        assertTrue(lm.state.value.isPro)
        val check = lm.refresh()
        assertTrue(check is PlayCheck.Unavailable)
        assertTrue("un echec reseau ne retire jamais l'acces", lm.state.value.isPro)
    }

    @Test fun `remboursement detecte a la verification en ligne - acces retire`() = runBlocking {
        val store = FakeStorage(LicenseState(owned = true, lastVerifiedAt = now - 1000))
        val lm = LicenseManager(FakeBilling(check = { PlayCheck.Answered(owned = false, pending = false) }), store, clock = { now })
        lm.load()
        lm.refresh()
        assertFalse(lm.state.value.isPro)
        assertFalse(store.flow.value.owned)
    }

    @Test fun `restauration apres reinstallation - achat retrouve`() = runBlocking {
        val lm = LicenseManager(FakeBilling(check = { PlayCheck.Answered(owned = true, pending = false) }), FakeStorage(), clock = { now })
        assertEquals(PlayCheck.Answered(true, false), lm.restore())
        assertTrue(lm.state.value.isPro)
    }

    @Test fun `politique - indice de reverification apres 30 jours seulement`() {
        val s = LicenseState(owned = true, lastVerifiedAt = now)
        assertFalse(LicensePolicy.needsReverifyHint(s, now + 29L * 24 * 3600 * 1000))
        assertTrue(LicensePolicy.needsReverifyHint(s, now + 31L * 24 * 3600 * 1000))
        assertFalse(LicensePolicy.needsReverifyHint(LicenseState(), now + 99L * 24 * 3600 * 1000))
    }
}
