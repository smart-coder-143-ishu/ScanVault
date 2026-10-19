package com.scanvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.scanvault.app.ui.navigation.ScanVaultNavHost
import com.scanvault.app.ui.theme.ScanVaultTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ScanVaultApplication
        val scanRepository = app.scanRepository
        val productRepository = app.productRepository

        setContent {
            ScanVaultTheme {
                val navController = rememberNavController()
                ScanVaultNavHost(
                    navController = navController,
                    scanRepository = scanRepository,
                    productRepository = productRepository
                )
            }
        }
    }
}
