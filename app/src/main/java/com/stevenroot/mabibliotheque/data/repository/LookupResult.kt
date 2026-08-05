package com.stevenroot.mabibliotheque.data.repository

/** Outcome of a barcode-driven metadata lookup, surfaced to the scan UI. */
sealed interface LookupResult<out T> {
    data class Found<T>(val candidates: List<T>) : LookupResult<T>
    data object NotFound : LookupResult<Nothing>
    data class Error(val message: String) : LookupResult<Nothing>
}
