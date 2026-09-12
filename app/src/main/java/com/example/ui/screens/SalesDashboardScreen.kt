package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppSettingsEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.SaleOrderEntity
import com.example.data.remote.SmsService
import com.example.ui.components.ConfirmationDialog
import com.example.ui.theme.*
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.util.DigitalReceiptGenerator
import com.example.util.Formatters
import java.util.Calendar

enum class DateRangeFilter(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month")
}

enum class ProductTypeFilter(val label: String, val keywords: List<String>) {
    ALL("All Products", emptyList()),
    EGGS("Eggs 🥚", listOf("egg", "crate")),
    DRESSED_CHICKEN("Dressed Chicken 🍗", listOf("dressed", "dressed broiler", "dressed sasso")),
    LIVE_CHICKEN("Live Chicken 🐔", listOf("live", "live broiler", "live sasso")),
    BROILER("Broiler 🐔", listOf("broiler")),
    SASSO("Sasso 🐓", listOf("sasso"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesDashboardScreen(
    orders: List<SaleOrderEntity>,
    debtors: List<CustomerEntity>,
    totalDebt: Double,
    totalRevenue: Double,
    totalCollected: Double,
    settings: AppSettingsEntity,
    onNavigateToNewSale: () -> Unit,
    onNavigateToDebtors: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onSelectOrderReceipt: (SaleOrderEntity) -> Unit,
    onSendSms: (SaleOrderEntity) -> Unit,
    onEditOrder: ((SaleOrderEntity) -> Unit)? = null,
    onVoidOrder: ((SaleOrderEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    var orderToVoid by remember { mutableStateOf<SaleOrderEntity?>(null) }

    // Search and Filter States
    var searchQuery by remember { mutableStateOf("") }
    var selectedDateRange by remember { mutableStateOf(DateRangeFilter.ALL) }
    var selectedProductType by remember { mutableStateOf(ProductTypeFilter.ALL) }

    // Filter Logic
    val filteredOrders = remember(orders, searchQuery, selectedDateRange, selectedProductType) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        // Calculate start of today
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis

        // Calculate start of this week (Monday)
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val startOfWeek = cal.timeInMillis

        // Calculate start of this month
        cal.timeInMillis = now
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        orders.filter { order ->
            // 1. Text Search matching (Customer Name, Invoice #, Phone, or Line Item Name)
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val queryLower = searchQuery.trim().lowercase()
                order.customerName.lowercase().contains(queryLower) ||
                        order.invoiceNumber.lowercase().contains(queryLower) ||
                        order.customerPhone.contains(queryLower) ||
                        order.itemsJson.lowercase().contains(queryLower) ||
                        order.notes.lowercase().contains(queryLower)
            }

            // 2. Date Range filter
            val matchesDate = when (selectedDateRange) {
                DateRangeFilter.ALL -> true
                DateRangeFilter.TODAY -> order.timestamp >= startOfToday
                DateRangeFilter.THIS_WEEK -> order.timestamp >= startOfWeek
                DateRangeFilter.THIS_MONTH -> order.timestamp >= startOfMonth
            }

            // 3. Product Type filter
            val matchesProduct = if (selectedProductType == ProductTypeFilter.ALL) {
                true
            } else {
                val items = Formatters.parseCartItems(order.itemsJson)
                items.any { item ->
                    val nameLower = item.productName.lowercase()
                    val categoryLower = item.category.lowercase()
                    selectedProductType.keywords.any { kw ->
                        nameLower.contains(kw) || categoryLower.contains(kw)
                    }
                }
            }

            matchesSearch && matchesDate && matchesProduct
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Business Brand Header
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("farm_header_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BrandCharcoalDark),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_naadriel_logo),
                            contentDescription = "Naadriel Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(46.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = settings.businessName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = settings.businessTagline,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = BrandGreenPrimaryDark
                        )
                        Text(
                            text = "Eggs • Dressed & Live Chicken (Broilers / Sasso)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC2C9BD)
                        )
                    }
                }
            }
        }

        // 2. Metrics Overview Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Debt Hero Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDebtors() }
                        .testTag("debt_hero_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (totalDebt > 0) DebtRedContainer else BrandGreenPrimaryContainer
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (totalDebt > 0) DebtRed.copy(alpha = 0.5f) else BrandGreenPrimary.copy(alpha = 0.5f)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (totalDebt > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (totalDebt > 0) DebtRed else BrandGreenPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "OUTSTANDING BALANCES",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (totalDebt > 0) DebtRed else BrandGreenPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = Formatters.formatCurrency(totalDebt, settings.currencySymbol),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (totalDebt > 0) DebtRed else BrandGreenPrimary
                            )
                            Text(
                                text = "${debtors.size} customer(s) with pending balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "View Balances",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Revenue & Collected Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricMiniCard(
                        title = "Total Sales",
                        value = Formatters.formatCurrency(totalRevenue, settings.currencySymbol),
                        icon = Icons.Default.TrendingUp,
                        iconTint = BrandGreenPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    MetricMiniCard(
                        title = "Cash Collected",
                        value = Formatters.formatCurrency(totalCollected, settings.currencySymbol),
                        icon = Icons.Default.Payments,
                        iconTint = FarmGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Action Hub
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // New Sale Main Button
                Button(
                    onClick = onNavigateToNewSale,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(54.dp)
                        .testTag("dashboard_new_sale_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreenPrimary)
                ) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Sale / Bill", fontWeight = FontWeight.Bold)
                }

                // Balances Button
                OutlinedButton(
                    onClick = onNavigateToDebtors,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("dashboard_debtors_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Balances", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 4. Products & Pricing Quick Shortcut
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToProducts() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = BrandGreenPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Poultry Catalog & Prices", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(text = "Eggs, Live/Dressed Broilers & Sasso Chicken", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }

        // 5. Search Bar & Filter Chips Header
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sales & Invoices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${filteredOrders.size} of ${orders.size} orders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sales_search_input"),
                    placeholder = { Text("Search customer, invoice #, phone, product...") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = BrandGreenPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                )

                // Date Range Filter Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "FILTER BY DATE RANGE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(DateRangeFilter.values()) { dateFilter ->
                            FilterChip(
                                selected = selectedDateRange == dateFilter,
                                onClick = { selectedDateRange = dateFilter },
                                label = {
                                    Text(
                                        text = dateFilter.label,
                                        fontWeight = if (selectedDateRange == dateFilter) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandGreenPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Product Type Filter Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "FILTER BY PRODUCT TYPE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ProductTypeFilter.values()) { prodFilter ->
                            FilterChip(
                                selected = selectedProductType == prodFilter,
                                onClick = { selectedProductType = prodFilter },
                                label = {
                                    Text(
                                        text = prodFilter.label,
                                        fontWeight = if (selectedProductType == prodFilter) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandGreenPrimaryDark,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Active filters clear button when filters applied
                if (searchQuery.isNotBlank() || selectedDateRange != DateRangeFilter.ALL || selectedProductType != ProductTypeFilter.ALL) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Showing matching filtered transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandGreenPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                searchQuery = ""
                                selectedDateRange = DateRangeFilter.ALL
                                selectedProductType = ProductTypeFilter.ALL
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Filters", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 6. Orders List
        if (orders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No sales recorded yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap 'New Sale / Bill' above to record sales for Eggs, Live or Dressed Broilers and Sasso chicken.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else if (filteredOrders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.FilterAltOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No matching sales found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "No transactions match your current search query, date range, or product filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                searchQuery = ""
                                selectedDateRange = DateRangeFilter.ALL
                                selectedProductType = ProductTypeFilter.ALL
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Clear All Filters")
                        }
                    }
                }
            }
        } else {
            items(filteredOrders) { order ->
                SaleOrderCard(
                    order = order,
                    settings = settings,
                    onClickReceipt = { onSelectOrderReceipt(order) },
                    onWhatsAppShare = {
                        val phone = if (order.customerWhatsapp.isNotBlank()) order.customerWhatsapp else order.customerPhone
                        val imageFile = DigitalReceiptGenerator.generateReceiptBitmap(context, order, settings)
                        if (imageFile != null) {
                            val caption = "🧾 Official Receipt #${order.invoiceNumber} from ${settings.businessName}"
                            DigitalReceiptGenerator.shareDigitalFile(
                                context = context,
                                file = imageFile,
                                mimeType = "image/png",
                                captionText = caption,
                                recipientPhone = phone,
                                preferWhatsApp = true
                            )
                        } else {
                            val receiptText = Formatters.buildWhatsAppReceipt(order, settings)
                            SmsService.shareViaWhatsApp(context, phone, receiptText)
                        }
                    },
                    onSendSms = { onSendSms(order) },
                    onEditOrder = if (onEditOrder != null) { { onEditOrder(order) } } else null,
                    onVoidOrder = if (onVoidOrder != null) { { orderToVoid = order } } else null
                )
            }
        }
    }

    // Void Order Confirmation Dialog
    if (orderToVoid != null && onVoidOrder != null) {
        val target = orderToVoid!!
        val debtMsg = if (target.balanceDue > 0) {
            "\n• Reverse customer's balance due: ${Formatters.formatCurrency(target.balanceDue, settings.currencySymbol)}"
        } else ""

        ConfirmationDialog(
            title = "Void Sale Invoice?",
            message = "Are you sure you want to void Invoice #${target.invoiceNumber} for '${target.customerName}'?\n\nThis will automatically:\n• Restore all line items back to your inventory stock$debtMsg\n• Permanently remove this sale from records.",
            confirmButtonText = "Void & Restore Stock",
            cancelButtonText = "Keep Sale",
            isDestructive = true,
            onConfirm = {
                val toCancel = orderToVoid
                orderToVoid = null
                if (toCancel != null) {
                    onVoidOrder(toCancel)
                }
            },
            onDismiss = { orderToVoid = null }
        )
    }
}

@Composable
fun MetricMiniCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SaleOrderCard(
    order: SaleOrderEntity,
    settings: AppSettingsEntity,
    onClickReceipt: () -> Unit,
    onWhatsAppShare: () -> Unit,
    onSendSms: () -> Unit,
    onEditOrder: (() -> Unit)? = null,
    onVoidOrder: (() -> Unit)? = null
) {
    val items = Formatters.parseCartItems(order.itemsJson)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickReceipt() }
            .testTag("sale_order_card_${order.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.customerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "#${order.invoiceNumber} • ${Formatters.formatDateShort(order.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (order.paymentStatus) {
                            "PAID" -> FarmGreenContainer
                            "PARTIAL" -> BrandAmberGoldContainer
                            else -> DebtRedContainer
                        }
                    ) {
                        Text(
                            text = when (order.paymentStatus) {
                                "PAID" -> "PAID"
                                "PARTIAL" -> "PARTIAL"
                                else -> "BALANCE DUE"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (order.paymentStatus) {
                                "PAID" -> FarmGreen
                                "PARTIAL" -> BrandAmberGold
                                else -> DebtRed
                            }
                        )
                    }

                    if (onVoidOrder != null) {
                        IconButton(
                            onClick = onVoidOrder,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("void_order_button_${order.id}")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Void Sale Invoice",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Items breakdown summary
            if (items.isNotEmpty()) {
                Text(
                    text = items.joinToString(", ") { "${if (it.quantity % 1.0 == 0.0) it.quantity.toInt() else it.quantity} ${it.productName}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Financial row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Total: ${Formatters.formatCurrency(order.totalAmount, settings.currencySymbol)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (order.discountAmount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = WarningAmber.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Sell,
                                        contentDescription = null,
                                        tint = WarningAmber,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "-${Formatters.formatCurrency(order.discountAmount, settings.currencySymbol)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = WarningAmber
                                    )
                                }
                            }
                        }
                    }
                    if (order.balanceDue > 0) {
                        Text(
                            text = "Balance Due: ${Formatters.formatCurrency(order.balanceDue, settings.currencySymbol)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = DebtRed
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Edit Invoice Button
                    if (onEditOrder != null) {
                        FilledTonalButton(
                            onClick = onEditOrder,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("edit_order_button_${order.id}"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Invoice", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // WhatsApp share icon button
                    IconButton(
                        onClick = onWhatsAppShare,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(WhatsAppGreen.copy(alpha = 0.15f))
                            .testTag("whatsapp_order_button_${order.id}")
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share on WhatsApp",
                            tint = WhatsAppGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // SMS trigger icon button
                    IconButton(
                        onClick = onSendSms,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandGreenPrimary.copy(alpha = 0.15f))
                            .testTag("sms_order_button_${order.id}")
                    ) {
                        Icon(
                            Icons.Default.Sms,
                            contentDescription = "Send SMS",
                            tint = BrandGreenPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
