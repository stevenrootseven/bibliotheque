package com.stevenroot.mabibliotheque.data.repository

/**
 * A book's EAN-13 barcode *is* its ISBN-13 (the "Bookland" 978/979 prefix range reserved by
 * GS1 for publications) — no external lookup is needed to go from barcode to ISBN, unlike a
 * movie's UPC/EAN which is an opaque retail code with no relation to the film's identity.
 */
object BarcodeClassifier {
    fun isIsbn13(rawCode: String): Boolean {
        val digits = rawCode.filter { it.isDigit() }
        return digits.length == 13 && (digits.startsWith("978") || digits.startsWith("979"))
    }
}
