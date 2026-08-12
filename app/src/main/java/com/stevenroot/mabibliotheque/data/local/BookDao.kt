package com.stevenroot.mabibliotheque.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY dateAddedMillis DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findById(id: Long): BookEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE barcode = :barcode)")
    suspend fun existsWithBarcode(barcode: String): Boolean

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)
}
