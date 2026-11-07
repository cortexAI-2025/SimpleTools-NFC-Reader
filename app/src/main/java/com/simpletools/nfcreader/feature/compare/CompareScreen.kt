package com.simpletools.nfcreader.feature.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simpletools.nfcreader.AppContainer
import com.simpletools.nfcreader.R
import com.simpletools.nfcreader.core.analysis.ReadingComparator
import com.simpletools.nfcreader.core.models.TagSnapshot
import com.simpletools.nfcreader.ui.components.AppScaffold
import com.simpletools.nfcreader.ui.components.ScreenColumn
import com.simpletools.nfcreader.ui.components.SectionCard

/** Comparaison de deux lectures enregistrées (PRO). Affiche chaque champ et signale les différences. */
@Composable
fun CompareScreen(container: AppContainer, idA: Long, idB: Long, onBack: () -> Unit) {
    var left by remember { mutableStateOf<TagSnapshot?>(null) }
    var right by remember { mutableStateOf<TagSnapshot?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(idA, idB) {
        left = container.history.get(idA)?.snapshot
        right = container.history.get(idB)?.snapshot
        loaded = true
    }
    AppScaffold(stringResource(R.string.compare_title), onBack) {
        ScreenColumn {
            val a = left; val b = right
            if (!loaded) Text(stringResource(R.string.loading))
            else if (a == null || b == null) Text(stringResource(R.string.reading_not_found))
            else {
                val diffs = ReadingComparator.compare(a, b)
                val same = diffs.count { it.same }
                SectionCard(stringResource(R.string.compare_summary, same, diffs.size - same)) {
                    diffs.forEach { d ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(d.field, style = MaterialTheme.typography.labelLarge,
                                color = if (d.same) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(d.left.ifBlank { "—" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(d.right.ifBlank { "—" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                SectionCard(stringResource(R.string.compare_mime_title)) {
                    val mimes = ReadingComparator.mimeTypes(a) + ReadingComparator.mimeTypes(b)
                    Text(if (mimes.isEmpty()) stringResource(R.string.no_mime) else mimes.distinct().joinToString("\n"))
                }
            }
        }
    }
}
