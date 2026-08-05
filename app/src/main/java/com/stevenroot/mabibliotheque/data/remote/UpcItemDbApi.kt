package com.stevenroot.mabibliotheque.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * UPCitemdb trial endpoint — no API key, rate-limited to ~100 lookups/day per IP (plenty for
 * personal collection scanning). Only translates a generic UPC/EAN into a product title; the
 * real movie metadata (poster, cast, genres) still comes from [TmdbApi] via a title search.
 * Coverage for non-US retail barcodes (e.g. French Blu-rays) is unverified until tested on a
 * real device — if a barcode isn't found here, the UI must fall back to manual title search.
 */
interface UpcItemDbApi {
    @GET("prod/trial/lookup")
    suspend fun lookup(@Query("upc") upc: String): UpcLookupResponse

    companion object {
        const val BASE_URL = "https://api.upcitemdb.com/"
    }
}

@Serializable
data class UpcLookupResponse(
    val code: String? = null,
    val total: Int = 0,
    val items: List<UpcItem> = emptyList(),
)

@Serializable
data class UpcItem(
    val title: String? = null,
    val brand: String? = null,
    val description: String? = null,
)
