package com.stevenroot.mabibliotheque.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.sqlite.db.SimpleSQLiteQuery
import com.stevenroot.mabibliotheque.data.local.AppDatabase
import com.stevenroot.mabibliotheque.data.repository.NetworkShareConfig
import com.stevenroot.mabibliotheque.data.repository.NetworkShareSettingsRepository
import com.stevenroot.mabibliotheque.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full-collection backup/restore, independent of any cloud account. Bundles the Room database
 * (movies, books, series, copies, episodes), every locally stored cover photo, the TMDB key and
 * the NAS connection settings into one .zip the user can move anywhere — a second phone, a USB
 * drive, any cloud app of their own choice — and restore from later.
 *
 * The NAS password is normally encrypted with a key tied to the Android Keystore of the
 * *current install* — that key is destroyed on uninstall, so without this, both the TMDB key and
 * the NAS login would be silently lost on every reinstall, backup or not. Reading them here
 * (through the still-live repositories, before the key dies) and writing them back through the
 * same repositories on restore sidesteps that entirely: the new install re-encrypts them with
 * its own fresh key. The trade-off is that the .zip now carries a real NAS password in
 * [settings.json] — acceptable since it's the user's own file, going wherever *they* choose to
 * send it, but worth being upfront about in the UI that offers this button.
 */
object BackupManager {
    private const val DB_ENTRY = "database/ma_bibliotheque.db"
    private const val COVERS_ENTRY_PREFIX = "covers/"
    private const val SETTINGS_ENTRY = "settings.json"
    private const val DB_FILE_NAME = "ma_bibliotheque.db"

    @Serializable
    private data class BackupSettings(
        val tmdbApiKey: String? = null,
        val nasHost: String = "",
        val nasShare: String = "",
        val nasUsername: String = "",
        val nasPassword: String = "",
        val nasIncludedFolders: List<String> = emptyList(),
        val nasSeriesFolder: String? = null,
    )

    suspend fun exportBackup(
        context: Context,
        database: AppDatabase,
        settingsRepository: SettingsRepository,
        networkShareSettingsRepository: NetworkShareSettingsRepository,
    ): File = withContext(Dispatchers.IO) {
        // Merges the write-ahead log into the main file so the .db alone is a complete,
        // consistent snapshot — no need to also carry the -wal/-shm files.
        database.openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).close()

        val dbFile = context.getDatabasePath(DB_FILE_NAME)
        val coversDir = File(context.filesDir, "covers")
        val nasConfig = networkShareSettingsRepository.config.value
        val settings = BackupSettings(
            tmdbApiKey = settingsRepository.currentTmdbApiKey(),
            nasHost = nasConfig.host,
            nasShare = nasConfig.shareName,
            nasUsername = nasConfig.username,
            nasPassword = nasConfig.password,
            nasIncludedFolders = nasConfig.includedFolders.toList(),
            nasSeriesFolder = nasConfig.seriesFolder,
        )
        val zipFile = File(backupDir(context), "mabibliotheque_backup_${timestamp()}.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            addFileToZip(zip, dbFile, DB_ENTRY)
            coversDir.listFiles()?.forEach { file ->
                if (file.isFile) addFileToZip(zip, file, COVERS_ENTRY_PREFIX + file.name)
            }
            zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
            zip.write(Json.encodeToString(BackupSettings.serializer(), settings).toByteArray())
            zip.closeEntry()
        }
        zipFile
    }

    sealed interface RestoreResult {
        data object Success : RestoreResult
        data class Error(val message: String) : RestoreResult
    }

    /** Wholesale replace, not a merge: closes the live database, overwrites the .db file and the
     * entire covers directory from the backup, and re-saves the TMDB key / NAS config through
     * the live repositories (see the class doc for why that round-trip matters). The caller MUST
     * restart the app right after a [RestoreResult.Success] — swapping the database file out
     * from under a live Room connection (and every repository/DAO built on top of it) isn't safe
     * otherwise. */
    suspend fun restoreBackup(
        context: Context,
        database: AppDatabase,
        settingsRepository: SettingsRepository,
        networkShareSettingsRepository: NetworkShareSettingsRepository,
        backupUri: Uri,
    ): RestoreResult =
        withContext(Dispatchers.IO) {
            val tempDir = File(context.cacheDir, "restore_tmp").apply {
                deleteRecursively()
                mkdirs()
            }
            try {
                val opened = context.contentResolver.openInputStream(backupUri)?.use { input ->
                    ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val dest = File(tempDir, entry.name)
                                dest.parentFile?.mkdirs()
                                FileOutputStream(dest).use { out -> zip.copyTo(out) }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                    true
                }
                if (opened != true) return@withContext RestoreResult.Error("Impossible de lire le fichier sélectionné.")

                val restoredDb = File(tempDir, DB_ENTRY)
                if (!restoredDb.exists()) {
                    return@withContext RestoreResult.Error("Ce fichier ne semble pas être une sauvegarde Ma Bibliothèque valide.")
                }

                database.close()

                val dbFile = context.getDatabasePath(DB_FILE_NAME)
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
                restoredDb.copyTo(dbFile, overwrite = true)

                val coversDir = File(context.filesDir, "covers")
                coversDir.deleteRecursively()
                coversDir.mkdirs()
                File(tempDir, COVERS_ENTRY_PREFIX).listFiles()?.forEach { file ->
                    file.copyTo(File(coversDir, file.name), overwrite = true)
                }

                // Older backups (made before this field existed) simply won't have this entry —
                // restore everything else and leave the current TMDB/NAS settings untouched.
                File(tempDir, SETTINGS_ENTRY).takeIf { it.exists() }?.let { file ->
                    val settings = Json.decodeFromString(BackupSettings.serializer(), file.readText())
                    settings.tmdbApiKey?.let { settingsRepository.setTmdbApiKey(it) }
                    if (settings.nasHost.isNotBlank()) {
                        networkShareSettingsRepository.save(
                            NetworkShareConfig(
                                host = settings.nasHost,
                                shareName = settings.nasShare,
                                username = settings.nasUsername,
                                password = settings.nasPassword,
                                includedFolders = settings.nasIncludedFolders.toSet(),
                                seriesFolder = settings.nasSeriesFolder,
                            ),
                        )
                    }
                }

                RestoreResult.Success
            } catch (e: Exception) {
                RestoreResult.Error(e.message ?: "Erreur lors de la restauration.")
            } finally {
                tempDir.deleteRecursively()
            }
        }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Partager la sauvegarde").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Restarts the whole app process so Room reopens the just-restored database file fresh —
     * required after [restoreBackup], since the old process still holds the closed connection
     * and every repository built on top of it. */
    fun restartApp(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val restartIntent = Intent.makeRestartActivityTask(launchIntent?.component)
        context.startActivity(restartIntent)
        Runtime.getRuntime().exit(0)
    }

    private fun addFileToZip(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    // Reuses the "exports" cache-path already declared for FileProvider (see file_paths.xml) —
    // a dedicated "backups" folder would need its own <cache-path> entry there too.
    private fun backupDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())
}
