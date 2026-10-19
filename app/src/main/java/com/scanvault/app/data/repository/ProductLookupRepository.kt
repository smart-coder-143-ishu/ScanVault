package com.scanvault.app.data.repository

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ProductLookupResult {
    data class Success(
        val barcode: String,
        val productName: String,
        val brand: String?,
        val categories: String?,
        val quantity: String?,
        val imageUrl: String?,
        val ingredientsSummary: String?,
        val sourceDatabase: String = "Open Food Facts"
    ) : ProductLookupResult()

    data class NotFound(val barcode: String) : ProductLookupResult()
    data class Error(val message: String) : ProductLookupResult()
}

class ProductLookupRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun lookupBarcode(barcode: String): ProductLookupResult = withContext(Dispatchers.IO) {
        val trimmed = barcode.trim()
        val url = "https://world.openfoodfacts.org/api/v2/product/$trimmed.json"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ScanVault-AndroidApp/1.0 (scanvault-app@example.com)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) return@withContext ProductLookupResult.NotFound(trimmed)
                    if (response.code == 429) return@withContext ProductLookupResult.Error("API rate limit reached. Please try again in a few moments.")
                    return@withContext ProductLookupResult.Error("Server returned code ${response.code}")
                }

                val bodyStr = response.body?.string() ?: return@withContext ProductLookupResult.NotFound(trimmed)
                val root: JsonObject = JsonParser.parseString(bodyStr).asJsonObject

                val status = root.get("status")?.asInt ?: 0
                if (status != 1) {
                    return@withContext ProductLookupResult.NotFound(trimmed)
                }

                val product = root.getAsJsonObject("product") ?: return@withContext ProductLookupResult.NotFound(trimmed)

                val productName = product.get("product_name")?.asString
                    ?: product.get("product_name_en")?.asString
                    ?: "Unnamed Item"
                val brand = product.get("brands")?.asString
                val categories = product.get("categories")?.asString
                val quantity = product.get("quantity")?.asString
                val imageUrl = product.get("image_front_url")?.asString
                val ingredients = product.get("ingredients_text")?.asString

                ProductLookupResult.Success(
                    barcode = trimmed,
                    productName = productName,
                    brand = brand,
                    categories = categories,
                    quantity = quantity,
                    imageUrl = imageUrl,
                    ingredientsSummary = ingredients
                )
            }
        } catch (_: IOException) {
            ProductLookupResult.Error("Unable to connect to product database. Check network connection.")
        } catch (e: Exception) {
            ProductLookupResult.Error("Lookup error: ${e.localizedMessage ?: "Unknown"}")
        }
    }
}
