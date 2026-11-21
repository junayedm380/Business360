package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fullName: String,
    val email: String,
    val passwordHash: String,
    val role: String,
    val permissionsCsv: String,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val userName: String,
    val userRole: String,
    val action: String,
    val module: String,
    val recordId: String,
    val previousValue: String = "",
    val newValue: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val id: String = "default",
    val businessName: String = "Business360",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val currencySymbol: String = "৳",
    val invoicePrefix: String = "INV-",
    val purchasePrefix: String = "PO-",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val targetAudience: String, // "ADMIN_ONLY", "OPS_AND_ADMIN", "USER:<uid>"
    val title: String,
    val message: String,
    val category: String,
    val referenceId: String = "",
    val read: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val description: String = "",
    val unit: String = "pcs",
    val imageUri: String = "",
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "product_models",
    indices = [Index(value = ["productId"]), Index(value = ["modelCode"], unique = true)]
)
data class ProductModelEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val modelName: String,
    val modelCode: String,
    val barcode: String = "",
    val imageUri: String = "",
    val productionCost: Double,
    val wholesalePrice: Double,
    val retailPrice: Double,
    val minimumStock: Double = 5.0,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bom_items",
    indices = [Index(value = ["modelId"]), Index(value = ["rawMaterialId"])]
)
data class BomItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val modelId: String,
    val rawMaterialId: String,
    val quantityPerUnit: Double
)

@Entity(tableName = "price_history")
data class PriceHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val modelId: String,
    val oldCost: Double,
    val newCost: Double,
    val oldWholesale: Double,
    val newWholesale: Double,
    val oldRetail: Double,
    val newRetail: Double,
    val changedByUserId: String,
    val changedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "warehouses")
data class WarehouseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val location: String = "",
    val defaultWarehouse: Boolean = false,
    val active: Boolean = true
)

@Entity(
    tableName = "raw_materials",
    indices = [Index(value = ["materialCode"], unique = true)]
)
data class RawMaterialEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val materialCode: String,
    val category: String,
    val unit: String,
    val purchasePrice: Double,
    val supplierId: String = "",
    val warehouseId: String,
    val availableQuantity: Double = 0.0,
    val minimumStock: Double = 10.0,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "material_ledger",
    indices = [Index(value = ["rawMaterialId"])]
)
data class MaterialLedgerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val rawMaterialId: String,
    val warehouseId: String,
    val movementType: String, // PURCHASE, CONSUMPTION, WASTAGE, ADJUSTMENT, TRANSFER
    val quantityDelta: Double,
    val balanceAfter: Double,
    val referenceDoc: String,
    val userId: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "production_batches",
    indices = [Index(value = ["batchNumber"], unique = true), Index(value = ["modelId"])]
)
data class ProductionBatchEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val batchNumber: String,
    val productionDate: Long = System.currentTimeMillis(),
    val productId: String,
    val modelId: String,
    val warehouseId: String,
    val plannedQty: Double,
    val actualQty: Double,
    val rejectedQty: Double,
    val goodQty: Double,
    val wastageQty: Double = 0.0,
    val materialCost: Double = 0.0,
    val laborCost: Double = 0.0,
    val machineCost: Double = 0.0,
    val electricityCost: Double = 0.0,
    val otherCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val status: String = "DRAFT", // DRAFT, SUBMITTED, REVIEWED, APPROVED, COMPLETED
    val stockApplied: Boolean = false,
    val responsibleUserId: String,
    val supervisorId: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "inventory_balances",
    indices = [Index(value = ["modelId"]), Index(value = ["warehouseId"])]
)
data class InventoryBalanceEntity(
    @PrimaryKey val id: String, // "$modelId:$warehouseId"
    val modelId: String,
    val warehouseId: String,
    val goodQty: Double = 0.0,
    val reservedQty: Double = 0.0,
    val damagedQty: Double = 0.0,
    val scrapQty: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "stock_movements",
    indices = [Index(value = ["modelId"]), Index(value = ["warehouseId"])]
)
data class StockMovementEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val modelId: String,
    val warehouseId: String,
    val quantityDelta: Double,
    val movementType: String,
    val referenceDoc: String,
    val responsibleUserId: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val businessName: String = "",
    val phone: String,
    val altPhone: String = "",
    val address: String = "",
    val customerType: String = "RETAIL", // WHOLESALE, RETAIL
    val openingBalance: Double = 0.0,
    val creditLimit: Double = 0.0,
    val currentDue: Double = 0.0,
    val status: String = "ACTIVE",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "customer_ledger",
    indices = [Index(value = ["customerId"])]
)
data class CustomerLedgerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val entryType: String, // OPENING, SALE_INVOICE, PAYMENT, RETURN_CREDIT, REVERSAL
    val referenceDoc: String,
    val debitAmount: Double = 0.0,
    val creditAmount: Double = 0.0,
    val balanceAfter: Double = 0.0,
    val notes: String = "",
    val createdByUserId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sales",
    indices = [Index(value = ["invoiceNumber"], unique = true), Index(value = ["customerId"])]
)
data class SaleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: String,
    val saleType: String, // WHOLESALE, RETAIL
    val customerId: String,
    val warehouseId: String,
    val subtotal: Double,
    val discount: Double = 0.0,
    val additionalCharges: Double = 0.0,
    val grandTotal: Double,
    val paidAmount: Double = 0.0,
    val dueAmount: Double = 0.0,
    val deliveryStatus: String = "PENDING", // PENDING, PARTIAL, DELIVERED, CANCELLED
    val paymentMethod: String = "CASH",
    val createdByUserId: String,
    val notes: String = "",
    val saleDate: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sale_items",
    indices = [Index(value = ["saleId"]), Index(value = ["modelId"])]
)
data class SaleItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val saleId: String,
    val modelId: String,
    val productNameSnapshot: String,
    val modelNameSnapshot: String,
    val quantity: Double,
    val deliveredQty: Double = 0.0,
    val unitPriceSnapshot: Double,
    val costPriceSnapshot: Double,
    val discount: Double = 0.0,
    val lineTotal: Double
)

