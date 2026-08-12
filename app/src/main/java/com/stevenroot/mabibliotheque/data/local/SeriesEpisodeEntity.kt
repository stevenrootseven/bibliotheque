package com.stevenroot.mabibliotheque.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "series_episode",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("seriesId"), Index(value = ["sourcePath"], unique = true)],
)
data class SeriesEpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val fileName: String,
    val sourcePath: String,
    val dateAddedMillis: Long,
)
