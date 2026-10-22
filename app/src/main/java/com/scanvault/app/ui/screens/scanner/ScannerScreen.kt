package com.scanvault.app.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Scanner palette
private val TealGlow = Color(0xFF2DD4BF)
private val TealMain = Color(0xFF14B8A6)
private val TealDeep = Color(0xFF0D9488)
private val GlassDark = Color(0xFF0B1220)

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
    var scanHandled by remember { mutableStateOf(false) }
    var showDevPresets by remember { mutableStateOf(false) }

    // Developer test presets are only available in debuggable builds
    val isDebuggable = remember {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    // Short lock so one physical scan can never navigate twice
    LaunchedEffect(scanHandled) {
        if (scanHandled) {
            delay(1500)
            scanHandled = false
        }
    }

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
                        val found = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                        if (found != null) {
                            val value = found.rawValue!!
                            val format = BarcodeFormatType.fromMlKitFormat(found.format)
                            coroutineScope.launch {
                                val id = scanRepository.saveScan(value, format)
                                navController.navigate(Screen.ScanResult.createRoute(id))
                            }
                        } else {
                            Toast.makeText(context, "No QR or barcode found in this image", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        isProcessingGalleryImage = false
                        Toast.makeText(context, "Couldn't read this image", Toast.LENGTH_SHORT).show()
                    }
            } catch (_: Exception) {
                isProcessingGalleryImage = false
                Toast.makeText(context, "Couldn't open this image", Toast.LENGTH_SHORT).show()
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
        PermissionRequestContent(
            onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        )
        return
    }

    // ---------- Active camera scanner ----------
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(
            isTorchEnabled = isTorchEnabled,
            onBarcodeDetected = { rawValue, format ->
                if (!scanHandled) {
                    scanHandled = true
                    vibrateOnScan()
                    coroutineScope.launch {
                        val recordId = scanRepository.saveScan(rawValue, format)
                        navController.navigate(Screen.ScanResult.createRoute(recordId))
                    }
                }
            }
        )

        val infiniteTransition = rememberInfiniteTransition(label = "ScannerFx")
        val laserProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "LaserProgress"
        )
        val cornerPulse by infiniteTransition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "CornerPulse"
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // One shared geometry for the viewfinder, laser and hint text
            val frameSize = maxWidth * 0.78f
            val frameTop = maxOf(72.dp, maxHeight * 0.36f - frameSize / 2)

            // Dimmed scrim with a rounded cut-out + corner brackets + laser
            Canvas(modifier = Modifier.fillMaxSize()) {
                val fs = frameSize.toPx()
                val left = (size.width - fs) / 2f
                val top = frameTop.toPx()
                val right = left + fs
                val bottom = top + fs
                val radius = 28.dp.toPx()
                val frameRect = Rect(left, top, right, bottom)
                val frameShape = RoundRect(frameRect, CornerRadius(radius))

                // Scrim (everything except the frame)
                val scrim = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(frameShape)
                }
                drawPath(scrim, Color.Black.copy(alpha = 0.55f))

                // Laser, clipped to the frame so the glow never leaks outside
                val framePath = Path().apply { addRoundRect(frameShape) }
                clipPath(framePath) {
                    val inset = 18.dp.toPx()
                    val laserY = top + inset + (fs - inset * 2f) * laserProgress
                    val glow = 30.dp.toPx()

                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                TealGlow.copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            startY = laserY - glow,
                            endY = laserY + glow
                        ),
                        topLeft = Offset(left, laserY - glow),
                        size = Size(fs, glow * 2f)
                    )
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, TealGlow, Color.Transparent),
                            startX = left + inset,
                            endX = right - inset
                        ),
                        start = Offset(left + inset, laserY),
                        end = Offset(right - inset, laserY),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Corner brackets: soft glow underneath, crisp line on top
                val arm = 52.dp.toPx()
                drawCornerBrackets(
                    left, top, right, bottom, radius, arm,
                    color = TealGlow.copy(alpha = 0.28f * cornerPulse),
                    strokeWidth = 9.dp.toPx()
                )
                drawCornerBrackets(
                    left, top, right, bottom, radius, arm,
                    color = TealGlow.copy(alpha = cornerPulse),
                    strokeWidth = 4.dp.toPx()
                )
            }

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    onClick = { navController.popBackStack() }
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(GlassDark.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                        .pointerInput(isDebuggable) {
                            // Long-press the title in debug builds to reveal test presets
                            if (isDebuggable) {
                                detectTapGestures(onLongPress = { showDevPresets = !showDevPresets })
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = TealGlow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SCAN",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                GlassIconButton(
                    icon = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flashlight",
                    onClick = { isTorchEnabled = !isTorchEnabled },
                    active = isTorchEnabled
                )
            }

            // Hint text under the viewfinder
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = frameTop + frameSize + 28.dp)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Align QR or barcode inside the frame",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Dense codes like Aadhaar QR: move closer and hold steady",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Bottom area: optional dev presets + action dock
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
            ) {
                if (isDebuggable && showDevPresets) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PresetChip(label = "URL", color = Color(0xFF38BDF8), modifier = Modifier.weight(1f)) {
                            coroutineScope.launch {
                                val id = scanRepository.saveScan("https://google.com", BarcodeFormatType.QR_CODE)
                                navController.navigate(Screen.ScanResult.createRoute(id))
                            }
                        }
                        PresetChip(label = "Fake URL", color = Color(0xFFFB7185), modifier = Modifier.weight(1f)) {
                            coroutineScope.launch {
                                val id = scanRepository.saveScan("http://paypal-login-update.com", BarcodeFormatType.QR_CODE)
                                navController.navigate(Screen.ScanResult.createRoute(id))
                            }
                        }
                        PresetChip(label = "Wi-Fi", color = Color(0xFF34D399), modifier = Modifier.weight(1f)) {
                            coroutineScope.launch {
                                val id = scanRepository.saveScan("WIFI:S:MyNetwork;T:WPA;P:Password123;;", BarcodeFormatType.QR_CODE)
                                navController.navigate(Screen.ScanResult.createRoute(id))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(GlassDark.copy(alpha = 0.72f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DockButton(
                        label = "Gallery",
                        icon = Icons.Default.PhotoLibrary,
                        filled = false,
                        modifier = Modifier.weight(1f)
                    ) { galleryLauncher.launch("image/*") }

                    DockButton(
                        label = "Batch Mode",
                        icon = Icons.Default.Layers,
                        filled = true,
                        modifier = Modifier.weight(1f)
                    ) { navController.navigate(Screen.BulkScan.route) }
                }
            }
        }

        // Gallery processing indicator
        if (isProcessingGalleryImage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TealMain)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Reading code...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// Four rounded L-shaped corner brackets around the viewfinder
private fun DrawScope.drawCornerBrackets(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    arm: Float,
    color: Color,
    strokeWidth: Float
) {
    val d = radius * 2f
    val path = Path().apply {
        // Top-left
        moveTo(left, top + arm)
        lineTo(left, top + radius)
        arcTo(Rect(left, top, left + d, top + d), 180f, 90f, false)
        lineTo(left + arm, top)
        // Top-right
        moveTo(right - arm, top)
        lineTo(right - radius, top)
        arcTo(Rect(right - d, top, right, top + d), 270f, 90f, false)
        lineTo(right, top + arm)
        // Bottom-right
        moveTo(right, bottom - arm)
        lineTo(right, bottom - radius)
        arcTo(Rect(right - d, bottom - d, right, bottom), 0f, 90f, false)
        lineTo(right - arm, bottom)
        // Bottom-left
        moveTo(left + arm, bottom)
        lineTo(left + radius, bottom)
        arcTo(Rect(left, bottom - d, left + d, bottom), 90f, 90f, false)
        lineTo(left, bottom - arm)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) TealDeep else GlassDark.copy(alpha = 0.55f))
            .border(
                1.dp,
                Color.White.copy(alpha = if (active) 0.28f else 0.14f),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun DockButton(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = if (filled) {
        Brush.horizontalGradient(listOf(TealDeep, TealMain))
    } else {
        Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.06f))
        )
    }
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GlassDark.copy(alpha = 0.72f))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PermissionRequestContent(
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(TealDeep.copy(alpha = 0.25f), TealGlow.copy(alpha = 0.10f))))
                    .border(1.dp, TealGlow.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Camera access needed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ScanVault reads barcodes and QR codes on your device in real time. Camera frames are never recorded or uploaded.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = "Allow Camera Access", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onOpenSettings) {
                Text(text = "Open App Settings", fontSize = 12.sp)
            }
        }
    }
}
