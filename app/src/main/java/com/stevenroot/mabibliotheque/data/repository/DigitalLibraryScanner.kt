package com.stevenroot.mabibliotheque.data.repository

import com.stevenroot.mabibliotheque.data.local.CopyKind
import com.stevenroot.mabibliotheque.data.local.MovieCopyEntity
import com.stevenroot.mabibliotheque.data.local.MovieDao
import com.stevenroot.mabibliotheque.data.local.MovieEntity
import com.stevenroot.mabibliotheque.data.remote.SmbClient
import com.stevenroot.mabibliotheque.data.remote.SmbEntry
import kotlinx.coroutines.CancellationException

data class DigitalScanProgress(val phase: String, val done: Int, val total: Int)
data class DigitalScanResult(val added: Int, val removed: Int, val unmatched: Int, val totalFiles: Int)

/**
 * Runs only on explicit user action ("Rescanner" in Settings, cancellable — a NAS folder can
 * hold thousands of files, each needing a TMDB round-trip, so this can run for a long time and
 * must be interruptible) — walks every folder the user opted into, diffs the file list against
 * what's already catalogued (by SMB path), removes copies for files that disappeared, and
 * identifies new ones by parsing the filename and searching TMDB. Unlike the barcode scan flow,
 * this never asks the user to confirm each match — rescanning hundreds of files can't be a
 * per-item conversation — so an unmatched or wrongly-matched file just becomes a movie titled
 * from its filename, fixable later.
 */
