package com.scanvault.app.data.repository

import android.content.Context
import android.net.Uri
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.scanvault.app.data.local.InventoryDao
import com.scanvault.app.data.local.InventoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

sealed class ProductLookupResult {
    data class Success(
        val barcode: String,
        val productName: String,
        val brand: String? = null,
        val categories: String? = null,
        val quantity: String? = null,
        val price: String? = null, // Naya field local DB ke liye
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
