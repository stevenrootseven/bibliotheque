package com.stevenroot.mabibliotheque.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkShareConfig(
    val host: String = "",
    val shareName: String = "",
    val username: String = "",
    val password: String = "",
    /** SMB paths (relative to the share root, "/"-separated) whose video files should be
     * scanned — lets the user exclude personal/family folders that happen to sit on the same
     * share. */
    val includedFolders: Set<String> = emptySet(),
    /** Root folder whose direct subfolders are TV shows — scanned separately from
     * [includedFolders] since episodes aren't identified the same way as movies. */
    val seriesFolder: String? = null,
) {
    val isConfigured: Boolean get() = host.isNotBlank() && shareName.isNotBlank()
}

/**
 * NAS credentials are meaningfully more sensitive than the TMDB API key (a real login to the
 * user's home network), so — unlike [SettingsRepository]'s plain DataStore — this is backed by
 * EncryptedSharedPreferences (AES-256, Android Keystore-derived key), and this app is designed
 * to be shared with other people cataloguing their own NAS: nothing here is ever hardcoded, it's
 * always whatever the user themselves typed into the settings screen.
 */
class NetworkShareSettingsRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "network_share_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<NetworkShareConfig> = _config.asStateFlow()

    private fun readConfig(): NetworkShareConfig = NetworkShareConfig(
        host = prefs.getString(KEY_HOST, "").orEmpty(),
        shareName = prefs.getString(KEY_SHARE, "").orEmpty(),
        username = prefs.getString(KEY_USER, "").orEmpty(),
        password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
        includedFolders = prefs.getStringSet(KEY_FOLDERS, emptySet()).orEmpty(),
        seriesFolder = prefs.getString(KEY_SERIES_FOLDER, null),
    )

    fun save(config: NetworkShareConfig) {
        prefs.edit()
            .putString(KEY_HOST, config.host)
            .putString(KEY_SHARE, config.shareName)
            .putString(KEY_USER, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putStringSet(KEY_FOLDERS, config.includedFolders)
            .putString(KEY_SERIES_FOLDER, config.seriesFolder)
            .apply()
        _config.value = config
    }

    fun saveIncludedFolders(folders: Set<String>) {
        save(_config.value.copy(includedFolders = folders))
    }

    companion object {
        private const val KEY_HOST = "host"
        private const val KEY_SHARE = "share"
        private const val KEY_USER = "user"
        private const val KEY_PASSWORD = "password"
        private const val KEY_FOLDERS = "folders"
        private const val KEY_SERIES_FOLDER = "series_folder"
    }
}
