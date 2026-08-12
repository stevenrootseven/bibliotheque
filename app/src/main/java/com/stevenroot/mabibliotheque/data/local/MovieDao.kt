package com.stevenroot.mabibliotheque.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {

    @Transaction
    @Query("SELECT * FROM movie ORDER BY dateAddedMillis DESC")
    fun observeAllWithCopies(): Flow<List<MovieWithCopies>>

    @Transaction
    @Query("SELECT * FROM movie WHERE id = :id")
    fun observeByIdWithCopies(id: Long): Flow<MovieWithCopies?>

    @Query("SELECT * FROM movie WHERE id = :id")
    suspend fun findMovieById(id: Long): MovieEntity?

    @Query("SELECT * FROM movie WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun findMovieByTmdbId(tmdbId: Int): MovieEntity?

    @Query("SELECT * FROM movie WHERE title = :title COLLATE NOCASE LIMIT 1")
    suspend fun findMovieByExactTitle(title: String): MovieEntity?

    @Insert
    suspend fun insertMovie(movie: MovieEntity): Long

    @Update
    suspend fun updateMovie(movie: MovieEntity)

    @Delete
    suspend fun deleteMovie(movie: MovieEntity)

    @Insert
    suspend fun insertCopy(copy: MovieCopyEntity): Long

    @Update
    suspend fun updateCopy(copy: MovieCopyEntity)

    @Delete
    suspend fun deleteCopy(copy: MovieCopyEntity)

    @Query("SELECT * FROM movie_copy WHERE barcode = :barcode LIMIT 1")
    suspend fun findCopyByBarcode(barcode: String): MovieCopyEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM movie_copy WHERE barcode = :barcode)")
    suspend fun existsCopyWithBarcode(barcode: String): Boolean

    @Query("SELECT * FROM movie_copy WHERE sourcePath = :path LIMIT 1")
    suspend fun findCopyBySourcePath(path: String): MovieCopyEntity?

    @Query("SELECT * FROM movie_copy WHERE kind = 'DIGITAL'")
    suspend fun allDigitalCopies(): List<MovieCopyEntity>

    @Query(
        """
        SELECT movie_copy.* FROM movie_copy
        INNER JOIN movie ON movie.id = movie_copy.movieId
        WHERE movie_copy.kind = 'DIGITAL' AND movie.tmdbId IS NULL
        """,
    )
    suspend fun unmatchedDigitalCopies(): List<MovieCopyEntity>

    @Query("SELECT COUNT(*) FROM movie_copy WHERE movieId = :movieId")
    suspend fun countCopiesForMovie(movieId: Long): Int
}
