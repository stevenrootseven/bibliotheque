package com.stevenroot.mabibliotheque.data.repository

import com.stevenroot.mabibliotheque.data.local.SeriesDao
import com.stevenroot.mabibliotheque.data.local.SeriesEntity
import com.stevenroot.mabibliotheque.data.local.SeriesEpisodeEntity
import com.stevenroot.mabibliotheque.data.remote.SmbClient
import kotlinx.coroutines.CancellationException

data class SeriesScanProgress(val phase: String, val done: Int, val total: Int)
data class SeriesScanResult(val showsFound: Int, val episodesAdded: Int, val episodesRemoved: Int)

/**
 * Deliberately dumber than [DigitalLibraryScanner]: a show isn't identified against any
 * database, its name is just whatever its folder is called directly under the configured series
 * root — one level down (each such folder = one show), everything found recursively inside it
 * is an "episode" file with no season/episode-number parsing. Good enough to know what shows
 * exist and how many files each has; per-episode metadata is a possible future step, not this one.
 */
class SeriesScanner(
    private val smbClient: SmbClient,
    private val seriesDao: SeriesDao,
) {
    suspend fun scan(
        config: NetworkShareConfig,
        onProgress: (SeriesScanProgress) -> Unit,
    ): Result<SeriesScanResult> {
        val root = config.seriesFolder
        if (root.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Aucun dossier de séries choisi."))
        }
        return try {
            Result.success(doScan(config, root, onProgress))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun doScan(
        config: NetworkShareConfig,
        root: String,
        onProgress: (SeriesScanProgress) -> Unit,
    ): SeriesScanResult {
        onProgress(SeriesScanProgress("Lecture du dossier des séries…", 0, 0))
        val showFolders = smbClient.listDirectory(config, root).getOrThrow().filter { it.isDirectory }

        var episodesAdded = 0
        var episodesRemoved = 0

        showFolders.forEachIndexed { index, show ->
            onProgress(SeriesScanProgress("Analyse : ${show.name}", index, showFolders.size))
            val files = smbClient.listVideoFilesRecursive(config, show.path).getOrThrow()
            val foundPaths = files.map { it.path }.toSet()

            val seriesId = seriesDao.findByName(show.name)?.id
                ?: seriesDao.insertSeries(SeriesEntity(name = show.name, dateAddedMillis = System.currentTimeMillis()))

            val existingEpisodes = seriesDao.allEpisodes().filter { it.seriesId == seriesId }
            val existingPaths = existingEpisodes.map { it.sourcePath }.toSet()

            for (episode in existingEpisodes) {
                if (episode.sourcePath !in foundPaths) {
                    seriesDao.deleteEpisode(episode)
                    episodesRemoved++
                }
            }
            for (file in files) {
                if (file.path !in existingPaths) {
                    seriesDao.insertEpisode(
                        SeriesEpisodeEntity(
                            seriesId = seriesId,
                            fileName = file.name,
                            sourcePath = file.path,
                            dateAddedMillis = System.currentTimeMillis(),
                        ),
                    )
                    episodesAdded++
                }
            }

            if (files.isEmpty() && existingEpisodes.isEmpty()) {
                seriesDao.findSeriesById(seriesId)?.let { seriesDao.deleteSeries(it) }
            }
        }

        onProgress(SeriesScanProgress("Terminé", showFolders.size, showFolders.size))
        return SeriesScanResult(showsFound = showFolders.size, episodesAdded = episodesAdded, episodesRemoved = episodesRemoved)
    }
}
