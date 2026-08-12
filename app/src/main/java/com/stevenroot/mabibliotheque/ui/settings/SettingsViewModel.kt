package com.stevenroot.mabibliotheque.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevenroot.mabibliotheque.data.export.CollectionExporter
import com.stevenroot.mabibliotheque.data.local.BookEntity
import com.stevenroot.mabibliotheque.data.local.MovieWithCopies
import com.stevenroot.mabibliotheque.data.local.SeriesWithEpisodes
import com.stevenroot.mabibliotheque.data.repository.BookRepository
import com.stevenroot.mabibliotheque.data.repository.CollectionImporter
import com.stevenroot.mabibliotheque.data.repository.ImportProgress
import com.stevenroot.mabibliotheque.data.repository.ImportResult
import com.stevenroot.mabibliotheque.data.repository.MovieRepository
import com.stevenroot.mabibliotheque.data.repository.SeriesRepository
import com.stevenroot.mabibliotheque.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Running(val progress: ImportProgress) : ImportUiState
    data class Done(val result: ImportResult) : ImportUiState
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data object Running : ExportUiState
    data class Error(val message: String) : ExportUiState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val collectionImporter: CollectionImporter,
    private val movieRepository: MovieRepository,
    private val bookRepository: BookRepository,
    private val seriesRepository: SeriesRepository,
) : ViewModel() {

    private val _apiKeyInput = MutableStateFlow("")
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    val importAvailable: Boolean get() = collectionImporter.isAvailable()

    init {
        viewModelScope.launch {
            _apiKeyInput.value = settingsRepository.currentTmdbApiKey().orEmpty()
        }
    }

    fun onInputChanged(value: String) {
        _apiKeyInput.value = value
        _saved.value = false
    }

    fun save() = viewModelScope.launch {
        settingsRepository.setTmdbApiKey(_apiKeyInput.value)
        _saved.value = true
    }

    fun runImport() = viewModelScope.launch {
        _importState.value = ImportUiState.Running(ImportProgress(0, 1, ""))
        val result = collectionImporter.import { progress ->
            _importState.value = ImportUiState.Running(progress)
        }
        _importState.value = ImportUiState.Done(result)
    }

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    fun exportCsv(context: Context) = runExport(context) { movies, books, series ->
        CollectionExporter.exportCsv(context.applicationContext, movies, books, series) to "text/csv"
    }

    fun exportPdf(context: Context) = runExport(context) { movies, books, series ->
        CollectionExporter.exportPdf(context.applicationContext, movies, books, series) to "application/pdf"
    }

    private fun runExport(
        context: Context,
        build: suspend (List<MovieWithCopies>, List<BookEntity>, List<SeriesWithEpisodes>) -> Pair<File, String>,
    ) = viewModelScope.launch {
        _exportState.value = ExportUiState.Running
        runCatching {
            val movies = movieRepository.observeAll().first()
            val books = bookRepository.observeAll().first()
            val series = seriesRepository.observeAll().first()
            build(movies, books, series)
        }.onSuccess { (file, mimeType) ->
            _exportState.value = ExportUiState.Idle
            context.startActivity(CollectionExporter.shareIntent(context, file, mimeType))
        }.onFailure {
            _exportState.value = ExportUiState.Error(it.message ?: "Erreur lors de l'export")
        }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val collectionImporter: CollectionImporter,
        private val movieRepository: MovieRepository,
        private val bookRepository: BookRepository,
        private val seriesRepository: SeriesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(settingsRepository, collectionImporter, movieRepository, bookRepository, seriesRepository) as T
    }
}
