package com.scanvault.app.ui.screens.tools

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanvault.app.data.model.BarcodeFormatType
import com.scanvault.app.generator.BarcodeGenerator
import kotlinx.coroutines.launch
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf("QR Code") }
    var textInput by remember { mutableStateOf("") }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var wifiAuth by remember { mutableStateOf("WPA") }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }

    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    val generatorTypes = listOf("QR Code", "Wi-Fi QR", "Contact vCard", "Code 128", "EAN-13")

    fun triggerGenerate() {
        errorMessage = null
        val payload: String
        val format: BarcodeFormatType

        when (selectedType) {
            "QR Code" -> {
                if (textInput.isBlank()) {
                    errorMessage = "Please enter text or URL to generate QR code."
                    return
                }
                payload = textInput.trim()
                format = BarcodeFormatType.QR_CODE
            }
            "Wi-Fi QR" -> {
                if (wifiSsid.isBlank()) {
                    errorMessage = "Network SSID is required."
                    return
                }
                payload = "WIFI:S:$wifiSsid;T:$wifiAuth;P:$wifiPassword;H:false;;"
                format = BarcodeFormatType.QR_CODE
            }
            "Contact vCard" -> {
                if (contactName.isBlank()) {
                    errorMessage = "Contact name is required."
                    return
                }
                payload = "BEGIN:VCARD\nVERSION:3.0\nFN:$contactName\nTEL:$contactPhone\nEMAIL:$contactEmail\nEND:VCARD"
                format = BarcodeFormatType.QR_CODE
            }
            "Code 128" -> {
                if (textInput.isBlank()) {
                    errorMessage = "Please enter characters to encode in Code 128."
                    return
                }
                payload = textInput.trim()
                format = BarcodeFormatType.CODE_128
            }
            "EAN-13" -> {
                val clean = textInput.trim()
                if (clean.length !in 12..13 || !clean.all { it.isDigit() }) {
                    errorMessage = "EAN-13 requires 12 or 13 numeric digits."
                    return
                }
                payload = clean
                format = BarcodeFormatType.EAN_13
            }
            else -> {
                payload = textInput
                format = BarcodeFormatType.QR_CODE
            }
        }

        isGenerating = true
        coroutineScope.launch {
            val result = BarcodeGenerator.generateBitmap(payload, format)
            isGenerating = false
            result.onSuccess { bitmap ->
                generatedBitmap = bitmap
            }.onFailure { err ->
                errorMessage = "Generation failed: ${err.localizedMessage ?: "Invalid input format"}"
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh, // Wand2 ki jagah Android ka icon
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Code Generator", 
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Type Selector
            ScrollableTabRow(
                selectedTabIndex = generatorTypes.indexOf(selectedType),
                edgePadding = 0.dp,
                divider = {}
            ) {
                generatorTypes.forEach { type ->
                    Tab(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            errorMessage = null
                        },
                        text = { Text(type, fontSize = 12.sp) }
                    )
                }
            }

            // Input Form Based on Type
            when (selectedType) {
                "QR Code", "Code 128", "EAN-13" -> {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (selectedType == "EAN-13") "Enter 12/13 digits" else "Content or URL") },
                        placeholder = { Text(if (selectedType == "EAN-13") "e.g. 5901234123457" else "https://example.com or any text") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                "Wi-Fi QR" -> {
                    OutlinedTextField(
                        value = wifiSsid,
                        onValueChange = { wifiSsid = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Network SSID (Name)") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = wifiPassword,
                        onValueChange = { wifiPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Wi-Fi Password") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                "Contact vCard" -> {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Full Name *") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Phone Number") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = contactEmail,
                        onValueChange = { contactEmail = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email Address") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Validation Error Callout
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Generate Button
            Button(
                onClick = { triggerGenerate() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isGenerating
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Code")
                }
            }

            // Generated Code Preview Canvas
            generatedBitmap?.let { bmp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            color = androidx.compose.ui.graphics.Color.White
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Generated Code",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    saveBitmapToGallery(context, bmp)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Image", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    shareBitmap(context, bmp)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    try {
        val filename = "ScanVault_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ScanVault")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(context, "Saved to Pictures/ScanVault", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun shareBitmap(context: Context, bitmap: Bitmap) {
    try {
        val path = MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "ScanVault Code", null)
        val uri = Uri.parse(path)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        context.startActivity(Intent.createChooser(share, "Share Generated Code"))
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to share image", Toast.LENGTH_SHORT).show()
    }
}
