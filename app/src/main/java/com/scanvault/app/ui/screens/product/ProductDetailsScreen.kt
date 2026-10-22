package com.scanvault.app.ui.screens.product

import android.widget.Toast
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.scanvault.app.data.repository.ProductLookupRepository
import com.scanvault.app.data.repository.ProductLookupResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsScreen(
    barcode: String,
    navController: NavController,
    productRepository: ProductLookupRepository
) {
    val coroutineScope = rememberCoroutineScope()
    var lookupResult by remember { mutableStateOf<ProductLookupResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun performLookup() {
        isLoading = true
        coroutineScope.launch {
            val res = productRepository.lookupBarcode(barcode)
            lookupResult = res
            isLoading = false
        }
    }

    LaunchedEffect(barcode) {
        performLookup()
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
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = "Product Details",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { performLookup() }, enabled = !isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface)
                }
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
            // Barcode Source Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DECODED BARCODE NUMBER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                                                
                        Text(
                            // Yahan humne function call kiya hai jisse lamba number decode ho jayega
                            text = com.scanvault.app.data.repository.decodeAadhaarQR(barcode),
                            
                            // Lamba Aadhaar data ho toh font chhota kar dega, warna normal 18sp rahega
                            fontSize = if (barcode.length > 50) 13.sp else 18.sp, 
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "External Lookup",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Querying database...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                when (val result = lookupResult) {
                    is ProductLookupResult.Success -> {
                        val context = LocalContext.current
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = result.productName,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                result.brand?.let { b ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Brand: $b",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Divider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                result.price?.let { p ->
                                    if(p.isNotEmpty()) {
                                        ProductMetaRow(label = "Price (₹)", value = "₹$p")
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }

                                result.categories?.let { cat ->
                                    ProductMetaRow(label = "Category", value = cat)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                result.quantity?.let { q ->
                                    ProductMetaRow(label = "Package Size", value = q)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                result.ingredientsSummary?.let { ing ->
                                    ProductMetaRow(label = "Ingredients Summary", value = ing)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                ProductMetaRow(label = "Database Source", value = result.sourceDatabase)
                                
                                // --- NAYA LOCAL SAVE BUTTON YAHAN HAI ---
                                if (result.sourceDatabase != "Local Inventory (POS)") {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                productRepository.addProductToInventory(
                                                    barcode = result.barcode,
                                                    name = result.productName,
                                                    price = result.price ?: "",
                                                    category = result.categories ?: "Uncategorized",
                                                    qty = result.quantity ?: ""
                                                )
                                                Toast.makeText(context, "Saved to Local Inventory!", Toast.LENGTH_SHORT).show()
                                                performLookup() // Refresh to show it as local
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                                    ) {
                                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Save Data to Local Database")
                                    }
                                }
                            }
                        }
                    }

                    is ProductLookupResult.NotFound -> {
                        var newName by remember { mutableStateOf("") }
                        var newPrice by remember { mutableStateOf("") }
                        var newCategory by remember { mutableStateOf("") }
                        var newQty by remember { mutableStateOf("") }
                
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("Product Not Found!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                                Text("Add this product to your local inventory.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Product Name") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = newPrice, onValueChange = { newPrice = it }, label = { Text("Price (₹)") }, modifier = Modifier.weight(1f))
                                    OutlinedTextField(value = newQty, onValueChange = { newQty = it }, label = { Text("Quantity/Size") }, modifier = Modifier.weight(1f))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(value = newCategory, onValueChange = { newCategory = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            productRepository.addProductToInventory(barcode, newName, newPrice, newCategory, newQty)
                                            performLookup() 
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Save to Inventory")
                                }
                            }
                        }
                    }
                        
                    is ProductLookupResult.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Lookup Unavailable",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = result.message,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { performLookup() },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Retry Connection")
                                }
                            }
                        }
                    }

                    null -> {}
                }
            }
        }
    }
}

@Composable
fun ProductMetaRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
