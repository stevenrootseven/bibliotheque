package com.stevenroot.mabibliotheque.data.repository

import android.content.Context
import com.stevenroot.mabibliotheque.data.local.CopyKind
import com.stevenroot.mabibliotheque.data.local.CoverStorage
import com.stevenroot.mabibliotheque.data.local.MovieCopyEntity
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
 * missing rather than overwritten — this is also how re-running the import safely resumes. A
 * title matching an existing movie (by exact name — imports carry no tmdbId) gains a second
 * copy instead of a duplicate card, same merge rule as [MovieRepository.findOrCreateMovie].
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

            val existingCopy = movieDao.findCopyByBarcode(barcode)
            if (existingCopy != null) {
                movieDao.updateCopy(
                    existingCopy.copy(
                        frontCoverPath = existingCopy.frontCoverPath ?: frontPath,
                        backCoverPath = existingCopy.backCoverPath ?: backPath,
                    ),
                )
                val movie = movieDao.findMovieById(existingCopy.movieId)
                if (movie != null) {
                    movieDao.updateMovie(
                        movie.copy(
                            director = movie.director ?: row.director,
                            actors = movie.actors ?: row.actors,
                            runtimeMinutes = movie.runtimeMinutes ?: parseRuntime(row.runtime),
                            year = movie.year ?: row.year?.toIntOrNull(),
                        ),
                    )
                }
                updated++
            } else {
                val movieId = movieDao.findMovieByExactTitle(row.title)?.id
                    ?: movieDao.insertMovie(
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
                            tmdbId = null,
                            dateAddedMillis = System.currentTimeMillis(),
                        ),
                    )
                movieDao.insertCopy(
                    MovieCopyEntity(
                        movieId = movieId,
                        kind = CopyKind.PHYSICAL,
                        format = "Blu-ray",
                        barcode = barcode,
                        frontCoverPath = frontPath,
                        backCoverPath = backPath,
                        dateAddedMillis = System.currentTimeMillis(),
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
