package com.scanvault.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.scanvault.app.data.local.ScanDatabase
import com.scanvault.app.data.repository.ProductLookupRepository
import com.scanvault.app.data.repository.ScanRepository

class ScanVaultApplication : Application() {

    // Lazy initialization of Database and Repositories
    val database: ScanDatabase by lazy { ScanDatabase.getDatabase(this) }
    val scanRepository: ScanRepository by lazy { ScanRepository(database.scanDao()) }
    val productRepository: ProductLookupRepository by lazy { ProductLookupRepository() }

    override fun onCreate() {
        super.onCreate()
        // Initialize Mobile Ads SDK (runs asynchronously without blocking startup)
        try {
            MobileAds.initialize(this) {}
        } catch (_: Exception) {
            // Graceful handling if Google Play services are absent
        }
    }
}