class DigitalLibraryScanner(
    private val smbClient: SmbClient,
    private val movieDao: MovieDao,
    private val movieRepository: MovieRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun scan(
        config: NetworkShareConfig,
        onProgress: (DigitalScanProgress) -> Unit,
    ): Result<DigitalScanResult> {
        return try {
            Result.success(doScan(config, onProgress))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun doScan(
        config: NetworkShareConfig,
        onProgress: (DigitalScanProgress) -> Unit,
    ): DigitalScanResult {
        val apiKey = settingsRepository.currentTmdbApiKey()
            ?: error("Clé TMDB manquante — configure-la d'abord dans Réglages.")

        onProgress(DigitalScanProgress("Lecture du partage…", 0, 0))
        val rawFiles = mutableListOf<SmbEntry>()
        for (folder in config.includedFolders) {
            rawFiles += smbClient.listVideoFilesRecursive(config, folder).getOrThrow()
        }
        // Selected folders can overlap (e.g. a parent and one of its own subfolders both
        // checked) — the same file would then be listed twice, which must not become two rows.
        val allFiles = rawFiles.distinctBy { it.path }
        val foundPaths = allFiles.map { it.path }.toSet()

        val existingDigital = movieDao.allDigitalCopies()
        val removedCopies = existingDigital.filter { it.sourcePath !in foundPaths }
        for (copy in removedCopies) {
            movieRepository.deleteCopy(copy)
        }

        // Backfills fileSizeBytes for copies catalogued before that column existed — cheap since
        // the SMB listing we already fetched above carries sizes for every file, no extra round-trip.
        val sizeByPath = allFiles.associate { it.path to it.sizeBytes }
        for (copy in existingDigital) {
            if (copy.fileSizeBytes == null) {
                sizeByPath[copy.sourcePath]?.let { size -> movieRepository.updateCopy(copy.copy(fileSizeBytes = size)) }
            }
        }

        val existingPaths = existingDigital.map { it.sourcePath }.toSet()
        val newFiles = allFiles.filter { it.path !in existingPaths }
        var unmatched = 0

        newFiles.forEachIndexed { index, file ->
            onProgress(DigitalScanProgress("Identification : ${file.name}", index, newFiles.size))
            val parsed = DigitalFileParser.parse(file.name)
            val matchedMovieId = matchTitle(parsed.title, parsed.year, apiKey)

            val movieId = matchedMovieId ?: run {
                unmatched++
                movieDao.findMovieByExactTitle(parsed.title)?.id
                    ?: movieDao.insertMovie(
                        MovieEntity(
                            title = parsed.title,
                            originalTitle = null,
                            year = parsed.year,
                            overview = null,
                            genres = null,
                            runtimeMinutes = null,
                            director = null,
                            actors = null,
                            posterUrl = null,
                            tmdbId = null,
                            dateAddedMillis = System.currentTimeMillis(),
                        ),
                    )
            }

            movieDao.insertCopy(
                MovieCopyEntity(
                    movieId = movieId,
                    kind = CopyKind.DIGITAL,
                    format = parsed.quality,
                    sourcePath = file.path,
                    languages = parsed.languages,
                    fileSizeBytes = file.sizeBytes,
                    dateAddedMillis = System.currentTimeMillis(),
                ),
            )
        }

        onProgress(DigitalScanProgress("Terminé", newFiles.size, newFiles.size))
        return DigitalScanResult(added = newFiles.size, removed = removedCopies.size, unmatched = unmatched, totalFiles = allFiles.size)
    }

    /**
     * Re-attempts TMDB matching for digital copies already catalogued without a match — the
     * normal scan only ever looks at files it hasn't seen before (diffed by SMB path), so a
     * parser improvement alone never fixes titles that already sit in the DB unmatched. Runs
     * independently of any folder scan: everything it needs (filename, previous parse) is
     * already local.
     */
    suspend fun retryUnmatched(onProgress: (DigitalScanProgress) -> Unit): Result<DigitalScanResult> {
        return try {
            Result.success(doRetryUnmatched(onProgress))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun doRetryUnmatched(onProgress: (DigitalScanProgress) -> Unit): DigitalScanResult {
        val apiKey = settingsRepository.currentTmdbApiKey()
            ?: error("Clé TMDB manquante — configure-la d'abord dans Réglages.")

        val candidates = movieDao.unmatchedDigitalCopies()
        var fixed = 0
        candidates.forEachIndexed { index, copy ->
            val fileName = copy.sourcePath?.substringAfterLast('/')
            if (fileName != null) {
                onProgress(DigitalScanProgress("Nouvelle tentative : $fileName", index, candidates.size))
                val parsed = DigitalFileParser.parse(fileName)
                val oldMovieId = copy.movieId
                val newMovieId = matchTitle(parsed.title, parsed.year, apiKey)
                if (newMovieId != null && newMovieId != oldMovieId) {
                    movieDao.updateCopy(copy.copy(movieId = newMovieId))
                    if (movieDao.countCopiesForMovie(oldMovieId) == 0) {
                        movieDao.findMovieById(oldMovieId)?.let { movieDao.deleteMovie(it) }
                    }
                    fixed++
                }
            }
        }

        onProgress(DigitalScanProgress("Terminé", candidates.size, candidates.size))
        return DigitalScanResult(added = fixed, removed = 0, unmatched = candidates.size - fixed, totalFiles = candidates.size)
    }

    /**
     * Scene-release filenames rarely give the bare movie title: sort-order digits ("Ripoux 1"),
     * roman numerals, and dash-separated edition subtitles ("Highlander I - 30th Anniversary
     * Edition") all get treated by [DigitalFileParser] as part of the title since it can't tell
     * them apart from a real numbered title ("Despicable Me 3"). Rather than guess up front, try
     * the parsed title first and only fall back to stripped variants when TMDB draws a blank.
     */
    private suspend fun matchTitle(rawTitle: String, year: Int?, apiKey: String): Long? {
        val withoutOrdinal = TRAILING_ORDINAL_REGEX.replace(rawTitle, "").trim().takeIf { it.isNotBlank() && it != rawTitle }
        val withoutSubtitle = TRAILING_SUBTITLE_REGEX.replace(rawTitle, "").trim().takeIf { it.isNotBlank() && it != rawTitle }
        val withoutBoth = withoutOrdinal
            ?.let { TRAILING_SUBTITLE_REGEX.replace(it, "").trim() }
            ?.takeIf { it.isNotBlank() && it != withoutOrdinal }

        val candidates = listOfNotNull(rawTitle, withoutOrdinal, withoutSubtitle, withoutBoth).distinct()

        for (candidateTitle in candidates) {
            val result = try {
                movieRepository.searchByTitle(candidateTitle, apiKey, year)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (result is LookupResult.Found) {
                val details = movieRepository.fetchDetails(result.candidates.first().id, apiKey)
                return movieRepository.findOrCreateMovie(details)
            }
        }
        return null
    }

    companion object {
        private val TRAILING_ORDINAL_REGEX = Regex("(?i)\\s+(\\d{1,2}|I|II|III|IV|V|VI|VII|VIII|IX|X)$")
        private val TRAILING_SUBTITLE_REGEX = Regex("\\s+-\\s+.+$")
    }
}
