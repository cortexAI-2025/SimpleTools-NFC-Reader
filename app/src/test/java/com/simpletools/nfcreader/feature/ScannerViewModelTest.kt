package com.simpletools.nfcreader.feature

import com.simpletools.nfcreader.CurrentScanHolder
import com.simpletools.nfcreader.Fixtures
import com.simpletools.nfcreader.core.models.ReadIssue
import com.simpletools.nfcreader.core.nfc.AccessError
import com.simpletools.nfcreader.core.nfc.NfcAvailability
import com.simpletools.nfcreader.core.nfc.NfcStatusProvider
import com.simpletools.nfcreader.core.nfc.TagInterpreter
import com.simpletools.nfcreader.feature.scanner.ScanPhase
import com.simpletools.nfcreader.feature.scanner.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private var hardware = NfcAvailability.ENABLED
    private val provider = NfcStatusProvider { hardware }
    private lateinit var holder: CurrentScanHolder

    @Before fun setUp() { Dispatchers.setMain(dispatcher); holder = CurrentScanHolder() }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm() = ScannerViewModel(provider, holder, TagInterpreter(), dispatcher)

    @Test fun `detection materielle - absence de NFC`() {
        hardware = NfcAvailability.ABSENT
        val vm = vm()
        assertEquals(NfcAvailability.ABSENT, vm.state.value.nfc)
        assertEquals(ScanPhase.IDLE, vm.state.value.phase)
        assertNull(vm.state.value.snapshot) // aucun résultat fictif
    }

    @Test fun `NFC present et active`() {
        assertEquals(NfcAvailability.ENABLED, vm().state.value.nfc)
    }

    @Test fun `NFC desactive puis active au retour des reglages`() {
        hardware = NfcAvailability.DISABLED
        val vm = vm()
        assertEquals(NfcAvailability.DISABLED, vm.state.value.nfc)
        hardware = NfcAvailability.ENABLED          // l'utilisateur active le NFC dans les réglages
        assertEquals(NfcAvailability.ENABLED, vm.refreshStatus())   // onResume
        assertEquals(NfcAvailability.ENABLED, vm.state.value.nfc)
    }

    @Test fun `un scan reussi produit un resultat conserve en memoire seulement`() {
        val vm = vm()
        vm.onTagDiscovered(Fixtures.ndefTag(Fixtures.textRecord("salut")))
        val s = vm.state.value
        assertEquals(ScanPhase.DONE, s.phase)
        assertEquals("salut", s.snapshot!!.ndefRecords.single().text)
        assertEquals(1, s.scanCount)
        assertSame(s.snapshot, holder.snapshot.value)
    }

    @Test fun `tag retire en cours de lecture - resultat partiel et pas de crash`() {
        val vm = vm()
        vm.onTagDiscovered(Fixtures.FakeTag(ndef = Fixtures.FakeNdef(failure = AccessError.TAG_LOST)))
        assertEquals(ScanPhase.DONE, vm.state.value.phase)
        assertEquals(listOf(ReadIssue.TAG_LOST), vm.state.value.snapshot!!.issues)
    }

    @Test fun `rotation d'ecran - l'etat du ViewModel est conserve`() {
        val vm = vm()
        vm.onTagDiscovered(Fixtures.ndefTag(Fixtures.uriRecord("example.com")))
        // Une rotation recrée l'UI mais conserve le même ViewModel : on relit simplement l'état.
        val afterRotation = vm.state.value
        assertEquals("https://example.com", afterRotation.snapshot!!.ndefRecords.single().uri)
        assertEquals(ScanPhase.DONE, afterRotation.phase)
    }

    @Test fun `reprise apres arriere-plan - dernier resultat conserve et statut NFC reevalue`() {
        val vm = vm()
        vm.onTagDiscovered(Fixtures.ndefTag(Fixtures.textRecord("avant")))
        hardware = NfcAvailability.DISABLED      // NFC coupé pendant que l'app est en arrière-plan
        vm.refreshStatus()                       // ON_RESUME
        assertEquals(NfcAvailability.DISABLED, vm.state.value.nfc)
        assertEquals("avant", vm.state.value.snapshot!!.ndefRecords.single().text)
    }

    @Test fun `nouvelle instance de ViewModel (process recree) reprend la lecture en memoire`() {
        vm().onTagDiscovered(Fixtures.ndefTag(Fixtures.textRecord("persist")))
        val second = vm()
        assertNotNull(second.state.value.snapshot)
        assertEquals(ScanPhase.DONE, second.state.value.phase)
    }

    @Test fun `deux scans successifs - le second remplace le premier`() {
        val vm = vm()
        vm.onTagDiscovered(Fixtures.ndefTag(Fixtures.textRecord("un")))
        vm.onTagDiscovered(Fixtures.ndefTag(Fixtures.textRecord("deux")))
        assertEquals("deux", vm.state.value.snapshot!!.ndefRecords.single().text)
        assertEquals(2, vm.state.value.scanCount)
    }

    @Test fun `clear efface la lecture en memoire`() {
        val vm = vm()
        vm.onTagDiscovered(Fixtures.ndefTag(Fixtures.textRecord("x")))
        vm.clear()
        assertNull(vm.state.value.snapshot); assertNull(holder.snapshot.value)
    }

    @Test fun `erreur inattendue de l'interpreteur - etat FAILED sans crash`() {
        val boom = TagInterpreter(clock = { throw IllegalStateException("boom") })
        val vm = ScannerViewModel(provider, holder, boom, dispatcher)
        vm.onTagDiscovered(Fixtures.FakeTag())
        assertEquals(ScanPhase.FAILED, vm.state.value.phase)
    }
}
