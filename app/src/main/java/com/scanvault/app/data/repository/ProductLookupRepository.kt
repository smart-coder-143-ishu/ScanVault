package com.scanvault.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.scanvault.app.data.local.InventoryDao
import com.scanvault.app.data.local.InventoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

sealed class ProductLookupResult {
    data class Success(
        val barcode: String,
        val productName: String,
        val brand: String? = null,
        val categories: String? = null,
        val quantity: String? = null,
        val price: String? = null,
        val imageUrl: String? = null,
        val ingredientsSummary: String? = null,
        val sourceDatabase: String = "Open Food Facts"
    ) : ProductLookupResult()
    data class NotFound(val barcode: String) : ProductLookupResult()
    data class Error(val message: String) : ProductLookupResult()
}

data class AadhaarData(
    val qrType: String,
    val maskedUid: String,
    val referenceId: String,
    val name: String,
    val dob: String,
    val gender: String,
    val careOf: String,
    val house: String,
    val street: String,
    val landmark: String,
    val locality: String,
    val vtc: String,
    val postOffice: String,
    val subDistrict: String,
    val district: String,
    val state: String,
    val pincode: String,
    val hasEmbeddedPhoto: Boolean,
    val extraInfo: String
)

class ProductLookupRepository(private val inventoryDao: InventoryDao) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun lookupBarcode(barcode: String): ProductLookupResult = withContext(Dispatchers.IO) {
        val trimmed = barcode.trim()
        
        val localProduct = inventoryDao.getProductByBarcode(trimmed)
        if (localProduct != null) {
            return@withContext ProductLookupResult.Success(
                barcode = trimmed,
                productName = localProduct.name,
                price = localProduct.price,
                categories = localProduct.category,
                quantity = localProduct.quantity,
                sourceDatabase = "Local Inventory (POS)"
            )
        }

