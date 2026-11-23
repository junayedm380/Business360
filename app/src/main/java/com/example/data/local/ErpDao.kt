package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ErpDao {
    // --- Users & Security ---
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:identifier) LIMIT 1")
    suspend fun findUserByEmail(identifier: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun observeUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: String)

    @Query("SELECT COUNT(*) FROM users WHERE role = 'SUPER_ADMIN' AND active = 1")
    suspend fun countActiveSuperAdmins(): Int

    // --- Audit Logs & Settings & Notifications ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT 200")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM app_settings WHERE id = 'default' LIMIT 1")
    fun observeSettings(): Flow<AppSettingEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 'default' LIMIT 1")
    suspend fun getSettings(): AppSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(setting: AppSettingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY createdAt DESC LIMIT 100")
    fun observeAllNotifications(): Flow<List<NotificationEntity>>

    @Query("UPDATE notifications SET read = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: String)

    // --- Products, Models, BOM, Price History ---
    @Query("SELECT * FROM products ORDER BY createdAt DESC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity)

    @Query("SELECT * FROM product_models ORDER BY createdAt DESC")
    fun observeModels(): Flow<List<ProductModelEntity>>

    @Query("SELECT * FROM product_models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): ProductModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModel(model: ProductModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(history: PriceHistoryEntity)

    @Query("SELECT * FROM price_history WHERE modelId = :modelId ORDER BY changedAt DESC")
    fun observePriceHistory(modelId: String): Flow<List<PriceHistoryEntity>>

    @Query("SELECT * FROM bom_items")
    fun observeAllBomItems(): Flow<List<BomItemEntity>>

    @Query("SELECT * FROM bom_items WHERE modelId = :modelId")
    suspend fun getBomForModel(modelId: String): List<BomItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBomItem(item: BomItemEntity)

    @Query("DELETE FROM bom_items WHERE id = :id")
    suspend fun deleteBomItem(id: String)

    // --- Warehouses & Raw Materials ---
    @Query("SELECT * FROM warehouses ORDER BY defaultWarehouse DESC, name ASC")
    fun observeWarehouses(): Flow<List<WarehouseEntity>>

    @Query("SELECT COUNT(*) FROM warehouses")
    suspend fun getWarehouseCount(): Int

    @Query("SELECT * FROM warehouses WHERE defaultWarehouse = 1 LIMIT 1")
    suspend fun getDefaultWarehouse(): WarehouseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWarehouse(warehouse: WarehouseEntity)

    @Query("SELECT * FROM raw_materials ORDER BY name ASC")
    fun observeRawMaterials(): Flow<List<RawMaterialEntity>>

    @Query("SELECT * FROM raw_materials WHERE id = :id LIMIT 1")
    suspend fun getRawMaterialById(id: String): RawMaterialEntity?

    @Query("SELECT * FROM raw_materials WHERE LOWER(materialCode) = LOWER(:code) LIMIT 1")
    suspend fun getRawMaterialByCode(code: String): RawMaterialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRawMaterial(material: RawMaterialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterialLedger(entry: MaterialLedgerEntity)

    @Query("SELECT * FROM material_ledger ORDER BY createdAt DESC LIMIT 150")
    fun observeMaterialLedger(): Flow<List<MaterialLedgerEntity>>

    // --- Production ---
    @Query("SELECT * FROM production_batches ORDER BY createdAt DESC")
    fun observeProductionBatches(): Flow<List<ProductionBatchEntity>>

    @Query("SELECT * FROM production_batches WHERE id = :id LIMIT 1")
    suspend fun getProductionBatchById(id: String): ProductionBatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProductionBatch(batch: ProductionBatchEntity)

    // --- Inventory ---
    @Query("SELECT * FROM inventory_balances")
    fun observeInventoryBalances(): Flow<List<InventoryBalanceEntity>>

    @Query("SELECT * FROM inventory_balances WHERE modelId = :modelId AND warehouseId = :warehouseId LIMIT 1")
    suspend fun getInventoryBalance(modelId: String, warehouseId: String): InventoryBalanceEntity?

    @Query("SELECT * FROM inventory_balances WHERE modelId = :modelId")
    suspend fun getInventoryBalancesForModel(modelId: String): List<InventoryBalanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInventoryBalance(balance: InventoryBalanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovement(movement: StockMovementEntity)

    @Query("SELECT * FROM stock_movements ORDER BY createdAt DESC LIMIT 200")
    fun observeStockMovements(): Flow<List<StockMovementEntity>>

    // --- Customers & Ledger ---
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun observeCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomer(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerLedger(entry: CustomerLedgerEntity)

    @Query("SELECT * FROM customer_ledger WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observeCustomerLedger(customerId: String): Flow<List<CustomerLedgerEntity>>

    @Query("SELECT * FROM customer_ledger ORDER BY createdAt DESC LIMIT 200")
    fun observeAllCustomerLedgers(): Flow<List<CustomerLedgerEntity>>

    // --- Sales & Returns ---
    @Query("SELECT * FROM sales ORDER BY saleDate DESC")
    fun observeSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getSaleById(id: String): SaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSale(sale: SaleEntity)

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItems(saleId: String): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items")
    fun observeAllSaleItems(): Flow<List<SaleItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSaleItems(items: List<SaleItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalesReturn(ret: SalesReturnEntity)

    @Query("SELECT * FROM sales_returns ORDER BY createdAt DESC")
    fun observeSalesReturns(): Flow<List<SalesReturnEntity>>

    // --- Payments ---
    @Query("SELECT * FROM payment_submissions ORDER BY createdAt DESC")
    fun observePaymentSubmissions(): Flow<List<PaymentSubmissionEntity>>

    @Query("SELECT * FROM payment_submissions WHERE submittedByUserId = :uid ORDER BY createdAt DESC")
    fun observeMyPaymentSubmissions(uid: String): Flow<List<PaymentSubmissionEntity>>

    @Query("SELECT * FROM payment_submissions WHERE id = :id LIMIT 1")
    suspend fun getPaymentSubmissionById(id: String): PaymentSubmissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPaymentSubmission(submission: PaymentSubmissionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity)

    @Query("SELECT * FROM payments ORDER BY createdAt DESC")
    fun observePayments(): Flow<List<PaymentEntity>>

    // --- Suppliers & Purchases ---
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun observeSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun getSupplierById(id: String): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSupplier(supplier: SupplierEntity)

    @Query("SELECT * FROM purchase_orders ORDER BY createdAt DESC")
    fun observePurchaseOrders(): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_orders WHERE id = :id LIMIT 1")
    suspend fun getPurchaseOrderById(id: String): PurchaseOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPurchaseOrder(po: PurchaseOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplierLedger(entry: SupplierLedgerEntity)

    @Query("SELECT * FROM supplier_ledger WHERE supplierId = :supplierId ORDER BY createdAt DESC")
    fun observeSupplierLedger(supplierId: String): Flow<List<SupplierLedgerEntity>>

    // --- Cash Accounts, Expenses, Accounting, Daily Closing ---
    @Query("SELECT * FROM cash_accounts ORDER BY name ASC")
    fun observeCashAccounts(): Flow<List<CashAccountEntity>>

    @Query("SELECT * FROM cash_accounts WHERE id = :id LIMIT 1")
    suspend fun getCashAccountById(id: String): CashAccountEntity?

    @Query("SELECT COUNT(*) FROM cash_accounts")
    suspend fun getCashAccountCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCashAccount(account: CashAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountTransaction(tx: AccountTransactionEntity)

    @Query("SELECT * FROM account_transactions ORDER BY createdAt DESC LIMIT 200")
    fun observeAccountTransactions(): Flow<List<AccountTransactionEntity>>

    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE submittedByUserId = :uid ORDER BY createdAt DESC")
    fun observeMyExpenses(uid: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Query("SELECT COUNT(*) FROM chart_of_accounts")
    suspend fun getChartOfAccountsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChartOfAccounts(accounts: List<ChartOfAccountEntity>)

    @Query("SELECT * FROM chart_of_accounts ORDER BY code ASC")
    fun observeChartOfAccounts(): Flow<List<ChartOfAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalLines(lines: List<JournalEntryLineEntity>)

    @Query("SELECT * FROM journal_entries ORDER BY entryDate DESC")
    fun observeJournalEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entry_lines")
    fun observeJournalLines(): Flow<List<JournalEntryLineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyClosing(closing: DailyClosingEntity)

    @Query("SELECT * FROM daily_closings ORDER BY closingDate DESC")
    fun observeDailyClosings(): Flow<List<DailyClosingEntity>>

    // --- Employee Tasks ---
    @Query("SELECT * FROM employee_tasks ORDER BY createdAt DESC")
    fun observeAllTasks(): Flow<List<EmployeeTaskEntity>>

    @Query("SELECT * FROM employee_tasks WHERE assignedToUserId = :uid ORDER BY createdAt DESC")
    fun observeMyTasks(uid: String): Flow<List<EmployeeTaskEntity>>

    @Query("SELECT * FROM employee_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): EmployeeTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: EmployeeTaskEntity)
}
