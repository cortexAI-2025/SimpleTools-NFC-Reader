package com.simpletools.nfcreader.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpletools.nfcreader.core.database.HistoryRepository
import com.simpletools.nfcreader.core.database.ReadingEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repo: HistoryRepository) : ViewModel() {
    val query = MutableStateFlow("")

    val items: StateFlow<List<ReadingEntity>?> = query
        .flatMapLatest { repo.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(q: String) { query.value = q }
    fun delete(id: Long) { viewModelScope.launch { repo.delete(id) } }
    fun rename(id: Long, name: String) { viewModelScope.launch { repo.rename(id, name) } }
    fun deleteAll() { viewModelScope.launch { repo.deleteAll() } }
}
