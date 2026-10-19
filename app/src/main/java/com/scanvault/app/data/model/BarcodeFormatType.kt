package com.scanvault.app.data.model

enum class BarcodeFormatType(val displayName: String, val is2D: Boolean) {
    QR_CODE("QR Code", true),
    DATA_MATRIX("Data Matrix", true),
    AZTEC("Aztec", true),
    PDF417("PDF417", true),
    EAN_13("EAN-13", false),
    EAN_8("EAN-8", false),
    UPC_A("UPC-A", false),
    UPC_E("UPC-E", false),
    CODE_128("Code 128", false),
    CODE_39("Code 39", false),
    CODE_93("Code 93", false),
    CODABAR("Codabar", false),
    ITF("ITF (Interleaved 2 of 5)", false),
    UNKNOWN("Unknown Barcode", false);

    companion object {
        fun fromMlKitFormat(format: Int): BarcodeFormatType {
            return when (format) {
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE -> QR_CODE
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX -> DATA_MATRIX
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC -> AZTEC
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417 -> PDF417
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13 -> EAN_13
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8 -> EAN_8
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A -> UPC_A
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E -> UPC_E
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128 -> CODE_128
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_39 -> CODE_39
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_93 -> CODE_93
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODABAR -> CODABAR
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ITF -> ITF
                else -> UNKNOWN
            }
        }
    }
}
