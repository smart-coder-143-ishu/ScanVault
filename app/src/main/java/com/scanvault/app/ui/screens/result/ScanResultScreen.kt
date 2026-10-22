package com.scanvault.app.ui.screens.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.scanvault.app.data.local.ScanRecordEntity
import com.scanvault.app.data.model.BarcodeFormatType
import com.scanvault.app.data.model.ScanContent
import com.scanvault.app.data.parser.BarcodePayloadParser
import com.scanvault.app.data.repository.AadhaarData
import com.scanvault.app.data.repository.ScanRepository
import com.scanvault.app.data.repository.decodeAadhaarQR
import com.scanvault.app.data.repository.parseAadhaarDataObject
import com.scanvault.app.ui.navigation.Screen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(
    recordId: Long,
    navController: NavController,
    scanRepository: ScanRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var record by remember { mutableStateOf<ScanRecordEntity?>(null) }
    var parsedContent by remember { mutableStateOf<ScanContent?>(null) }
    var aadhaarData by remember { mutableStateOf<AadhaarData?>(null) }

    LaunchedEffect(recordId) {
        val loaded = scanRepository.getScanById(recordId)
        record = loaded
        if (loaded != null) {
            aadhaarData = parseAadhaarDataObject(loaded.rawValue)
            val formatType = try {
                BarcodeFormatType.valueOf(loaded.format)
            } catch (_: Exception) {
                BarcodeFormatType.UNKNOWN
            }
            parsedContent = BarcodePayloadParser.parse(loaded.rawValue, formatType)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = if (aadhaarData != null) "Aadhaar Verification" else "Scan Details",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                record?.let { rec ->
                    IconButton(onClick = {
                        coroutineScope.launch {
                            scanRepository.toggleFavorite(rec)
                            record = rec.copy(isFavorite = !rec.isFavorite)
                        }
                    }) {
                        Icon(
                            imageVector = if (rec.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (rec.isFavorite) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            scanRepository.deleteScan(rec.id)
                            navController.popBackStack()
                        }
                    }) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    ) { innerPadding ->
        if (record == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val currentRecord = record!!
            val content = parsedContent
            val detectedAadhaar = aadhaarData
            val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy • HH:mm:ss", Locale.getDefault())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Card with Category & Format Badges
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (detectedAadhaar != null) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = if (detectedAadhaar != null) "VERIFIED UIDAI PAYLOAD" else currentRecord.category.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (detectedAadhaar != null) Color(0xFF10B981) else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Text(
                                text = currentRecord.format,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = dateFormat.format(Date(currentRecord.timestamp)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // If Aadhaar is detected, render Premium Aadhaar Card; else standard analyzer cards
                if (detectedAadhaar != null) {
                    PremiumAadhaarCard(data = detectedAadhaar)
                } else {
                    when (content) {
                        is ScanContent.Url -> UrlAnalyzerCard(urlContent = content, context = context)
                        is ScanContent.Wifi -> WifiAnalyzerCard(wifiContent = content, context = context)
                        is ScanContent.Product -> ProductAnalyzerCard(productContent = content, navController = navController)
                        is ScanContent.Contact -> ContactAnalyzerCard(contactContent = content, context = context)
                        is ScanContent.Phone -> PhoneAnalyzerCard(phoneContent = content, context = context)
                        is ScanContent.Email -> EmailAnalyzerCard(emailContent = content, context = context)
                        is ScanContent.Sms -> SmsAnalyzerCard(smsContent = content, context = context)
                        is ScanContent.Geo -> GeoAnalyzerCard(geoContent = content, context = context)
                        is ScanContent.PlainText, null -> PlainTextViewerCard(rawValue = currentRecord.rawValue)
                    }
                }

                // Raw Payload / Summary Card (Hides 3000-digit garbage if Aadhaar is decoded)
                val copyableText = if (detectedAadhaar != null) decodeAadhaarQR(currentRecord.rawValue) else currentRecord.rawValue
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (detectedAadhaar != null) "Formatted Text Summary" else "Raw Decoded Payload",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = copyableText,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Quick Action Buttons (Copies formatted Aadhaar text instead of raw digits!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("ScanVault Data", copyableText))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy")
                    }

                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, copyableText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Scanned Code"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumAadhaarCard(data: AadhaarData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = data.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = data.qrType,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    AadhaarInfoRow(icon = Icons.Default.Badge, label = "Aadhaar Number", value = data.maskedUid)
                }
                Box(modifier = Modifier.weight(1f)) {
                    AadhaarInfoRow(icon = Icons.Default.Event, label = "Date of Birth", value = data.dob)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    AadhaarInfoRow(icon = Icons.Default.Person, label = "Gender", value = data.gender)
                }
                Box(modifier = Modifier.weight(1f)) {
                    AadhaarInfoRow(icon = Icons.Default.FamilyRestroom, label = "Care Of (Guardian)", value = data.careOf)
                }
            }

            val fullAddress = listOf(
                data.house, data.street, data.landmark, data.locality,
                data.vtc, data.postOffice, data.subDistrict, data.district, data.state
            ).map { it.trim() }.filter { it.isNotEmpty() && it != "N/A" }.distinct().joinToString(", ")

            AadhaarInfoRow(
                icon = Icons.Default.LocationOn,
                label = "Complete Address",
                value = if (fullAddress.isNotEmpty()) "$fullAddress - ${data.pincode}" else "${data.district}, ${data.state} - ${data.pincode}"
            )

            if (data.extraInfo.isNotEmpty() && data.extraInfo != "None") {
                AadhaarInfoRow(
                    icon = Icons.Default.Info,
                    label = "Other Locality Details (VTC / PO / Tehsil)",
                    value = data.extraInfo
                )
            }

            if (data.referenceId.isNotEmpty() && data.referenceId != "N/A") {
                AadhaarInfoRow(
                    icon = Icons.Default.Tag,
                    label = "UIDAI Reference ID",
                    value = data.referenceId
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (data.hasEmbeddedPhoto) "JPEG-2000 Biometric Photo stream detected & filtered cleanly" else "Standard XML QR (No Embedded Photo Stream)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AadhaarInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value.ifEmpty { "N/A" },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun UrlAnalyzerCard(urlContent: ScanContent.Url, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Website Domain",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (urlContent.isHttps) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (urlContent.isHttps) "HTTPS SECURE" else "HTTP UNENCRYPTED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (urlContent.isHttps) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = urlContent.host,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = urlContent.fullUrl,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 3
            )

            if (urlContent.warningMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (urlContent.isSuspicious) Color(0xFFF43F5E).copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (urlContent.isSuspicious) Color(0xFFF43F5E) else Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = urlContent.warningMessage,
                            fontSize = 12.sp,
                            color = if (urlContent.isSuspicious) Color(0xFFF43F5E) else Color(0xFFF59E0B),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlContent.fullUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open URL in Browser")
            }
        }
    }
}

@Composable
fun WifiAnalyzerCard(wifiContent: ScanContent.Wifi, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Wi-Fi Configuration",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = wifiContent.ssid,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Encryption", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = wifiContent.authType, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text(text = "Password", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = wifiContent.passwordMasked, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Wi-Fi Password", wifiContent.rawPassword))
                    Toast.makeText(context, "Wi-Fi password copied securely", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Wi-Fi Password")
            }
        }
    }
}

@Composable
fun ProductAnalyzerCard(
    productContent: ScanContent.Product,
    navController: NavController
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Retail Product Barcode",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (productContent.isChecksumValid) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF43F5E).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (productContent.isChecksumValid) "CHECKSUM VALID" else "CHECKSUM INVALID",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (productContent.isChecksumValid) Color(0xFF10B981) else Color(0xFFF43F5E),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = productContent.barcode,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    navController.navigate(Screen.ProductDetails.createRoute(productContent.barcode))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Lookup Product Details")
            }
        }
    }
}

