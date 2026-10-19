package com.scanvault.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Scanner : Screen("scanner", "Scanner", Icons.Default.QrCodeScanner)
    object History : Screen("history", "History", Icons.Default.History)
    object Tools : Screen("tools", "Tools", Icons.Default.Build)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    // Sub-screens
    object ScanResult : Screen("result/{recordId}", "Scan Details") {
        fun createRoute(recordId: Long) = "result/$recordId"
    }

    object ProductDetails : Screen("product/{barcode}", "Product Details") {
        fun createRoute(barcode: String) = "product/$barcode"
    }

    object BulkScan : Screen("bulk_scan", "Bulk Scanner")
}

val BottomNavItems = listOf(
    Screen.Home,
    Screen.Scanner,
    Screen.History,
    Screen.Tools,
    Screen.Settings
)
