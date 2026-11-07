package com.simpletools.nfcreader.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.ui.components.AppScaffold
import com.simpletools.nfcreader.ui.components.ScreenColumn

@Composable
fun HomeScreen(
    isPro: Boolean,
    onScanner: () -> Unit,
    onWrite: () -> Unit,
    onHistory: () -> Unit,
    onTemplates: () -> Unit,
    onInventory: () -> Unit,
    onAuth: () -> Unit,
    onInfo: () -> Unit,
    onPro: () -> Unit,
    onSettings: () -> Unit,
) {
    AppScaffold(stringResource(R.string.app_name), onBack = null) {
        ScreenColumn {
            Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.bodyLarge)
            HomeCard(painterResource(R.drawable.ic_nfc_waves), R.string.home_scanner, R.string.home_scanner_desc, onScanner, primary = true)
            HomeCard(painterResource(R.drawable.ic_nfc_waves), R.string.home_write, if (isPro) R.string.home_write_desc else R.string.home_write_locked, onWrite)
            HomeCard(rememberVectorPainter(Icons.AutoMirrored.Filled.List), R.string.home_history, R.string.home_history_desc, onHistory)
            HomeCard(rememberVectorPainter(Icons.Default.Star), R.string.home_templates, if (isPro) R.string.home_templates_desc else R.string.home_templates_locked, onTemplates)
            HomeCard(rememberVectorPainter(Icons.Default.Build), R.string.home_inventory, if (isPro) R.string.home_inventory_desc else R.string.home_inventory_locked, onInventory)
            HomeCard(rememberVectorPainter(Icons.Default.ShoppingCart), R.string.home_auth, R.string.home_auth_desc, onAuth)
            HomeCard(rememberVectorPainter(Icons.Default.Info), R.string.home_info, R.string.home_info_desc, onInfo)
            HomeCard(rememberVectorPainter(Icons.Default.Star), R.string.home_pro, if (isPro) R.string.home_pro_active else R.string.home_pro_desc, onPro)
            HomeCard(rememberVectorPainter(Icons.Default.Settings), R.string.home_settings, R.string.home_settings_desc, onSettings)
            Text(stringResource(R.string.home_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeCard(icon: Painter, title: Int, desc: Int, onClick: () -> Unit, primary: Boolean = false) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
