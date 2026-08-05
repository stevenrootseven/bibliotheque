package com.stevenroot.mabibliotheque.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val overview: String?,
    val genres: String?,
    val runtimeMinutes: Int?,
    val director: String?,
    val actors: String?,
    val posterUrl: String?,
    val format: String,
    val barcode: String,
    val tmdbId: Int?,
    val dateAddedMillis: Long,
    val frontCoverPath: String? = null,
    val backCoverPath: String? = null,
)
