package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AccountTransactionEntity
import com.example.data.local.AppSettingEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.BomItemEntity
import com.example.data.local.CashAccountEntity
import com.example.data.local.CustomerLedgerEntity
import com.example.data.local.EmployeeTaskEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.InventoryBalanceEntity
import com.example.data.local.JournalEntryEntity
import com.example.data.local.MaterialLedgerEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PaymentSubmissionEntity
import com.example.data.local.ProductEntity
import com.example.data.local.ProductModelEntity
import com.example.data.local.ProductionBatchEntity
import com.example.data.local.PurchaseOrderEntity
import com.example.data.local.RawMaterialEntity
import com.example.data.local.SalesReturnEntity
import com.example.data.local.SessionPreferences
import com.example.data.local.StockMovementEntity
import com.example.data.local.SupplierEntity
import com.example.data.local.SupplierLedgerEntity
import com.example.data.local.UserEntity
import com.example.data.local.WarehouseEntity
import com.example.data.repository.AccountingStatements
import com.example.data.repository.AuthAndGovernanceRepository
import com.example.data.repository.CustomerSanitizedView
import com.example.data.repository.DailyClosingPreview
import com.example.data.repository.FinanceRepository
import com.example.data.repository.OperationsRepository
import com.example.data.repository.ReportType
import com.example.data.repository.RoleDashboardSnapshot
import com.example.data.repository.SaleItemRequest
import com.example.data.repository.SaleSanitizedView
import com.example.domain.AppLanguage
import com.example.domain.AppPermission
import com.example.domain.AppStrings
import com.example.domain.SessionUser
import com.example.domain.Translations
import com.example.domain.UserRole
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object NeedsInitialSuperAdmin : AuthUiState
    data object Unauthenticated : AuthUiState
    data class Authenticated(val user: SessionUser) : AuthUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ErpViewModel(
    private val authRepo: AuthAndGovernanceRepository,
    private val opsRepo: OperationsRepository,
    private val finRepo: FinanceRepository,
    private val prefs: SessionPreferences
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _exportedCsv = MutableStateFlow<Pair<String, String>?>(null)
    val exportedCsv: StateFlow<Pair<String, String>?> = _exportedCsv.asStateFlow()

    private val _selectedCustomerId = MutableStateFlow<String?>(null)
    private val _selectedSupplierId = MutableStateFlow<String?>(null)

    val language: StateFlow<AppLanguage> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppLanguage.EN)

    val themeMode: StateFlow<ThemeMode> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ThemeMode.SYSTEM)

    val strings: StateFlow<AppStrings> = language
        .map { Translations.forLanguage(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), Translations.english)

    private val currentSessionFlow: Flow<SessionUser?> = authState.map {
        (it as? AuthUiState.Authenticated)?.user
    }

    init {
        bootstrapSession()
    }

    private fun bootstrapSession() {
        viewModelScope.launch {
            authRepo.ensureStructuralDefaults()
            val hasUsers = authRepo.hasAnyUsers()
            if (!hasUsers) {
                _authState.value = AuthUiState.NeedsInitialSuperAdmin
                return@launch
            }
            val savedUid = prefs.loggedInUserId.first()
            val user = savedUid?.let { authRepo.loadSessionUser(it) }
            _authState.value = if (user != null) AuthUiState.Authenticated(user) else AuthUiState.Unauthenticated
        }
    }

    private fun <T> sessionFlow(defaultValue: T, provider: (SessionUser) -> Flow<T>): StateFlow<T> =
        currentSessionFlow.flatMapLatest { session ->
            if (session == null) flowOf(defaultValue)
            else provider(session).catch { emit(defaultValue) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), defaultValue)

    val dashboardSnapshot: StateFlow<RoleDashboardSnapshot?> =
        sessionFlow(null) { finRepo.observeRoleDashboard(it) }

    val settings: StateFlow<AppSettingEntity?> =
        sessionFlow(null) { authRepo.observeSettings() }

    val notifications: StateFlow<List<NotificationEntity>> =
        sessionFlow(emptyList()) { authRepo.observeNotifications(it) }

    val products: StateFlow<List<ProductEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.VIEW_PRODUCTS) || s.has(AppPermission.VIEW_PRODUCTION)) opsRepo.observeProducts()
            else flowOf(emptyList())
        }

    val models: StateFlow<List<ProductModelEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.VIEW_PRODUCTS) || s.has(AppPermission.VIEW_PRODUCTION) || s.has(AppPermission.CREATE_SALES) || s.has(AppPermission.VIEW_INVENTORY)) {
                opsRepo.observeModelsSanitized(s)
            } else flowOf(emptyList())
        }

    val bomItems: StateFlow<List<BomItemEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.canViewCosting) opsRepo.observeBomItems(s) else flowOf(emptyList())
        }

    val warehouses: StateFlow<List<WarehouseEntity>> =
        sessionFlow(emptyList()) { opsRepo.observeWarehouses() }

    val rawMaterials: StateFlow<List<RawMaterialEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.VIEW_RAW_MATERIALS) || s.isFullAdmin) opsRepo.observeRawMaterialsSanitized(s) else flowOf(emptyList())
        }

    val materialLedger: StateFlow<List<MaterialLedgerEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.isFullAdmin) opsRepo.observeMaterialLedger(s) else flowOf(emptyList())
        }

    val productionBatches: StateFlow<List<ProductionBatchEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.VIEW_PRODUCTION)) opsRepo.observeProductionBatchesSanitized(s) else flowOf(emptyList())
        }

    val inventoryBalances: StateFlow<List<InventoryBalanceEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.VIEW_INVENTORY)) opsRepo.observeInventoryBalances(s) else flowOf(emptyList())
        }

    val stockMovements: StateFlow<List<StockMovementEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.VIEW_INVENTORY)) opsRepo.observeStockMovements(s) else flowOf(emptyList())
        }

    val customers: StateFlow<List<CustomerSanitizedView>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.VIEW_CUSTOMERS)) opsRepo.observeCustomersSanitized(s) else flowOf(emptyList())
        }

    val sales: StateFlow<List<SaleSanitizedView>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.CREATE_SALES) || s.canViewFinancials) opsRepo.observeSalesSanitized(s) else flowOf(emptyList())
        }

    val salesReturns: StateFlow<List<SalesReturnEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.MANAGE_RETURNS) || s.isFullAdmin) opsRepo.observeSalesReturns(s) else flowOf(emptyList())
        }

    val tasks: StateFlow<List<EmployeeTaskEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.isFullAdmin || s.has(AppPermission.MANAGE_TASKS)) opsRepo.observeTasks(s) else flowOf(emptyList())
        }

    val paymentSubmissions: StateFlow<List<PaymentSubmissionEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.APPROVE_PAYMENTS) || s.has(AppPermission.SUBMIT_PAYMENT_COLLECTION)) {
                finRepo.observePaymentSubmissions(s)
            } else flowOf(emptyList())
        }

    val suppliers: StateFlow<List<SupplierEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.VIEW_SUPPLIERS)) finRepo.observeSuppliers(s) else flowOf(emptyList())
        }

    val purchaseOrders: StateFlow<List<PurchaseOrderEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.VIEW_SUPPLIERS) || s.has(AppPermission.MANAGE_PURCHASES)) finRepo.observePurchaseOrders(s) else flowOf(emptyList())
        }

    val expenses: StateFlow<List<ExpenseEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.SUBMIT_EXPENSE) || s.has(AppPermission.VIEW_EXPENSE_TOTALS) || s.has(AppPermission.APPROVE_EXPENSES)) {
                finRepo.observeExpenses(s)
            } else flowOf(emptyList())
        }

    val cashAccounts: StateFlow<List<CashAccountEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.VIEW_CASH_BANK)) finRepo.observeCashAccounts(s) else flowOf(emptyList())
        }

    val accountTransactions: StateFlow<List<AccountTransactionEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.VIEW_CASH_BANK)) finRepo.observeAccountTransactions(s) else flowOf(emptyList())
        }

    val accountingStatements: StateFlow<AccountingStatements?> =
        sessionFlow(null) { s ->
            if (s.has(AppPermission.VIEW_ACCOUNTING) || s.has(AppPermission.VIEW_PROFIT_LOSS)) {
                finRepo.observeAccountingStatements(s)
            } else flowOf(null)
        }

    val journalEntries: StateFlow<List<JournalEntryEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.has(AppPermission.VIEW_ACCOUNTING)) finRepo.observeJournalEntries(s) else flowOf(emptyList())
        }

    val dailyClosingPreview: StateFlow<DailyClosingPreview?> =
        sessionFlow(null) { s ->
            if (s.has(AppPermission.DAILY_CLOSING)) finRepo.observeDailyClosingPreview(s) else flowOf(null)
        }

    val managedUsers: StateFlow<List<UserEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.isFullAdmin || s.has(AppPermission.MANAGE_USERS) || s.has(AppPermission.MANAGE_TASKS)) authRepo.observeUsers(s) else flowOf(emptyList())
        }

    val auditLogs: StateFlow<List<AuditLogEntity>> =
        sessionFlow(emptyList()) { s ->
            if (s.isFullAdmin || s.has(AppPermission.VIEW_AUDIT_LOGS)) authRepo.observeAuditLogs(s) else flowOf(emptyList())
        }

    val selectedCustomerLedger: StateFlow<List<CustomerLedgerEntity>> =
        currentSessionFlow.flatMapLatest { session ->
            _selectedCustomerId.flatMapLatest { custId ->
                if (session != null && custId != null && session.has(AppPermission.VIEW_CUSTOMER_LEDGER)) {
                    finRepo.observeCustomerLedger(session, custId)
                } else flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val selectedSupplierLedger: StateFlow<List<SupplierLedgerEntity>> =
        currentSessionFlow.flatMapLatest { session ->
            _selectedSupplierId.flatMapLatest { supId ->
                if (session != null && supId != null && session.has(AppPermission.VIEW_SUPPLIER_LEDGER)) {
                    finRepo.observeSupplierLedger(session, supId)
                } else flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // --- Actions ---
    fun clearFeedback() { _feedback.value = null }
    fun clearAuthError() { _authError.value = null }
    fun clearExportedCsv() { _exportedCsv.value = null }
    fun selectCustomerForLedger(id: String?) { _selectedCustomerId.value = id }
    fun selectSupplierForLedger(id: String?) { _selectedSupplierId.value = id }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { prefs.setLanguage(lang) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun claimInitialSuperAdmin(businessName: String, fullName: String, email: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            authRepo.claimInitialSuperAdmin(businessName, fullName, email, password)
                .onSuccess { session ->
                    prefs.setLoggedInUserId(session.id)
                    _authState.value = AuthUiState.Authenticated(session)
                }
                .onFailure { _authError.value = it.message ?: "Setup failed" }
        }
    }

    fun login(identifier: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            authRepo.login(identifier, password)
                .onSuccess { session ->
                    prefs.setLoggedInUserId(session.id)
                    _authState.value = AuthUiState.Authenticated(session)
                }
                .onFailure { _authError.value = it.message ?: "Sign in failed" }
        }
    }

    fun logout() {
        val current = (authState.value as? AuthUiState.Authenticated)?.user
        viewModelScope.launch {
            if (current != null) authRepo.logout(current)
            prefs.setLoggedInUserId(null)
            _authState.value = AuthUiState.Unauthenticated
        }
    }

    private inline fun withSession(crossinline block: suspend (SessionUser) -> Result<Any?>, successMsg: String) {
        val session = (authState.value as? AuthUiState.Authenticated)?.user ?: return
        viewModelScope.launch {
            block(session)
                .onSuccess { _feedback.value = successMsg }
                .onFailure { _feedback.value = "Error: ${it.message ?: "Operation failed"}" }
        }
    }

    fun saveProduct(
        existingId: String?,
        name: String,
        category: String,
        description: String,
        unit: String,
        imageUri: String
    ) = withSession({
        opsRepo.saveProduct(it, name, category, description, unit, imageUri, existingId)
    }, if (existingId == null) "Product saved" else "Product updated")

    fun saveProductModel(
        existingId: String?, productId: String, modelName: String, modelCode: String,
        barcode: String, cost: Double, wholesale: Double, retail: Double, minStock: Double, imageUri: String
    ) = withSession({
        opsRepo.saveProductModel(it, existingId, productId, modelName, modelCode, barcode, cost, wholesale, retail, minStock, imageUri)
    }, "Model saved")

    fun saveBomItem(modelId: String, rawMaterialId: String, qtyPerUnit: Double) =
        withSession({ opsRepo.saveBomItem(it, modelId, rawMaterialId, qtyPerUnit) }, "BOM item added")

    fun deleteBomItem(bomId: String) =
        withSession({ opsRepo.deleteBomItem(it, bomId) }, "BOM item removed")

    fun saveRawMaterial(
        name: String, code: String, category: String, unit: String,
        price: Double, warehouseId: String, initialQty: Double, minStock: Double
    ) = withSession({
        opsRepo.saveRawMaterial(it, name, code, category, unit, price, warehouseId, initialQty, minStock)
    }, "Raw material saved")

    fun createWarehouse(name: String, location: String) =
        withSession({ opsRepo.createWarehouse(it, name, location) }, "Warehouse created")

    fun createProductionBatch(
        modelId: String, warehouseId: String, planned: Double, actual: Double, rejected: Double,
        wastage: Double, labor: Double, machine: Double, electricity: Double, other: Double, notes: String
    ) = withSession({
        opsRepo.createProductionBatch(it, modelId, warehouseId, planned, actual, rejected, wastage, labor, machine, electricity, other, notes)
    }, "Production batch submitted")

    fun editProductionBatch(
        batchId: String, modelId: String, warehouseId: String, planned: Double, actual: Double, rejected: Double,
        wastage: Double, labor: Double, machine: Double, electricity: Double, other: Double, notes: String
    ) = withSession({
        opsRepo.editProductionBatch(it, batchId, modelId, warehouseId, planned, actual, rejected, wastage, labor, machine, electricity, other, notes)
    }, "Production batch updated")

    fun advanceProductionBatch(batchId: String, nextStatus: String) =
        withSession({ opsRepo.advanceProductionBatch(it, batchId, nextStatus) }, "Production status updated to $nextStatus")

    fun transferStock(modelId: String, fromWh: String, toWh: String, qty: Double, notes: String) =
        withSession({ opsRepo.transferStock(it, modelId, fromWh, toWh, qty, notes) }, "Stock transferred")

    fun adjustStock(modelId: String, wh: String, delta: Double, reason: String) =
        withSession({ opsRepo.adjustStock(it, modelId, wh, delta, reason) }, "Stock adjusted")

    fun createCustomer(name: String, businessName: String, phone: String, address: String, type: String, opening: Double, limit: Double) =
        withSession({ opsRepo.createCustomer(it, name, businessName, phone, address, type, opening, limit) }, "Customer saved")

    fun createSale(
        saleType: String, customerId: String, warehouseId: String, items: List<SaleItemRequest>,
        discount: Double, charges: Double, paid: Double, method: String, deliverNow: Boolean, notes: String
    ) = withSession({
        opsRepo.createSale(it, saleType, customerId, warehouseId, items, discount, charges, paid, method, deliverNow, notes)
    }, "Sale order recorded")

    fun confirmDelivery(saleId: String, fraction: Double) =
        withSession({ opsRepo.confirmDelivery(it, saleId, fraction) }, "Delivery confirmed & inventory updated")

    fun processSalesReturn(saleId: String, modelId: String, qty: Double, classification: String, reason: String) =
        withSession({ opsRepo.processSalesReturn(it, saleId, modelId, qty, classification, reason) }, "Sales return processed")

    fun submitPaymentCollection(customerId: String, saleId: String, amount: Double, method: String, ref: String, proofUri: String, notes: String) =
        withSession({ finRepo.submitPaymentCollection(it, customerId, saleId, amount, method, ref, proofUri, notes) }, "Payment submitted (PENDING Admin approval)")

    fun reviewPaymentSubmission(submissionId: String, approve: Boolean, note: String) =
        withSession({ finRepo.reviewPaymentSubmission(it, submissionId, approve, note) }, if (approve) "Payment approved & posted to ledger" else "Payment rejected")

    fun createSupplier(name: String, company: String, phone: String, address: String, opening: Double) =
        withSession({ finRepo.createSupplier(it, name, company, phone, address, opening) }, "Supplier saved")

    fun createAndReceivePurchase(supplierId: String, materialId: String, warehouseId: String, qty: Double, price: Double, paid: Double, receiveNow: Boolean) =
        withSession({ finRepo.createAndReceivePurchase(it, supplierId, materialId, warehouseId, qty, price, paid, receiveNow) }, "Purchase recorded")

    fun receivePurchaseOrder(poId: String) =
        withSession({ finRepo.receivePurchaseOrder(it, poId) }, "Purchase received into raw material stock")

    fun submitExpense(category: String, amount: Double, accountId: String, description: String, receiptUri: String) =
        withSession({ finRepo.submitExpense(it, category, amount, accountId, description, receiptUri) }, "Expense recorded")

    fun reviewExpense(expenseId: String, approve: Boolean) =
        withSession({ finRepo.reviewExpense(it, expenseId, approve) }, if (approve) "Expense approved" else "Expense rejected")

    fun cashInOutOrTransfer(fromId: String, toId: String?, amount: Double, isCashIn: Boolean, notes: String) =
        withSession({ finRepo.recordCashInOutOrTransfer(it, fromId, toId, amount, isCashIn, notes) }, "Account transaction recorded")

    fun executeDailyClosing(notes: String) =
        withSession({ finRepo.executeDailyClosing(it, notes) }, "Daily business closing completed")

    fun createTask(title: String, desc: String, assigneeId: String) =
        withSession({ opsRepo.createTask(it, title, desc, assigneeId) }, "Task assigned")

    fun updateTaskStatus(taskId: String, status: String) =
        withSession({ opsRepo.updateTaskStatus(it, taskId, status) }, "Task updated")

    fun createUser(fullName: String, email: String, password: String, role: UserRole, perms: Set<AppPermission>?) =
        withSession({ authRepo.createUser(it, fullName, email, password, role, perms) }, "User account created")

    fun updateUserAccess(targetId: String, role: UserRole, perms: Set<AppPermission>, active: Boolean) =
        withSession({ authRepo.updateUserAccess(it, targetId, role, perms, active) }, "User permissions updated")

    fun updateSettings(newSettings: AppSettingEntity) =
        withSession({ authRepo.updateSettings(it, newSettings) }, "Settings saved")

    fun markNotificationRead(id: String) {
        viewModelScope.launch { authRepo.markNotificationRead(id) }
    }

    fun exportReport(type: ReportType) {
        val session = (authState.value as? AuthUiState.Authenticated)?.user ?: return
        viewModelScope.launch {
            finRepo.generateReportCsv(session, type)
                .onSuccess { _exportedCsv.value = it }
                .onFailure { _feedback.value = "Export blocked: ${it.message}" }
        }
    }
}
