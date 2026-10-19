package com.scanvault.app.data.repository

import com.scanvault.app.data.local.ScanDao
import com.scanvault.app.data.local.ScanRecordEntity
import com.scanvault.app.data.model.BarcodeFormatType
import com.scanvault.app.data.model.ScanContent
import com.scanvault.app.data.parser.BarcodePayloadParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanRepository(private val scanDao: ScanDao) {

    val allScans: Flow<List<ScanRecordEntity>> = scanDao.getAllScans()
    val recentScans: Flow<List<ScanRecordEntity>> = scanDao.getRecentScans(5)
    val favoriteScans: Flow<List<ScanRecordEntity>> = scanDao.getFavoriteScans()
    val totalCount: Flow<Int> = scanDao.getTotalScanCount()

    fun searchScans(query: String): Flow<List<ScanRecordEntity>> = scanDao.searchScans(query)

    fun getScansByCategory(category: String): Flow<List<ScanRecordEntity>> =
        scanDao.getScansByCategory(category)

    suspend fun getScanById(id: Long): ScanRecordEntity? = withContext(Dispatchers.IO) {
        scanDao.getScanById(id)
    }

    suspend fun saveScan(
        rawValue: String,
        format: BarcodeFormatType,
        productName: String? = null,
        productBrand: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val parsed = BarcodePayloadParser.parse(rawValue, format)
        val entity = ScanRecordEntity(
            rawValue = rawValue,
            format = format.name,
            category = parsed.categoryName,
            timestamp = System.currentTimeMillis(),
            productName = productName,
            productBrand = productBrand
        )
        scanDao.insertScan(entity)
    }

    suspend fun toggleFavorite(record: ScanRecordEntity) = withContext(Dispatchers.IO) {
        scanDao.updateScan(record.copy(isFavorite = !record.isFavorite))
    }

    suspend fun deleteScan(id: Long) = withContext(Dispatchers.IO) {
        scanDao.deleteById(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        scanDao.clearAll()
    }

    suspend fun exportToCsv(records: List<ScanRecordEntity>): String = withContext(Dispatchers.Default) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("ID,Timestamp,Format,Category,Value,Product Name,Brand,Favorite\n")
        for (r in records) {
            val dateStr = dateFormat.format(Date(r.timestamp))
            val safeVal = escapeCsv(r.rawValue)
            val safeProd = escapeCsv(r.productName ?: "")
            val safeBrand = escapeCsv(r.productBrand ?: "")
            sb.append("${r.id},\"$dateStr\",\"${r.format}\",\"${r.category}\",\"$safeVal\",\"$safeProd\",\"$safeBrand\",${r.isFavorite}\n")
        }
        sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"")
    }
}
