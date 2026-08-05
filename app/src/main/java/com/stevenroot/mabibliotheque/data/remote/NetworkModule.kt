package com.stevenroot.mabibliotheque.data.remote

import com.stevenroot.mabibliotheque.data.repository.SettingsRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class NetworkModule(private val settingsRepository: SettingsRepository) {

    private val json = Json { ignoreUnknownKeys = true }
    private val contentType = "application/json".toMediaType()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private fun retrofit(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

    val tmdbApi: TmdbApi = retrofit(TmdbApi.BASE_URL).create(TmdbApi::class.java)
    val upcItemDbApi: UpcItemDbApi = retrofit(UpcItemDbApi.BASE_URL).create(UpcItemDbApi::class.java)
    val googleBooksApi: GoogleBooksApi = retrofit(GoogleBooksApi.BASE_URL).create(GoogleBooksApi::class.java)
}
