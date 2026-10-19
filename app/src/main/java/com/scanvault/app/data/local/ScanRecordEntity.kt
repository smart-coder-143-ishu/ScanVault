package com.scanvault.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.scanvault.app.data.model.BarcodeFormatType

@Entity(tableName = "scan_records")
data class ScanRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawValue: String,
    val format: String, // String representation of BarcodeFormatType
    val category: String, // e.g. "Website URL", "Product Barcode", "Wi-Fi Network"
    val timestamp: Long = System.currentTimeMillis(),
    val productName: String? = null,
    val productBrand: String? = null,
    val isFavorite: Boolean = false,
    val notes: String? = null
)
