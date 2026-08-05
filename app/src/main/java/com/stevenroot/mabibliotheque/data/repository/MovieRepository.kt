package com.stevenroot.mabibliotheque.data.repository

import com.stevenroot.mabibliotheque.data.local.MovieDao
import com.stevenroot.mabibliotheque.data.local.MovieEntity
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
    fun observeAll(): Flow<List<MovieEntity>> = movieDao.observeAll()

    fun observeById(id: Long): Flow<MovieEntity?> = movieDao.observeById(id)

    suspend fun alreadyOwns(barcode: String): Boolean = movieDao.existsWithBarcode(barcode)

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

    suspend fun searchByTitle(query: String, apiKey: String): LookupResult<TmdbMovieSummary> {
        val results = runCatching { tmdbApi.searchMovies(apiKey = apiKey, query = query) }
            .getOrElse { return LookupResult.Error(it.message ?: "Erreur réseau (TMDB)") }
            .results
        return if (results.isEmpty()) LookupResult.NotFound else LookupResult.Found(results)
    }

    suspend fun fetchDetails(tmdbId: Int, apiKey: String): TmdbMovieDetails =
        tmdbApi.getMovieDetails(id = tmdbId, apiKey = apiKey)

    suspend fun save(details: TmdbMovieDetails, barcode: String, format: String): Long {
        val director = details.credits?.crew?.firstOrNull { it.job == "Director" }?.name
        val actors = details.credits?.cast
            ?.sortedBy { it.order }
            ?.take(6)
            ?.joinToString(", ") { it.name }

        val entity = MovieEntity(
            title = details.title,
            originalTitle = details.originalTitle?.takeIf { it != details.title },
            year = details.releaseDate?.take(4)?.toIntOrNull(),
            overview = details.overview,
            genres = details.genres.joinToString(", ") { it.name }.ifEmpty { null },
            runtimeMinutes = details.runtime,
            director = director,
            actors = actors,
            posterUrl = details.posterPath?.let { TmdbApi.IMAGE_BASE_URL + it },
            format = format,
            barcode = barcode,
            tmdbId = details.id,
            dateAddedMillis = System.currentTimeMillis(),
        )
        return movieDao.insert(entity)
    }

    suspend fun delete(movie: MovieEntity) = movieDao.delete(movie)

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
