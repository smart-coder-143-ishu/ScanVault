package com.scanvault.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScanRecordEntity::class], version = 1, exportSchema = false)
abstract class ScanDatabase : RoomDatabase() {

    abstract fun scanDao(): ScanDao

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
