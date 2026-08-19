package com.stevenroot.mabibliotheque.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.sqlite.db.SimpleSQLiteQuery
import com.stevenroot.mabibliotheque.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * (movies, books, series, copies, episodes) and every locally stored cover photo into one .zip
 * the user can move anywhere — a second phone, a USB drive, any cloud app of their own choice —
 * and restore from later.
 *
 * Deliberately excludes the TMDB key and NAS credentials: the NAS password is encrypted with a
 * key tied to the Android Keystore of the *original* device, so it can never be decrypted on a
 * different phone anyway — re-entering both on the new device is unavoidable either way, and
 * safer than putting them in a file that might end up copied or shared loosely.
 */
object BackupManager {
    private const val DB_ENTRY = "database/ma_bibliotheque.db"
    private const val COVERS_ENTRY_PREFIX = "covers/"
    private const val DB_FILE_NAME = "ma_bibliotheque.db"

    suspend fun exportBackup(context: Context, database: AppDatabase): File = withContext(Dispatchers.IO) {
        // Merges the write-ahead log into the main file so the .db alone is a complete,
        // consistent snapshot — no need to also carry the -wal/-shm files.
        database.openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).close()

        val dbFile = context.getDatabasePath(DB_FILE_NAME)
        val coversDir = File(context.filesDir, "covers")
        val zipFile = File(backupDir(context), "mabibliotheque_backup_${timestamp()}.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            addFileToZip(zip, dbFile, DB_ENTRY)
            coversDir.listFiles()?.forEach { file ->
                if (file.isFile) addFileToZip(zip, file, COVERS_ENTRY_PREFIX + file.name)
            }
        }
        zipFile
    }

    sealed interface RestoreResult {
        data object Success : RestoreResult
        data class Error(val message: String) : RestoreResult
    }

    /** Wholesale replace, not a merge: closes the live database, overwrites the .db file and the
     * entire covers directory from the backup. The caller MUST restart the app right after a
     * [RestoreResult.Success] — swapping the database file out from under a live Room connection
     * (and every repository/DAO built on top of it) isn't safe otherwise. */
    suspend fun restoreBackup(context: Context, database: AppDatabase, backupUri: Uri): RestoreResult =
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
