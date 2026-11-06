package com.simpletools.nfcreader.feature.authentication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.core.nfc.NfcReaderController
import com.simpletools.nfcreader.feature.scanner.NfcReaderEffect
import com.simpletools.nfcreader.feature.scanner.ScanStatusPanel
import com.simpletools.nfcreader.feature.scanner.ScannerViewModel
import com.simpletools.nfcreader.ui.components.AppScaffold
import com.simpletools.nfcreader.ui.components.InfoRow
import com.simpletools.nfcreader.ui.components.NoticeCard
import com.simpletools.nfcreader.ui.components.ScreenColumn
import com.simpletools.nfcreader.ui.components.SectionCard
import com.simpletools.nfcreader.ui.components.rememberConfirmedUrlOpener
import com.simpletools.nfcreader.ui.components.warningRes

@Composable
fun AuthenticationScreen(
    vm: ScannerViewModel,
    controller: NfcReaderController,
    onBack: () -> Unit,
    onDetails: () -> Unit,
) {
    NfcReaderEffect(vm, controller)
    val state by vm.state.collectAsStateWithLifecycle()
    val openUrl = rememberConfirmedUrlOpener()
    val analysis = remember(state.snapshot) { state.snapshot?.let(ProductAuthAnalyzer::analyze) }

    AppScaffold(stringResource(R.string.auth_title), onBack) {
        ScreenColumn {
            NoticeCard(stringResource(R.string.auth_disclaimer), title = stringResource(R.string.auth_disclaimer_title))
            ScanStatusPanel(state, Modifier.fillMaxWidth())

            if (analysis != null && state.snapshot != null) {
                SectionCard(stringResource(R.string.auth_result_title)) {
                    Text(
                        stringResource(
                            when (analysis.verdict) {
                                AuthVerdict.NO_PUBLIC_DATA -> R.string.auth_verdict_no_data
                                AuthVerdict.NO_URL -> R.string.auth_verdict_no_url
                                AuthVerdict.URL_OFFICIAL_DOMAIN -> R.string.auth_verdict_official_domain
                                AuthVerdict.URL_UNRECOGNIZED_DOMAIN -> R.string.auth_verdict_unrecognized
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(stringResource(R.string.auth_not_proof), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                analysis.urls.forEach { u ->
                    SectionCard(stringResource(R.string.auth_url_found)) {
                        Text(u.url, style = MaterialTheme.typography.bodyLarge)
                        if (u.brand != null) InfoRow(stringResource(R.string.auth_domain_match), stringResource(R.string.auth_domain_match_value, u.brand.name))
                        u.info?.warnings?.forEach { w ->
                            Text("⚠ " + stringResource(warningRes(w)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (u.info == null) {
                            Text(stringResource(R.string.link_not_openable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        } else {
                            OutlinedButton(onClick = { openUrl(u.url) }) { Text(stringResource(R.string.open_link_button)) }
                        }
                    }
                }
                OutlinedButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.details_save_export)) }
            }

            SectionCard(stringResource(R.string.auth_official_services)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.auth_official_body), style = MaterialTheme.typography.bodyMedium)
                    BrandRegistry.all.forEach { b ->
                        Button(onClick = { openUrl(b.officialUrl) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.auth_open_official, b.name))
                        }
                        Text(b.officialUrl, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(stringResource(R.string.auth_no_affiliation), style = MaterialTheme.typography.bodySmall)
        }
    }
}
