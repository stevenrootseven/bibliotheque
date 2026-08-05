package com.stevenroot.mabibliotheque.data.repository

import android.content.Context
import com.stevenroot.mabibliotheque.data.local.CoverStorage
import com.stevenroot.mabibliotheque.data.local.MovieDao
import com.stevenroot.mabibliotheque.data.local.MovieEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ImportRow(
    val barcode: String? = null,
    val title: String,
    val country: String? = null,
    val year: String? = null,
    val release_date: String? = null,
    val director: String? = null,
    val actors: String? = null,
    val runtime: String? = null,
    val front_cover: String? = null,
    val back_cover: String? = null,
)

data class ImportProgress(val done: Int, val total: Int, val currentTitle: String)
data class ImportResult(val inserted: Int, val updated: Int, val skipped: Int)

/**
 * One-time migration path for a collection already catalogued in another app (here: My Movies
 * Pro's own PDF exports, cross-referenced and pre-processed offline into a single manifest —
 * see MaBibliotheque/README.md). Reads from the app's own external-files staging directory
 * (no storage permission needed, nothing bundled in the APK) rather than app assets, since this
 * ~40MB of cover images is only ever needed once per install, not shipped to every user forever.
 * A barcode already present (e.g. from manual scanning) is enriched with any fields it's
 * missing rather than overwritten — this is also how re-running the import safely resumes.
 */
class CollectionImporter(
    private val context: Context,
    private val movieDao: MovieDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun stagingDir(): File = context.getExternalFilesDir(null) ?: context.filesDir
    private fun manifestFile(): File = File(stagingDir(), "collection_import.json")

    fun isAvailable(): Boolean = manifestFile().exists()

    suspend fun import(onProgress: (ImportProgress) -> Unit): ImportResult = withContext(Dispatchers.IO) {
        val rows: List<ImportRow> = json.decodeFromString(manifestFile().readText())
        var inserted = 0
        var updated = 0
        var skipped = 0

        rows.forEachIndexed { index, row ->
            onProgress(ImportProgress(index, rows.size, row.title))
            val barcode = row.barcode
            if (barcode.isNullOrBlank()) {
                skipped++
                return@forEachIndexed
            }
            val frontPath = row.front_cover?.let { copyIfExists(it, "${barcode}_front.jpg") }
            val backPath = row.back_cover?.let { copyIfExists(it, "${barcode}_back.jpg") }

            val existing = movieDao.findByBarcode(barcode)
            if (existing != null) {
                movieDao.update(
                    existing.copy(
                        director = existing.director ?: row.director,
                        actors = existing.actors ?: row.actors,
                        runtimeMinutes = existing.runtimeMinutes ?: parseRuntime(row.runtime),
                        year = existing.year ?: row.year?.toIntOrNull(),
                        frontCoverPath = existing.frontCoverPath ?: frontPath,
                        backCoverPath = existing.backCoverPath ?: backPath,
                    ),
                )
                updated++
            } else {
                movieDao.insert(
                    MovieEntity(
                        title = row.title,
                        originalTitle = null,
                        year = row.year?.toIntOrNull(),
                        overview = null,
                        genres = null,
                        runtimeMinutes = parseRuntime(row.runtime),
                        director = row.director,
                        actors = row.actors,
                        posterUrl = null,
                        format = "Blu-ray",
                        barcode = barcode,
                        tmdbId = null,
                        dateAddedMillis = System.currentTimeMillis(),
                        frontCoverPath = frontPath,
                        backCoverPath = backPath,
                    ),
                )
                inserted++
            }
        }
        onProgress(ImportProgress(rows.size, rows.size, ""))
        ImportResult(inserted, updated, skipped)
    }

    private fun copyIfExists(relativePath: String, destName: String): String? {
        val src = File(stagingDir(), relativePath)
        if (!src.exists()) return null
        return CoverStorage.copyInto(context, src, destName)
    }

    private fun parseRuntime(text: String?): Int? {
        if (text.isNullOrBlank() || text == "-") return null
        val h = Regex("(\\d+)h").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val m = Regex("(\\d+)m").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return (h * 60 + m).takeIf { it > 0 }
    }
}
