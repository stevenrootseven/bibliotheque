package com.stevenroot.mabibliotheque.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MovieEntity::class, MovieCopyEntity::class, BookEntity::class,
        SeriesEntity::class, SeriesEpisodeEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun bookDao(): BookDao
    abstract fun seriesDao(): SeriesDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movies ADD COLUMN frontCoverPath TEXT")
                db.execSQL("ALTER TABLE movies ADD COLUMN backCoverPath TEXT")
            }
        }

        /**
         * Splits the old one-row-per-barcode `movies` table into a canonical `movie` table plus
         * a `movie_copy` table (one row per exemplaire: a disc, or later a NAS file) — needed so
         * a film owned on both Blu-ray and 4K (or physically and digitally) is one movie with
         * several copies instead of duplicate cards. Legacy rows are grouped by normalized
         * title+year; the row with the richest metadata (has a tmdbId, longest overview) becomes
         * the canonical movie, every original row becomes one copy under it.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movies RENAME TO movies_old")

                db.execSQL(
                    """
                    CREATE TABLE movie (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        originalTitle TEXT,
                        year INTEGER,
                        overview TEXT,
                        genres TEXT,
                        runtimeMinutes INTEGER,
                        director TEXT,
                        actors TEXT,
                        posterUrl TEXT,
                        tmdbId INTEGER,
                        dateAddedMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE TABLE movie_copy (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        movieId INTEGER NOT NULL,
                        kind TEXT NOT NULL,
                        format TEXT NOT NULL,
                        barcode TEXT,
                        sourcePath TEXT,
                        languages TEXT,
                        frontCoverPath TEXT,
                        backCoverPath TEXT,
                        dateAddedMillis INTEGER NOT NULL,
                        FOREIGN KEY(movieId) REFERENCES movie(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX index_movie_copy_movieId ON movie_copy(movieId)")
                db.execSQL("CREATE UNIQUE INDEX index_movie_copy_barcode ON movie_copy(barcode)")
                db.execSQL("CREATE UNIQUE INDEX index_movie_copy_sourcePath ON movie_copy(sourcePath)")

                data class OldRow(
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
                    val frontCoverPath: String?,
                    val backCoverPath: String?,
                )

                val rows = mutableListOf<OldRow>()
                db.query("SELECT * FROM movies_old").use { c ->
                    val titleIdx = c.getColumnIndexOrThrow("title")
                    val originalTitleIdx = c.getColumnIndexOrThrow("originalTitle")
                    val yearIdx = c.getColumnIndexOrThrow("year")
                    val overviewIdx = c.getColumnIndexOrThrow("overview")
                    val genresIdx = c.getColumnIndexOrThrow("genres")
                    val runtimeIdx = c.getColumnIndexOrThrow("runtimeMinutes")
                    val directorIdx = c.getColumnIndexOrThrow("director")
                    val actorsIdx = c.getColumnIndexOrThrow("actors")
                    val posterIdx = c.getColumnIndexOrThrow("posterUrl")
                    val formatIdx = c.getColumnIndexOrThrow("format")
                    val barcodeIdx = c.getColumnIndexOrThrow("barcode")
                    val tmdbIdIdx = c.getColumnIndexOrThrow("tmdbId")
                    val dateAddedIdx = c.getColumnIndexOrThrow("dateAddedMillis")
                    val frontIdx = c.getColumnIndexOrThrow("frontCoverPath")
                    val backIdx = c.getColumnIndexOrThrow("backCoverPath")
                    while (c.moveToNext()) {
                        rows.add(
                            OldRow(
                                title = c.getString(titleIdx),
                                originalTitle = if (c.isNull(originalTitleIdx)) null else c.getString(originalTitleIdx),
                                year = if (c.isNull(yearIdx)) null else c.getInt(yearIdx),
                                overview = if (c.isNull(overviewIdx)) null else c.getString(overviewIdx),
                                genres = if (c.isNull(genresIdx)) null else c.getString(genresIdx),
                                runtimeMinutes = if (c.isNull(runtimeIdx)) null else c.getInt(runtimeIdx),
                                director = if (c.isNull(directorIdx)) null else c.getString(directorIdx),
                                actors = if (c.isNull(actorsIdx)) null else c.getString(actorsIdx),
                                posterUrl = if (c.isNull(posterIdx)) null else c.getString(posterIdx),
                                format = c.getString(formatIdx),
                                barcode = c.getString(barcodeIdx),
                                tmdbId = if (c.isNull(tmdbIdIdx)) null else c.getInt(tmdbIdIdx),
                                dateAddedMillis = c.getLong(dateAddedIdx),
                                frontCoverPath = if (c.isNull(frontIdx)) null else c.getString(frontIdx),
                                backCoverPath = if (c.isNull(backIdx)) null else c.getString(backIdx),
                            ),
                        )
                    }
                }

                // The old `movies` table had no uniqueness constraint on barcode at all, so
                // repeated testing could leave two rows recording the exact same disc — the new
                // movie_copy.barcode index is unique, so collapse those before inserting.
                val dedupedRows = rows
                    .groupBy { it.barcode }
                    .values
                    .map { sameBarcode -> sameBarcode.maxBy { (if (it.tmdbId != null) 1000 else 0) + (it.overview?.length ?: 0) } }

                fun normalize(t: String) = t.trim().lowercase()
                val groups = LinkedHashMap<String, MutableList<OldRow>>()
                for (row in dedupedRows) {
                    val key = normalize(row.title) + "|" + (row.year?.toString() ?: "")
                    groups.getOrPut(key) { mutableListOf() }.add(row)
                }

                val insertMovie = db.compileStatement(
                    "INSERT INTO movie (title, originalTitle, year, overview, genres, runtimeMinutes, director, actors, posterUrl, tmdbId, dateAddedMillis) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                )
                val insertCopy = db.compileStatement(
                    "INSERT INTO movie_copy (movieId, kind, format, barcode, sourcePath, languages, frontCoverPath, backCoverPath, dateAddedMillis) VALUES (?,?,?,?,?,?,?,?,?)",
                )

                for (group in groups.values) {
                    val canonical = group.maxBy { (if (it.tmdbId != null) 1000 else 0) + (it.overview?.length ?: 0) }

                    insertMovie.clearBindings()
                    insertMovie.bindString(1, canonical.title)
                    canonical.originalTitle?.let { insertMovie.bindString(2, it) } ?: insertMovie.bindNull(2)
                    canonical.year?.let { insertMovie.bindLong(3, it.toLong()) } ?: insertMovie.bindNull(3)
                    canonical.overview?.let { insertMovie.bindString(4, it) } ?: insertMovie.bindNull(4)
                    canonical.genres?.let { insertMovie.bindString(5, it) } ?: insertMovie.bindNull(5)
                    canonical.runtimeMinutes?.let { insertMovie.bindLong(6, it.toLong()) } ?: insertMovie.bindNull(6)
                    canonical.director?.let { insertMovie.bindString(7, it) } ?: insertMovie.bindNull(7)
                    canonical.actors?.let { insertMovie.bindString(8, it) } ?: insertMovie.bindNull(8)
                    canonical.posterUrl?.let { insertMovie.bindString(9, it) } ?: insertMovie.bindNull(9)
                    canonical.tmdbId?.let { insertMovie.bindLong(10, it.toLong()) } ?: insertMovie.bindNull(10)
                    insertMovie.bindLong(11, group.minOf { it.dateAddedMillis })
                    val movieId = insertMovie.executeInsert()

                    for (row in group) {
                        insertCopy.clearBindings()
                        insertCopy.bindLong(1, movieId)
                        insertCopy.bindString(2, "PHYSICAL")
                        insertCopy.bindString(3, row.format)
                        insertCopy.bindString(4, row.barcode)
                        insertCopy.bindNull(5)
                        insertCopy.bindNull(6)
                        row.frontCoverPath?.let { insertCopy.bindString(7, it) } ?: insertCopy.bindNull(7)
                        row.backCoverPath?.let { insertCopy.bindString(8, it) } ?: insertCopy.bindNull(8)
                        insertCopy.bindLong(9, row.dateAddedMillis)
                        insertCopy.executeInsert()
                    }
                }

                db.execSQL("DROP TABLE movies_old")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE series (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        dateAddedMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE series_episode (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        seriesId INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        sourcePath TEXT NOT NULL,
                        dateAddedMillis INTEGER NOT NULL,
                        FOREIGN KEY(seriesId) REFERENCES series(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX index_series_episode_seriesId ON series_episode(seriesId)")
                db.execSQL("CREATE UNIQUE INDEX index_series_episode_sourcePath ON series_episode(sourcePath)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movie_copy ADD COLUMN fileSizeBytes INTEGER")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE series ADD COLUMN coverPath TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "ma_bibliotheque.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}
