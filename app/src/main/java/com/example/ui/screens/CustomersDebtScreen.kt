package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettingsEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.SaleOrderEntity
import com.example.data.remote.SmsService
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.CustomerFormDialog
import com.example.ui.components.CustomerPaymentHistoryComponent
import com.example.ui.components.CustomerPaymentHistoryDialog
import com.example.ui.theme.*
import com.example.util.DigitalReceiptGenerator
import com.example.util.Formatters

@Composable
fun CustomersDebtScreen(
    customers: List<CustomerEntity>,
    debtors: List<CustomerEntity>,
    totalDebt: Double,
    settings: AppSettingsEntity,
    orders: List<SaleOrderEntity> = emptyList(),
    payments: List<PaymentEntity> = emptyList(),
    onRecordPayment: (CustomerEntity) -> Unit,
    onSendReminderSms: (CustomerEntity) -> Unit,
    onSaveCustomer: (CustomerEntity) -> Unit,
    onDeleteCustomer: (CustomerEntity) -> Unit,
    onAddCustomer: () -> Unit,
    onRecordOrderPayment: ((SaleOrderEntity) -> Unit)? = null,
    onViewReceipt: ((SaleOrderEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Debtors, 1: All Customers
    var searchQuery by remember { mutableStateOf("") }
    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var customerForHistoryDialog by remember { mutableStateOf<CustomerEntity?>(null) }

    val listToDisplay = if (selectedTab == 0) debtors else customers
    val filteredList = if (searchQuery.isBlank()) {
        listToDisplay
    } else {
        listToDisplay.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = BrandGreenPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .padding(bottom = 60.dp)
                    .testTag("add_customer_fab")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Debt Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (totalDebt > 0) DebtRedContainer else FarmGreenContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (totalDebt > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (totalDebt > 0) DebtRed else FarmGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "OUTSTANDING CUSTOMER DEBT",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (totalDebt > 0) DebtRed else FarmGreen
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = Formatters.formatCurrency(totalDebt, settings.currencySymbol),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (totalDebt > 0) DebtRed else FarmGreen
                        )
                        Text(
                            text = "${debtors.size} debtor(s) out of ${customers.size} total registered customer(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Search & Tab Filter
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by customer name or phone...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_search_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Debtors (Owing: ${debtors.size})", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("All Customers (${customers.size})", fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }

            // 3. Customers / Debtors List
            if (filteredList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Default.CheckCircle else Icons.Default.People,
                                contentDescription = null,
                                tint = if (selectedTab == 0) FarmGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (selectedTab == 0) "No Outstanding Debtors!" else "No customers found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (selectedTab == 0) "All customers have cleared their balances with Naadriel Enterprise." else "Add customers using the '+' button or when recording sales.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredList) { customer ->
                    val customerOrders = remember(orders, customer.id) {
                        orders.filter { it.customerId == customer.id }
                    }
                    val customerPayments = remember(payments, customer.id) {
                        payments.filter { it.customerId == customer.id }
                    }
                    CustomerDebtCard(
                        customer = customer,
                        orders = customerOrders,
                        payments = customerPayments,
                        settings = settings,
                        onRecordPayment = { onRecordPayment(customer) },
                        onSendReminderSms = { onSendReminderSms(customer) },
                        onEditCustomer = { customerToEdit = customer },
                        onViewPaymentHistory = { customerForHistoryDialog = customer },
                        onRecordOrderPayment = { order -> onRecordOrderPayment?.invoke(order) },
                        onViewReceipt = { order -> onViewReceipt?.invoke(order) },
                        onShareWhatsAppStatement = {
                            val phone = if (customer.whatsapp.isNotBlank()) customer.whatsapp else customer.phone
                            val imageFile = DigitalReceiptGenerator.generateCustomerStatementBitmap(context, customer, customerOrders, settings)
                            if (imageFile != null) {
                                val caption = "📋 Account Statement for ${customer.name} from ${settings.businessName}"
                                DigitalReceiptGenerator.shareDigitalFile(
                                    context = context,
                                    file = imageFile,
                                    mimeType = "image/png",
                                    captionText = caption,
                                    recipientPhone = phone,
                                    preferWhatsApp = true
                                )
                            } else {
                                val statement = Formatters.buildWhatsAppCustomerStatement(customer, customerOrders, settings)
                                SmsService.shareViaWhatsApp(context, phone, statement)
                            }
                        }
                    )
                }
            }
        }
    }

    // Add / Edit Customer Form Dialog
    if (showAddDialog || customerToEdit != null) {
        val targetCustomer = customerToEdit
        CustomerFormDialog(
            customer = targetCustomer,
            currencySymbol = settings.currencySymbol,
            onDismiss = {
                showAddDialog = false
                customerToEdit = null
            },
            onSaveCustomer = { updated ->
                onSaveCustomer(updated)
                showAddDialog = false
                customerToEdit = null
            },
            onDeleteCustomer = { toDelete ->
                onDeleteCustomer(toDelete)
                showAddDialog = false
                customerToEdit = null
            }
        )
    }

    // Customer Full Payment & Debt History Modal Dialog
    customerForHistoryDialog?.let { targetCustomer ->
        val custOrders = remember(orders, targetCustomer.id) {
            orders.filter { it.customerId == targetCustomer.id }
        }
        val custPayments = remember(payments, targetCustomer.id) {
            payments.filter { it.customerId == targetCustomer.id }
        }
        CustomerPaymentHistoryDialog(
            customer = targetCustomer,
            orders = custOrders,
            payments = custPayments,
            settings = settings,
            onDismiss = { customerForHistoryDialog = null },
            onRecordPayment = { cust ->
                customerForHistoryDialog = null
                onRecordPayment(cust)
            },
            onRecordOrderPayment = { order ->
                customerForHistoryDialog = null
                onRecordOrderPayment?.invoke(order)
            },
            onViewReceipt = { order ->
                customerForHistoryDialog = null
                onViewReceipt?.invoke(order)
            },
            onShareStatement = { cust ->
                val phone = if (cust.whatsapp.isNotBlank()) cust.whatsapp else cust.phone
                val imageFile = DigitalReceiptGenerator.generateCustomerStatementBitmap(context, cust, custOrders, settings)
                if (imageFile != null) {
                    val caption = "📋 Account Statement for ${cust.name} from ${settings.businessName}"
                    DigitalReceiptGenerator.shareDigitalFile(
                        context = context,
                        file = imageFile,
                        mimeType = "image/png",
                        captionText = caption,
                        recipientPhone = phone,
                        preferWhatsApp = true
                    )
                } else {
                    val statement = Formatters.buildWhatsAppCustomerStatement(cust, custOrders, settings)
                    SmsService.shareViaWhatsApp(context, phone, statement)
                }
            }
        )
    }
}

@Composable
fun CustomerDebtCard(
    customer: CustomerEntity,
    orders: List<SaleOrderEntity> = emptyList(),
    payments: List<PaymentEntity> = emptyList(),
    settings: AppSettingsEntity,
    onRecordPayment: () -> Unit,
    onSendReminderSms: () -> Unit,
    onEditCustomer: () -> Unit,
    onViewPaymentHistory: () -> Unit = {},
    onRecordOrderPayment: ((SaleOrderEntity) -> Unit)? = null,
    onViewReceipt: ((SaleOrderEntity) -> Unit)? = null,
    onShareWhatsAppStatement: () -> Unit
) {
    val owesMoney = customer.currentBalance > 0
    var isExpandedInline by remember { mutableStateOf(false) }

    val paidOrders = remember(orders) { orders.filter { it.balanceDue <= 0.0 } }
    val unpaidOrders = remember(orders) { orders.filter { it.balanceDue > 0.0 }.sortedBy { it.timestamp } }
    val customerPayments = remember(payments, customer.id) {
        payments.filter { it.customerId == customer.id }.sortedByDescending { it.timestamp }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("customer_debt_card_${customer.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (owesMoney) CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(DebtRed.copy(alpha = 0.5f))
        ) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Name & Balance Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onEditCustomer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Customer",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "Phone: ${customer.phone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (owesMoney) DebtRedContainer else FarmGreenContainer
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (owesMoney) "OWES" else "CLEARED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (owesMoney) DebtRed else FarmGreen
                        )
                        Text(
                            text = Formatters.formatCurrency(customer.currentBalance, settings.currencySymbol),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (owesMoney) DebtRed else FarmGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Summary Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Bought: ${Formatters.formatCurrency(customer.totalPurchases, settings.currencySymbol)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Total Paid: ${Formatters.formatCurrency(customer.totalPaid, settings.currencySymbol)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = FarmGreen
                )
            }

            // Visual Status Badges: Paid in Full vs Carrying Balance vs Part Payments
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FarmGreenContainer.copy(alpha = 0.7f),
                    modifier = Modifier.testTag("badge_paid_in_full_${customer.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = FarmGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${paidOrders.size} Paid in Full",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = FarmGreen,
                            fontSize = 11.sp
                        )
                    }
                }

                if (unpaidOrders.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DebtRedContainer.copy(alpha = 0.7f),
                        modifier = Modifier.testTag("badge_carrying_balance_${customer.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = DebtRed,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${unpaidOrders.size} Carrying Balance",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = DebtRed,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.testTag("badge_part_payments_${customer.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Payments,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${customerPayments.size} Part Payments",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (customer.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "📍 ${customer.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Card Banner for Quick History Exploration
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onViewPaymentHistory() }
                    .testTag("view_payment_history_banner_${customer.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.HistoryEdu,
                            contentDescription = null,
                            tint = BrandGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Track Part Payments & Invoices",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Inspect paid in full vs remaining balance orders",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Full History",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandGreenPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = BrandGreenPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Inline Expansion Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { isExpandedInline = !isExpandedInline },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("toggle_inline_history_${customer.id}")
                ) {
                    Text(
                        text = if (isExpandedInline) "Collapse inline view ▲" else "Quick inline view ▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Inline Visual Component if toggled
            AnimatedVisibility(visible = isExpandedInline) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    CustomerPaymentHistoryComponent(
                        customer = customer,
                        orders = orders,
                        payments = payments,
                        settings = settings,
                        onRecordPayment = { onRecordPayment() },
                        onRecordOrderPayment = { order -> onRecordOrderPayment?.invoke(order) },
                        onViewReceipt = { order -> onViewReceipt?.invoke(order) },
                        onShareStatement = { onShareWhatsAppStatement() }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (owesMoney) {
                    // Record Payment Button
                    Button(
                        onClick = onRecordPayment,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("pay_debt_button_${customer.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FarmGreen)
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pay Debt", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // SMS Reminder Button
                    FilledTonalButton(
                        onClick = onSendReminderSms,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sms_reminder_button_${customer.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BrandGreenPrimary.copy(alpha = 0.15f),
                            contentColor = BrandGreenPrimary
                        )
                    ) {
                        Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SMS", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Dedicated View History Icon Button
                IconButton(
                    onClick = onViewPaymentHistory,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("payment_history_button_${customer.id}")
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "View Part Payment History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // WhatsApp Statement Button
                IconButton(
                    onClick = onShareWhatsAppStatement,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(WhatsAppGreen.copy(alpha = 0.15f))
                        .testTag("whatsapp_statement_button_${customer.id}")
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share Account Statement via WhatsApp",
                        tint = WhatsAppGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
