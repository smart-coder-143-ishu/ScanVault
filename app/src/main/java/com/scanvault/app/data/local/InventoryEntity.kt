package com.scanvault.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val price: String,
    val category: String,
    val quantity: String
)
