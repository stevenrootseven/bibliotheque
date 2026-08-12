package com.stevenroot.mabibliotheque.data.repository

import com.stevenroot.mabibliotheque.data.local.SeriesDao
import com.stevenroot.mabibliotheque.data.local.SeriesEntity
import com.stevenroot.mabibliotheque.data.local.SeriesWithEpisodes
import kotlinx.coroutines.flow.Flow

class SeriesRepository(private val seriesDao: SeriesDao) {
    fun observeAll(): Flow<List<SeriesWithEpisodes>> = seriesDao.observeAllWithEpisodes()
    fun observeById(id: Long): Flow<SeriesWithEpisodes?> = seriesDao.observeByIdWithEpisodes(id)
    suspend fun delete(series: SeriesEntity) = seriesDao.deleteSeries(series)

    suspend fun updateCoverPath(seriesId: Long, path: String) {
        val existing = seriesDao.findSeriesById(seriesId) ?: return
        seriesDao.updateSeries(existing.copy(coverPath = path))
    }
}
