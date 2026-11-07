package com.simpletools.nfcreader.feature.scanner

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.core.nfc.NfcAvailability
import com.simpletools.nfcreader.core.nfc.NfcReaderController
import com.simpletools.nfcreader.ui.components.AppScaffold
import com.simpletools.nfcreader.ui.components.NoticeCard
import com.simpletools.nfcreader.ui.components.ScreenColumn
import com.simpletools.nfcreader.ui.components.TagContent
import com.simpletools.nfcreader.ui.components.rememberConfirmedUrlOpener

@Composable
fun ScannerScreen(
    vm: ScannerViewModel,
    controller: NfcReaderController,
    maskIds: Boolean,
    isPro: Boolean,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
    onDetails: () -> Unit,
) {
    NfcReaderEffect(vm, controller)
    val state by vm.state.collectAsStateWithLifecycle()
    val openUrl = rememberConfirmedUrlOpener()

    AppScaffold(stringResource(R.string.scanner_title), onBack) {
        ScreenColumn {
            ScanStatusPanel(state, Modifier.fillMaxWidth())
            state.snapshot?.let { snap ->
                Button(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.details_save_export)) }
                TagContent(snap, maskIds, openUrl, isPro = isPro, onUnlock = onUnlock)
                OutlinedButton(onClick = vm::clear, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.new_scan)) }
            }
        }
    }
}

/** Panneau d'état NFC + animation de détection, partagé avec l'écran d'authentification. */
@Composable
fun ScanStatusPanel(state: ScannerUiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state.nfc) {
            NfcAvailability.ABSENT -> NoticeCard(
                title = stringResource(R.string.nfc_absent_title),
                text = stringResource(R.string.nfc_absent_body),
                isError = true,
            )
            NfcAvailability.DISABLED -> {
                NoticeCard(
                    title = stringResource(R.string.nfc_disabled_title),
                    text = stringResource(R.string.nfc_disabled_body),
                    isError = true,
                )
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_NFC_SETTINGS)
                    try { context.startActivity(intent) } catch (e: Exception) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                    }
                }) { Text(stringResource(R.string.open_nfc_settings)) }
            }
            NfcAvailability.ENABLED -> {
                if (state.phase == ScanPhase.READING) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.reading_in_progress), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                } else {
                    PulseAnimation()
                    Text(
                        stringResource(if (state.snapshot == null) R.string.approach_tag else R.string.approach_another),
                        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    )
                    Text(stringResource(R.string.approach_hint), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                if (state.phase == ScanPhase.FAILED) {
                    NoticeCard(stringResource(R.string.read_failed), isError = true)
                }
            }
        }
    }
}

@Composable
private fun PulseAnimation() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "p")
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(180.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            repeat(3) { i ->
                val p = (progress + i / 3f) % 1f
                drawCircle(color.copy(alpha = (1f - p) * 0.5f), radius = size.minDimension / 2 * (0.35f + 0.65f * p), center = center)
            }
        }
        Icon(
            painterResource(R.drawable.ic_nfc_waves),
            contentDescription = stringResource(R.string.cd_nfc_animation),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
    }
}
