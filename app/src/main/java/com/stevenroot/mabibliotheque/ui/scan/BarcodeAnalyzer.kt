package com.stevenroot.mabibliotheque.ui.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Books and movie discs are both sold under EAN-13/UPC-A retail barcodes. Requires the same
 * value to be read [CONFIRM_THRESHOLD] times in a row before firing [onBarcodeConfirmed] — a
 * cheap guard against a single misread frame, the same pattern validated on the TWP scanner.
 */
class BarcodeAnalyzer(private val onBarcodeConfirmed: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    private var lastValue: String? = null
    private var consecutiveCount = 0

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes -> handleResults(barcodes) }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun handleResults(barcodes: List<Barcode>) {
        val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
        if (value == null) {
            lastValue = null
            consecutiveCount = 0
            return
        }
        if (value == lastValue) {
            consecutiveCount++
        } else {
            lastValue = value
            consecutiveCount = 1
        }
        if (consecutiveCount >= CONFIRM_THRESHOLD) {
            onBarcodeConfirmed(value)
        }
    }

    companion object {
        private const val CONFIRM_THRESHOLD = 2
    }
}
