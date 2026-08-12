package com.stevenroot.mabibliotheque.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {

    @Transaction
    @Query("SELECT * FROM series ORDER BY name COLLATE NOCASE")
    fun observeAllWithEpisodes(): Flow<List<SeriesWithEpisodes>>

    @Transaction
    @Query("SELECT * FROM series WHERE id = :id")
    fun observeByIdWithEpisodes(id: Long): Flow<SeriesWithEpisodes?>

    @Query("SELECT * FROM series WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): SeriesEntity?

    @Insert
    suspend fun insertSeries(series: SeriesEntity): Long

    @Update
    suspend fun updateSeries(series: SeriesEntity)

    @Delete
    suspend fun deleteSeries(series: SeriesEntity)

    @Insert
    suspend fun insertEpisode(episode: SeriesEpisodeEntity)

    @Delete
    suspend fun deleteEpisode(episode: SeriesEpisodeEntity)

    @Query("SELECT * FROM series_episode")
    suspend fun allEpisodes(): List<SeriesEpisodeEntity>

    @Query("SELECT COUNT(*) FROM series_episode WHERE seriesId = :seriesId")
    suspend fun countEpisodesForSeries(seriesId: Long): Int

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun findSeriesById(id: Long): SeriesEntity?
}
