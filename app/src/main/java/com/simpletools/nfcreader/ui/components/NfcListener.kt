package com.simpletools.nfcreader.ui.components

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
import com.simpletools.nfcreader.core.nfc.NfcReaderController
import com.simpletools.nfcreader.core.nfc.TagAccess

/**
 * Mode lecture NFC actif pendant que l'écran est RESUMED et que [active] est vrai.
 * Réutilisé par les écrans d'écriture : une seule connexion au tag à la fois.
 */
@Composable
fun NfcListener(active: Boolean, controller: NfcReaderController, onTag: (TagAccess) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentOnTag = rememberUpdatedState(onTag)
    val isActive = rememberUpdatedState(active)

    DisposableEffect(owner, context) {
        val activity = context.findActivity()
        fun start() {
            if (activity != null && isActive.value) {
                controller.enable(activity) { tag -> currentOnTag.value(tag) }
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
            }
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            runCatching { context.applicationContext.unregisterReceiver(receiver) }
            activity?.let(controller::disable)
        }
    }
    // Réévalue quand [active] change pendant que l'écran est visible.
    DisposableEffect(active) {
        val activity = context.findActivity()
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && activity != null) {
            if (active) controller.enable(activity) { tag -> currentOnTag.value(tag) } else controller.disable(activity)
        }
        onDispose { }
    }
}