        val url = "https://world.openfoodfacts.org/api/v2/product/$trimmed.json"
        val request = Request.Builder().url(url).header("User-Agent", "ScanVault").build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext if (response.code == 404) ProductLookupResult.NotFound(trimmed)
                    else ProductLookupResult.Error("API rate limit or server error.")
                }
                val bodyStr = response.body?.string() ?: return@withContext ProductLookupResult.NotFound(trimmed)
                val root: JsonObject = JsonParser.parseString(bodyStr).asJsonObject
                if ((root.get("status")?.asInt ?: 0) != 1) return@withContext ProductLookupResult.NotFound(trimmed)

                val p = root.getAsJsonObject("product") ?: return@withContext ProductLookupResult.NotFound(trimmed)
                ProductLookupResult.Success(
                    barcode = trimmed,
                    productName = p.get("product_name")?.asString ?: "Unnamed Item",
                    brand = p.get("brands")?.asString,
                    categories = p.get("categories")?.asString,
                    quantity = p.get("quantity")?.asString,
                    imageUrl = p.get("image_front_url")?.asString,
                    ingredientsSummary = p.get("ingredients_text")?.asString
                )
            }
        } catch (e: Exception) {
            ProductLookupResult.Error("Lookup error: ${e.localizedMessage}")
        }
    }

    suspend fun addProductToInventory(barcode: String, name: String, price: String, category: String, qty: String) {
        withContext(Dispatchers.IO) {
            inventoryDao.insertProduct(InventoryEntity(barcode, name, price, category, qty))
        }
    }

    suspend fun importCsvToInventory(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val items = mutableListOf<InventoryEntity>()
            
            reader.readLine() 
            
            reader.forEachLine { line ->
                val tokens = line.split(",")
                if (tokens.size >= 2) {
                    items.add(
                        InventoryEntity(
                            barcode = tokens[0].trim(),
                            name = tokens[1].trim(),
                            price = tokens.getOrNull(2)?.trim() ?: "",
                            category = tokens.getOrNull(3)?.trim() ?: "",
                            quantity = tokens.getOrNull(4)?.trim() ?: ""
                        )
                    )
                }
            }
            inventoryDao.insertAll(items)
            reader.close()
            Result.success(items.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getAllLocalInventory(): List<InventoryEntity> = withContext(Dispatchers.IO) {
        inventoryDao.getAllItems()
    }

    suspend fun deleteFromInventory(barcode: String) = withContext(Dispatchers.IO) {
        inventoryDao.deleteItemByBarcode(barcode)
    }
}

// ==========================================
// 3-IN-1 DEEP AADHAAR DECODER ENGINE
// Supports:
// A) Old XML QR Code (<PrintLetterBarcodeData>)
// B) Secure Encrypted QR Code (BigInteger + GZIP + 0xFF Delimiter + JJ2000)
// C) mAadhaar / e-Aadhaar PDF Dynamic Secure QR (V2/V3/V4/V5 & Base64)
// ==========================================

fun parseAadhaarDataObject(scannedData: String): AadhaarData? {
    try {
        val cleanInput = scannedData.trim().replace("\uFEFF", "").replace("\u200B", "")

        // TYPE A: Old Printed XML QR Code
        if (cleanInput.startsWith("<?xml", ignoreCase = true) || cleanInput.contains("<PrintLetterBarcodeData", ignoreCase = true) || cleanInput.contains("<QPDB", ignoreCase = true)) {
            return parseXmlAadhaar(cleanInput)
        }

        // Fallback: Agar pehle se corrupted UTF-8 string (with  or JJ2000) pass hui ho
        if (cleanInput.startsWith("V") && (cleanInput.contains('\uFFFD') || cleanInput.contains("JJ2000"))) {
            return parseCorruptedStringAadhaar(cleanInput)
        }

        // TYPE B & C: Secure Numeric (BigInteger) or Base64 GZIP QR Code
        val noWhitespace = cleanInput.replace("\\s".toRegex(), "")
        val rawCompressedBytes: ByteArray? = when {
            noWhitespace.length > 80 && noWhitespace.all { it.isDigit() } -> {
                var bytes = BigInteger(noWhitespace).toByteArray()
                if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
                    bytes = bytes.copyOfRange(1, bytes.size)
                }
                bytes
            }
            noWhitespace.length > 80 && isBase64Format(noWhitespace) -> {
                Base64.decode(noWhitespace, Base64.DEFAULT)
            }
            else -> null
        }

        if (rawCompressedBytes != null) {
            val decompressedBytes = gunzipBytes(rawCompressedBytes)
            if (decompressedBytes != null && decompressedBytes.isNotEmpty()) {
                // Check if decompressed bytes are actually XML
                val utf8Preview = String(decompressedBytes, StandardCharsets.UTF_8)
                if (utf8Preview.contains("<PrintLetterBarcodeData") || utf8Preview.startsWith("<?xml")) {
                    return parseXmlAadhaar(utf8Preview)
                }
                // Otherwise parse binary 0xFF delimited Secure QR (Type B & C)
                return parseSecureBinaryPacket(decompressedBytes)
            }
        }
    } catch (_: Exception) {
        return null
    }
    return null
}

fun decodeAadhaarQR(scannedData: String): String {
    val data = parseAadhaarDataObject(scannedData) ?: return scannedData
    return formatAadhaarSummaryText(data)
}

private fun formatAadhaarSummaryText(data: AadhaarData): String {
    val addressParts = listOf(
        data.house, data.street, data.landmark, data.locality,
        data.vtc, data.postOffice, data.subDistrict, data.district, data.state
    ).map { it.trim() }.filter { it.isNotEmpty() && it != "N/A" }.distinct()

    val fullAddress = if (addressParts.isNotEmpty()) {
        addressParts.joinToString(", ") + (if (data.pincode != "N/A" && data.pincode.isNotEmpty()) " - ${data.pincode}" else "")
    } else {
        "N/A"
    }

    return buildString {
        append("Aadhaar Details (${data.qrType})\n")
        append("----------------------------------\n")
        append("Name         : ${data.name}\n")
        append("DOB          : ${data.dob}\n")
        append("Gender       : ${data.gender}\n")
        append("Care Of      : ${data.careOf}\n")
        append("Aadhaar No   : ${data.maskedUid}\n")
        append("Address      : $fullAddress\n")
        append("Pincode      : ${data.pincode}\n")
        append("Photo Status : ${if (data.hasEmbeddedPhoto) "Embedded (JPEG-2000)" else "Not Present"}\n")
        if (data.referenceId.isNotEmpty() && data.referenceId != "N/A") {
            append("Reference ID : ${data.referenceId}\n")
        }
        if (data.extraInfo.isNotEmpty() && data.extraInfo != "None") {
            append("Other Info   : ${data.extraInfo}")
        }
    }.trim()
}

