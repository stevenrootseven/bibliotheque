package com.stevenroot.mabibliotheque.data.repository

import com.stevenroot.mabibliotheque.data.local.BookDao
import com.stevenroot.mabibliotheque.data.local.BookEntity
import com.stevenroot.mabibliotheque.data.remote.GoogleBookVolumeInfo
import com.stevenroot.mabibliotheque.data.remote.GoogleBooksApi
import kotlinx.coroutines.flow.Flow

class BookRepository(
    private val bookDao: BookDao,
    private val googleBooksApi: GoogleBooksApi,
) {
    fun observeAll(): Flow<List<BookEntity>> = bookDao.observeAll()

    fun observeById(id: Long): Flow<BookEntity?> = bookDao.observeById(id)

    suspend fun alreadyOwns(barcode: String): Boolean = bookDao.existsWithBarcode(barcode)

    /** A book's barcode is its ISBN-13 — a single direct lookup, no intermediate translation. */
    suspend fun lookupByIsbn(isbn: String): LookupResult<GoogleBookVolumeInfo> =
        searchByQuery(GoogleBooksApi.isbnQuery(isbn))

    suspend fun searchByTitle(query: String): LookupResult<GoogleBookVolumeInfo> =
        searchByQuery(query)

    private suspend fun searchByQuery(query: String): LookupResult<GoogleBookVolumeInfo> {
        val items = runCatching { googleBooksApi.search(query) }
            .getOrElse { return LookupResult.Error(it.message ?: "Erreur réseau (Google Books)") }
            .items
        return if (items.isEmpty()) LookupResult.NotFound else LookupResult.Found(items.map { it.volumeInfo })
    }

    suspend fun save(info: GoogleBookVolumeInfo, barcode: String): Long {
        val entity = BookEntity(
            title = info.title,
            authors = info.authors.joinToString(", ").ifEmpty { null },
            isbn13 = info.industryIdentifiers.firstOrNull { it.type == "ISBN_13" }?.identifier,
            isbn10 = info.industryIdentifiers.firstOrNull { it.type == "ISBN_10" }?.identifier,
            publisher = info.publisher,
            publishedYear = info.publishedDate?.take(4)?.toIntOrNull(),
            pageCount = info.pageCount,
            description = info.description,
            coverUrl = info.imageLinks?.thumbnail?.replace("http://", "https://"),
            barcode = barcode,
            dateAddedMillis = System.currentTimeMillis(),
        )
        return bookDao.insert(entity)
    }

    suspend fun delete(book: BookEntity) = bookDao.delete(book)
}
