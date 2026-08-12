package com.stevenroot.mabibliotheque.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** https://openlibrary.org/dev/docs/api/search — free, keyless fallback for when [BnfApi] (the
 * primary, French-focused source) has nothing: strong for English-language and classic titles,
 * weaker for recent French releases. */
interface OpenLibraryApi {
    @GET("search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("fields") fields: String = FIELDS,
        @Query("limit") limit: Int = 20,
    ): OpenLibrarySearchResponse

    companion object {
        const val BASE_URL = "https://openlibrary.org/"
        private const val FIELDS = "title,author_name,first_publish_year,cover_i,isbn,number_of_pages_median,publisher"
        fun isbnQuery(isbn: String) = "isbn:$isbn"
        fun coverUrl(coverId: Int) = "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
    }
}

@Serializable
data class OpenLibrarySearchResponse(val docs: List<OpenLibraryDoc> = emptyList())

@Serializable
data class OpenLibraryDoc(
    val title: String,
    @SerialName("author_name") val authorName: List<String> = emptyList(),
    @SerialName("first_publish_year") val firstPublishYear: Int? = null,
    @SerialName("cover_i") val coverId: Int? = null,
    val isbn: List<String> = emptyList(),
    @SerialName("number_of_pages_median") val pageCount: Int? = null,
    val publisher: List<String> = emptyList(),
) {
    /** Open Library's search results carry no per-ISBN split of ISBN-10 vs ISBN-13 — classify by
     * string length, which is reliable since the two formats have fixed, different lengths. */
    fun toBookVolumeInfo(): BookVolumeInfo = BookVolumeInfo(
        title = title,
        authors = authorName,
        publisher = publisher.firstOrNull(),
        publishedDate = firstPublishYear?.toString(),
        pageCount = pageCount,
        description = null,
        industryIdentifiers = isbn.map { id ->
            BookIdentifier(type = if (id.length == 13) "ISBN_13" else "ISBN_10", identifier = id)
        },
        coverUrl = coverId?.let { OpenLibraryApi.coverUrl(it) },
    )
}

/** Source-agnostic book metadata, as saved into [com.stevenroot.mabibliotheque.data.local.BookEntity] —
 * populated from whichever of [BnfApi]/[OpenLibraryApi] found a match, not tied to either
 * provider's response shape. */
data class BookVolumeInfo(
    val title: String,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedDate: String? = null,
    val pageCount: Int? = null,
    val description: String? = null,
    val industryIdentifiers: List<BookIdentifier> = emptyList(),
    val coverUrl: String? = null,
)

data class BookIdentifier(val type: String, val identifier: String)
