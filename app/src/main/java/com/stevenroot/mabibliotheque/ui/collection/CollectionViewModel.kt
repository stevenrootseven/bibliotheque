package com.stevenroot.mabibliotheque.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevenroot.mabibliotheque.data.local.BookEntity
import com.stevenroot.mabibliotheque.data.local.MovieWithCopies
import com.stevenroot.mabibliotheque.data.local.SeriesWithEpisodes
import com.stevenroot.mabibliotheque.data.repository.BookRepository
import com.stevenroot.mabibliotheque.data.repository.MovieRepository
import com.stevenroot.mabibliotheque.data.repository.SeriesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SortMode(val label: String) {
    DATE_ADDED("Ajout récent"),
    TITLE("Titre A-Z"),
    YEAR("Année"),
}

class CollectionViewModel(
    movieRepository: MovieRepository,
    bookRepository: BookRepository,
    seriesRepository: SeriesRepository,
) : ViewModel() {

    private val allMovies: StateFlow<List<MovieWithCopies>> = movieRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allBooks: StateFlow<List<BookEntity>> = bookRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allSeries: StateFlow<List<SeriesWithEpisodes>> = seriesRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE_ADDED)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    val movies: StateFlow<List<MovieWithCopies>> = combine(allMovies, _searchQuery, _sortMode) { list, query, sort ->
        val filtered = if (query.isBlank()) list else list.filter { movieMatches(it, query) }
        sortMovies(filtered, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val books: StateFlow<List<BookEntity>> = combine(allBooks, _searchQuery, _sortMode) { list, query, sort ->
        val filtered = if (query.isBlank()) list else list.filter { bookMatches(it, query) }
        sortBooks(filtered, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val series: StateFlow<List<SeriesWithEpisodes>> = combine(allSeries, _searchQuery) { list, query ->
        if (query.isBlank()) list else list.filter { it.series.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSortModeChanged(mode: SortMode) {
        _sortMode.value = mode
    }

    private fun movieMatches(entry: MovieWithCopies, rawQuery: String): Boolean {
        val query = rawQuery.trim()
        val movie = entry.movie
        return movie.title.contains(query, ignoreCase = true) ||
            movie.originalTitle?.contains(query, ignoreCase = true) == true ||
            movie.year?.toString()?.contains(query) == true ||
            movie.director?.contains(query, ignoreCase = true) == true ||
            movie.actors?.contains(query, ignoreCase = true) == true ||
            movie.genres?.contains(query, ignoreCase = true) == true ||
            entry.copies.any { it.format.contains(query, ignoreCase = true) }
    }

    private fun bookMatches(book: BookEntity, rawQuery: String): Boolean {
        val query = rawQuery.trim()
        return book.title.contains(query, ignoreCase = true) ||
            book.publishedYear?.toString()?.contains(query) == true ||
            book.authors?.contains(query, ignoreCase = true) == true ||
            book.publisher?.contains(query, ignoreCase = true) == true
    }

    private fun sortMovies(list: List<MovieWithCopies>, sort: SortMode): List<MovieWithCopies> = when (sort) {
        SortMode.DATE_ADDED -> list.sortedByDescending { it.movie.dateAddedMillis }
        SortMode.TITLE -> list.sortedBy { it.movie.title.lowercase() }
        SortMode.YEAR -> list.sortedByDescending { it.movie.year ?: 0 }
    }

    private fun sortBooks(list: List<BookEntity>, sort: SortMode): List<BookEntity> = when (sort) {
        SortMode.DATE_ADDED -> list.sortedByDescending { it.dateAddedMillis }
        SortMode.TITLE -> list.sortedBy { it.title.lowercase() }
        SortMode.YEAR -> list.sortedByDescending { it.publishedYear ?: 0 }
    }

    class Factory(
        private val movieRepository: MovieRepository,
        private val bookRepository: BookRepository,
        private val seriesRepository: SeriesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CollectionViewModel(movieRepository, bookRepository, seriesRepository) as T
    }
}
