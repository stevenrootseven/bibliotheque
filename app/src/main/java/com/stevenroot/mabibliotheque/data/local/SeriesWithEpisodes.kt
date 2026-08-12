package com.stevenroot.mabibliotheque.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class SeriesWithEpisodes(
    @Embedded val series: SeriesEntity,
    @Relation(parentColumn = "id", entityColumn = "seriesId")
    val episodes: List<SeriesEpisodeEntity>,
)
