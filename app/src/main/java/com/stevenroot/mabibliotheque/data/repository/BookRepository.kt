package com.stevenroot.mabibliotheque.data.repository

import com.stevenroot.mabibliotheque.data.local.BookDao
import com.stevenroot.mabibliotheque.data.local.BookEntity
import com.stevenroot.mabibliotheque.data.remote.BnfApi
import com.stevenroot.mabibliotheque.data.remote.BnfXmlParser
import com.stevenroot.mabibliotheque.data.remote.BookVolumeInfo
import com.stevenroot.mabibliotheque.data.remote.OpenLibraryApi
import kotlinx.coroutines.flow.Flow

class BookRepository(
    private val bookDao: BookDao,
    private val bnfApi: BnfApi,
    private val openLibraryApi: OpenLibraryApi,
) {
    fun observeAll(): Flow<List<BookEntity>> = bookDao.observeAll()

    fun observeById(id: Long): Flow<BookEntity?> = bookDao.observeById(id)

    suspend fun alreadyOwns(barcode: String): Boolean = bookDao.existsWithBarcode(barcode)

    /** A book's barcode is its ISBN-13 — a single direct lookup, no intermediate translation.
     * BnF (France's legal-deposit library) goes first since it's the most complete source for
     * French books; Open Library fills in when BnF has nothing (English titles, older/foreign
     * editions BnF never received). */
    suspend fun lookupByIsbn(isbn: String): LookupResult<BookVolumeInfo> =
        searchBnf(BnfApi.isbnQuery(isbn)).orElse { searchOpenLibrary(OpenLibraryApi.isbnQuery(isbn)) }

    suspend fun searchByTitle(query: String): LookupResult<BookVolumeInfo> =
        searchBnf(BnfApi.titleQuery(query)).orElse { searchOpenLibrary(query) }

    private suspend fun searchBnf(query: String): LookupResult<BookVolumeInfo> {
        val xml = runCatching { bnfApi.search(query).string() }
            .getOrElse { return LookupResult.Error(it.message ?: "Erreur réseau (BnF)") }
        val results = runCatching { BnfXmlParser.parse(xml) }.getOrDefault(emptyList())
        return if (results.isEmpty()) LookupResult.NotFound else LookupResult.Found(results)
    }

    private suspend fun searchOpenLibrary(query: String): LookupResult<BookVolumeInfo> {
        val docs = runCatching { openLibraryApi.search(query) }
            .getOrElse { return LookupResult.Error(it.message ?: "Erreur réseau (Open Library)") }
            .docs
        return if (docs.isEmpty()) LookupResult.NotFound else LookupResult.Found(docs.map { it.toBookVolumeInfo() })
    }

    private suspend fun LookupResult<BookVolumeInfo>.orElse(
        fallback: suspend () -> LookupResult<BookVolumeInfo>,
    ): LookupResult<BookVolumeInfo> = if (this is LookupResult.Found) this else fallback()

    suspend fun save(info: BookVolumeInfo, barcode: String): Long {
        val entity = BookEntity(
            title = info.title,
            authors = info.authors.joinToString(", ").ifEmpty { null },
            isbn13 = info.industryIdentifiers.firstOrNull { it.type == "ISBN_13" }?.identifier,
            isbn10 = info.industryIdentifiers.firstOrNull { it.type == "ISBN_10" }?.identifier,
            publisher = info.publisher,
            publishedYear = info.publishedDate?.take(4)?.toIntOrNull(),
            pageCount = info.pageCount,
            description = info.description,
            coverUrl = info.coverUrl,
            barcode = barcode,
            dateAddedMillis = System.currentTimeMillis(),
        )
        return bookDao.insert(entity)
    }

    suspend fun delete(book: BookEntity) = bookDao.delete(book)

    /** Sets/replaces the cover with a locally captured or picked image — same local-path-vs-URL
     * duality as [MovieCopyEntity.frontCoverPath][com.stevenroot.mabibliotheque.data.local.MovieCopyEntity]
     * (the UI layer decides how to load [BookEntity.coverUrl] based on whether it looks like a URL). */
    suspend fun updateCoverPath(bookId: Long, path: String) {
        val existing = bookDao.findById(bookId) ?: return
        bookDao.update(existing.copy(coverUrl = path))
    }

    /** Overwrites a book's fields in place — same row id, same barcode, same dateAdded — for
     * when the original ISBN lookup picked the wrong edition/book. */
    suspend fun overwriteBook(bookId: Long, info: BookVolumeInfo) {
        val existing = bookDao.findById(bookId) ?: return
        bookDao.update(
            existing.copy(
                title = info.title,
                authors = info.authors.joinToString(", ").ifEmpty { null },
                isbn13 = info.industryIdentifiers.firstOrNull { it.type == "ISBN_13" }?.identifier ?: existing.isbn13,
                isbn10 = info.industryIdentifiers.firstOrNull { it.type == "ISBN_10" }?.identifier ?: existing.isbn10,
                publisher = info.publisher,
                publishedYear = info.publishedDate?.take(4)?.toIntOrNull(),
                pageCount = info.pageCount,
                description = info.description,
                coverUrl = info.coverUrl,
            ),
        )
    }
}
