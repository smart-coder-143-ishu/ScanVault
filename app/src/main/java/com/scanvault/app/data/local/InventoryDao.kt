package com.scanvault.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): InventoryEntity?

    @Query("SELECT * FROM inventory_items")
    suspend fun getAllItems(): List<InventoryEntity>

    @Query("DELETE FROM inventory_items WHERE barcode = :barcode")
    suspend fun deleteItemByBarcode(barcode: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: InventoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<InventoryEntity>)
}