@Entity(
    tableName = "sales_returns",
    indices = [Index(value = ["saleId"]), Index(value = ["customerId"])]
)
data class SalesReturnEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val saleId: String,
    val customerId: String,
    val modelId: String,
    val warehouseId: String,
    val quantity: Double,
    val classification: String, // GOOD, DAMAGED, SCRAP
    val refundAmount: Double,
    val creditNoteNumber: String,
    val reason: String,
    val processedByUserId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payment_submissions",
    indices = [Index(value = ["customerId"]), Index(value = ["status"])]
)
data class PaymentSubmissionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val saleId: String = "",
    val amount: Double,
    val paymentMethod: String, // CASH, BKASH, NAGAD, BANK, OTHER
    val transactionRef: String = "",
    val proofUri: String = "",
    val notes: String = "",
    val submittedByUserId: String,
    val submittedByName: String,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val reviewedByUserId: String = "",
    val reviewNote: String = "",
    val reviewedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payments",
    indices = [Index(value = ["customerId"])]
)
data class PaymentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val submissionId: String = "",
    val customerId: String,
    val saleId: String = "",
    val cashAccountId: String,
    val amount: Double,
    val paymentMethod: String,
    val transactionRef: String = "",
    val approvedByUserId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val companyName: String = "",
    val phone: String,
    val address: String = "",
    val openingBalance: Double = 0.0,
    val currentPayable: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "purchase_orders",
    indices = [Index(value = ["poNumber"], unique = true), Index(value = ["supplierId"])]
)
data class PurchaseOrderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val poNumber: String,
    val supplierId: String,
    val warehouseId: String,
    val rawMaterialId: String,
    val quantity: Double,
    val unitPrice: Double,
    val discount: Double = 0.0,
    val additionalCharges: Double = 0.0,
    val totalAmount: Double,
    val paidAmount: Double = 0.0,
    val payableAmount: Double = 0.0,
    val status: String = "ORDERED", // ORDERED, RECEIVED
    val createdByUserId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "supplier_ledger",
    indices = [Index(value = ["supplierId"])]
)
data class SupplierLedgerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val supplierId: String,
    val entryType: String, // OPENING, PURCHASE, PAYMENT
    val referenceDoc: String,
    val creditAmount: Double = 0.0,
    val debitAmount: Double = 0.0,
    val balanceAfter: Double = 0.0,
    val notes: String = "",
    val createdByUserId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cash_accounts")
data class CashAccountEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val accountType: String, // CASH, BANK, BKASH, NAGAD, OTHER
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0,
    val active: Boolean = true
)

@Entity(
    tableName = "account_transactions",
    indices = [Index(value = ["cashAccountId"])]
)
data class AccountTransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val cashAccountId: String,
    val txType: String, // CASH_IN, CASH_OUT, TRANSFER_IN, TRANSFER_OUT, COLLECTION, SUPPLIER_PAYMENT, EXPENSE, SALE
    val amount: Double,
    val balanceAfter: Double,
    val referenceDoc: String,
    val notes: String = "",
    val createdByUserId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val expenseDate: Long = System.currentTimeMillis(),
    val category: String,
    val amount: Double,
    val cashAccountId: String,
    val description: String,
    val receiptUri: String = "",
    val submittedByUserId: String,
    val submittedByName: String,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val approvedByUserId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chart_of_accounts",
    indices = [Index(value = ["code"], unique = true)]
)
data class ChartOfAccountEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val category: String // ASSET, LIABILITY, EQUITY, REVENUE, COGS, EXPENSE
)

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryNumber: String,
    val entryDate: Long = System.currentTimeMillis(),
    val referenceDoc: String,
    val description: String,
    val totalDebit: Double,
    val totalCredit: Double,
    val createdByUserId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "journal_entry_lines",
    indices = [Index(value = ["journalEntryId"]), Index(value = ["accountId"])]
)
data class JournalEntryLineEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val journalEntryId: String,
    val accountId: String,
    val accountName: String,
    val debit: Double = 0.0,
    val credit: Double = 0.0
)

@Entity(tableName = "daily_closings")
data class DailyClosingEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val closingDate: Long = System.currentTimeMillis(),
    val openingCash: Double,
    val cashSales: Double,
    val cashCollections: Double,
    val bankCollections: Double,
    val totalExpenses: Double,
    val supplierPayments: Double,
    val cashWithdrawals: Double,
    val closingCash: Double,
    val bankBalance: Double,
    val closedByUserId: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "employee_tasks",
    indices = [Index(value = ["assignedToUserId"])]
)
data class EmployeeTaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val assignedToUserId: String,
    val assignedByUserId: String,
    val status: String = "PENDING", // PENDING, IN_PROGRESS, COMPLETED
    val dueDate: Long = System.currentTimeMillis() + 86_400_000L,
    val createdAt: Long = System.currentTimeMillis()
)
