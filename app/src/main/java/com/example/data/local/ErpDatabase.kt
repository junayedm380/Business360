package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        AuditLogEntity::class,
        AppSettingEntity::class,
        NotificationEntity::class,
        ProductEntity::class,
        ProductModelEntity::class,
        BomItemEntity::class,
        PriceHistoryEntity::class,
        WarehouseEntity::class,
        RawMaterialEntity::class,
        MaterialLedgerEntity::class,
        ProductionBatchEntity::class,
        InventoryBalanceEntity::class,
        StockMovementEntity::class,
        CustomerEntity::class,
        CustomerLedgerEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        SalesReturnEntity::class,
        PaymentSubmissionEntity::class,
        PaymentEntity::class,
        SupplierEntity::class,
        PurchaseOrderEntity::class,
        SupplierLedgerEntity::class,
        CashAccountEntity::class,
        AccountTransactionEntity::class,
        ExpenseEntity::class,
        ChartOfAccountEntity::class,
        JournalEntryEntity::class,
        JournalEntryLineEntity::class,
        DailyClosingEntity::class,
        EmployeeTaskEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ErpDatabase : RoomDatabase() {
    abstract fun erpDao(): ErpDao

    companion object {
        @Volatile
        private var INSTANCE: ErpDatabase? = null

        fun getInstance(context: Context): ErpDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ErpDatabase::class.java,
                    "crafterp_database.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