// ==========================================
// BINARY PACKET PARSER (TYPE B & TYPE C)
// ==========================================
private fun parseSecureBinaryPacket(bytes: ByteArray): AadhaarData? {
    // 0xFF (255) UIDAI Secure QR ka official delimiter hai
    val fields = mutableListOf<String>()
    var start = 0

    // Hum pehle 18 text fields nikalenge (photo binary shuru hone se pehle)
    for (i in bytes.indices) {
        if (bytes[i] == 0xFF.toByte()) {
            val segment = String(bytes, start, i - start, StandardCharsets.ISO_8859_1).trim()
            fields.add(segment)
            start = i + 1
            if (fields.size >= 19) break
        }
    }

    if (fields.size < 6) return null

    // Detect if Index 0 is Version ("V2", "V3", "V4", "V5") or Email/Mobile Indicator
    val hasVersionPrefix = fields[0].startsWith("V", ignoreCase = true) && fields[0].length <= 4
    val offset = if (hasVersionPrefix) 1 else 0
    val versionLabel = if (hasVersionPrefix) "e-Aadhaar / Secure QR (${fields[0]})" else "Secure QR (Standard)"

    val refId = fields.getOrNull(offset + 1) ?: "N/A"
    val name = fields.getOrNull(offset + 2) ?: "N/A"
    val dob = fields.getOrNull(offset + 3) ?: "N/A"
    val rawGender = fields.getOrNull(offset + 4) ?: "N/A"
    val gender = when {
        rawGender.equals("M", true) || rawGender.equals("Male", true) -> "Male"
        rawGender.equals("F", true) || rawGender.equals("Female", true) -> "Female"
        rawGender.equals("T", true) -> "Transgender"
        else -> rawGender
    }
    val careOf = fields.getOrNull(offset + 5) ?: "N/A"
    val district = fields.getOrNull(offset + 6) ?: "N/A"
    val landmark = fields.getOrNull(offset + 7) ?: ""
    val house = fields.getOrNull(offset + 8) ?: ""
    val location = fields.getOrNull(offset + 9) ?: ""
    val pincode = fields.getOrNull(offset + 10) ?: "N/A"
    val postOffice = fields.getOrNull(offset + 11) ?: ""
    val state = fields.getOrNull(offset + 12) ?: "N/A"
    val street = fields.getOrNull(offset + 13) ?: ""
    val subDistrict = fields.getOrNull(offset + 14) ?: ""
    val vtc = fields.getOrNull(offset + 15) ?: ""

    // Last 4 digits ya XXXXXX8376 jaise masked number nikalna
    val explicitMasked = fields.find { it.contains("XXXX", ignoreCase = true) }
    val maskedUid = when {
        explicitMasked != null -> explicitMasked
        refId.length >= 4 && refId.take(4).all { it.isDigit() } -> "XXXX-XXXX-${refId.take(4)}"
        else -> "N/A"
    }

    // Check if JPEG-2000 header (FF 4F FF 51 or "JJ2000") exists in remaining bytes
    val rawIsoString = String(bytes, StandardCharsets.ISO_8859_1)
    val hasPhoto = rawIsoString.contains("JJ2000") || hasJpeg2000Signature(bytes)

    val extraParts = listOf(house, street, landmark, location, vtc, postOffice, subDistrict)
        .map { cleanReadableText(it) }
        .filter { it.isNotEmpty() }
        .distinct()

    return AadhaarData(
        qrType = versionLabel,
        maskedUid = cleanReadableText(maskedUid),
        referenceId = cleanReadableText(refId),
        name = cleanReadableText(name),
        dob = cleanReadableText(dob),
        gender = gender,
        careOf = cleanReadableText(careOf),
        house = cleanReadableText(house),
        street = cleanReadableText(street),
        landmark = cleanReadableText(landmark),
        locality = cleanReadableText(location),
        vtc = cleanReadableText(vtc),
        postOffice = cleanReadableText(postOffice),
        subDistrict = cleanReadableText(subDistrict),
        district = cleanReadableText(district),
        state = cleanReadableText(state),
        pincode = cleanReadableText(pincode),
        hasEmbeddedPhoto = hasPhoto,
        extraInfo = if (extraParts.isNotEmpty()) extraParts.joinToString(", ") else "None"
    )
}

