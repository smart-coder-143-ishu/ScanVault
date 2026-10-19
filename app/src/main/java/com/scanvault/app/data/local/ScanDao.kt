package com.scanvault.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Query("SELECT * FROM scan_records ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanRecordEntity>>

    @Query("SELECT * FROM scan_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentScans(limit: Int = 5): Flow<List<ScanRecordEntity>>

    @Query("SELECT * FROM scan_records WHERE id = :id")
    suspend fun getScanById(id: Long): ScanRecordEntity?

    @Query("SELECT * FROM scan_records WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteScans(): Flow<List<ScanRecordEntity>>

    @Query("SELECT * FROM scan_records WHERE rawValue LIKE '%' || :query || '%' OR productName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchScans(query: String): Flow<List<ScanRecordEntity>>

    @Query("SELECT * FROM scan_records WHERE category = :category ORDER BY timestamp DESC")
    fun getScansByCategory(category: String): Flow<List<ScanRecordEntity>>

    @Query("SELECT COUNT(*) FROM scan_records")
    fun getTotalScanCount(): Flow<Int>

    @Query("SELECT * FROM scan_records WHERE rawValue = :rawValue ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLastByRawValue(rawValue: String): ScanRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(record: ScanRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ScanRecordEntity>)

    @Update
    suspend fun updateScan(record: ScanRecordEntity)

    @Delete
    suspend fun deleteScan(record: ScanRecordEntity)

    @Query("DELETE FROM scan_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scan_records")
    suspend fun clearAll()
}
