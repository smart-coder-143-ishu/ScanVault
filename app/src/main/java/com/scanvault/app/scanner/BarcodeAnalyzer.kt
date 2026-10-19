package com.scanvault.app.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.scanvault.app.data.model.BarcodeFormatType

class BarcodeAnalyzer(
    private val onBarcodeDetected: (rawValue: String, format: BarcodeFormatType) -> Unit
) : ImageAnalysis.Analyzer {

    // Configure ML Kit scanner to recognize all requested 1D and 2D formats
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODABAR,
            Barcode.FORMAT_ITF
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    // Debounce duplicate frame triggers
    private var lastScannedValue: String? = null
    private var lastScannedTimestamp: Long = 0
    private val debounceDelayMs = 1200L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (!rawValue.isNullOrBlank()) {
                            val now = System.currentTimeMillis()
                            if (rawValue != lastScannedValue || (now - lastScannedTimestamp) > debounceDelayMs) {
                                lastScannedValue = rawValue
                                lastScannedTimestamp = now
                                val format = BarcodeFormatType.fromMlKitFormat(barcode.format)
                                onBarcodeDetected(rawValue, format)
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    // Failures in frame parsing ignored to avoid crashing camera stream
                }
                .addOnCompleteListener {
                    // Crucial: close imageProxy so CameraX can feed next frame
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