// ==========================================
// FALLBACK CORRUPTED STRING PARSER
// ==========================================
private fun parseCorruptedStringAadhaar(rawStr: String): AadhaarData {
    val beforePhoto = rawStr.substringBefore("JJ2000").substringBefore("Created by")
    val rawParts = beforePhoto.split('\uFFFD').map { cleanReadableText(it) }.filter { it.isNotEmpty() }

    val uidIndex = rawParts.indexOfFirst { it.contains("XXXX", ignoreCase = true) }
    val safeParts = if (uidIndex != -1) rawParts.take(uidIndex + 1) else rawParts

    val version = if (safeParts.firstOrNull()?.startsWith("V") == true) safeParts.first() else "V5"
    val refId = safeParts.find { it.length >= 18 && it.all { c -> c.isDigit() } } ?: "N/A"
    val dob = safeParts.find { it.matches(Regex("\\d{2}[-/]\\d{2}[-/]\\d{4}")) } ?: "N/A"
    val dobIdx = safeParts.indexOf(dob)

    val name = if (dobIdx > 0) safeParts[dobIdx - 1] else safeParts.getOrNull(3) ?: "N/A"
    val rawGender = if (dobIdx != -1 && dobIdx + 1 < safeParts.size) safeParts[dobIdx + 1] else "N/A"
    val gender = if (rawGender.equals("M", true)) "Male" else if (rawGender.equals("F", true)) "Female" else rawGender
    val careOf = if (dobIdx != -1 && dobIdx + 2 < safeParts.size) safeParts[dobIdx + 2] else "N/A"
    val district = if (dobIdx != -1 && dobIdx + 3 < safeParts.size) safeParts[dobIdx + 3] else "N/A"
    val pincode = safeParts.find { it.length == 6 && it.all { c -> c.isDigit() } } ?: "N/A"
    val maskedUid = safeParts.find { it.contains("XXXX", ignoreCase = true) }
        ?: if (refId.length >= 4) "XXXX-XXXX-${refId.take(4)}" else "N/A"

    val knownStates = listOf(
        "Uttar Pradesh", "Maharashtra", "Bihar", "West Bengal", "Madhya Pradesh",
        "Tamil Nadu", "Rajasthan", "Karnataka", "Gujarat", "Andhra Pradesh",
        "Odisha", "Telangana", "Kerala", "Jharkhand", "Assam", "Punjab",
        "Chhattisgarh", "Haryana", "Delhi", "Jammu and Kashmir", "Uttarakhand", "Himachal Pradesh"
    )
    val state = safeParts.find { part -> knownStates.any { st -> part.equals(st, ignoreCase = true) } } ?: "N/A"

    val usedSet = setOf(version, "2", refId, name, dob, rawGender, careOf, district, pincode, state, maskedUid)
    val extraList = safeParts.filter { it !in usedSet && it.length > 2 }

    return AadhaarData(
        qrType = "e-Aadhaar / Secure QR ($version)",
        maskedUid = maskedUid,
        referenceId = refId,
        name = name,
        dob = dob,
        gender = gender,
        careOf = careOf,
        house = "",
        street = "",
        landmark = "",
        locality = "",
        vtc = extraList.getOrNull(1) ?: "",
        postOffice = extraList.getOrNull(0) ?: "",
        subDistrict = extraList.getOrNull(2) ?: "",
        district = district,
        state = state,
        pincode = pincode,
        hasEmbeddedPhoto = true,
        extraInfo = if (extraList.isNotEmpty()) extraList.joinToString(", ") else "None"
    )
}

