package com.stevenroot.mabibliotheque.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** https://developer.themoviedb.org/reference — requires a free personal API key (v3 auth). */
interface TmdbApi {
    @GET("search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "fr-FR",
        @Query("year") year: Int? = null,
    ): TmdbSearchResponse

    @GET("movie/{id}")
    suspend fun getMovieDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "fr-FR",
        @Query("append_to_response") appendToResponse: String = "credits",
    ): TmdbMovieDetails

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"
    }
}

@Serializable
data class TmdbSearchResponse(val results: List<TmdbMovieSummary> = emptyList())

@Serializable
data class TmdbMovieSummary(
    val id: Int,
    val title: String,
    @kotlinx.serialization.SerialName("original_title") val originalTitle: String? = null,
    @kotlinx.serialization.SerialName("release_date") val releaseDate: String? = null,
    @kotlinx.serialization.SerialName("poster_path") val posterPath: String? = null,
    val overview: String? = null,
)

@Serializable
data class TmdbMovieDetails(
    val id: Int,
    val title: String,
    @kotlinx.serialization.SerialName("original_title") val originalTitle: String? = null,
    @kotlinx.serialization.SerialName("release_date") val releaseDate: String? = null,
    @kotlinx.serialization.SerialName("poster_path") val posterPath: String? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    val genres: List<TmdbGenre> = emptyList(),
    val credits: TmdbCredits? = null,
)

@Serializable
data class TmdbGenre(val id: Int, val name: String)

@Serializable
data class TmdbCredits(
    val cast: List<TmdbCastMember> = emptyList(),
    val crew: List<TmdbCrewMember> = emptyList(),
)

@Serializable
data class TmdbCastMember(val name: String, val order: Int = Int.MAX_VALUE)

@Serializable
data class TmdbCrewMember(val name: String, val job: String)
