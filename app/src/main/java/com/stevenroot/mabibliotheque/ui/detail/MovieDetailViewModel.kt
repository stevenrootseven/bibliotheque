package com.stevenroot.mabibliotheque.ui.detail

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevenroot.mabibliotheque.data.local.CoverStorage
import com.stevenroot.mabibliotheque.data.local.MovieCopyEntity
import com.stevenroot.mabibliotheque.data.local.MovieWithCopies
import com.stevenroot.mabibliotheque.data.remote.TmdbMovieSummary
import com.stevenroot.mabibliotheque.data.repository.LookupResult
import com.stevenroot.mabibliotheque.data.repository.MovieRepository
import com.stevenroot.mabibliotheque.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for the "search again" dialog — a movie's fiche is wrong (bad barcode/filename match)
 * and the user wants to look it up on TMDB by title instead, same candidate-picking flow as the
 * scan screen but reachable from an existing entry. Null [rematch] means the dialog is closed. */
data class RematchState(
    val query: String,
    val loading: Boolean = false,
    val results: List<TmdbMovieSummary> = emptyList(),
    val error: String? = null,
)

class MovieDetailViewModel(
    movieId: Long,
    private val movieRepository: MovieRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val movie: StateFlow<MovieWithCopies?> = movieRepository.observeById(movieId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _rematch = MutableStateFlow<RematchState?>(null)
    val rematch: StateFlow<RematchState?> = _rematch.asStateFlow()

    fun openRematch() {
        _rematch.value = RematchState(query = movie.value?.movie?.title.orEmpty())
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
            val apiKey = settingsRepository.currentTmdbApiKey()
            if (apiKey.isNullOrBlank()) {
                _rematch.value = state.copy(loading = false, error = "Clé TMDB manquante — configure-la dans Réglages.")
                return@launch
            }
            when (val result = movieRepository.searchByTitle(state.query, apiKey)) {
                is LookupResult.Found -> _rematch.value = state.copy(loading = false, results = result.candidates)
                LookupResult.NotFound -> _rematch.value = state.copy(loading = false, results = emptyList(), error = "Aucun résultat.")
                is LookupResult.Error -> _rematch.value = state.copy(loading = false, error = result.message)
            }
        }
    }

    fun selectRematchCandidate(candidate: TmdbMovieSummary) {
        val movieId = movie.value?.movie?.id ?: return
        viewModelScope.launch {
            val apiKey = settingsRepository.currentTmdbApiKey() ?: return@launch
            val details = runCatching { movieRepository.fetchDetails(candidate.id, apiKey) }.getOrNull() ?: return@launch
            movieRepository.overwriteMovie(movieId, details)
            _rematch.value = null
        }
    }

    /** Removes a single exemplaire (a Blu-ray, a NAS file...); the movie itself disappears once
     * its last copy is gone. */
    fun deleteCopy(copy: MovieCopyEntity, onLastCopyDeleted: () -> Unit) {
        val remainingAfterDelete = (movie.value?.copies?.size ?: 1) - 1
        viewModelScope.launch {
            movieRepository.deleteCopy(copy)
            if (remainingAfterDelete <= 0) onLastCopyDeleted()
        }
    }

    fun deleteMovie(onDeleted: () -> Unit) {
        val current = movie.value?.movie ?: return
        viewModelScope.launch {
            movieRepository.deleteMovie(current)
            onDeleted()
        }
    }

    /** The copy whose cover is shown/edited on this screen — the first one, since most movies
     * have a single exemplaire and there's no natural "which copy" choice to surface for cover
     * editing otherwise. */
    private fun primaryCopy(): MovieCopyEntity? = movie.value?.copies?.firstOrNull()

    fun replaceCoverFromBitmap(context: Context, bitmap: Bitmap, isFront: Boolean) {
        val copy = primaryCopy() ?: return
        viewModelScope.launch {
            val suffix = if (isFront) "front" else "back"
            val path = CoverStorage.saveBitmap(context, bitmap, "${copy.id}_${suffix}_${System.currentTimeMillis()}.jpg")
            applyCoverPath(copy, path, isFront)
        }
    }

    fun replaceCoverFromUri(context: Context, uri: Uri, isFront: Boolean) {
        val copy = primaryCopy() ?: return
        viewModelScope.launch {
            val suffix = if (isFront) "front" else "back"
            val path = CoverStorage.saveFromUri(context, uri, "${copy.id}_${suffix}_${System.currentTimeMillis()}.jpg")
            applyCoverPath(copy, path, isFront)
        }
    }

    private suspend fun applyCoverPath(copy: MovieCopyEntity, path: String, isFront: Boolean) {
        movieRepository.updateCopy(
            if (isFront) copy.copy(frontCoverPath = path) else copy.copy(backCoverPath = path),
        )
    }

    class Factory(
        private val movieId: Long,
        private val movieRepository: MovieRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MovieDetailViewModel(movieId, movieRepository, settingsRepository) as T
    }
}
