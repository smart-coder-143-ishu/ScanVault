package com.scanvault.app.generator

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.scanvault.app.data.model.BarcodeFormatType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.EnumMap

object BarcodeGenerator {

    suspend fun generateBitmap(
        content: String,
        formatType: BarcodeFormatType,
        width: Int = 600,
        height: Int = if (formatType.is2D) 600 else 240
    ): Result<Bitmap> = withContext(Dispatchers.Default) {
        try {
            val zxingFormat = when (formatType) {
                BarcodeFormatType.QR_CODE -> BarcodeFormat.QR_CODE
                BarcodeFormatType.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
                BarcodeFormatType.AZTEC -> BarcodeFormat.AZTEC
                BarcodeFormatType.PDF417 -> BarcodeFormat.PDF_417
                BarcodeFormatType.EAN_13 -> BarcodeFormat.EAN_13
                BarcodeFormatType.EAN_8 -> BarcodeFormat.EAN_8
                BarcodeFormatType.UPC_A -> BarcodeFormat.UPC_A
                BarcodeFormatType.UPC_E -> BarcodeFormat.UPC_E
                BarcodeFormatType.CODE_128 -> BarcodeFormat.CODE_128
                BarcodeFormatType.CODE_39 -> BarcodeFormat.CODE_39
                BarcodeFormatType.CODABAR -> BarcodeFormat.CODABAR
                BarcodeFormatType.ITF -> BarcodeFormat.ITF
                else -> BarcodeFormat.QR_CODE
            }

            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 2)
            }

            val writer = MultiFormatWriter()
            val bitMatrix: BitMatrix = writer.encode(content, zxingFormat, width, height, hints)

            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)

            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)

            Result.success(bitmap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
