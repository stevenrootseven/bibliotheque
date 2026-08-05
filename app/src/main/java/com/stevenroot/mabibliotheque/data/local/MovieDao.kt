package com.stevenroot.mabibliotheque.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies ORDER BY dateAddedMillis DESC")
    fun observeAll(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE id = :id")
    fun observeById(id: Long): Flow<MovieEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM movies WHERE barcode = :barcode)")
    suspend fun existsWithBarcode(barcode: String): Boolean

    @Query("SELECT * FROM movies WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): MovieEntity?

    @Insert
    suspend fun insert(movie: MovieEntity): Long

    @Update
    suspend fun update(movie: MovieEntity)

    @Delete
    suspend fun delete(movie: MovieEntity)
}
