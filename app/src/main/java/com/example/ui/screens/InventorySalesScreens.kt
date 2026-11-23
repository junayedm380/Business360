package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.InventoryBalanceEntity
import com.example.data.local.ProductModelEntity
import com.example.data.local.SalesReturnEntity
import com.example.data.local.StockMovementEntity
import com.example.data.local.WarehouseEntity
import com.example.data.repository.CustomerSanitizedView
import com.example.data.repository.ReportType
import com.example.data.repository.SaleItemRequest
import com.example.data.repository.SaleSanitizedView
import com.example.domain.AppPermission
import com.example.domain.AppStrings
import com.example.domain.Formatters
import com.example.domain.SessionUser
import com.example.ui.components.EmptyStateNotice
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    session: SessionUser,
    strings: AppStrings,
    models: List<ProductModelEntity>,
    warehouses: List<WarehouseEntity>,
    balances: List<InventoryBalanceEntity>,
    movements: List<StockMovementEntity>,
    onCreateWarehouse: (String, String) -> Unit,
    onTransferStock: (String, String, String, Double, String) -> Unit,
    onAdjustStock: (String, String, Double, String) -> Unit,
    onExportStock: (ReportType) -> Unit,
    initialSubTab: String? = null,
    onClearInitialSubTab: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    LaunchedEffect(initialSubTab) {
        if (!initialSubTab.isNullOrBlank()) {
            selectedTab = when (initialSubTab) {
                "MOVEMENTS" -> 1
                "WAREHOUSES" -> 2
                else -> 0
            }
            onClearInitialSubTab()
        }
    }
    var showWhDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Stock Balances") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Movements") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Warehouses") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (session.has(AppPermission.TRANSFER_STOCK) && warehouses.size >= 2 && models.isNotEmpty()) {
                                Button(onClick = { showTransferDialog = true }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Transfer")
                                }
                            }
                            if (session.has(AppPermission.ADJUST_STOCK) && warehouses.isNotEmpty() && models.isNotEmpty()) {
                                FilledTonalButton(onClick = { showAdjustDialog = true }, modifier = Modifier.weight(1f)) {
                                    Text("Adjust Stock")
                                }
                            }
                            if (session.has(AppPermission.EXPORT_REPORTS)) {
                                OutlinedButton(onClick = { onExportStock(ReportType.OPERATIONAL_STOCK) }) {
                                    Icon(Icons.Outlined.Download, contentDescription = "Export Stock CSV")
                                }
                            }
                        }
                    }
                    if (balances.isEmpty()) {
                        item { EmptyStateNotice(strings.noDataYet) }
                    } else {
                        items(balances, key = { it.id }) { bal ->
                            val model = models.firstOrNull { it.id == bal.modelId }
                            val wh = warehouses.firstOrNull { it.id == bal.warehouseId }
                            SectionCard(title = "${model?.modelName ?: bal.modelId} (${model?.modelCode ?: ""})") {
                                Text("Warehouse: ${wh?.name ?: bal.warehouseId}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Sellable: ${Formatters.formatQty(bal.goodQty)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Damaged: ${Formatters.formatQty(bal.damagedQty)} | Scrap: ${Formatters.formatQty(bal.scrapQty)}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (movements.isEmpty()) {
                        item { EmptyStateNotice(strings.noDataYet) }
                    } else {
                        items(movements, key = { it.id }) { mv ->
                            val modelName = models.firstOrNull { it.id == mv.modelId }?.modelName ?: mv.modelId
                            SectionCard(title = "${mv.movementType} • $modelName") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Ref: ${mv.referenceDoc} • ${Formatters.formatDateTime(mv.createdAt)}", style = MaterialTheme.typography.bodySmall)
                                    Text("${if (mv.quantityDelta >= 0) "+" else ""}${Formatters.formatQty(mv.quantityDelta)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                if (mv.notes.isNotBlank()) Text(mv.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        if (session.has(AppPermission.MANAGE_SETTINGS) || session.has(AppPermission.TRANSFER_STOCK)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(onClick = { showWhDialog = true }) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Warehouse")
                                }
                            }
                        }
                    }
                    items(warehouses, key = { it.id }) { wh ->
                        SectionCard(title = wh.name) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(wh.location.ifBlank { "Standard Warehouse" }, style = MaterialTheme.typography.bodyMedium)
                                if (wh.defaultWarehouse) StatusChip("DEFAULT")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWhDialog) {
        var name by remember { mutableStateOf("") }
        var loc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWhDialog = false },
            title = { Text("New Warehouse") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Warehouse Name") })
                    OutlinedTextField(value = loc, onValueChange = { loc = it }, label = { Text("Location") })
                }
            },
            confirmButton = { Button(onClick = { onCreateWarehouse(name, loc); showWhDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showWhDialog = false }) { Text("Cancel") } }
        )
    }

    if (showTransferDialog && warehouses.size >= 2 && models.isNotEmpty()) {
        TransferStockDialog(models, warehouses, onDismiss = { showTransferDialog = false }) { mId, fWh, tWh, qty, notes ->
            onTransferStock(mId, fWh, tWh, qty, notes)
            showTransferDialog = false
        }
    }

    if (showAdjustDialog && warehouses.isNotEmpty() && models.isNotEmpty()) {
        AdjustStockDialog(models, warehouses, onDismiss = { showAdjustDialog = false }) { mId, wh, delta, reason ->
            onAdjustStock(mId, wh, delta, reason)
            showAdjustDialog = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesAndCustomersScreen(
    session: SessionUser,
    strings: AppStrings,
    models: List<ProductModelEntity>,
    warehouses: List<WarehouseEntity>,
    customers: List<CustomerSanitizedView>,
    sales: List<SaleSanitizedView>,
    returns: List<SalesReturnEntity>,
    onCreateCustomer: (String, String, String, String, String, Double, Double) -> Unit,
    onCreateSale: (String, String, String, List<SaleItemRequest>, Double, Double, Double, String, Boolean, String) -> Unit,
    onConfirmDelivery: (String, Double) -> Unit,
    onProcessReturn: (String, String, Double, String, String) -> Unit,
    onOpenCustomerLedger: (String) -> Unit,
    initialSubTab: String? = null,
    onClearInitialSubTab: () -> Unit = {}
) {
    val tabs = buildList {
        if (session.has(AppPermission.CREATE_SALES) || session.isFullAdmin) add("ORDERS")
        if (session.has(AppPermission.VIEW_CUSTOMERS)) add("CUSTOMERS")
        if (session.has(AppPermission.MANAGE_RETURNS) || session.isFullAdmin) add("RETURNS")
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    LaunchedEffect(initialSubTab, tabs) {
        if (!initialSubTab.isNullOrBlank()) {
            val idx = tabs.indexOf(initialSubTab)
            if (idx >= 0) selectedTab = idx
            onClearInitialSubTab()
        }
    }
    val activeTab = tabs.getOrElse(selectedTab) { tabs.firstOrNull() ?: "ORDERS" }
    var search by remember { mutableStateOf("") }
    var showNewSaleDialog by remember { mutableStateOf<String?>(null) } // "WHOLESALE" or "RETAIL"
    var showCustomerDialog by remember { mutableStateOf(false) }
    var showReturnDialogForSale by remember { mutableStateOf<SaleSanitizedView?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (tabs.size > 1) {
            PrimaryTabRow(selectedTabIndex = selectedTab.coerceAtMost(tabs.lastIndex)) {
                tabs.forEachIndexed { idx, key ->
                    val title = when (key) {
                        "ORDERS" -> "Orders & POS"
                        "CUSTOMERS" -> strings.customers
                        else -> strings.returnsAndRefunds
                    }
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(title) })
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeTab) {
                "ORDERS" -> {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (session.has(AppPermission.CREATE_SALES)) {
                                Button(
                                    onClick = { showNewSaleDialog = "WHOLESALE" },
                                    enabled = customers.isNotEmpty() && models.isNotEmpty() && warehouses.isNotEmpty(),
                                    modifier = Modifier.weight(1f).testTag("btn_new_wholesale")
                                ) {
                                    Text("+ Wholesale")
                                }
                                FilledTonalButton(
                                    onClick = { showNewSaleDialog = "RETAIL" },
                                    enabled = customers.isNotEmpty() && models.isNotEmpty() && warehouses.isNotEmpty(),
                                    modifier = Modifier.weight(1f).testTag("btn_new_retail")
                                ) {
                                    Text("+ Retail POS")
                                }
                            }
                        }
                    }
                    if (sales.isEmpty()) {
                        item { EmptyStateNotice(strings.noDataYet) }
                    } else {
                        items(sales, key = { it.id }) { sale ->
                            SectionCard(title = "${sale.invoiceNumber} • ${sale.saleType}") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${sale.customerName} • ${Formatters.formatDate(sale.saleDate)}", style = MaterialTheme.typography.bodySmall)
                                    StatusChip(sale.deliveryStatus)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("Total Quantity: ${Formatters.formatQty(sale.totalItemQty, "units")}", style = MaterialTheme.typography.bodyMedium)
                                if (sale.grandTotal != null) {
                                    val priceText = if (sale.paidAmount != null) {
                                        "Grand Total: ${Formatters.formatTaka(sale.grandTotal)} | Paid: ${Formatters.formatTaka(sale.paidAmount)} | Due: ${Formatters.formatTaka(sale.dueAmount ?: 0.0)}"
                                    } else {
                                        "Sale Total: ${Formatters.formatTaka(sale.grandTotal)}"
                                    }
                                    Text(
                                        priceText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (session.has(AppPermission.DISPATCH_DELIVERY) && sale.deliveryStatus in listOf("PENDING", "PARTIAL")) {
                                        FilledTonalButton(onClick = { onConfirmDelivery(sale.id, 1.0) }) {
                                            Icon(Icons.Filled.LocalShipping, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Deliver Full")
                                        }
                                        OutlinedButton(onClick = { onConfirmDelivery(sale.id, 0.5) }) {
                                            Text("Partial 50%")
                                        }
                                    }
                                    if (session.has(AppPermission.MANAGE_RETURNS) && sale.deliveryStatus in listOf("DELIVERED", "PARTIAL")) {
                                        OutlinedButton(onClick = { showReturnDialogForSale = sale }) {
                                            Text("Return")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "CUSTOMERS" -> {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                label = { Text(strings.searchHint) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (session.has(AppPermission.MANAGE_CUSTOMERS) || session.has(AppPermission.CREATE_SALES)) {
                                Button(onClick = { showCustomerDialog = true }, modifier = Modifier.testTag("btn_add_customer")) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                }
                            }
                        }
                    }
                    val filtered = customers.filter {
                        search.isBlank() || it.name.contains(search, ignoreCase = true) || it.businessName.contains(search, ignoreCase = true)
                    }
                    if (filtered.isEmpty()) {
                        item { EmptyStateNotice(strings.noDataYet) }
                    } else {
                        items(filtered, key = { it.id }) { cust ->
                            SectionCard(
                                title = cust.name,
                                actionLabel = if (session.has(AppPermission.VIEW_CUSTOMER_LEDGER)) "Ledger" else null,
                                onAction = if (session.has(AppPermission.VIEW_CUSTOMER_LEDGER)) ({ onOpenCustomerLedger(cust.id) }) else null
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(cust.businessName.ifBlank { cust.customerType }, style = MaterialTheme.typography.bodySmall)
                                    StatusChip(cust.customerType)
                                }
                                if (cust.phone != null) {
                                    Text("Phone: ${cust.phone}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (cust.currentDue != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Outstanding Due: ${Formatters.formatTaka(cust.currentDue)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
                "RETURNS" -> {
                    if (returns.isEmpty()) {
                        item { EmptyStateNotice(strings.noDataYet) }
                    } else {
                        items(returns, key = { it.id }) { ret ->
                            val modelName = models.firstOrNull { it.id == ret.modelId }?.modelName ?: ret.modelId
                            SectionCard(title = "${ret.creditNoteNumber} • $modelName") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Qty: ${Formatters.formatQty(ret.quantity)} • ${Formatters.formatDate(ret.createdAt)}", style = MaterialTheme.typography.bodySmall)
                                    StatusChip(ret.classification)
                                }
                                if (session.canViewFinancials) {
                                    Text("Credit Note Amount: ${Formatters.formatTaka(ret.refundAmount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                }
                                Text(ret.reason, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomerDialog) {
        CustomerFormDialog(session = session, onDismiss = { showCustomerDialog = false }) { name, biz, phone, addr, type, opening, limit ->
            onCreateCustomer(name, biz, phone, addr, type, opening, limit)
            showCustomerDialog = false
        }
    }

    showNewSaleDialog?.let { sType ->
        SaleFormDialog(
            saleType = sType,
            session = session,
            customers = customers,
            models = models,
            warehouses = warehouses,
            onDismiss = { showNewSaleDialog = null },
            onSubmit = { custId, whId, items, disc, charges, paid, method, deliverNow, notes ->
                onCreateSale(sType, custId, whId, items, disc, charges, paid, method, deliverNow, notes)
                showNewSaleDialog = null
            }
        )
    }

    showReturnDialogForSale?.let { targetSale ->
        SalesReturnDialog(
            sale = targetSale,
            models = models,
            onDismiss = { showReturnDialogForSale = null },
            onSubmit = { mId, qty, cls, reason ->
                onProcessReturn(targetSale.id, mId, qty, cls, reason)
                showReturnDialogForSale = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferStockDialog(models: List<ProductModelEntity>, warehouses: List<WarehouseEntity>, onDismiss: () -> Unit, onSubmit: (String, String, String, Double, String) -> Unit) {
    var model by remember { mutableStateOf(models.first()) }
    var fromWh by remember { mutableStateOf(warehouses.first()) }
    var toWh by remember { mutableStateOf(warehouses.last()) }
    var qty by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }
    var mExp by remember { mutableStateOf(false) }
    var fExp by remember { mutableStateOf(false) }
    var tExp by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer Stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = mExp, onExpandedChange = { mExp = !mExp }) {
                    OutlinedTextField(value = model.modelName, onValueChange = {}, readOnly = true, label = { Text("Model") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = mExp, onDismissRequest = { mExp = false }) { models.forEach { m -> DropdownMenuItem(text = { Text(m.modelName) }, onClick = { model = m; mExp = false }) } }
                }
                ExposedDropdownMenuBox(expanded = fExp, onExpandedChange = { fExp = !fExp }) {
                    OutlinedTextField(value = fromWh.name, onValueChange = {}, readOnly = true, label = { Text("From Warehouse") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(fExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = fExp, onDismissRequest = { fExp = false }) { warehouses.forEach { w -> DropdownMenuItem(text = { Text(w.name) }, onClick = { fromWh = w; fExp = false }) } }
                }
                ExposedDropdownMenuBox(expanded = tExp, onExpandedChange = { tExp = !tExp }) {
                    OutlinedTextField(value = toWh.name, onValueChange = {}, readOnly = true, label = { Text("To Warehouse") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = tExp, onDismissRequest = { tExp = false }) { warehouses.forEach { w -> DropdownMenuItem(text = { Text(w.name) }, onClick = { toWh = w; tExp = false }) } }
                }
                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = { Button(onClick = { onSubmit(model.id, fromWh.id, toWh.id, qty.toDoubleOrNull() ?: 0.0, notes) }) { Text("Transfer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustStockDialog(models: List<ProductModelEntity>, warehouses: List<WarehouseEntity>, onDismiss: () -> Unit, onSubmit: (String, String, Double, String) -> Unit) {
    var model by remember { mutableStateOf(models.first()) }
    var wh by remember { mutableStateOf(warehouses.first()) }
    var delta by remember { mutableStateOf("0") }
    var reason by remember { mutableStateOf("") }
    var mExp by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stock Adjustment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = mExp, onExpandedChange = { mExp = !mExp }) {
                    OutlinedTextField(value = model.modelName, onValueChange = {}, readOnly = true, label = { Text("Model") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = mExp, onDismissRequest = { mExp = false }) { models.forEach { m -> DropdownMenuItem(text = { Text(m.modelName) }, onClick = { model = m; mExp = false }) } }
                }
                OutlinedTextField(value = delta, onValueChange = { delta = it }, label = { Text("Delta (+/- quantity)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason (Physical count, damage)") })
            }
        },
        confirmButton = { Button(onClick = { onSubmit(model.id, wh.id, delta.toDoubleOrNull() ?: 0.0, reason) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CustomerFormDialog(session: SessionUser, onDismiss: () -> Unit, onSave: (String, String, String, String, String, Double, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var biz by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var addr by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("WHOLESALE") }
    var opening by remember { mutableStateOf("0") }
    var limit by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Customer") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Customer Name") })
                OutlinedTextField(value = biz, onValueChange = { biz = it }, label = { Text("Business Name") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("Address") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("WHOLESALE", "RETAIL").forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                    }
                }
                if (session.canViewFinancials) {
                    OutlinedTextField(value = opening, onValueChange = { opening = it }, label = { Text("Opening Balance (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = limit, onValueChange = { limit = it }, label = { Text("Credit Limit (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, biz, phone, addr, type, opening.toDoubleOrNull() ?: 0.0, limit.toDoubleOrNull() ?: 0.0) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaleFormDialog(
    saleType: String,
    session: SessionUser,
    customers: List<CustomerSanitizedView>,
    models: List<ProductModelEntity>,
    warehouses: List<WarehouseEntity>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, List<SaleItemRequest>, Double, Double, Double, String, Boolean, String) -> Unit
) {
    var customer by remember { mutableStateOf(customers.first()) }
    var warehouse by remember { mutableStateOf(warehouses.first()) }
    var model by remember { mutableStateOf(models.first()) }
    var qty by remember { mutableStateOf("1") }
    var customPrice by remember {
        mutableStateOf((if (saleType == "WHOLESALE") model.wholesalePrice else model.retailPrice).toString())
    }
    val cart = remember { mutableStateListOf<SaleItemRequest>() }
    var discount by remember { mutableStateOf("0") }
    var charges by remember { mutableStateOf("0") }
    var paid by remember { mutableStateOf("0") }
    var method by remember { mutableStateOf("CASH") }
    val canDispatch = session.has(AppPermission.DISPATCH_DELIVERY)
    var deliverNow by remember { mutableStateOf(canDispatch && saleType == "RETAIL") }
    var notes by remember { mutableStateOf("") }
    var cExp by remember { mutableStateOf(false) }
    var mExp by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$saleType Order") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = cExp, onExpandedChange = { cExp = !cExp }) {
                    OutlinedTextField(value = customer.name, onValueChange = {}, readOnly = true, label = { Text("Customer") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = cExp, onDismissRequest = { cExp = false }) {
                        customers.forEach { c -> DropdownMenuItem(text = { Text("${c.name} (${c.customerType})") }, onClick = { customer = c; cExp = false }) }
                    }
                }
                ExposedDropdownMenuBox(expanded = mExp, onExpandedChange = { mExp = !mExp }) {
                    OutlinedTextField(value = model.modelName, onValueChange = {}, readOnly = true, label = { Text("Product Model") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = mExp, onDismissRequest = { mExp = false }) {
                        models.forEach { m ->
                            DropdownMenuItem(
                                text = { Text("${m.modelName} (${m.modelCode})") },
                                onClick = {
                                    model = m
                                    customPrice = (if (saleType == "WHOLESALE") m.wholesalePrice else m.retailPrice).toString()
                                    mExp = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    if (session.has(AppPermission.VIEW_UNIT_PRICES) || session.canViewFinancials) {
                        OutlinedTextField(value = customPrice, onValueChange = { customPrice = it }, label = { Text("Unit Price (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                }
                FilledTonalButton(onClick = {
                    val q = qty.toDoubleOrNull() ?: 0.0
                    if (q > 0) cart.add(SaleItemRequest(model.id, q, customPrice.toDoubleOrNull()))
                }) { Text("Add Line Item (${cart.size})") }
                cart.forEach { item ->
                    val mName = models.firstOrNull { it.id == item.modelId }?.modelName ?: item.modelId
                    val priceLabel = item.customUnitPrice?.let { " @ ${Formatters.formatTaka(it)}" } ?: ""
                    Text("• $mName x ${item.quantity}$priceLabel", style = MaterialTheme.typography.bodySmall)
                }
                if (session.has(AppPermission.VIEW_UNIT_PRICES) || session.canViewFinancials) {
                    OutlinedTextField(value = discount, onValueChange = { discount = it }, label = { Text("Discount (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = charges, onValueChange = { charges = it }, label = { Text("Additional Charges (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                if (session.canViewFinancials) {
                    OutlinedTextField(value = paid, onValueChange = { paid = it }, label = { Text("Paid Amount (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                if (canDispatch) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deliverNow, onCheckedChange = { deliverNow = it })
                        Text("Deliver immediately & deduct stock", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(
                enabled = cart.isNotEmpty(),
                onClick = {
                    onSubmit(
                        customer.id, warehouse.id, cart.toList(),
                        discount.toDoubleOrNull() ?: 0.0, charges.toDoubleOrNull() ?: 0.0,
                        if (session.canViewFinancials) (paid.toDoubleOrNull() ?: 0.0) else 0.0,
                        method, if (canDispatch) deliverNow else false, notes
                    )
                }
            ) { Text("Create Invoice") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalesReturnDialog(sale: SaleSanitizedView, models: List<ProductModelEntity>, onDismiss: () -> Unit, onSubmit: (String, Double, String, String) -> Unit) {
    var model by remember { mutableStateOf(models.first()) }
    var mExp by remember { mutableStateOf(false) }
    var qty by remember { mutableStateOf("1") }
    var classification by remember { mutableStateOf("GOOD") }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Return for ${sale.invoiceNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = mExp, onExpandedChange = { mExp = !mExp }) {
                    OutlinedTextField(value = model.modelName, onValueChange = {}, readOnly = true, label = { Text("Model") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = mExp, onDismissRequest = { mExp = false }) {
                        models.forEach { m -> DropdownMenuItem(text = { Text(m.modelName) }, onClick = { model = m; mExp = false }) }
                    }
                }
                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Return Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("GOOD", "DAMAGED", "SCRAP").forEach { cls ->
                        FilterChip(selected = classification == cls, onClick = { classification = cls }, label = { Text(cls) })
                    }
                }
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Return Reason") })
            }
        },
        confirmButton = { Button(onClick = { onSubmit(model.id, qty.toDoubleOrNull() ?: 0.0, classification, reason) }) { Text("Process Return") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
