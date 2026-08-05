package com.stevenroot.mabibliotheque.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevenroot.mabibliotheque.data.local.BookEntity
import com.stevenroot.mabibliotheque.data.repository.BookRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookDetailViewModel(
    bookId: Long,
    private val bookRepository: BookRepository,
) : ViewModel() {

    val book: StateFlow<BookEntity?> = bookRepository.observeById(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
