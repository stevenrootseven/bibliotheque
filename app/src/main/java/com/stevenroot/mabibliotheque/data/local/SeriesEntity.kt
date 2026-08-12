package com.stevenroot.mabibliotheque.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A TV show, catalogued simply by its folder name on the NAS — no season/episode metadata,
 * no TMDB match, just "this show exists and here are its files" (see [SeriesEpisodeEntity]). */
@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dateAddedMillis: Long,
    /** Local file path to a manually added cover photo — never auto-fetched, since there's no
     * TMDB match for series in this app (see the class doc above). */
    val coverPath: String? = null,
)
