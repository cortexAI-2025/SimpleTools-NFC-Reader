package com.simpletools.nfcreader.feature.pro

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.simpletools.nfcreader.AppContainer
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.core.license.PRO_PRODUCT_ID
import com.simpletools.nfcreader.ui.components.AppScaffold
import com.simpletools.nfcreader.ui.components.ScreenColumn
import com.simpletools.nfcreader.ui.components.SectionCard
import com.simpletools.nfcreader.ui.components.findActivity

@Composable
fun ProScreen(container: AppContainer, vm: ProViewModel, onBack: () -> Unit) {
    val license by vm.licenseState.collectAsStateWithLifecycle()
    val product by vm.product.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val msgs = mapOf(
        "restore-ok" to stringResource(R.string.restore_ok),
        "restore-none" to stringResource(R.string.restore_none),
        "restore-unavailable" to stringResource(R.string.restore_unavailable),
        "buy-unavailable" to stringResource(R.string.buy_unavailable),
    )
    LaunchedEffect(message) {
        message?.let { key -> msgs[key]?.let { snackbar.showSnackbar(it) }; vm.consumeMessage() }
    }

    AppScaffold(stringResource(R.string.pro_title), onBack, snackbar) {
        ScreenColumn {
            SectionCard(stringResource(R.string.license_section)) {
                if (license.isPro) {
                    Text(stringResource(R.string.license_active), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    if (vm.needsReverifyHint(license)) Text(stringResource(R.string.license_reverify_hint), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(stringResource(R.string.license_inactive))
                }
                if (license.pending) Text(stringResource(R.string.license_pending))
                if (offline) Text(stringResource(R.string.license_offline), style = MaterialTheme.typography.bodySmall)
            }
            SectionCard(stringResource(R.string.compare_title)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.compare_col_feature), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(2f))
                    Text(stringResource(R.string.compare_col_free), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.compare_col_pro), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                }
                listOf(
                    R.string.cmp_read to (true to true),
                    R.string.cmp_history to (true to true),
                    R.string.cmp_share to (true to true),
                    R.string.cmp_write to (false to true),
                    R.string.cmp_templates to (false to true),
                    R.string.cmp_hex to (false to true),
                    R.string.cmp_compare to (false to true),
                    R.string.cmp_csv to (false to true),
                    R.string.cmp_inventory to (false to true),
                    R.string.cmp_batch to (false to true),
                    R.string.cmp_backup to (false to true),
                ).forEach { (label, avail) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(2f))
                        Text(mark(avail.first), modifier = Modifier.weight(1f))
                        Text(mark(avail.second), modifier = Modifier.weight(1f))
                    }
                }
                Text(stringResource(R.string.cmp_free_forever), style = MaterialTheme.typography.bodySmall)
            }
            if (!license.isPro) {
                SectionCard(stringResource(R.string.buy_section)) {
                    Text(stringResource(R.string.buy_body))
                    Text(
                        stringResource(R.string.buy_price, product?.formattedPrice ?: "—"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (product == null) Text(stringResource(R.string.price_unavailable), style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            val activity = context.findActivity() as? Activity
                            val launched = activity != null && container.billing.launchPurchase(activity)
                            if (!launched) scope.launch { snackbar.showSnackbar(msgs.getValue("buy-unavailable")) }
                        },
                        enabled = !busy && product != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.unlock_pro)) }
                }
            }
            SectionCard(stringResource(R.string.restore_section)) {
                Text(stringResource(R.string.restore_body))
                OutlinedButton(onClick = vm::restore, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator() else Text(stringResource(R.string.restore_purchases))
                }
            }
            SectionCard(stringResource(R.string.refund_section)) {
                Text(stringResource(R.string.refund_body))
                Text("ID produit : $PRO_PRODUCT_ID", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun mark(available: Boolean): String = if (available) "✓" else "—"
