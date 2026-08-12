package com.stevenroot.mabibliotheque.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A canonical film identity — one row per distinct movie, shared by every [MovieCopyEntity]
 * (Blu-ray, 4K, digital NAS file...) the user owns of it. */
@Entity(tableName = "movie")
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
    val tmdbId: Int?,
    val dateAddedMillis: Long,
)