// ==========================================
// XML PARSER (TYPE A: OLD AADHAAR QR)
// ==========================================
private fun parseXmlAadhaar(xml: String): AadhaarData? {
    try {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                (parser.name == "PrintLetterBarcodeData" || parser.name == "QPDB")
            ) {
                val rawUid = parser.getAttributeValue(null, "uid") ?: parser.getAttributeValue(null, "u") ?: ""
                val maskedUid = if (rawUid.length >= 4) "XXXX-XXXX-${rawUid.takeLast(4)}" else "N/A"
                val name = parser.getAttributeValue(null, "name") ?: parser.getAttributeValue(null, "n") ?: "N/A"
                val rawGender = parser.getAttributeValue(null, "gender") ?: parser.getAttributeValue(null, "g") ?: "N/A"
                val gender = when {
                    rawGender.equals("M", true) || rawGender.equals("Male", true) -> "Male"
                    rawGender.equals("F", true) || rawGender.equals("Female", true) -> "Female"
                    else -> rawGender
                }
                val dob = parser.getAttributeValue(null, "dob")
                    ?: parser.getAttributeValue(null, "d")
                    ?: parser.getAttributeValue(null, "yob")
                    ?: "N/A"
                val co = parser.getAttributeValue(null, "co") ?: parser.getAttributeValue(null, "gname") ?: "N/A"
                val house = parser.getAttributeValue(null, "house") ?: ""
                val street = parser.getAttributeValue(null, "street") ?: ""
                val lm = parser.getAttributeValue(null, "lm") ?: ""
                val loc = parser.getAttributeValue(null, "loc") ?: ""
                val vtc = parser.getAttributeValue(null, "vtc") ?: ""
                val po = parser.getAttributeValue(null, "po") ?: ""
                val subdist = parser.getAttributeValue(null, "subdist") ?: ""
                val dist = parser.getAttributeValue(null, "dist") ?: "N/A"
                val state = parser.getAttributeValue(null, "state") ?: "N/A"
                val pc = parser.getAttributeValue(null, "pc") ?: "N/A"

                val extras = listOf(house, street, lm, loc, vtc, po, subdist).filter { it.isNotBlank() }

                return AadhaarData(
                    qrType = "Legacy XML QR (Type A)",
                    maskedUid = maskedUid,
                    referenceId = "N/A",
                    name = name,
                    dob = dob,
                    gender = gender,
                    careOf = co,
                    house = house,
                    street = street,
                    landmark = lm,
                    locality = loc,
                    vtc = vtc,
                    postOffice = po,
                    subDistrict = subdist,
                    district = dist,
                    state = state,
                    pincode = pc,
                    hasEmbeddedPhoto = false,
                    extraInfo = if (extras.isNotEmpty()) extras.joinToString(", ") else "None"
                )
            }
            eventType = parser.next()
        }
    } catch (_: Exception) {
        return null
    }
    return null
}

private fun gunzipBytes(compressed: ByteArray): ByteArray? {
    return try {
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
            val buffer = ByteArrayOutputStream()
            val data = ByteArray(1024)
            var nRead: Int
            while (gis.read(data, 0, data.size).also { nRead = it } != -1) {
                buffer.write(data, 0, nRead)
            }
            buffer.toByteArray()
        }
    } catch (_: Exception) {
        null
    }
}

private fun hasJpeg2000Signature(bytes: ByteArray): Boolean {
    for (i in 0 until bytes.size - 3) {
        if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0x4F.toByte() &&
            bytes[i + 2] == 0xFF.toByte() && bytes[i + 3] == 0x51.toByte()
        ) {
            return true
        }
    }
    return false
}

private fun cleanReadableText(input: String): String {
    return input.filter { ch ->
        ch.isLetterOrDigit() || ch == ' ' || ch == '-' || ch == '/' || ch == '.' || ch == ',' || ch == '(' || ch == ')'
    }.trim()
}

private fun isBase64Format(str: String): Boolean {
    return str.matches(Regex("^[a-zA-Z0-9+/]*={0,2}$"))
}
