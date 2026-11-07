package com.simpletools.nfcreader.core.license

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.licenseStore: DataStore<Preferences> by preferencesDataStore(name = "license")

/** État de licence persistant (DataStore local). Ne contient aucune donnée personnelle ni jeton d'achat. */
/** Stockage de l'état de licence (abstrait pour les tests). */
interface LicenseStorage {
    val state: Flow<LicenseState>
    suspend fun save(state: LicenseState)
}

class LicenseStore(private val store: DataStore<Preferences>) : LicenseStorage {
    constructor(context: Context) : this(context.applicationContext.licenseStore)

    override val state: Flow<LicenseState> = store.data.map { p ->
        LicenseState(
            owned = p[OWNED] ?: false,
            lastVerifiedAt = p[VERIFIED_AT] ?: 0L,
            pending = p[PENDING] ?: false,
        )
    }

    override suspend fun save(state: LicenseState) {
        store.edit {
            it[OWNED] = state.owned
            it[VERIFIED_AT] = state.lastVerifiedAt
            it[PENDING] = state.pending
        }
    }

    suspend fun clear() { store.edit { it.clear() } }

    private companion object {
        val OWNED = booleanPreferencesKey("pro_owned")
        val VERIFIED_AT = longPreferencesKey("pro_verified_at")
        val PENDING = booleanPreferencesKey("pro_pending")
    }
}
