package com.simpletools.nfcreader.feature.info

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.core.reader.ReaderCatalog
import com.simpletools.nfcreader.core.reader.ReaderKind
import com.simpletools.nfcreader.core.reader.ReaderSupport
import com.simpletools.nfcreader.ui.components.AppScaffold
import com.simpletools.nfcreader.ui.components.NoticeCard
import com.simpletools.nfcreader.ui.components.ScreenColumn
import com.simpletools.nfcreader.ui.components.SectionCard

@Composable
fun InfoScreen(onBack: () -> Unit) {
    AppScaffold(stringResource(R.string.info_title), onBack) {
        ScreenColumn {
            SectionCard(stringResource(R.string.info_reads_title)) { Text(stringResource(R.string.info_reads_body)) }
            SectionCard(stringResource(R.string.info_not_title)) { Text(stringResource(R.string.info_not_body)) }
            NoticeCard(stringResource(R.string.info_animal_body), title = stringResource(R.string.info_animal_title))
            SectionCard(stringResource(R.string.info_readers_title)) {
                ReaderCatalog.all.forEach { r ->
                    val name = when (r.kind) {
                        ReaderKind.BUILT_IN_NFC -> R.string.reader_builtin
                        ReaderKind.BLUETOOTH_RFID -> R.string.reader_bluetooth
                        ReaderKind.USB_C_RFID -> R.string.reader_usb
                    }
                    val status = if (r.support == ReaderSupport.SUPPORTED) R.string.reader_supported else R.string.reader_planned
                    Text(stringResource(name) + " · ${r.kind.frequency} · " + stringResource(status), style = MaterialTheme.typography.bodyMedium)
                }
            }
            SectionCard(stringResource(R.string.info_auth_title)) { Text(stringResource(R.string.info_auth_body)) }
        }
    }
}
