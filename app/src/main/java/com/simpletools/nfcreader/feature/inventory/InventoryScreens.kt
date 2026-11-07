package com.simpletools.nfcreader.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simpletools.nfcreader.AppContainer
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.core.database.CollectionEntity
import com.simpletools.nfcreader.core.database.InventoryItemEntity
import com.simpletools.nfcreader.core.inventory.InventoryRepository
import com.simpletools.nfcreader.ui.components.AppScaffold
import com.simpletools.nfcreader.ui.components.ProLockedCard
import com.simpletools.nfcreader.ui.components.ScreenColumn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Liste des collections d'inventaire (PRO). */
@Composable
fun InventoryScreen(container: AppContainer, isPro: Boolean, onUnlock: () -> Unit, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val collections by container.inventory.observeCollections().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var newName by rememberSaveable { mutableStateOf("") }
    var toDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    AppScaffold(stringResource(R.string.inventory_title), onBack) {
        if (!isPro) { ScreenColumn { ProLockedCard(stringResource(R.string.inventory_title), onUnlock) }; return@AppScaffold }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(newName, { newName = it.take(60) }, singleLine = true, modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.collection_name)) })
                Button(onClick = { scope.launch { container.inventory.createCollection(newName); newName = "" } },
                    enabled = newName.isNotBlank()) { Text(stringResource(R.string.add)) }
            }
            if (collections.isEmpty()) Text(stringResource(R.string.inventory_empty))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(collections, key = { it.id }) { c ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { toDelete = c.id }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.collection_delete_title)) },
            text = { Text(stringResource(R.string.collection_delete_body)) },
            confirmButton = { TextButton(onClick = { toDelete = null; scope.launch { container.inventory.deleteCollection(id) } }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Contenu d'une collection : recherche, filtre par catégorie, notes, suppression, export CSV. */
@Composable
fun CollectionScreen(container: AppContainer, collectionId: Long, onBack: () -> Unit, onExported: (String) -> Unit) {
    val items by container.inventory.observeItems(collectionId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<InventoryItemEntity?>(null) }
    val categories = items.map { it.category }.distinct().sorted()

    AppScaffold(stringResource(R.string.collection_title), onBack) {
        ScreenColumn {
            OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_hint)) })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c) })
                }
            }
            val visible = items.filter { InventoryRepository.matches(it, query, category) }
            if (visible.isEmpty()) Text(stringResource(R.string.inventory_items_empty))
            visible.forEach { item ->
                Card(Modifier.fillMaxWidth().clickable { editing = item }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(item.category + " · " + item.chipType, style = MaterialTheme.typography.bodySmall)
                        item.uid?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (item.notes.isNotBlank()) Text(item.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            OutlinedButton(onClick = { scope.launch { onExported(container.inventory.exportCsv(includeUid = false)) } },
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_csv_no_uid)) }
        }
    }

    editing?.let { item ->
        var name by remember(item.id) { mutableStateOf(item.name) }
        var notes by remember(item.id) { mutableStateOf(item.notes) }
        var cat by remember(item.id) { mutableStateOf(item.category) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.item_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(80) }, singleLine = true, label = { Text(stringResource(R.string.item_name)) })
                    OutlinedTextField(cat, { cat = it.take(40) }, singleLine = true, label = { Text(stringResource(R.string.item_category)) })
                    OutlinedTextField(notes, { notes = it.take(500) }, label = { Text(stringResource(R.string.notes)) })
                }
            },
            confirmButton = { TextButton(onClick = {
                scope.launch { container.inventory.updateItem(item.copy(name = name.ifBlank { item.name }, category = cat.ifBlank { "Général" }, notes = notes)) }
                editing = null
            }) { Text(stringResource(R.string.save)) } },
            dismissButton = { Row { TextButton(onClick = { scope.launch { container.inventory.deleteItem(item.id) }; editing = null }) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } } },
        )
    }
}
