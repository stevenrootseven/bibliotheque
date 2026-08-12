package com.stevenroot.mabibliotheque.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CopyKind { PHYSICAL, DIGITAL }

/** One exemplaire of a [MovieEntity] the user owns — a specific disc (with its own barcode and
 * cover photos) or a specific file on the NAS (with its own path and resolution). A film owned
 * on both Blu-ray and 4K, or physically and digitally, has one [MovieEntity] and several of
 * these. */
@Entity(
    tableName = "movie_copy",
    foreignKeys = [
        ForeignKey(
            entity = MovieEntity::class,
            parentColumns = ["id"],
            childColumns = ["movieId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("movieId"), Index(value = ["barcode"], unique = true), Index(value = ["sourcePath"], unique = true)],
)
data class MovieCopyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val movieId: Long,
    val kind: CopyKind,
    /** "Blu-ray" / "4K Ultra HD" / "DVD" / "Autre" for physical; "HD" / "4K" for digital. */
    val format: String,
    val barcode: String? = null,
    /** SMB path of the source file, digital copies only. */
    val sourcePath: String? = null,
    /** Best-effort, parsed from the filename for digital copies; free text for physical. */
    val languages: String? = null,
    val frontCoverPath: String? = null,
    val backCoverPath: String? = null,
    /** From the SMB directory listing, digital copies only — lets the user compare two
     * versions of the same film by size (a decent quality proxy) without leaving the app. */
    val fileSizeBytes: Long? = null,
    val dateAddedMillis: Long,
)
