package com.stevenroot.mabibliotheque.data.repository

import com.stevenroot.mabibliotheque.data.local.CopyKind
import com.stevenroot.mabibliotheque.data.local.MovieCopyEntity
import com.stevenroot.mabibliotheque.data.local.MovieDao
import com.stevenroot.mabibliotheque.data.local.MovieEntity
import com.stevenroot.mabibliotheque.data.local.MovieWithCopies
import com.stevenroot.mabibliotheque.data.remote.TmdbApi
import com.stevenroot.mabibliotheque.data.remote.TmdbMovieDetails
import com.stevenroot.mabibliotheque.data.remote.TmdbMovieSummary
import com.stevenroot.mabibliotheque.data.remote.UpcItemDbApi
import kotlinx.coroutines.flow.Flow

class MovieRepository(
    private val movieDao: MovieDao,
    private val tmdbApi: TmdbApi,
    private val upcItemDbApi: UpcItemDbApi,
) {
    fun observeAll(): Flow<List<MovieWithCopies>> = movieDao.observeAllWithCopies()

    fun observeById(id: Long): Flow<MovieWithCopies?> = movieDao.observeByIdWithCopies(id)

    suspend fun alreadyOwnsBarcode(barcode: String): Boolean = movieDao.existsCopyWithBarcode(barcode)

    /**
     * A UPC/EAN barcode carries no film identity by itself — first resolve it to a retail
     * product title via UPCitemdb's free trial lookup, then search TMDB by that title. Either
     * step can come up empty (especially for French/older discs), in which case the caller
     * should fall back to [searchByTitle] with a manually typed title.
     */
    suspend fun lookupByBarcode(barcode: String, apiKey: String): LookupResult<TmdbMovieSummary> {
        val item = runCatching { upcItemDbApi.lookup(barcode) }
            .getOrElse { return LookupResult.Error(it.message ?: "Erreur réseau (UPCitemdb)") }
            .items.firstOrNull()
            ?: return LookupResult.NotFound
        val productTitle = item.title ?: return LookupResult.NotFound

        return searchByTitle(cleanProductTitle(productTitle, item.brand), apiKey)
    }

    suspend fun searchByTitle(query: String, apiKey: String, year: Int? = null): LookupResult<TmdbMovieSummary> {
        val results = runCatching { tmdbApi.searchMovies(apiKey = apiKey, query = query, year = year) }
            .getOrElse { return LookupResult.Error(it.message ?: "Erreur réseau (TMDB)") }
            .results
        return if (results.isEmpty()) LookupResult.NotFound else LookupResult.Found(results)
    }

    suspend fun fetchDetails(tmdbId: Int, apiKey: String): TmdbMovieDetails =
        tmdbApi.getMovieDetails(id = tmdbId, apiKey = apiKey)

    /** Finds the existing [MovieEntity] this TMDB title already belongs to (by tmdbId first,
     * then exact title as a fallback for rows imported without one), or creates it — so a film
     * already owned in one format gains a second copy instead of a duplicate card. */
    suspend fun findOrCreateMovie(details: TmdbMovieDetails): Long {
        movieDao.findMovieByTmdbId(details.id)?.let { return it.id }
        movieDao.findMovieByExactTitle(details.title)?.let { existing ->
            if (existing.tmdbId == null) {
                movieDao.updateMovie(buildMovieEntity(details, id = existing.id, dateAddedMillis = existing.dateAddedMillis))
            }
            return existing.id
        }
        return movieDao.insertMovie(buildMovieEntity(details, id = 0, dateAddedMillis = System.currentTimeMillis()))
    }

    private fun buildMovieEntity(details: TmdbMovieDetails, id: Long, dateAddedMillis: Long): MovieEntity {
        val director = details.credits?.crew?.firstOrNull { it.job == "Director" }?.name
        val actors = details.credits?.cast
            ?.sortedBy { it.order }
            ?.take(6)
            ?.joinToString(", ") { it.name }
        return MovieEntity(
            id = id,
            title = details.title,
            originalTitle = details.originalTitle?.takeIf { it != details.title },
            year = details.releaseDate?.take(4)?.toIntOrNull(),
            overview = details.overview,
            genres = details.genres.joinToString(", ") { it.name }.ifEmpty { null },
            runtimeMinutes = details.runtime,
            director = director,
            actors = actors,
            posterUrl = details.posterPath?.let { TmdbApi.IMAGE_BASE_URL + it },
            tmdbId = details.id,
            dateAddedMillis = dateAddedMillis,
        )
    }

    suspend fun save(details: TmdbMovieDetails, barcode: String?, format: String): Long {
        val movieId = findOrCreateMovie(details)
        movieDao.insertCopy(
            MovieCopyEntity(
                movieId = movieId,
                kind = CopyKind.PHYSICAL,
                format = format,
                barcode = barcode?.takeIf { it.isNotBlank() },
                dateAddedMillis = System.currentTimeMillis(),
            ),
        )
        return movieId
    }

    /** Deletes a single copy; if it was the movie's last one, the (now empty) movie is removed too. */
    suspend fun deleteCopy(copy: MovieCopyEntity) {
        movieDao.deleteCopy(copy)
        if (movieDao.countCopiesForMovie(copy.movieId) == 0) {
            movieDao.findMovieById(copy.movieId)?.let { movieDao.deleteMovie(it) }
        }
    }

    suspend fun deleteMovie(movie: MovieEntity) = movieDao.deleteMovie(movie)

    suspend fun updateCopy(copy: MovieCopyEntity) = movieDao.updateCopy(copy)

    /** Overwrites a movie's TMDB-sourced fields in place — same row id, same copies, same
     * dateAdded — for when the original barcode/filename match picked the wrong film. Doesn't
     * attempt to merge with any other Movie row that might already hold this same tmdbId; a
     * resulting duplicate is left for the user to delete via the normal delete action, same as
     * every other data-quality issue in this app. */
    suspend fun overwriteMovie(movieId: Long, details: TmdbMovieDetails) {
        val existing = movieDao.findMovieById(movieId) ?: return
        movieDao.updateMovie(buildMovieEntity(details, id = movieId, dateAddedMillis = existing.dateAddedMillis))
    }

    companion object {
        // UPCitemdb's retail listing titles are typically "<Movie title> (<format>) <cast names>"
        // e.g. "Fast & Furious Presents Hobbs & Shaw (Blu-ray) Dwayne Johnson Jason Statham Idris
        // Elba" — searching TMDB with that whole string reliably returns zero results, so trim
        // it down to just the part before the format tag before searching.
        private val TRAILING_FORMAT_WORDS = listOf(
            "4K Ultra HD", "Ultra HD", "Blu-ray", "Blu Ray", "BluRay", "DVD",
            "Digital HD", "Digital Copy", "Steelbook", "UHD",
        )

        internal fun cleanProductTitle(raw: String, brand: String? = null): String {
            var title = raw
            // Some listings prefix the title with the studio/brand, e.g. "Universal Studios -
            // Oblivion [ULTRA HD]" — strip that exact known prefix (only when it's genuinely the
            // brand, not a coincidental dash inside a real movie title).
            if (!brand.isNullOrBlank()) {
                val prefixVariants = listOf("$brand - ", "$brand: ", "$brand-")
                for (prefix in prefixVariants) {
                    if (title.startsWith(prefix, ignoreCase = true)) {
                        title = title.substring(prefix.length)
                        break
                    }
                }
            }
            val cutIndex = title.indexOfFirst { it == '(' || it == '[' }
            if (cutIndex > 0) title = title.substring(0, cutIndex)
            var changed = true
            while (changed) {
                changed = false
                for (word in TRAILING_FORMAT_WORDS) {
                    val withoutSuffix = title.trim().removeSuffix(word).trim()
                    if (withoutSuffix != title.trim()) {
                        title = withoutSuffix
                        changed = true
                    }
                }
            }
            return title.trim().trim('-', ':').trim().ifBlank { raw.trim() }
        }
    }
}
