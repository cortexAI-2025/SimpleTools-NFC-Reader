package com.simpletools.nfcreader.feature.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simpletools.nfcreader.core.nfc.NfcAvailability
import com.simpletools.nfcreader.core.nfc.NfcReaderController
import com.simpletools.nfcreader.ui.components.findActivity

/**
 * Active le mode lecture NFC tant que l'écran est au premier plan (RESUMED) et le désactive
 * en pause. Réévalue l'état NFC au retour des réglages et quand l'utilisateur active/désactive
 * le NFC pendant que l'écran est visible.
 */
@Composable
fun NfcReaderEffect(vm: ScannerViewModel, controller: NfcReaderController) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentVm = rememberUpdatedState(vm)

    DisposableEffect(owner, context) {
        val activity = context.findActivity()
        fun start() {
            val status = currentVm.value.refreshStatus()
            if (activity != null && status == NfcAvailability.ENABLED) {
                controller.enable(activity) { tag -> currentVm.value.onTagDiscovered(tag) }
            } else if (activity != null) {
                controller.disable(activity)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> activity?.let(controller::disable)
                else -> Unit
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
                else currentVm.value.refreshStatus()
            }
        }
        ContextCompat.registerReceiver(
            context.applicationContext, receiver, IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            runCatching { context.applicationContext.unregisterReceiver(receiver) }
            activity?.let(controller::disable)
        }
    }
}
