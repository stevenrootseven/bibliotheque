package com.stevenroot.mabibliotheque.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** https://developers.google.com/books/docs/v1/using — no API key needed for this volume. */
interface GoogleBooksApi {
    @GET("books/v1/volumes")
    suspend fun search(@Query("q") query: String): GoogleBooksResponse

    companion object {
        const val BASE_URL = "https://www.googleapis.com/"
        fun isbnQuery(isbn: String) = "isbn:$isbn"
    }
}

@Serializable
data class GoogleBooksResponse(val items: List<GoogleBookItem> = emptyList())

@Serializable
data class GoogleBookItem(val volumeInfo: GoogleBookVolumeInfo)

@Serializable
data class GoogleBookVolumeInfo(
    val title: String,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    @kotlinx.serialization.SerialName("publishedDate") val publishedDate: String? = null,
    @kotlinx.serialization.SerialName("pageCount") val pageCount: Int? = null,
    val description: String? = null,
    @kotlinx.serialization.SerialName("industryIdentifiers") val industryIdentifiers: List<GoogleBookIdentifier> = emptyList(),
    @kotlinx.serialization.SerialName("imageLinks") val imageLinks: GoogleBookImageLinks? = null,
)

@Serializable
data class GoogleBookIdentifier(val type: String, val identifier: String)

@Serializable
data class GoogleBookImageLinks(val thumbnail: String? = null)
