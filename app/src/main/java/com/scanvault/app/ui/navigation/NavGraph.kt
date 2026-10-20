package com.scanvault.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.scanvault.app.data.repository.ProductLookupRepository
import com.scanvault.app.data.repository.ScanRepository
import com.scanvault.app.ui.screens.bulk.BulkScanScreen
import com.scanvault.app.ui.screens.history.HistoryScreen
import com.scanvault.app.ui.screens.home.HomeScreen
import com.scanvault.app.ui.screens.product.ProductDetailsScreen
import com.scanvault.app.ui.screens.result.ScanResultScreen
import com.scanvault.app.ui.screens.scanner.ScannerScreen
import com.scanvault.app.ui.screens.tools.GeneratorScreen
import com.scanvault.app.ui.screens.settings.SettingsScreen

@Composable
fun ScanVaultNavHost(
    navController: NavHostController,
    scanRepository: ScanRepository,
    productRepository: ProductLookupRepository
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Only display bottom navigation on top-level tabs
    val showBottomBar = currentRoute in BottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    tonalElevation = 6.dp
                ) {
                    BottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { item.icon?.let { Icon(it, contentDescription = item.title) } },
                            label = { Text(item.title, fontSize = 11.sp) },
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Screen.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(navController = navController, scanRepository = scanRepository)
            }
            composable(Screen.Scanner.route) {
                ScannerScreen(navController = navController, scanRepository = scanRepository)
            }
            composable(Screen.History.route) {
                HistoryScreen(navController = navController, scanRepository = scanRepository)
            }
            composable(Screen.Tools.route) {
                GeneratorScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController, scanRepository = scanRepository, productRepository = productRepository)
            }
            composable(Screen.BulkScan.route) {
                BulkScanScreen(navController = navController, scanRepository = scanRepository)
            }
            composable(
                route = Screen.ScanResult.route,
                arguments = listOf(navArgument("recordId") { type = NavType.LongType })
            ) { backStackEntry ->
                val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
                ScanResultScreen(
                    recordId = recordId,
                    navController = navController,
                    scanRepository = scanRepository
                )
            }
            composable(
                route = Screen.ProductDetails.route,
                arguments = listOf(navArgument("barcode") { type = NavType.StringType })
            ) { backStackEntry ->
                val barcode = backStackEntry.arguments?.getString("barcode") ?: ""
                ProductDetailsScreen(
                    barcode = barcode,
                    navController = navController,
                    productRepository = productRepository
                )
            }
        }
    }
}
