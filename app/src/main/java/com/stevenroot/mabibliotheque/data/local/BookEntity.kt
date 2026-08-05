package com.stevenroot.mabibliotheque.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val authors: String?,
    val isbn13: String?,
    val isbn10: String?,
    val publisher: String?,
    val publishedYear: Int?,
    val pageCount: Int?,
    val description: String?,
    val coverUrl: String?,
    val barcode: String,
    val dateAddedMillis: Long,
)
