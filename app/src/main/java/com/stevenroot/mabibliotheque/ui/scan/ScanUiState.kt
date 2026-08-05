package com.stevenroot.mabibliotheque.ui.scan

import com.stevenroot.mabibliotheque.data.remote.GoogleBookVolumeInfo
import com.stevenroot.mabibliotheque.data.remote.TmdbMovieDetails
import com.stevenroot.mabibliotheque.data.remote.TmdbMovieSummary

enum class MediaKind { MOVIE, BOOK }

/**
 * Every path ends in an explicit user-confirmed [MovieConfirm]/[BookConfirm] step before
 * anything is written to Room — never auto-save straight off a scan or a single search result,
 * mirroring the "editable right up to the last tap" rule validated on the TWP scanning app.
 */
sealed interface ScanUiState {
    data object Scanning : ScanUiState
    data class LookingUp(val barcode: String) : ScanUiState
    data class MissingApiKey(val barcode: String) : ScanUiState
    data class MovieCandidates(val barcode: String, val candidates: List<TmdbMovieSummary>) : ScanUiState
    data class MovieConfirm(val barcode: String, val details: TmdbMovieDetails, val format: String) : ScanUiState
    data class BookCandidates(val barcode: String, val candidates: List<GoogleBookVolumeInfo>) : ScanUiState
    data class BookConfirm(val barcode: String, val info: GoogleBookVolumeInfo) : ScanUiState
    data class NotFound(val barcode: String, val kind: MediaKind) : ScanUiState
    data class CoverPhoto(val barcode: String, val kind: MediaKind) : ScanUiState
    data class RecognizingCover(val barcode: String, val kind: MediaKind) : ScanUiState
    data class ManualSearch(
        val kind: MediaKind,
        val barcode: String,
        val query: String = "",
        val suggestions: List<String> = emptyList(),
    ) : ScanUiState
    data class Error(val message: String, val barcode: String?) : ScanUiState
    data class Saved(val kind: MediaKind, val title: String) : ScanUiState
}

val AVAILABLE_MOVIE_FORMATS = listOf("Blu-ray", "4K Ultra HD", "DVD", "Autre")
