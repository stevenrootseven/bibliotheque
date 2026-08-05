package com.stevenroot.mabibliotheque.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevenroot.mabibliotheque.data.local.MovieEntity
import com.stevenroot.mabibliotheque.data.repository.MovieRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MovieDetailViewModel(
    movieId: Long,
    private val movieRepository: MovieRepository,
) : ViewModel() {

    val movie: StateFlow<MovieEntity?> = movieRepository.observeById(movieId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(onDeleted: () -> Unit) {
        val current = movie.value ?: return
        viewModelScope.launch {
            movieRepository.delete(current)
            onDeleted()
        }
    }

    class Factory(
        private val movieId: Long,
        private val movieRepository: MovieRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MovieDetailViewModel(movieId, movieRepository) as T
    }
}
