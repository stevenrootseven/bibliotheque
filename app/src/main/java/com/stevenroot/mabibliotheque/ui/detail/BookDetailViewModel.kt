package com.stevenroot.mabibliotheque.ui.detail

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevenroot.mabibliotheque.data.local.BookEntity
import com.stevenroot.mabibliotheque.data.local.CoverStorage
import com.stevenroot.mabibliotheque.data.remote.BookVolumeInfo
import com.stevenroot.mabibliotheque.data.repository.BookRepository
import com.stevenroot.mabibliotheque.data.repository.LookupResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for the "search again" dialog — same idea as [com.stevenroot.mabibliotheque.ui.detail.RematchState]
 * for movies, but against Open Library by title instead of TMDB. */
data class BookRematchState(
    val query: String,
    val loading: Boolean = false,
    val results: List<BookVolumeInfo> = emptyList(),
    val error: String? = null,
)

class BookDetailViewModel(
    bookId: Long,
    private val bookRepository: BookRepository,
) : ViewModel() {

    val book: StateFlow<BookEntity?> = bookRepository.observeById(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _rematch = MutableStateFlow<BookRematchState?>(null)
    val rematch: StateFlow<BookRematchState?> = _rematch.asStateFlow()

    fun openRematch() {
        _rematch.value = BookRematchState(query = book.value?.title.orEmpty())
    }

    fun updateRematchQuery(query: String) {
        _rematch.value = _rematch.value?.copy(query = query)
    }

    fun dismissRematch() {
        _rematch.value = null
    }

    fun searchRematch() {
        val state = _rematch.value ?: return
        if (state.query.isBlank()) return
        viewModelScope.launch {
            _rematch.value = state.copy(loading = true, error = null)
            when (val result = bookRepository.searchByTitle(state.query)) {
                is LookupResult.Found -> _rematch.value = state.copy(loading = false, results = result.candidates)
                LookupResult.NotFound -> _rematch.value = state.copy(loading = false, results = emptyList(), error = "Aucun résultat.")
                is LookupResult.Error -> _rematch.value = state.copy(loading = false, error = result.message)
            }
        }
    }

    /** Retries by the book's own ISBN rather than its (possibly hand-typed, possibly imperfect)
     * title — for a manually-created fiche, or one saved before BnF/Open Library indexed the
     * title, this is the reliable way to check "is it there now?" without retyping anything. */
    fun searchRematchByIsbn() {
        val state = _rematch.value ?: return
        val isbn = rematchIsbn() ?: return
        viewModelScope.launch {
            _rematch.value = state.copy(loading = true, error = null)
            when (val result = bookRepository.lookupByIsbn(isbn)) {
                is LookupResult.Found -> _rematch.value = state.copy(loading = false, results = result.candidates)
                LookupResult.NotFound -> _rematch.value = state.copy(loading = false, results = emptyList(), error = "Aucun résultat.")
                is LookupResult.Error -> _rematch.value = state.copy(loading = false, error = result.message)
            }
        }
    }

    fun rematchIsbn(): String? {
        val current = book.value ?: return null
        val isbn = current.isbn13 ?: current.isbn10 ?: current.barcode
        return isbn.filter { it.isDigit() }.takeIf { it.length == 13 || it.length == 10 }
    }

    fun selectRematchCandidate(candidate: BookVolumeInfo) {
        val bookId = book.value?.id ?: return
        viewModelScope.launch {
            bookRepository.overwriteBook(bookId, candidate)
            _rematch.value = null
        }
    }

    fun replaceCoverFromBitmap(context: Context, bitmap: Bitmap) {
        val bookId = book.value?.id ?: return
        viewModelScope.launch {
            val path = CoverStorage.saveBitmap(context, bitmap, "book_${bookId}_${System.currentTimeMillis()}.jpg")
            bookRepository.updateCoverPath(bookId, path)
        }
    }

    fun replaceCoverFromUri(context: Context, uri: Uri) {
        val bookId = book.value?.id ?: return
        viewModelScope.launch {
            val path = CoverStorage.saveFromUri(context, uri, "book_${bookId}_${System.currentTimeMillis()}.jpg")
            bookRepository.updateCoverPath(bookId, path)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val current = book.value ?: return
        viewModelScope.launch {
            bookRepository.delete(current)
            onDeleted()
        }
    }

    class Factory(
        private val bookId: Long,
        private val bookRepository: BookRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BookDetailViewModel(bookId, bookRepository) as T
    }
}
