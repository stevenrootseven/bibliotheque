package com.stevenroot.mabibliotheque.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val tmdbApiKeyKey = stringPreferencesKey("tmdb_api_key")

    val tmdbApiKey: Flow<String?> =
        context.settingsDataStore.data.map { it[tmdbApiKeyKey]?.trim()?.ifEmpty { null } }

    suspend fun currentTmdbApiKey(): String? = tmdbApiKey.first()

    suspend fun setTmdbApiKey(key: String) {
        context.settingsDataStore.edit { it[tmdbApiKeyKey] = key.trim() }
    }
}