@Composable
fun ContactAnalyzerCard(contactContent: ScanContent.Contact, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Contact Card",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = contactContent.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            contactContent.phone?.let { p ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Phone: $p", fontSize = 13.sp)
            }
            contactContent.email?.let { e ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Email: $e", fontSize = 13.sp)
            }
            contactContent.organization?.let { o ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Org: $o", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun PhoneAnalyzerCard(phoneContent: ScanContent.Phone, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Telephone Number",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = phoneContent.phoneNumber,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phoneContent.phoneNumber}"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Phone, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Dial Number")
            }
        }
    }
}

@Composable
fun EmailAnalyzerCard(emailContent: ScanContent.Email, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = "Email Message", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = emailContent.recipient, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            emailContent.subject?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Subject: $it", fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:${emailContent.recipient}")
                        emailContent.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                        emailContent.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Email, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compose Email")
            }
        }
    }
}

@Composable
fun SmsAnalyzerCard(smsContent: ScanContent.Sms, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = "SMS Message", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = smsContent.recipient, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            smsContent.messageBody?.takeIf { it.isNotBlank() }?.let { body ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Message: $body", fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:${smsContent.recipient}")
                        smsContent.messageBody?.let { putExtra("sms_body", it) }
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Sms, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compose SMS")
            }
        }
    }
}

@Composable
fun GeoAnalyzerCard(geoContent: ScanContent.Geo, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = "Geographic Location", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${geoContent.latitude}, ${geoContent.longitude}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            geoContent.query?.takeIf { it.isNotBlank() }?.let { q ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Query: $q", fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    val label = geoContent.query?.takeIf { it.isNotBlank() }?.let { "(" + Uri.encode(it) + ")" } ?: ""
                    val uri = Uri.parse("geo:${geoContent.latitude},${geoContent.longitude}?q=${geoContent.latitude},${geoContent.longitude}$label")
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) {
                        Toast.makeText(context, "No maps app found", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Map, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open in Maps")
            }
        }
    }
}

@Composable
fun PlainTextViewerCard(rawValue: String) {
    val decodedText = decodeAadhaarQR(rawValue)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Text Content (${decodedText.length} characters)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = decodedText,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
