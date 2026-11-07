package com.simpletools.nfcreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simpletools.nfcreader.R

/** Carte affichée à la place d'une fonction PRO quand la licence n'est pas active. */
@Composable
fun ProLockedCard(title: String, onUnlock: () -> Unit) {
    SectionCard(title) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pro_locked_body))
            Button(onClick = onUnlock) { Text(stringResource(R.string.unlock_pro)) }
        }
    }
}
