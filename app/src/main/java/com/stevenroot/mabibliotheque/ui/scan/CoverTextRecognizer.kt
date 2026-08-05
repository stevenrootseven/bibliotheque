package com.stevenroot.mabibliotheque.ui.scan

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads the text printed on a photographed disc cover so a movie can be identified even when
 * its UPC/EAN isn't in UPCitemdb's database at all (common for French/older releases) — the
 * cover always has a legible title, unlike the barcode. A cover's title is normally the most
 * visually prominent line, so lines are ranked by their bounding-box height as a cheap proxy
 * for font size; the caller shows the top guess pre-filled and editable, with the rest as
 * quick-pick suggestions, mirroring the "always end in an editable confirm step" rule that
 * proved out on the TWP scanner's OCR fallback.
 */
object CoverTextRecognizer {

    data class RecognizedLine(val text: String, val heightPx: Int)

    suspend fun recognizeLines(bitmap: Bitmap): List<RecognizedLine> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return result.textBlocks
            .flatMap { block -> block.lines }
            .mapNotNull { line ->
                val text = line.text.trim()
                if (text.length < 2) return@mapNotNull null
                RecognizedLine(text, line.boundingBox?.height() ?: 0)
            }
            .sortedByDescending { it.heightPx }
    }
}
