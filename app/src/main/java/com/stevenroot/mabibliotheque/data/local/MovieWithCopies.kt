package com.stevenroot.mabibliotheque.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class MovieWithCopies(
    @Embedded val movie: MovieEntity,
    @Relation(parentColumn = "id", entityColumn = "movieId")
    val copies: List<MovieCopyEntity>,
)
