package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CashAccountEntity
import com.example.data.local.CustomerLedgerEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.JournalEntryEntity
import com.example.data.local.PaymentSubmissionEntity
import com.example.data.local.PurchaseOrderEntity
import com.example.data.local.RawMaterialEntity
import com.example.data.local.SupplierEntity
import com.example.data.local.WarehouseEntity
import com.example.data.repository.AccountingStatements
import com.example.data.repository.CustomerSanitizedView
import com.example.data.repository.DailyClosingPreview
import com.example.data.repository.ReportType
import com.example.domain.AppPermission
import com.example.domain.AppStrings
import com.example.domain.Formatters
import com.example.domain.SessionUser
import com.example.ui.components.EmptyStateNotice
import com.example.ui.components.RestrictedBanner
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusChip
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HeroDeepStart
import com.example.ui.theme.HeroTealMid
import com.example.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceAndOpsScreen(
    session: SessionUser,
    strings: AppStrings,
    customers: List<CustomerSanitizedView>,
    paymentSubmissions: List<PaymentSubmissionEntity>,
    selectedCustomerLedger: List<CustomerLedgerEntity>,
    suppliers: List<SupplierEntity>,
    purchases: List<PurchaseOrderEntity>,
    rawMaterials: List<RawMaterialEntity>,
    warehouses: List<WarehouseEntity>,
    expenses: List<ExpenseEntity>,
    cashAccounts: List<CashAccountEntity>,
    accounting: AccountingStatements?,
    journalEntries: List<JournalEntryEntity>,
    dailyClosing: DailyClosingPreview?,
    onSubmitPaymentCollection: (String, String, Double, String, String, String, String) -> Unit,
    onReviewPayment: (String, Boolean, String) -> Unit,
    onSelectCustomerLedger: (String?) -> Unit,
    onCreateSupplier: (String, String, String, String, Double) -> Unit,
    onCreatePurchase: (String, String, String, Double, Double, Double, Boolean) -> Unit,
    onReceivePurchase: (String) -> Unit,
    onSubmitExpense: (String, Double, String, String, String) -> Unit,
    onReviewExpense: (String, Boolean) -> Unit,
    onCashTx: (String, String?, Double, Boolean, String) -> Unit,
    onExecuteDailyClosing: (String) -> Unit,
    onExportReport: (ReportType) -> Unit,
    initialSubTab: String? = null,
    onClearInitialSubTab: () -> Unit = {}
) {
    // Build only tabs permitted for the logged-in user's role
    val tabs = buildList {
        if (session.has(AppPermission.SUBMIT_PAYMENT_COLLECTION) || session.has(AppPermission.APPROVE_PAYMENTS)) {
            add("Payments")
        }
        if (session.has(AppPermission.VIEW_SUPPLIERS) || session.has(AppPermission.MANAGE_PURCHASES)) {
            add("Suppliers & PO")
        }
        if (session.has(AppPermission.SUBMIT_EXPENSE) || session.has(AppPermission.VIEW_EXPENSE_TOTALS) || session.has(AppPermission.APPROVE_EXPENSES)) {
            add("Expenses")
        }
        if (session.has(AppPermission.VIEW_CASH_BANK)) {
            add("Cash & Bank")
        }
        if (session.has(AppPermission.VIEW_CUSTOMER_LEDGER)) {
            add("Ledgers")
        }
        if (session.has(AppPermission.VIEW_ACCOUNTING) || session.has(AppPermission.VIEW_PROFIT_LOSS)) {
            add("Accounting")
        }
        if (session.has(AppPermission.DAILY_CLOSING)) {
            add("Closing")
        }
    }

    if (tabs.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            RestrictedBanner(strings.restrictedNotice)
        }
        return
    }

    var selectedIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(initialSubTab, tabs) {
        if (!initialSubTab.isNullOrBlank()) {
            val idx = tabs.indexOf(initialSubTab)
            if (idx >= 0) selectedIdx = idx
            onClearInitialSubTab()
        }
    }
    val activeTab = tabs.getOrElse(selectedIdx) { tabs.first() }

    var showPaySubmitDialog by remember { mutableStateOf(false) }
    var showSupplierDialog by remember { mutableStateOf(false) }
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showCashTxDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = selectedIdx.coerceAtMost(tabs.lastIndex)) {
            tabs.forEachIndexed { idx, title ->
                Tab(selected = selectedIdx == idx, onClick = { selectedIdx = idx }, text = { Text(title) })
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeTab) {
                "Payments" -> {
                    item {
                        if (!session.has(AppPermission.APPROVE_PAYMENTS)) {
                            RestrictedBanner(
                                "Payment collections stay PENDING until Admin approval. Customer dues and ledger balances are hidden.",
                                modifier = Modifier.testTag("employee_payment_security_banner")
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        if (session.has(AppPermission.SUBMIT_PAYMENT_COLLECTION) || session.has(AppPermission.APPROVE_PAYMENTS)) {
                            Button(
                                onClick = { showPaySubmitDialog = true },
                                enabled = customers.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().testTag("btn_submit_payment_collection")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Submit Customer Payment Collection")
                            }
                        }
                    }
                    if (paymentSubmissions.isEmpty()) {
                        item { EmptyStateNotice(strings.noDataYet) }
                    } else {
                        items(paymentSubmissions, key = { it.id }) { sub ->
                            val custName = customers.firstOrNull { it.id == sub.customerId }?.name ?: "Customer"
                            SectionCard(title = "$custName • ${Formatters.formatTaka(sub.amount)}") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${sub.paymentMethod} • By ${sub.submittedByName}", style = MaterialTheme.typography.bodySmall)
                                    StatusChip(sub.status)
                                }
                                if (sub.transactionRef.isNotBlank()) {
                                    Text("Ref: ${sub.transactionRef}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (sub.notes.isNotBlank()) {
                                    Text(sub.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (sub.status == "PENDING" && session.has(AppPermission.APPROVE_PAYMENTS)) {
                                    Spacer(Modifier.height(10.dp))
                                    val isSelfSubmission = sub.submittedByUserId == session.id && !session.isFullAdmin
                                    if (isSelfSubmission) {
                                        RestrictedBanner("Self-approval disabled: another Admin must approve your submission.")
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { onReviewPayment(sub.id, true, "Approved") }, modifier = Modifier.weight(1f).testTag("btn_approve_payment")) {
                                                Icon(Icons.Filled.Check, contentDescription = null)
                                                Spacer(Modifier.width(4.dp))
                                                Text(strings.approve)
                                            }
                                            OutlinedButton(onClick = { onReviewPayment(sub.id, false, "Rejected by Admin") }, modifier = Modifier.weight(1f).testTag("btn_reject_payment")) {
                                                Icon(Icons.Filled.Close, contentDescription = null)
                                                Spacer(Modifier.width(4.dp))
                                                Text(strings.reject)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Suppliers & PO" -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (session.has(AppPermission.MANAGE_PURCHASES)) {
                                Button(onClick = { showSupplierDialog = true }, modifier = Modifier.weight(1f)) { Text("+ Supplier") }
                                FilledTonalButton(
                                    onClick = { showPurchaseDialog = true },
                                    enabled = suppliers.isNotEmpty() && rawMaterials.isNotEmpty() && warehouses.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("+ Purchase") }
                            }
                        }
                    }
                    items(suppliers, key = { it.id }) { sup ->
                        SectionCard(title = "${sup.name} (${sup.companyName.ifBlank { "Supplier" }})") {
                            Text("Phone: ${sup.phone}", style = MaterialTheme.typography.bodySmall)
                            if (session.has(AppPermission.VIEW_SUPPLIER_LEDGER)) {
                                Text("Payable: ${Formatters.formatTaka(sup.currentPayable)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = ErrorRed)
                            }
                        }
                    }
                    items(purchases, key = { it.id }) { po ->
                        val supName = suppliers.firstOrNull { it.id == po.supplierId }?.name ?: "Supplier"
                        val matName = rawMaterials.firstOrNull { it.id == po.rawMaterialId }?.name ?: "Material"
                        SectionCard(title = "${po.poNumber} • $matName") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("$supName • Qty: ${Formatters.formatQty(po.quantity)}", style = MaterialTheme.typography.bodySmall)
                                StatusChip(po.status)
                            }
                            if (session.canViewFinancials) {
                                Text("Unit: ${Formatters.formatTaka(po.unitPrice)} | Total: ${Formatters.formatTaka(po.totalAmount)} | Payable: ${Formatters.formatTaka(po.payableAmount)}", style = MaterialTheme.typography.bodySmall)
                            } else if (session.has(AppPermission.VIEW_PURCHASE_PRICES)) {
                                Text("Unit: ${Formatters.formatTaka(po.unitPrice)} | Total: ${Formatters.formatTaka(po.totalAmount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            if (po.status != "RECEIVED" && session.has(AppPermission.MANAGE_PURCHASES)) {
                                Spacer(Modifier.height(6.dp))
                                FilledTonalButton(onClick = { onReceivePurchase(po.id) }) { Text("Confirm Receipt into Stock") }
                            }
                        }
                    }
                }

                "Expenses" -> {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            if (session.has(AppPermission.VIEW_EXPENSE_TOTALS)) {
                                val approvedTotal = expenses.filter { it.status == "APPROVED" }.sumOf { it.amount }
                                Text("Approved Total: ${Formatters.formatTaka(approvedTotal)}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            } else {
                                Text("My Expense Requests", style = MaterialTheme.typography.titleSmall)
                            }
                            if (session.has(AppPermission.SUBMIT_EXPENSE) || session.has(AppPermission.APPROVE_EXPENSES)) {
                                Button(onClick = { showExpenseDialog = true }) { Text("+ Expense") }
                            }
                        }
                    }
                    items(expenses, key = { it.id }) { exp ->
                        SectionCard(title = "${exp.category} • ${Formatters.formatTaka(exp.amount)}") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${exp.submittedByName} • ${Formatters.formatDate(exp.expenseDate)}", style = MaterialTheme.typography.bodySmall)
                                StatusChip(exp.status)
                            }
                            Text(exp.description, style = MaterialTheme.typography.bodySmall)
                            if (exp.status == "PENDING" && session.has(AppPermission.APPROVE_EXPENSES)) {
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onReviewExpense(exp.id, true) }, modifier = Modifier.weight(1f)) { Text(strings.approve) }
                                    OutlinedButton(onClick = { onReviewExpense(exp.id, false) }, modifier = Modifier.weight(1f)) { Text(strings.reject) }
                                }
                            }
                        }
                    }
                }

                "Cash & Bank" -> {
                    item {
                        FinanceBannerCard(
                            title = strings.cashAndBank,
                            subtitle = "Total Liquidity: ${Formatters.formatTaka(cashAccounts.sumOf { it.currentBalance })}"
                        )
                    }
                    item {
                        if (session.has(AppPermission.MANAGE_CASH_BANK)) {
                            Button(onClick = { showCashTxDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Cash In / Out / Account Transfer")
                            }
                        }
                    }
                    items(cashAccounts, key = { it.id }) { acct ->
                        SectionCard(title = "${acct.name} (${acct.accountType})") {
                            Text(Formatters.formatTaka(acct.currentBalance), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                "Ledgers" -> {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Select Customer for Financial Statement", style = MaterialTheme.typography.titleSmall)
                            OutlinedButton(onClick = { onExportReport(ReportType.FINANCIAL_CUSTOMER_DUES) }) { Text("Export Dues") }
                        }
                    }
                    items(customers, key = { it.id }) { cust ->
                        SectionCard(title = cust.name, actionLabel = "View Ledger", onAction = { onSelectCustomerLedger(cust.id) }) {
                            Text("Outstanding Due: ${Formatters.formatTaka(cust.currentDue ?: 0.0)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    if (selectedCustomerLedger.isNotEmpty()) {
                        item {
                            SectionCard(title = "Customer Financial Ledger Entries") {
                                selectedCustomerLedger.forEach { entry ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${entry.entryType} (${entry.referenceDoc})", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                            Text(Formatters.formatDateTime(entry.createdAt), style = MaterialTheme.typography.bodySmall)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            if (entry.debitAmount > 0) Text("+Dr ${Formatters.formatTaka(entry.debitAmount)}", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                                            if (entry.creditAmount > 0) Text("-Cr ${Formatters.formatTaka(entry.creditAmount)}", color = SuccessGreen, style = MaterialTheme.typography.bodySmall)
                                            Text("Bal: ${Formatters.formatTaka(entry.balanceAfter)}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                "Accounting" -> {
                    if (accounting != null) {
                        item {
                            FinanceBannerCard(
                                title = strings.accountingAndReports,
                                subtitle = "Net Profit: ${Formatters.formatTaka(accounting.profitAndLoss.netProfit)}"
                            )
                        }
                        item {
                            val pl = accounting.profitAndLoss
                            SectionCard(
                                title = "Profit & Loss Statement",
                                actionLabel = strings.exportCsv,
                                onAction = { onExportReport(ReportType.FINANCIAL_PROFIT_LOSS) }
                            ) {
                                StatementRow("Sales Revenue", pl.salesRevenue)
                                StatementRow("Sales Returns", -pl.salesReturns)
                                StatementRow("Net Revenue", pl.netRevenue, bold = true)
                                StatementRow("Cost of Goods Sold", -pl.costOfGoodsSold)
                                StatementRow("Gross Profit", pl.grossProfit, bold = true)
                                StatementRow("Operating Expenses", -pl.operatingExpenses)
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                StatementRow("Net Profit", pl.netProfit, bold = true)
                            }
                        }
                        item {
                            val bs = accounting.balanceSheet
                            SectionCard(title = "Balance Sheet") {
                                StatementRow("Cash & Bank Assets", bs.cashAndBankAssets)
                                StatementRow("Accounts Receivable", bs.accountsReceivable)
                                StatementRow("Inventory Assets", bs.inventoryAssets)
                                StatementRow("Total Assets", bs.totalAssets, bold = true)
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                StatementRow("Accounts Payable", bs.accountsPayable)
                                StatementRow("Owner Equity & Retained Earnings", bs.ownerEquityAndRetainedEarnings, bold = true)
                            }
                        }
                        item {
                            SectionCard(title = "Trial Balance (${accounting.trialBalance.size} active accounts)") {
                                accounting.trialBalance.forEach { row ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${row.accountCode} ${row.accountName}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        Text("Dr ${Formatters.formatTaka(row.totalDebit)} | Cr ${Formatters.formatTaka(row.totalCredit)}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        item {
                            SectionCard(title = "Double-Entry Journal (${journalEntries.size})") {
                                journalEntries.take(15).forEach { je ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${je.entryNumber} • ${je.referenceDoc}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                            Text(je.description, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text("Dr/Cr ${Formatters.formatTaka(je.totalDebit)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                "Closing" -> {
                    if (dailyClosing != null) {
                        item {
                            var closingNotes by remember { mutableStateOf("") }
                            SectionCard(title = "Today's Daily Closing Summary") {
                                StatementRow("Opening Cash", dailyClosing.openingCash)
                                StatementRow("Cash Sales", dailyClosing.cashSales)
                                StatementRow("Cash Collections", dailyClosing.cashCollections)
                                StatementRow("Bank & Mobile Collections", dailyClosing.bankCollections)
                                StatementRow("Approved Expenses", -dailyClosing.totalExpenses)
                                StatementRow("Supplier Payments", -dailyClosing.supplierPayments)
                                StatementRow("Cash Withdrawals", -dailyClosing.cashWithdrawals)
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                StatementRow("Closing Cash", dailyClosing.closingCash, bold = true)
                                StatementRow("Bank & Mobile Balance", dailyClosing.bankBalance, bold = true)
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(value = closingNotes, onValueChange = { closingNotes = it }, label = { Text("Closing Notes") }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { onExecuteDailyClosing(closingNotes); closingNotes = "" }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Close Business Day")
                                }
                            }
                        }
                        items(dailyClosing.previousClosings, key = { it.id }) { prev ->
                            SectionCard(title = "Closed ${Formatters.formatDateTime(prev.closingDate)}") {
                                Text("Closing Cash: ${Formatters.formatTaka(prev.closingCash)} | Bank: ${Formatters.formatTaka(prev.bankBalance)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPaySubmitDialog && customers.isNotEmpty()) {
        PaymentCollectionDialog(customers, onDismiss = { showPaySubmitDialog = false }) { custId, amt, method, ref, proof, notes ->
            onSubmitPaymentCollection(custId, "", amt, method, ref, proof, notes)
            showPaySubmitDialog = false
        }
    }

    if (showSupplierDialog) {
        var name by remember { mutableStateOf("") }
        var company by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var opening by remember { mutableStateOf("0") }
        AlertDialog(
            onDismissRequest = { showSupplierDialog = false },
            title = { Text("New Supplier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Supplier Name") })
                    OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("Company Name") })
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") })
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") })
                    if (session.canViewFinancials) {
                        OutlinedTextField(value = opening, onValueChange = { opening = it }, label = { Text("Opening Payable (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                }
            },
            confirmButton = { Button(onClick = { onCreateSupplier(name, company, phone, address, opening.toDoubleOrNull() ?: 0.0); showSupplierDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showSupplierDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPurchaseDialog && suppliers.isNotEmpty() && rawMaterials.isNotEmpty() && warehouses.isNotEmpty()) {
        PurchaseDialog(session, suppliers, rawMaterials, warehouses, onDismiss = { showPurchaseDialog = false }) { sId, mId, wId, qty, price, paid, recNow ->
            onCreatePurchase(sId, mId, wId, qty, price, paid, recNow)
            showPurchaseDialog = false
        }
    }

    if (showExpenseDialog) {
        var cat by remember { mutableStateOf("Transport") }
        var amt by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showExpenseDialog = false },
            title = { Text("Submit Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Category (Transport, Labor, Rent...)") })
                    OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                }
            },
            confirmButton = { Button(onClick = { onSubmitExpense(cat, amt.toDoubleOrNull() ?: 0.0, "acct_cash", desc, ""); showExpenseDialog = false }) { Text("Submit") } },
            dismissButton = { TextButton(onClick = { showExpenseDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCashTxDialog && cashAccounts.isNotEmpty()) {
        CashAccountTxDialog(cashAccounts, onDismiss = { showCashTxDialog = false }) { fromId, toId, amt, isIn, notes ->
            onCashTx(fromId, toId, amt, isIn, notes)
            showCashTxDialog = false
        }
    }
}

@Composable
private fun StatementRow(label: String, amount: Double, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (bold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium)
        Text(Formatters.formatTaka(amount), style = if (bold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentCollectionDialog(
    customers: List<CustomerSanitizedView>,
    onDismiss: () -> Unit,
    onSubmit: (String, Double, String, String, String, String) -> Unit
) {
    var customer by remember { mutableStateOf(customers.first()) }
    var cExp by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("CASH") }
    var ref by remember { mutableStateOf("") }
    var proofUri by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) proofUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Collect Customer Payment") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = cExp, onExpandedChange = { cExp = !cExp }) {
                    OutlinedTextField(value = customer.name, onValueChange = {}, readOnly = true, label = { Text("Customer") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = cExp, onDismissRequest = { cExp = false }) {
                        customers.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { customer = c; cExp = false }) }
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Collected Amount (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("payment_amount_input"))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("CASH", "BKASH", "NAGAD", "BANK").forEach { m ->
                        FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) })
                    }
                }
                OutlinedTextField(value = ref, onValueChange = { ref = it }, label = { Text("Transaction Ref / TrxID") })
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Outlined.Receipt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (proofUri.isBlank()) "Attach Proof / Receipt" else "Proof Attached")
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Note") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(customer.id, amount.toDoubleOrNull() ?: 0.0, method, ref, proofUri, notes) },
                modifier = Modifier.testTag("confirm_submit_payment_button")
            ) { Text("Submit for Approval") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseDialog(
    session: SessionUser,
    suppliers: List<SupplierEntity>,
    materials: List<RawMaterialEntity>,
    warehouses: List<WarehouseEntity>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, Double, Double, Double, Boolean) -> Unit
) {
    var sup by remember { mutableStateOf(suppliers.first()) }
    var mat by remember { mutableStateOf(materials.first()) }
    var sExp by remember { mutableStateOf(false) }
    var mExp by remember { mutableStateOf(false) }
    var qty by remember { mutableStateOf("10") }
    var price by remember { mutableStateOf(mat.purchasePrice.toString()) }
    var paid by remember { mutableStateOf("0") }
    var recNow by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Raw Material Purchase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = sExp, onExpandedChange = { sExp = !sExp }) {
                    OutlinedTextField(value = sup.name, onValueChange = {}, readOnly = true, label = { Text("Supplier") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = sExp, onDismissRequest = { sExp = false }) { suppliers.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { sup = s; sExp = false }) } }
                }
                ExposedDropdownMenuBox(expanded = mExp, onExpandedChange = { mExp = !mExp }) {
                    OutlinedTextField(value = mat.name, onValueChange = {}, readOnly = true, label = { Text("Raw Material") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = mExp, onDismissRequest = { mExp = false }) { materials.forEach { m -> DropdownMenuItem(text = { Text("${m.name} (${m.unit})") }, onClick = { mat = m; price = m.purchasePrice.toString(); mExp = false }) } }
                }
                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (session.has(AppPermission.VIEW_PURCHASE_PRICES) || session.canViewFinancials) {
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Unit Price (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                if (session.canViewFinancials) {
                    OutlinedTextField(value = paid, onValueChange = { paid = it }, label = { Text("Paid Amount (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = recNow, onCheckedChange = { recNow = it })
                    Text("Receive immediately into stock")
                }
            }
        },
        confirmButton = { Button(onClick = { onSubmit(sup.id, mat.id, warehouses.first().id, qty.toDoubleOrNull() ?: 0.0, price.toDoubleOrNull() ?: 0.0, paid.toDoubleOrNull() ?: 0.0, recNow) }) { Text("Save PO") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashAccountTxDialog(accounts: List<CashAccountEntity>, onDismiss: () -> Unit, onSubmit: (String, String?, Double, Boolean, String) -> Unit) {
    var mode by remember { mutableStateOf("CASH_IN") } // CASH_IN, CASH_OUT, TRANSFER
    var fromAcct by remember { mutableStateOf(accounts.first()) }
    var toAcct by remember { mutableStateOf(accounts.last()) }
    var fExp by remember { mutableStateOf(false) }
    var tExp by remember { mutableStateOf(false) }
    var amt by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Account Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASH_IN", "CASH_OUT", "TRANSFER").forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) })
                    }
                }
                ExposedDropdownMenuBox(expanded = fExp, onExpandedChange = { fExp = !fExp }) {
                    OutlinedTextField(value = fromAcct.name, onValueChange = {}, readOnly = true, label = { Text(if (mode == "TRANSFER") "From Account" else "Account") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(fExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = fExp, onDismissRequest = { fExp = false }) { accounts.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { fromAcct = a; fExp = false }) } }
                }
                if (mode == "TRANSFER") {
                    ExposedDropdownMenuBox(expanded = tExp, onExpandedChange = { tExp = !tExp }) {
                        OutlinedTextField(value = toAcct.name, onValueChange = {}, readOnly = true, label = { Text("To Account") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tExp) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = tExp, onDismissRequest = { tExp = false }) { accounts.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { toAcct = a; tExp = false }) } }
                    }
                }
                OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount (৳)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = { Button(onClick = { onSubmit(fromAcct.id, if (mode == "TRANSFER") toAcct.id else null, amt.toDoubleOrNull() ?: 0.0, mode == "CASH_IN", notes) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FinanceBannerCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .background(Brush.horizontalGradient(listOf(HeroDeepStart, HeroTealMid)))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_finance_analytics_banner_1795378901702),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.32f)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }
    }
}
