package com.simpletools.nfcreader.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(val theme: ThemeMode = ThemeMode.SYSTEM, val maskIdentifiers: Boolean = false)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.dataStore)

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            theme = p[THEME]?.let { v -> ThemeMode.values().firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM,
            maskIdentifiers = p[MASK] ?: false,
        )
    }

    suspend fun setTheme(mode: ThemeMode) { store.edit { it[THEME] = mode.name } }
    suspend fun setMaskIdentifiers(mask: Boolean) { store.edit { it[MASK] = mask } }
    suspend fun reset() { store.edit { it.clear() } }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val MASK = booleanPreferencesKey("mask_identifiers")
    }
}
