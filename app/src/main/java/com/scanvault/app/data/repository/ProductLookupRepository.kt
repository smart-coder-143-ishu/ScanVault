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
import java.io.InputStreamReader
import java.io.StringReader
import java.math.BigInteger
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

class ProductLookupRepository(private val inventoryDao: InventoryDao) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun lookupBarcode(barcode: String): ProductLookupResult = withContext(Dispatchers.IO) {
        val trimmed = barcode.trim()
        
        // Step 1: Sabse pehle Local Database (Inventory) check karein
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

        // Step 2: Agar local nahi mila, toh API check karein
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

    // Function to Add Product Manually
    suspend fun addProductToInventory(barcode: String, name: String, price: String, category: String, qty: String) {
        withContext(Dispatchers.IO) {
            inventoryDao.insertProduct(InventoryEntity(barcode, name, price, category, qty))
        }
    }

    // Function to Import CSV Data
    suspend fun importCsvToInventory(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val items = mutableListOf<InventoryEntity>()
            
            // Skip header line
            reader.readLine() 
            
            reader.forEachLine { line ->
                val tokens = line.split(",") // Format: Barcode,Name,Price,Category,Quantity
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
// AADHAAR DECODER FUNCTIONS
// ==========================================

fun decodeAadhaarQR(scannedData: String): String {
    return try {
        // NAYA LOGIC: Scanner se aane wale hidden characters (space, enter, BOM) ko hatana
        val cleanStr = scannedData.trim().replace("\uFEFF", "").replace("\u200B", "")
        
        // Ab 'cleanStr' pakka 'V' se shuru hoga agar wo Aadhaar hai ya fir JJ2000 hoga
        if (cleanStr.startsWith("V") || cleanStr.contains("JJ2000")) {
            // Replacement character se data ko todna
            val parts = cleanStr.split('\uFFFD').map { it.trim() }.filter { it.isNotBlank() }
            
            // Photo ke kachre ko rokne ke liye, hum sirf 'XXXXXX' (Aadhaar No) tak ka data lenge
            val uidIndex = parts.indexOfFirst { it.contains("XXXXXX") }
            val safeEndIndex = if (uidIndex != -1) uidIndex + 1 else parts.size
            
            // Sirf human-readable hisse rakhein, baaki JJ2000 machine language filter kar dein
            val readableParts = parts.take(safeEndIndex).map { part ->
                part.filter { it.isLetterOrDigit() || it.isWhitespace() || it == '-' || it == '/' || it == '.' }
            }.map { it.trim() }.filter { it.isNotBlank() }
            
            // Fixed positions se main data nikalna
            val name = readableParts.getOrNull(3) ?: "N/A"
            val dob = readableParts.getOrNull(4) ?: "N/A"
            val rawGender = readableParts.getOrNull(5) ?: "N/A"
            val gender = if (rawGender.equals("M", ignoreCase = true)) "Male" else if (rawGender.equals("F", ignoreCase = true)) "Female" else rawGender
            val careOf = readableParts.getOrNull(6) ?: "N/A"
            
            val maskedUid = readableParts.find { it.contains("XXXXXX") } ?: "N/A"
            val pincode = readableParts.find { it.length == 6 && it.all { c -> c.isDigit() } } ?: "N/A"

            // Jo data use ho chuka hai uski list
            val usedItems = setOf(name, dob, rawGender, gender, careOf, maskedUid, pincode, readableParts.getOrNull(0), readableParts.getOrNull(1), readableParts.getOrNull(2))
            
            // Bacha hua extra data (Jaise Village, District, State) filter karna
            val extraDataList = readableParts.filter { it !in usedItems && it.length > 2 }
            val extraDataStr = if (extraDataList.isNotEmpty()) extraDataList.joinToString(", ") else "None"

            // Ekdum clean professional text format (UI ki condition match karne ke liye 'Aadhaar' capital 'A' se shuru kiya hai)
            return "Aadhaar Details (Decoded)\n" +
                   "-------------------------\n" +
                   "Name       : $name\n" +
                   "DOB        : $dob\n" +
                   "Gender     : $gender\n" +
                   "C/O        : $careOf\n" +
                   "Aadhaar No : $maskedUid\n" +
                   "Pincode    : $pincode\n" +
                   "Other Info : $extraDataStr"
        }

        // PURANA LOGIC: XML aur GZIP format ke liye
        val cleanData = scannedData.replace("\\s".toRegex(), "")
        val xmlData = when {
            cleanData.startsWith("<?xml") || cleanData.contains("<PrintLetterBarcodeData") -> cleanData
            cleanData.all { it.isDigit() } && cleanData.length > 50 -> {
                val bigInt = java.math.BigInteger(cleanData)
                var compressedBytes = bigInt.toByteArray()
                if (compressedBytes.isNotEmpty() && compressedBytes[0] == 0.toByte()) {
                    compressedBytes = compressedBytes.copyOfRange(1, compressedBytes.size)
                }
                decompressGzip(compressedBytes)
            }
            cleanData.length > 50 && isBase64(cleanData) -> {
                val compressedBytes = android.util.Base64.decode(cleanData, android.util.Base64.DEFAULT)
                decompressGzip(compressedBytes)
            }
            else -> return scannedData 
        }

        extractAadhaarDetails(xmlData)

    } catch (e: Exception) {
        "Decoding Failed: Format might be unsupported.\nRaw Data: ${scannedData.take(20)}..."
    }
}

// Helper function: GZIP decompress karne ke liye
private fun decompressGzip(compressed: ByteArray): String {
    return try {
        val gis = GZIPInputStream(ByteArrayInputStream(compressed))
        val decompressedBytes = gis.readBytes()
        String(decompressedBytes, Charsets.UTF_8)
    } catch (e: Exception) {
        // Agar GZIP nahi hai, toh as a string try karein
        String(compressed, Charsets.UTF_8)
    }
}

// Helper function: Check karein ki string Base64 hai ya nahi
private fun isBase64(str: String): Boolean {
    return str.matches(Regex("^[a-zA-Z0-9+/]*={0,2}$"))
}

private fun extractAadhaarDetails(xml: String): String {
    // Basic extraction logic: UIDAI ke XML tag se Naam aur Address nikalna
    try {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        
        var eventType = parser.eventType
        var result = "Aadhaar Card Details:\n"
        var dataFound = false
        
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "PrintLetterBarcodeData") {
                dataFound = true
                val uid = parser.getAttributeValue(null, "uid")?.let { "**** **** ${it.takeLast(4)}" } ?: "N/A"
                val name = parser.getAttributeValue(null, "name") ?: "N/A"
                val gender = parser.getAttributeValue(null, "gender") ?: "N/A"
                val yob = parser.getAttributeValue(null, "yob") ?: "N/A"
                val dist = parser.getAttributeValue(null, "dist") ?: "N/A"
                val state = parser.getAttributeValue(null, "state") ?: "N/A"
                val pc = parser.getAttributeValue(null, "pc") ?: "N/A" // Pincode
                
                result += "\nUID (Masked): $uid\nName: $name\nGender: $gender\nYear of Birth: $yob\nLocation: $dist, $state - $pc"
                return result
            }
            eventType = parser.next()
        }
        
        return if (dataFound) result else "Decoded XML (Tags missing or Encrypted):\n$xml"
        
    } catch (e: Exception) {
        return "Decoded Data:\n$xml"
    }
}
