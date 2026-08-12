package com.stevenroot.mabibliotheque.ui.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevenroot.mabibliotheque.data.remote.BookIdentifier
import com.stevenroot.mabibliotheque.data.remote.BookVolumeInfo
import com.stevenroot.mabibliotheque.data.remote.TmdbMovieSummary
import com.stevenroot.mabibliotheque.data.repository.BarcodeClassifier
import com.stevenroot.mabibliotheque.data.repository.BookRepository
import com.stevenroot.mabibliotheque.data.repository.LookupResult
import com.stevenroot.mabibliotheque.data.repository.MovieRepository
import com.stevenroot.mabibliotheque.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScanViewModel(
    private val movieRepository: MovieRepository,
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Scanning)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    /** Guards against ML Kit firing the analyzer repeatedly for the same still-visible barcode. */
    private var isProcessingScan = false

    fun onBarcodeDetected(rawCode: String) {
        if (isProcessingScan || _state.value !is ScanUiState.Scanning) return
        isProcessingScan = true
        startLookup(rawCode)
    }

    private fun startLookup(barcode: String) {
        _state.value = ScanUiState.LookingUp(barcode)
        if (BarcodeClassifier.isIsbn13(barcode)) {
            lookupBook(barcode.filter { it.isDigit() })
        } else {
            lookupMovie(barcode)
        }
    }

    private fun lookupMovie(barcode: String) = viewModelScope.launch {
        val apiKey = settingsRepository.currentTmdbApiKey()
        if (apiKey.isNullOrBlank()) {
            _state.value = ScanUiState.MissingApiKey(barcode)
            return@launch
        }
        when (val result = movieRepository.lookupByBarcode(barcode, apiKey)) {
            is LookupResult.Found -> handleMovieCandidates(barcode, result.candidates)
            LookupResult.NotFound -> _state.value = ScanUiState.NotFound(barcode, MediaKind.MOVIE)
            is LookupResult.Error -> _state.value = ScanUiState.Error(result.message, barcode)
        }
    }

    private fun lookupBook(isbn: String) = viewModelScope.launch {
        when (val result = bookRepository.lookupByIsbn(isbn)) {
            is LookupResult.Found -> handleBookCandidates(isbn, result.candidates)
            LookupResult.NotFound -> _state.value = ScanUiState.NotFound(isbn, MediaKind.BOOK)
            is LookupResult.Error -> _state.value = ScanUiState.Error(result.message, isbn)
        }
    }

    private fun handleMovieCandidates(barcode: String, candidates: List<TmdbMovieSummary>) {
        if (candidates.size == 1) {
            selectMovieCandidate(barcode, candidates.first())
        } else {
            _state.value = ScanUiState.MovieCandidates(barcode, candidates)
        }
    }

    private fun handleBookCandidates(barcode: String, candidates: List<BookVolumeInfo>) {
        _state.value = ScanUiState.BookConfirm(barcode, candidates.first())
    }

    fun selectMovieCandidate(barcode: String, summary: TmdbMovieSummary) = viewModelScope.launch {
        val apiKey = settingsRepository.currentTmdbApiKey() ?: run {
            _state.value = ScanUiState.MissingApiKey(barcode)
            return@launch
        }
        val details = runCatching { movieRepository.fetchDetails(summary.id, apiKey) }
            .getOrElse {
                _state.value = ScanUiState.Error(it.message ?: "Erreur réseau (TMDB)", barcode)
                return@launch
            }
        _state.value = ScanUiState.MovieConfirm(barcode, details, AVAILABLE_MOVIE_FORMATS.first())
    }

    fun setMovieFormat(format: String) {
        val current = _state.value
        if (current is ScanUiState.MovieConfirm) {
            _state.value = current.copy(format = format)
        }
    }

    fun confirmSaveMovie() {
        val current = _state.value as? ScanUiState.MovieConfirm ?: return
        viewModelScope.launch {
            movieRepository.save(current.details, current.barcode.takeIf { it.isNotBlank() }, current.format)
            _state.value = ScanUiState.Saved(MediaKind.MOVIE, current.details.title)
        }
    }

    /** When entry started from [startManualEntry] (no barcode was ever scanned), fall back to
     * the confirmed edition's own ISBN — the real identifier for that book — and only as a last
     * resort (no ISBN at all in the source data either) a synthetic value, since [BookEntity]
     * requires a non-null barcode. */
    fun confirmSaveBook() {
        val current = _state.value as? ScanUiState.BookConfirm ?: return
        viewModelScope.launch {
            val barcode = current.barcode.ifBlank {
                current.info.industryIdentifiers.firstOrNull { it.type == "ISBN_13" }?.identifier
                    ?: current.info.industryIdentifiers.firstOrNull()?.identifier
                    ?: "manual-${System.currentTimeMillis()}"
            }
            bookRepository.save(current.info, barcode)
            _state.value = ScanUiState.Saved(MediaKind.BOOK, current.info.title)
        }
    }

    fun startManualSearch(kind: MediaKind, barcode: String) {
        _state.value = ScanUiState.ManualSearch(kind, barcode)
    }

    /** Entry point for titles that have no scannable barcode at all (a damaged/missing barcode,
     * or a book whose ISBN is only printed as text) — skips straight to manual search with no
     * barcode context, rather than requiring a scan attempt first. */
    fun startManualEntry(kind: MediaKind) {
        isProcessingScan = true
        _state.value = ScanUiState.ManualSearch(kind, barcode = "")
    }

    fun startCoverPhoto(kind: MediaKind, barcode: String) {
        _state.value = ScanUiState.CoverPhoto(barcode, kind)
    }

    /**
     * Ranks recognized cover text by font size (bounding-box height) as a cheap proxy for the
     * title — normally the most prominent line on a front/back cover — then hands the user an
     * editable, pre-filled manual search rather than searching automatically: OCR on a disc
     * cover is noisy (taglines, actor names, review quotes can be larger than the title), so the
     * guess must stay correctable, same as every other path in this screen.
     */
    fun onCoverPhotoCaptured(bitmap: Bitmap) = viewModelScope.launch {
        val current = _state.value as? ScanUiState.CoverPhoto ?: return@launch
        _state.value = ScanUiState.RecognizingCover(current.barcode, current.kind)
        val lines = runCatching { CoverTextRecognizer.recognizeLines(bitmap) }
            .getOrElse {
                _state.value = ScanUiState.Error(it.message ?: "Erreur de lecture du texte", current.barcode)
                return@launch
            }
        val distinctTexts = lines.map { it.text }.distinct()
        _state.value = ScanUiState.ManualSearch(
            kind = current.kind,
            barcode = current.barcode,
            query = distinctTexts.firstOrNull().orEmpty(),
            suggestions = distinctTexts.drop(1).take(5),
        )
    }

    fun updateManualQuery(query: String) {
        val current = _state.value
        if (current is ScanUiState.ManualSearch) {
            _state.value = current.copy(query = query)
        }
    }

    fun selectManualSuggestion(text: String) {
        val current = _state.value
        if (current is ScanUiState.ManualSearch) {
            _state.value = current.copy(query = text)
        }
    }

    fun runManualSearch() = viewModelScope.launch {
        val current = _state.value as? ScanUiState.ManualSearch ?: return@launch
        if (current.query.isBlank()) return@launch
        _state.value = ScanUiState.LookingUp(current.barcode)
        when (current.kind) {
            MediaKind.MOVIE -> {
                val apiKey = settingsRepository.currentTmdbApiKey()
                if (apiKey.isNullOrBlank()) {
                    _state.value = ScanUiState.MissingApiKey(current.barcode)
                    return@launch
                }
                when (val result = movieRepository.searchByTitle(current.query, apiKey)) {
                    is LookupResult.Found -> _state.value = ScanUiState.MovieCandidates(current.barcode, result.candidates)
                    LookupResult.NotFound -> _state.value = ScanUiState.NotFound(current.barcode, MediaKind.MOVIE)
                    is LookupResult.Error -> _state.value = ScanUiState.Error(result.message, current.barcode)
                }
            }
            MediaKind.BOOK -> {
                when (val result = bookRepository.searchByTitle(current.query)) {
                    is LookupResult.Found -> _state.value = ScanUiState.BookCandidates(current.barcode, result.candidates)
                    LookupResult.NotFound -> _state.value = ScanUiState.NotFound(current.barcode, MediaKind.BOOK)
                    is LookupResult.Error -> _state.value = ScanUiState.Error(result.message, current.barcode)
                }
            }
        }
    }

    /** Reached when BnF + Open Library both come up empty even after a manual title search —
     * the user fills in the fiche by hand instead of giving up. */
    fun startBookManualEntry() {
        val current = _state.value
        val barcode = (current as? ScanUiState.ManualSearch)?.barcode
            ?: (current as? ScanUiState.NotFound)?.barcode
            ?: ""
        val prefillTitle = (current as? ScanUiState.ManualSearch)?.query.orEmpty()
        _state.value = ScanUiState.BookManualEntry(barcode = barcode, title = prefillTitle, isbn = barcode)
    }

    fun updateManualEntryTitle(value: String) {
        (_state.value as? ScanUiState.BookManualEntry)?.let { _state.value = it.copy(title = value) }
    }

    fun updateManualEntryAuthors(value: String) {
        (_state.value as? ScanUiState.BookManualEntry)?.let { _state.value = it.copy(authors = value) }
    }

    fun updateManualEntryYear(value: String) {
        (_state.value as? ScanUiState.BookManualEntry)?.let { _state.value = it.copy(year = value) }
    }

    fun updateManualEntryIsbn(value: String) {
        (_state.value as? ScanUiState.BookManualEntry)?.let { _state.value = it.copy(isbn = value) }
    }

    /** Builds a [BookVolumeInfo] straight from what the user typed — same shape a real BnF/Open
     * Library match would produce — and saves it through the normal path, so it behaves exactly
     * like any other book afterwards (editable cover, "rechercher à nouveau", etc). */
    fun confirmBookManualEntry() {
        val current = _state.value as? ScanUiState.BookManualEntry ?: return
        if (current.title.isBlank()) return
        viewModelScope.launch {
            val isbn = current.isbn.filter { it.isDigit() }
            val info = BookVolumeInfo(
                title = current.title.trim(),
                authors = current.authors.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                publisher = null,
                publishedDate = current.year.trim().ifBlank { null },
                pageCount = null,
                description = null,
                industryIdentifiers = when (isbn.length) {
                    13 -> listOf(BookIdentifier("ISBN_13", isbn))
                    10 -> listOf(BookIdentifier("ISBN_10", isbn))
                    else -> emptyList()
                },
                coverUrl = null,
            )
            val barcode = current.barcode.ifBlank { isbn }.ifBlank { "manual-${System.currentTimeMillis()}" }
            bookRepository.save(info, barcode)
            _state.value = ScanUiState.Saved(MediaKind.BOOK, info.title)
        }
    }

    fun onCoverPhotoError(message: String) {
        val current = _state.value as? ScanUiState.CoverPhoto ?: return
        _state.value = ScanUiState.Error(message, current.barcode)
    }

    fun selectBookCandidate(barcode: String, info: BookVolumeInfo) {
        _state.value = ScanUiState.BookConfirm(barcode, info)
    }

    fun resetToScanning() {
        isProcessingScan = false
        _state.value = ScanUiState.Scanning
    }

    class Factory(
        private val movieRepository: MovieRepository,
        private val bookRepository: BookRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScanViewModel(movieRepository, bookRepository, settingsRepository) as T
    }
}
