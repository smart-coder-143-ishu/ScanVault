package com.scanvault.app.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.scanvault.app.ui.navigation.Screen
import com.scanvault.app.data.repository.ScanRepository
import com.scanvault.app.data.repository.ProductLookupRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scanRepository: ScanRepository,
    productRepository: ProductLookupRepository // Naya Repository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Purane sabhi states
    var vibrateOnScan by remember { mutableStateOf(true) }
    var soundOnScan by remember { mutableStateOf(true) }
    var autoCopyScannedText by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf("System Default") }
    var showClearDialog by remember { mutableStateOf(false) }

    val totalScans by scanRepository.totalCount.collectAsState(initial = 0)

    // CSV File Import Launcher
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Importing inventory...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                val result = productRepository.importCsvToInventory(context, uri)
                result.onSuccess { count ->
                    Toast.makeText(context, "Successfully imported $count products!", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(context, "Failed to import CSV file.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Erase All Local Data?") },
            text = { Text("This will permanently remove all $totalScans scan records from your local Room database.") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        scanRepository.clearHistory()
                        showClearDialog = false
                    }
                }) {
                    Text("Erase Everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            // Premium Sleek Top Bar for Settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack, 
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Settings & Privacy", 
                    fontWeight = FontWeight.ExtraBold, 
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            
            // 1. --- INVENTORY MANAGEMENT SECTION (Naya) ---
            Text(
                text = "Inventory Management",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Import products via CSV file. When a barcode is scanned, your local inventory will be checked instantly even without internet.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { csvLauncher.launch("text/*") }, // Open File Picker
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Products from CSV", fontSize = 12.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { navController.navigate(Screen.InventoryDashboard.route) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Local Inventory", fontSize = 12.sp)
                    }
                }
            }

            // 2. --- SCANNER BEHAVIOR SECTION (Purana Pura Hissa) ---
            Text(
                text = "Scanner Behavior",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibrate on Scan", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Haptic pulse upon successful code detection", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = vibrateOnScan, onCheckedChange = { vibrateOnScan = it })
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Audio Confirmation", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Play subtle confirmation chime on scan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = soundOnScan, onCheckedChange = { soundOnScan = it })
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Copy to Clipboard", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Copy scanned payload text automatically", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoCopyScannedText, onCheckedChange = { autoCopyScannedText = it })
                    }
                }
            }

            // 3. --- PRIVACY & DATA SECTION ---
            Text(
                text = "Privacy & Local Storage",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "100% On-Device Architecture",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• No camera frames or video streams are ever transmitted or saved.\n" +
                                "• All barcode decoding is processed in real time via local Google ML Kit.\n" +
                                "• Scan history is kept exclusively in your device's local encrypted Room database.\n" +
                                "• Wi-Fi passwords and sensitive contact cards are never sent to external servers.\n" +
                                "• No user account registration or cloud login is required.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Erase Local Database ($totalScans records)", fontSize = 12.sp)
                    }
                }
            }

            // 4. --- APPLICATION ABOUT & VERSION (Restore kiya gaya) ---
            Text(
                text = "About",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "ScanVault Android", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(text = "Version 1.0.0 (Build 100)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Engineered with Kotlin 1.9, Jetpack Compose, Material 3, Android CameraX, Google ML Kit, and Android Room Database.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
