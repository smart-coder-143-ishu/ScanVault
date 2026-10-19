package com.scanvault.app.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.scanvault.app.data.model.BarcodeFormatType
import com.scanvault.app.data.repository.ScanRepository
import com.scanvault.app.scanner.CameraPreview
import com.scanvault.app.ui.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun ScannerScreen(
    navController: NavController,
    scanRepository: ScanRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var isTorchEnabled by remember { mutableStateOf(false) }
    var isProcessingGalleryImage by remember { mutableStateOf(false) }

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingGalleryImage = true
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                val scanner = BarcodeScanning.getClient()
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        isProcessingGalleryImage = false
                        val firstBarcode = barcodes.firstOrNull()
                        if (firstBarcode != null && !firstBarcode.rawValue.isNullOrBlank()) {
                            val format = BarcodeFormatType.fromMlKitFormat(firstBarcode.format)
                            coroutineScope.launch {
                                val id = scanRepository.saveScan(firstBarcode.rawValue!!, format)
                                navController.navigate(Screen.ScanResult.createRoute(id))
                            }
                        }
                    }
                    .addOnFailureListener {
                        isProcessingGalleryImage = false
                    }
            } catch (_: Exception) {
                isProcessingGalleryImage = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val vibrateOnScan = {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
            }
        } catch (_: Exception) {}
    }

    if (!hasCameraPermission) {
        // Permission Request Fallback Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ScanVault analyzes barcodes and QR codes locally in real-time. Camera frames are never recorded or uploaded.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Allow Camera Access")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text(text = "Open App Settings", fontSize = 12.sp)
                }
            }
        }
    } else {
        // Active Camera Scanner with Reticle & Animated Scan Laser
        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(
                isTorchEnabled = isTorchEnabled,
                onBarcodeDetected = { rawValue, format ->
                    vibrateOnScan()
                    coroutineScope.launch {
                        val recordId = scanRepository.saveScan(rawValue, format)
                        navController.navigate(Screen.ScanResult.createRoute(recordId))
                    }
                }
            )

            // Reticle Overlay with Animated Scanning Line
            val infiniteTransition = rememberInfiniteTransition(label = "Laser")
            val laserOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "LaserPosition"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxWidth = size.width * 0.72f
                val boxHeight = boxWidth
                val left = (size.width - boxWidth) / 2
                val top = (size.height - boxHeight) / 2.6f

                // Laser scan line
                val laserY = top + (boxHeight * laserOffset)
                drawLine(
                    color = Color(0xFF14B8A6),
                    start = Offset(left + 10f, laserY),
                    end = Offset(left + boxWidth - 10f, laserY),
                    strokeWidth = 3.dp.toPx()
                )
            }

            // Premium Viewfinder Border (Reticle)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(260.dp)
                    .border(2.dp, Color(0xFF14B8A6), RoundedCornerShape(24.dp))
                    .background(Color.Transparent)
            )

            // Simulator Test Presets (Bottom Sheet Style)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 90.dp) // Gallery button ke theek upar
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "SIMULATOR TEST PRESETS",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { 
                            coroutineScope.launch {
                                val id = scanRepository.saveScan("https://google.com", BarcodeFormatType.QR_CODE)
                                navController.navigate(Screen.ScanResult.createRoute(id))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("Valid HTTPS", fontSize = 10.sp) }

                    Button(
                        onClick = { 
                            coroutineScope.launch {
                                val id = scanRepository.saveScan("http://paypal-login-update.com", BarcodeFormatType.QR_CODE)
                                navController.navigate(Screen.ScanResult.createRoute(id))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9F1239)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("Phishing Alert", fontSize = 10.sp) }

                    Button(
                        onClick = { 
                            coroutineScope.launch {
                                val id = scanRepository.saveScan("WIFI:S:MyNetwork;T:WPA;P:Password123;;", BarcodeFormatType.QR_CODE)
                                navController.navigate(Screen.ScanResult.createRoute(id))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("Wi-Fi Payload", fontSize = 10.sp) }
                }
            }

            // Top Header Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Align code in frame",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = { isTorchEnabled = !isTorchEnabled },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isTorchEnabled) Color(0xFF0D9488) else Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flashlight",
                        tint = Color.White
                    )
                }
            }

            // Bottom Control Bar (Gallery Shortcut & Bulk Scan Button)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp, start = 24.dp, end = 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scan from Gallery
                Surface(
                    modifier = Modifier.clickable { galleryLauncher.launch("image/*") },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gallery Scan",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Switch to Bulk Continuous Scanning
                Surface(
                    modifier = Modifier.clickable { navController.navigate(Screen.BulkScan.route) },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0D9488),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Bulk",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Batch Mode",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
