package com.scanvault.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Yahan humne InventoryEntity add kiya hai aur version 2 kar diya hai
@Database(entities = [ScanRecordEntity::class, InventoryEntity::class], version = 2, exportSchema = false)
abstract class ScanDatabase : RoomDatabase() {
    
    abstract fun scanDao(): ScanDao
    abstract fun inventoryDao(): InventoryDao // Inventory ke liye naya DAO

    companion object {
        @Volatile
        private var INSTANCE: ScanDatabase? = null

        fun getDatabase(context: Context): ScanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScanDatabase::class.java,
                    "scan_vault.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
