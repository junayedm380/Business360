package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ErpDatabase
import com.example.data.repository.AuthAndGovernanceRepository
import com.example.data.repository.ErpSecurityException
import com.example.data.repository.FinanceRepository
import com.example.data.repository.HrOperationsRepository
import com.example.data.repository.HrPayrollRepository
import com.example.data.repository.ReportType
import com.example.domain.AppPermission
import com.example.domain.PermissionPolicy
import com.example.domain.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HrPayrollIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: ErpDatabase
    private lateinit var authRepo: AuthAndGovernanceRepository
    private lateinit var finRepo: FinanceRepository
    private lateinit var hrOpsRepo: HrOperationsRepository
    private lateinit var hrPayrollRepo: HrPayrollRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ErpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        authRepo = AuthAndGovernanceRepository(db)
        finRepo = FinanceRepository(db)
        hrOpsRepo = HrOperationsRepository(db)
        hrPayrollRepo = HrPayrollRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `migration 1 to 2 preserves existing Business360 records and upgrades HR permissions`() = runTest {
        val dbFile = File(context.cacheDir, "migration_v1_test.db")
        if (dbFile.exists()) dbFile.delete()

        val v1Helper = object : SQLiteOpenHelper(context, dbFile.absolutePath, null, 1) {
            override fun onCreate(sqlDb: SQLiteDatabase) {
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `users` (`id` TEXT NOT NULL, `fullName` TEXT NOT NULL, `email` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, `role` TEXT NOT NULL, `permissionsCsv` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_email` ON `users` (`email`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `userName` TEXT NOT NULL, `userRole` TEXT NOT NULL, `action` TEXT NOT NULL, `module` TEXT NOT NULL, `recordId` TEXT NOT NULL, `previousValue` TEXT NOT NULL, `newValue` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `app_settings` (`id` TEXT NOT NULL, `businessName` TEXT NOT NULL, `phone` TEXT NOT NULL, `email` TEXT NOT NULL, `address` TEXT NOT NULL, `currencySymbol` TEXT NOT NULL, `invoicePrefix` TEXT NOT NULL, `purchasePrefix` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` TEXT NOT NULL, `targetAudience` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `category` TEXT NOT NULL, `referenceId` TEXT NOT NULL, `read` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `products` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `unit` TEXT NOT NULL, `imageUri` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `product_models` (`id` TEXT NOT NULL, `productId` TEXT NOT NULL, `modelName` TEXT NOT NULL, `modelCode` TEXT NOT NULL, `barcode` TEXT NOT NULL, `imageUri` TEXT NOT NULL, `productionCost` REAL NOT NULL, `wholesalePrice` REAL NOT NULL, `retailPrice` REAL NOT NULL, `minimumStock` REAL NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_product_models_productId` ON `product_models` (`productId`)")
                sqlDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_product_models_modelCode` ON `product_models` (`modelCode`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `bom_items` (`id` TEXT NOT NULL, `modelId` TEXT NOT NULL, `rawMaterialId` TEXT NOT NULL, `quantityPerUnit` REAL NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_bom_items_modelId` ON `bom_items` (`modelId`)")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_bom_items_rawMaterialId` ON `bom_items` (`rawMaterialId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `price_history` (`id` TEXT NOT NULL, `modelId` TEXT NOT NULL, `oldCost` REAL NOT NULL, `newCost` REAL NOT NULL, `oldWholesale` REAL NOT NULL, `newWholesale` REAL NOT NULL, `oldRetail` REAL NOT NULL, `newRetail` REAL NOT NULL, `changedByUserId` TEXT NOT NULL, `changedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `warehouses` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `location` TEXT NOT NULL, `defaultWarehouse` INTEGER NOT NULL, `active` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `raw_materials` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `materialCode` TEXT NOT NULL, `category` TEXT NOT NULL, `unit` TEXT NOT NULL, `purchasePrice` REAL NOT NULL, `supplierId` TEXT NOT NULL, `warehouseId` TEXT NOT NULL, `availableQuantity` REAL NOT NULL, `minimumStock` REAL NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_raw_materials_materialCode` ON `raw_materials` (`materialCode`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `material_ledger` (`id` TEXT NOT NULL, `rawMaterialId` TEXT NOT NULL, `warehouseId` TEXT NOT NULL, `movementType` TEXT NOT NULL, `quantityDelta` REAL NOT NULL, `balanceAfter` REAL NOT NULL, `referenceDoc` TEXT NOT NULL, `userId` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_material_ledger_rawMaterialId` ON `material_ledger` (`rawMaterialId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `production_batches` (`id` TEXT NOT NULL, `batchNumber` TEXT NOT NULL, `productionDate` INTEGER NOT NULL, `productId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `warehouseId` TEXT NOT NULL, `plannedQty` REAL NOT NULL, `actualQty` REAL NOT NULL, `rejectedQty` REAL NOT NULL, `goodQty` REAL NOT NULL, `wastageQty` REAL NOT NULL, `materialCost` REAL NOT NULL, `laborCost` REAL NOT NULL, `machineCost` REAL NOT NULL, `electricityCost` REAL NOT NULL, `otherCost` REAL NOT NULL, `totalCost` REAL NOT NULL, `status` TEXT NOT NULL, `stockApplied` INTEGER NOT NULL, `responsibleUserId` TEXT NOT NULL, `supervisorId` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_production_batches_batchNumber` ON `production_batches` (`batchNumber`)")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_production_batches_modelId` ON `production_batches` (`modelId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `inventory_balances` (`id` TEXT NOT NULL, `modelId` TEXT NOT NULL, `warehouseId` TEXT NOT NULL, `goodQty` REAL NOT NULL, `reservedQty` REAL NOT NULL, `damagedQty` REAL NOT NULL, `scrapQty` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_balances_modelId` ON `inventory_balances` (`modelId`)")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_balances_warehouseId` ON `inventory_balances` (`warehouseId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `stock_movements` (`id` TEXT NOT NULL, `modelId` TEXT NOT NULL, `warehouseId` TEXT NOT NULL, `quantityDelta` REAL NOT NULL, `movementType` TEXT NOT NULL, `referenceDoc` TEXT NOT NULL, `responsibleUserId` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_modelId` ON `stock_movements` (`modelId`)")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_warehouseId` ON `stock_movements` (`warehouseId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `customers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `businessName` TEXT NOT NULL, `phone` TEXT NOT NULL, `altPhone` TEXT NOT NULL, `address` TEXT NOT NULL, `customerType` TEXT NOT NULL, `openingBalance` REAL NOT NULL, `creditLimit` REAL NOT NULL, `currentDue` REAL NOT NULL, `status` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `customer_ledger` (`id` TEXT NOT NULL, `customerId` TEXT NOT NULL, `entryType` TEXT NOT NULL, `referenceDoc` TEXT NOT NULL, `debitAmount` REAL NOT NULL, `creditAmount` REAL NOT NULL, `balanceAfter` REAL NOT NULL, `notes` TEXT NOT NULL, `createdByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_ledger_customerId` ON `customer_ledger` (`customerId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `sales` (`id` TEXT NOT NULL, `invoiceNumber` TEXT NOT NULL, `saleType` TEXT NOT NULL, `customerId` TEXT NOT NULL, `warehouseId` TEXT NOT NULL, `subtotal` REAL NOT NULL, `discount` REAL NOT NULL, `additionalCharges` REAL NOT NULL, `grandTotal` REAL NOT NULL, `paidAmount` REAL NOT NULL, `dueAmount` REAL NOT NULL, `deliveryStatus` TEXT NOT NULL, `paymentMethod` TEXT NOT NULL, `createdByUserId` TEXT NOT NULL, `notes` TEXT NOT NULL, `saleDate` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_invoiceNumber` ON `sales` (`invoiceNumber`)")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_customerId` ON `sales` (`customerId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `sale_items` (`id` TEXT NOT NULL, `saleId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `productNameSnapshot` TEXT NOT NULL, `modelNameSnapshot` TEXT NOT NULL, `quantity` REAL NOT NULL, `deliveredQty` REAL NOT NULL, `unitPriceSnapshot` REAL NOT NULL, `costPriceSnapshot` REAL NOT NULL, `discount` REAL NOT NULL, `lineTotal` REAL NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_saleId` ON `sale_items` (`saleId`)")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_modelId` ON `sale_items` (`modelId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `sales_returns` (`id` TEXT NOT NULL, `saleId` TEXT NOT NULL, `customerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `warehouseId` TEXT NOT NULL, `quantity` REAL NOT NULL, `classification` TEXT NOT NULL, `refundAmount` REAL NOT NULL, `creditNoteNumber` TEXT NOT NULL, `reason` TEXT NOT NULL, `processedByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_returns_saleId` ON `sales_returns` (`saleId`)")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_returns_customerId` ON `sales_returns` (`customerId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `payment_submissions` (`id` TEXT NOT NULL, `customerId` TEXT NOT NULL, `saleId` TEXT NOT NULL, `amount` REAL NOT NULL, `paymentMethod` TEXT NOT NULL, `transactionRef` TEXT NOT NULL, `proofUri` TEXT NOT NULL, `notes` TEXT NOT NULL, `submittedByUserId` TEXT NOT NULL, `submittedByName` TEXT NOT NULL, `status` TEXT NOT NULL, `reviewedByUserId` TEXT NOT NULL, `reviewNote` TEXT NOT NULL, `reviewedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_submissions_customerId` ON `payment_submissions` (`customerId`)")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_submissions_status` ON `payment_submissions` (`status`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `payments` (`id` TEXT NOT NULL, `submissionId` TEXT NOT NULL, `customerId` TEXT NOT NULL, `saleId` TEXT NOT NULL, `cashAccountId` TEXT NOT NULL, `amount` REAL NOT NULL, `paymentMethod` TEXT NOT NULL, `transactionRef` TEXT NOT NULL, `approvedByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_customerId` ON `payments` (`customerId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `suppliers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `companyName` TEXT NOT NULL, `phone` TEXT NOT NULL, `address` TEXT NOT NULL, `openingBalance` REAL NOT NULL, `currentPayable` REAL NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `purchase_orders` (`id` TEXT NOT NULL, `poNumber` TEXT NOT NULL, `supplierId` TEXT NOT NULL, `warehouseId` TEXT NOT NULL, `rawMaterialId` TEXT NOT NULL, `quantity` REAL NOT NULL, `unitPrice` REAL NOT NULL, `discount` REAL NOT NULL, `additionalCharges` REAL NOT NULL, `totalAmount` REAL NOT NULL, `paidAmount` REAL NOT NULL, `payableAmount` REAL NOT NULL, `status` TEXT NOT NULL, `createdByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_orders_poNumber` ON `purchase_orders` (`poNumber`)")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_orders_supplierId` ON `purchase_orders` (`supplierId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `supplier_ledger` (`id` TEXT NOT NULL, `supplierId` TEXT NOT NULL, `entryType` TEXT NOT NULL, `referenceDoc` TEXT NOT NULL, `creditAmount` REAL NOT NULL, `debitAmount` REAL NOT NULL, `balanceAfter` REAL NOT NULL, `notes` TEXT NOT NULL, `createdByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_ledger_supplierId` ON `supplier_ledger` (`supplierId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `cash_accounts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `accountType` TEXT NOT NULL, `openingBalance` REAL NOT NULL, `currentBalance` REAL NOT NULL, `active` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `account_transactions` (`id` TEXT NOT NULL, `cashAccountId` TEXT NOT NULL, `txType` TEXT NOT NULL, `amount` REAL NOT NULL, `balanceAfter` REAL NOT NULL, `referenceDoc` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_account_transactions_cashAccountId` ON `account_transactions` (`cashAccountId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `expenseDate` INTEGER NOT NULL, `category` TEXT NOT NULL, `amount` REAL NOT NULL, `cashAccountId` TEXT NOT NULL, `description` TEXT NOT NULL, `receiptUri` TEXT NOT NULL, `submittedByUserId` TEXT NOT NULL, `submittedByName` TEXT NOT NULL, `status` TEXT NOT NULL, `approvedByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `chart_of_accounts` (`id` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chart_of_accounts_code` ON `chart_of_accounts` (`code`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `journal_entries` (`id` TEXT NOT NULL, `entryNumber` TEXT NOT NULL, `entryDate` INTEGER NOT NULL, `referenceDoc` TEXT NOT NULL, `description` TEXT NOT NULL, `totalDebit` REAL NOT NULL, `totalCredit` REAL NOT NULL, `createdByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `journal_entry_lines` (`id` TEXT NOT NULL, `journalEntryId` TEXT NOT NULL, `accountId` TEXT NOT NULL, `accountName` TEXT NOT NULL, `debit` REAL NOT NULL, `credit` REAL NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entry_lines_journalEntryId` ON `journal_entry_lines` (`journalEntryId`)")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entry_lines_accountId` ON `journal_entry_lines` (`accountId`)")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `daily_closings` (`id` TEXT NOT NULL, `closingDate` INTEGER NOT NULL, `openingCash` REAL NOT NULL, `cashSales` REAL NOT NULL, `cashCollections` REAL NOT NULL, `bankCollections` REAL NOT NULL, `totalExpenses` REAL NOT NULL, `supplierPayments` REAL NOT NULL, `cashWithdrawals` REAL NOT NULL, `closingCash` REAL NOT NULL, `bankBalance` REAL NOT NULL, `closedByUserId` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `employee_tasks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `assignedToUserId` TEXT NOT NULL, `assignedByUserId` TEXT NOT NULL, `status` TEXT NOT NULL, `dueDate` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_employee_tasks_assignedToUserId` ON `employee_tasks` (`assignedToUserId`)")

                // Seed pre-existing v1 users, product, and cash account
                sqlDb.execSQL("INSERT INTO users VALUES ('u_admin', 'Legacy Admin', 'admin@b360.bd', 'h', 'ADMIN', 'VIEW_PRODUCTS,MANAGE_PRODUCTS', 1, 1000, 1000)")
                sqlDb.execSQL("INSERT INTO users VALUES ('u_mgr', 'Legacy Manager', 'mgr@b360.bd', 'h', 'MODERATOR_MANAGER', 'VIEW_PRODUCTS,ADD_PRODUCTION', 1, 1000, 1000)")
                sqlDb.execSQL("INSERT INTO users VALUES ('u_emp', 'Legacy Emp', 'emp@b360.bd', 'h', 'EMPLOYEE', 'VIEW_PRODUCTION,ADD_PRODUCTION,EDIT_PRODUCTION', 1, 1000, 1000)")
                sqlDb.execSQL("INSERT INTO products VALUES ('prod_1', 'Legacy Leather Bag', 'Bags', 'Classic', 'pcs', '', 'ACTIVE', 1000)")
                sqlDb.execSQL("INSERT INTO cash_accounts VALUES ('acct_cash', 'Main Cash Register', 'CASH', 50000.0, 50000.0, 1)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        v1Helper.writableDatabase.close()

        val migratedDb = Room.databaseBuilder(context, ErpDatabase::class.java, dbFile.absolutePath)
            .addMigrations(ErpDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            val users = migratedDb.erpDao().observeUsers().first()
            val admin = users.first { it.id == "u_admin" }
            val mgr = users.first { it.id == "u_mgr" }
            val emp = users.first { it.id == "u_emp" }

            assertTrue(admin.permissionsCsv.contains("VIEW_PAYROLL"))
            assertTrue(admin.permissionsCsv.contains("MANAGE_EMPLOYEES"))
            assertTrue(mgr.permissionsCsv.contains("MARK_ATTENDANCE"))
            assertTrue(mgr.permissionsCsv.contains("MANAGE_ATTENDANCE_CLOSING"))
            assertFalse(mgr.permissionsCsv.contains("VIEW_PAYROLL"))
            assertFalse(mgr.permissionsCsv.contains("VIEW_EMPLOYEE_SALARY"))
            assertFalse(emp.permissionsCsv.contains("MARK_ATTENDANCE"))

            val products = migratedDb.erpDao().observeProducts().first()
            assertEquals(1, products.size)
            assertEquals("Legacy Leather Bag", products.first().name)
            assertEquals(50000.0, migratedDb.erpDao().getCashAccountById("acct_cash")?.currentBalance ?: 0.0, 0.001)
        } finally {
            migratedDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun `employee management enforces unique code and hides confidential salary from manager`() = runTest {
        val superAdmin = authRepo.claimInitialSuperAdmin("Business360", "Owner", "owner@b360.bd", "secret123").getOrThrow()
        authRepo.createUser(superAdmin, "HR Manager", "mgr@b360.bd", "secret123", UserRole.MODERATOR_MANAGER).getOrThrow()
        val mgr = authRepo.login("mgr@b360.bd", "secret123").getOrThrow()
        authRepo.createUser(superAdmin, "Staff", "emp@b360.bd", "secret123", UserRole.EMPLOYEE).getOrThrow()
        val emp = authRepo.login("emp@b360.bd", "secret123").getOrThrow()

        val e1 = hrOpsRepo.saveEmployee(
            actor = superAdmin,
            existingId = null,
            employeeCode = "EMP-001",
            fullName = "Rahim Uddin",
            photoUri = "content://photo/1",
            phone = "01711000001",
            department = "Stitching",
            designation = "Senior Tailor",
            joiningDate = 1700000000000L,
            employmentStatus = "FULL_TIME",
            active = true,
            basicSalary = 30000.0,
            houseAllowance = 4000.0,
            medicalAllowance = 1000.0,
            transportAllowance = 1000.0,
            otherAllowance = 0.0,
            overtimeHourlyRate = 200.0
        ).getOrThrow()

        // Duplicate employee ID rejected
        val dupResult = hrOpsRepo.saveEmployee(
            actor = superAdmin,
            existingId = null,
            employeeCode = "EMP-001",
            fullName = "Dup Person",
            photoUri = "",
            phone = "01711000002",
            department = "Sales",
            designation = "Rep",
            joiningDate = 1700000000000L,
            employmentStatus = "FULL_TIME",
            active = true,
            basicSalary = 15000.0,
            houseAllowance = 0.0,
            medicalAllowance = 0.0,
            transportAllowance = 0.0,
            otherAllowance = 0.0,
            overtimeHourlyRate = 100.0
        )
        assertTrue(dupResult.isFailure)

        // SuperAdmin sees full salary
        val adminView = hrOpsRepo.observeEmployeesSanitized(superAdmin).first().first()
        assertEquals(30000.0, adminView.basicSalary ?: 0.0, 0.001)
        assertEquals(200.0, adminView.overtimeHourlyRate ?: 0.0, 0.001)

        // Manager sees basic info only; ALL salary fields strictly null
        val mgrView = hrOpsRepo.observeEmployeesSanitized(mgr).first().first()
        assertEquals("EMP-001", mgrView.employeeCode)
        assertEquals("Rahim Uddin", mgrView.fullName)
        assertEquals("Stitching", mgrView.department)
        assertNull(mgrView.basicSalary)
        assertNull(mgrView.houseAllowance)
        assertNull(mgrView.medicalAllowance)
        assertNull(mgrView.transportAllowance)
        assertNull(mgrView.otherAllowance)
        assertNull(mgrView.overtimeHourlyRate)

        // Manager & Employee cannot modify employees or access payroll
        assertTrue(hrOpsRepo.saveEmployee(
            actor = mgr,
            existingId = e1.id,
            employeeCode = "EMP-001",
            fullName = "Hacked",
            photoUri = "",
            phone = "017",
            department = "X",
            designation = "Y",
            joiningDate = 1L,
            employmentStatus = "FULL_TIME",
            active = true,
            basicSalary = 1.0,
            houseAllowance = 0.0,
            medicalAllowance = 0.0,
            transportAllowance = 0.0,
            otherAllowance = 0.0,
            overtimeHourlyRate = 0.0
        ).exceptionOrNull() is ErpSecurityException)
        assertTrue(hrOpsRepo.setEmployeeActive(mgr, e1.id, false).exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { hrOpsRepo.observeEmployeesSanitized(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { hrPayrollRepo.observePayrollRuns(mgr) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { hrPayrollRepo.observePayrollRuns(emp) }.exceptionOrNull() is ErpSecurityException)
    }

    @Test
    fun `manual attendance permissions, mark all present, daily closing lock and audit logs work securely`() = runTest {
        val superAdmin = authRepo.claimInitialSuperAdmin("Business360", "Owner", "owner@b360.bd", "secret123").getOrThrow()
        val mgrEntity = authRepo.createUser(superAdmin, "Floor Manager", "mgr@b360.bd", "secret123", UserRole.MODERATOR_MANAGER).getOrThrow()
        var mgr = authRepo.login("mgr@b360.bd", "secret123").getOrThrow()

        val e1 = hrOpsRepo.saveEmployee(
            actor = superAdmin, employeeCode = "E-101", fullName = "Karim Mia", photoUri = "",
            phone = "0171", department = "Prod", designation = "Operator",
            joiningDate = 1700000000000L, employmentStatus = "FULL_TIME", active = true,
            basicSalary = 18000.0, houseAllowance = 0.0, medicalAllowance = 0.0,
            transportAllowance = 0.0, otherAllowance = 0.0, overtimeHourlyRate = 120.0
        ).getOrThrow()
        hrOpsRepo.saveEmployee(
            actor = superAdmin, employeeCode = "E-102", fullName = "Nusrat Jahan", photoUri = "",
            phone = "0172", department = "Prod", designation = "QC",
            joiningDate = 1700000000000L, employmentStatus = "FULL_TIME", active = true,
            basicSalary = 20000.0, houseAllowance = 0.0, medicalAllowance = 0.0,
            transportAllowance = 0.0, otherAllowance = 0.0, overtimeHourlyRate = 130.0
        ).getOrThrow()

        val dateKey = "2026-04-10"
        // Manager marks e1 LATE, then marks remaining active present
        hrOpsRepo.markOrUpdateAttendance(mgr, e1.id, dateKey, "LATE", "09:40", "18:00", 1.5, "Bus delay").getOrThrow()
        val bulkMarked = hrOpsRepo.markAllPresent(mgr, dateKey).getOrThrow()
        assertEquals(1, bulkMarked) // Only e2 was unmarked

        val dayRows = hrOpsRepo.observeAttendanceForMonth(mgr, "2026-04").first().filter { it.dateKey == dateKey }
        assertEquals(2, dayRows.size)

        // Revoke EDIT_ATTENDANCE from Manager: initial marking on a new day still works, editing existing row is blocked
        val mgrNoEditPerms = PermissionPolicy.MODERATOR_ALLOWED_PERMISSIONS - AppPermission.EDIT_ATTENDANCE
        authRepo.updateUserAccess(superAdmin, mgrEntity.id, UserRole.MODERATOR_MANAGER, mgrNoEditPerms, true).getOrThrow()
        mgr = authRepo.login("mgr@b360.bd", "secret123").getOrThrow()

        assertTrue(
            hrOpsRepo.markOrUpdateAttendance(mgr, e1.id, dateKey, "PRESENT", "09:00", "18:00", 0.0, "Edit attempt").exceptionOrNull()
                is ErpSecurityException
        )
        hrOpsRepo.markOrUpdateAttendance(mgr, e1.id, "2026-04-11", "PRESENT", "09:00", "18:00", 0.0, "New day OK").getOrThrow()

        // Restore EDIT_ATTENDANCE & close daily attendance
        authRepo.updateUserAccess(superAdmin, mgrEntity.id, UserRole.MODERATOR_MANAGER, PermissionPolicy.MODERATOR_ALLOWED_PERMISSIONS, true).getOrThrow()
        mgr = authRepo.login("mgr@b360.bd", "secret123").getOrThrow()

        val closing = hrOpsRepo.closeDailyAttendance(mgr, dateKey, "Shift verified").getOrThrow()
        assertTrue(closing.locked)
        assertEquals(1, closing.presentCount)
        assertEquals(1, closing.lateCount)

        // Locked day blocks edits until Admin reopens
        val closedEditErr = hrOpsRepo.markOrUpdateAttendance(mgr, e1.id, dateKey, "PRESENT", "09:00", "18:00", 0.0, "After close").exceptionOrNull()
        assertNotNull(closedEditErr)

        hrOpsRepo.reopenDailyAttendance(superAdmin, dateKey).getOrThrow()
        hrOpsRepo.markOrUpdateAttendance(mgr, e1.id, dateKey, "PRESENT", "09:00", "18:00", 2.0, "Corrected after reopen").getOrThrow()

        val logs = hrOpsRepo.observeAttendanceAuditLogs(mgr).first()
        assertTrue(logs.any { it.action == "MARK_ATTENDANCE" })
        assertTrue(logs.any { it.action == "MARK_ALL_PRESENT" })
        assertTrue(logs.any { it.action == "ATTENDANCE_DAILY_CLOSING" })
        assertTrue(logs.any { it.action == "EDIT_ATTENDANCE" })
    }

    @Test
    fun `leave approval updates balances and syncs attendance without duplicate rows`() = runTest {
        val superAdmin = authRepo.claimInitialSuperAdmin("Business360", "Owner", "owner@b360.bd", "secret123").getOrThrow()
        authRepo.createUser(superAdmin, "Floor Manager", "mgr@b360.bd", "secret123", UserRole.MODERATOR_MANAGER).getOrThrow()
        val mgr = authRepo.login("mgr@b360.bd", "secret123").getOrThrow()

        val e1 = hrOpsRepo.saveEmployee(
            actor = superAdmin, employeeCode = "E-201", fullName = "Salma Akter", photoUri = "",
            phone = "0173", department = "Design", designation = "Pattern Maker",
            joiningDate = 1700000000000L, employmentStatus = "FULL_TIME", active = true,
            basicSalary = 25000.0, houseAllowance = 0.0, medicalAllowance = 0.0,
            transportAllowance = 0.0, otherAllowance = 0.0, overtimeHourlyRate = 150.0
        ).getOrThrow()

        // Pre-mark 2026-04-12 manually as ABSENT so we test overwrite without duplicates
        hrOpsRepo.markOrUpdateAttendance(mgr, e1.id, "2026-04-12", "ABSENT", "", "", 0.0, "Not arrived").getOrThrow()

        val annualType = hrOpsRepo.observeLeaveTypes(superAdmin).first().first { it.isPaid }
        val leaveReq = hrOpsRepo.submitLeaveRequest(
            superAdmin, e1.id, annualType.id, "2026-04-12", "2026-04-14", "Family trip"
        ).getOrThrow()
        assertEquals(3, leaveReq.totalDays)

        // Manager can view leave requests & balances, but cannot approve/submit
        assertEquals(1, hrOpsRepo.observeLeaveRequests(mgr).first().size)
        assertTrue(hrOpsRepo.reviewLeaveRequest(mgr, leaveReq.id, true, "Mgr approve").exceptionOrNull() is ErpSecurityException)

        val approved = hrOpsRepo.reviewLeaveRequest(superAdmin, leaveReq.id, true, "Approved by HR").getOrThrow()
        assertEquals("APPROVED", approved.status)
        assertTrue(approved.syncedToAttendance)

        val aprilRows = hrOpsRepo.observeAttendanceForMonth(superAdmin, "2026-04").first()
            .filter { it.employeeId == e1.id }
            .sortedBy { it.dateKey }
        assertEquals(listOf("2026-04-12", "2026-04-13", "2026-04-14"), aprilRows.map { it.dateKey })
        assertTrue(aprilRows.all { it.status == "LEAVE" && it.leaveRequestId == leaveReq.id })

        val balance = hrOpsRepo.observeLeaveBalances(superAdmin, 2026).first()
            .first { it.employeeId == e1.id && it.leaveTypeId == annualType.id }
        assertEquals(3, balance.usedDays)
        assertEquals(annualType.annualQuotaDays - 3, balance.remainingDays)
    }

    @Test
    fun `payroll end-to-end calculates attendance deductions, recovers advances, posts balanced GL, and enforces admin permissions`() = runTest {
        val superAdmin = authRepo.claimInitialSuperAdmin("Business360", "Owner", "owner@b360.bd", "secret123").getOrThrow()
        val adminEntity = authRepo.createUser(superAdmin, "Ops Admin", "opsadmin@b360.bd", "secret123", UserRole.ADMIN).getOrThrow()
        authRepo.createUser(superAdmin, "Manager", "mgr@b360.bd", "secret123", UserRole.MODERATOR_MANAGER).getOrThrow()
        val mgr = authRepo.login("mgr@b360.bd", "secret123").getOrThrow()

        // Seed cash via capital deposit
        finRepo.recordCashInOutOrTransfer(
            actor = superAdmin,
            fromAccountId = "acct_cash",
            toAccountId = null,
            amount = 200000.0,
            isCashIn = true,
            notes = "Owner capital"
        ).getOrThrow()

        // Create employee: Basic 30,000 + House 4,000 + Medical 1,000 + Transport 1,000 = 36,000 fixed, OT rate 200/hr
        val emp = hrOpsRepo.saveEmployee(
            actor = superAdmin, employeeCode = "B360-01", fullName = "Tariqul Islam <HQ>", photoUri = "",
            phone = "01755555555", department = "Finance", designation = "Officer",
            joiningDate = 1700000000000L, employmentStatus = "FULL_TIME", active = true,
            basicSalary = 30000.0, houseAllowance = 4000.0, medicalAllowance = 1000.0,
            transportAllowance = 1000.0, otherAllowance = 0.0, overtimeHourlyRate = 200.0
        ).getOrThrow()

        val monthKey = "2026-05"
        hrOpsRepo.markOrUpdateAttendance(superAdmin, emp.id, "2026-05-01", "PRESENT", "09:00", "22:00", 5.0, "Extra shift").getOrThrow()
        hrOpsRepo.markOrUpdateAttendance(superAdmin, emp.id, "2026-05-02", "ABSENT", "", "", 0.0, "Absent").getOrThrow()
        hrOpsRepo.markOrUpdateAttendance(superAdmin, emp.id, "2026-05-03", "HALF_DAY", "09:00", "13:30", 0.0, "Half").getOrThrow()

        // Unpaid leave on 2026-05-04 -> synced as LEAVE (unpaid -> -1000)
        val unpaidType = hrOpsRepo.observeLeaveTypes(superAdmin).first().first { !it.isPaid }
        val unpaidReq = hrOpsRepo.submitLeaveRequest(superAdmin, emp.id, unpaidType.id, "2026-05-04", "2026-05-04", "Personal").getOrThrow()
        hrOpsRepo.reviewLeaveRequest(superAdmin, unpaidReq.id, true, "Approved unpaid").getOrThrow()

        // Paid leave on 2026-05-05 -> 0 deduction
        val paidType = hrOpsRepo.observeLeaveTypes(superAdmin).first().first { it.isPaid }
        val paidReq = hrOpsRepo.submitLeaveRequest(superAdmin, emp.id, paidType.id, "2026-05-05", "2026-05-05", "Annual").getOrThrow()
        hrOpsRepo.reviewLeaveRequest(superAdmin, paidReq.id, true, "Approved paid").getOrThrow()

        // Issue Advance Salary 3,000 from acct_cash (cash goes 200,000 -> 197,000)
        val adv = hrPayrollRepo.issueEmployeeAdvance(superAdmin, emp.id, 3000.0, "acct_cash", "Medical advance", "2026-05-06").getOrThrow()
        assertEquals(3000.0, adv.remainingBalance, 0.001)
        assertEquals(197000.0, db.erpDao().getCashAccountById("acct_cash")!!.currentBalance, 0.001)

        // Configure 30-day payroll rule (verifies configurable rules: 30,000 / 30 = 1,000/day)
        hrPayrollRepo.updatePayrollConfig(
            actor = superAdmin,
            workingDaysPerMonth = 30,
            absentDeductionRate = 1.0,
            halfDayDeductionRate = 0.5,
            lateDaysPerHalfDayDeduction = 0,
            overtimeHourlyMultiplier = 1.5,
            deductUnpaidLeave = true
        ).getOrThrow()

        // Generate payroll for 2026-05 with 2,000 bonus
        val runs = hrPayrollRepo.generateMonthlyPayroll(superAdmin, monthKey, defaultBonus = 2000.0, otherDeduction = 0.0, notes = "May payroll").getOrThrow()
        val slip = runs.first { it.employeeId == emp.id }

        // Daily basic = 30,000 / 30 = 1,000
        // Gross = 30,000 + 6,000 (allowances) + 2,000 (bonus) + 1,000 (5h * 200) = 39,000
        // Deductions = 1,000 (absent) + 500 (half-day) + 1,000 (unpaid leave) + 3,000 (advance) = 5,500
        // Net Salary = 33,500
        assertEquals(39000.0, slip.grossSalary, 0.01)
        assertEquals(1000.0, slip.absenceDeduction, 0.01)
        assertEquals(500.0, slip.halfDayDeduction, 0.01)
        assertEquals(1000.0, slip.unpaidLeaveDeduction, 0.01)
        assertEquals(3000.0, slip.advanceDeduction, 0.01)
        assertEquals(5500.0, slip.totalDeductions, 0.01)
        assertEquals(33500.0, slip.netSalary, 0.01)

        // Verify Admin without MANAGE_PAYROLL is blocked from approving/paying
        val limitedAdminPerms = AppPermission.entries.toSet() - AppPermission.MANAGE_PAYROLL
        authRepo.updateUserAccess(superAdmin, adminEntity.id, UserRole.ADMIN, limitedAdminPerms, true).getOrThrow()
        val limitedAdmin = authRepo.login("opsadmin@b360.bd", "secret123").getOrThrow()
        assertTrue(hrPayrollRepo.approvePayrollRun(limitedAdmin, slip.id).exceptionOrNull() is ErpSecurityException)

        // SuperAdmin approves & pays salary from acct_cash (197,000 -> 163,500)
        hrPayrollRepo.approvePayrollRun(superAdmin, slip.id).getOrThrow()
        val paid = hrPayrollRepo.payPayrollRun(superAdmin, slip.id, "acct_cash").getOrThrow()
        assertEquals("PAID", paid.status)
        assertEquals(163500.0, db.erpDao().getCashAccountById("acct_cash")!!.currentBalance, 0.01)

        // Advance recovered completely
        val recoveredAdv = db.hrDao().observeEmployeeAdvances().first().first { it.id == adv.id }
        assertEquals("RECOVERED", recoveredAdv.status)
        assertEquals(0.0, recoveredAdv.remainingBalance, 0.001)

        // Verify Printable HTML Salary Slip escapes special chars safely & contains net payable
        val html = hrPayrollRepo.generatePrintableSalarySlipHtml(superAdmin, slip.id, "Business360", "৳").getOrThrow()
        assertTrue(html.contains("Tariqul Islam &lt;HQ&gt;"))
        assertTrue(html.contains(slip.slipNumber))
        assertTrue(html.contains("33,500.00"))

        // Manager cannot export payroll CSV or generate HTML slip, CAN export attendance report
        assertTrue(hrPayrollRepo.generatePayrollCsv(mgr, monthKey).exceptionOrNull() is ErpSecurityException)
        assertTrue(hrPayrollRepo.generatePrintableSalarySlipHtml(mgr, slip.id).exceptionOrNull() is ErpSecurityException)
        val attCsv = hrOpsRepo.generateHrOperationalCsv(mgr, ReportType.HR_ATTENDANCE_MONTHLY, monthKey).getOrThrow()
        assertTrue(attCsv.first.endsWith(".csv"))
        assertTrue(attCsv.second.contains("B360-01"))

        // Verify GL double-entry balance and AccountingStatements include Payroll Expense
        val entries = db.erpDao().observeJournalEntries().first()
        val allLines = db.erpDao().observeJournalLines().first()
        assertTrue(entries.isNotEmpty())
        for (je in entries) {
            assertTrue(abs(je.totalDebit - je.totalCredit) < 0.01)
            val lines = allLines.filter { it.journalEntryId == je.id }
            assertTrue(abs(lines.sumOf { it.debit } - je.totalDebit) < 0.01)
            assertTrue(abs(lines.sumOf { it.credit } - je.totalCredit) < 0.01)
        }
        val statements = finRepo.observeAccountingStatements(superAdmin).first()
        // Expense on Payroll = 33,500 cash + 3,000 advance recovery = 36,500 -> Net Profit = -36,500
        assertEquals(36500.0, statements.profitAndLoss.operatingExpenses, 0.01)
        assertEquals(-36500.0, statements.profitAndLoss.netProfit, 0.01)
    }

    @Test
    fun `employee removal cleans draft records and protects employees with finalized payroll or active advances`() = runTest {
        val superAdmin = authRepo.claimInitialSuperAdmin("Business360", "Owner", "owner@b360.bd", "secret123").getOrThrow()
        authRepo.createUser(superAdmin, "Floor Manager", "mgr@b360.bd", "secret123", UserRole.MODERATOR_MANAGER).getOrThrow()
        val mgr = authRepo.login("mgr@b360.bd", "secret123").getOrThrow()

        // Seed cash for advance
        finRepo.recordCashInOutOrTransfer(superAdmin, "acct_cash", null, 50000.0, isCashIn = true, "Capital").getOrThrow()

        val removableEmp = hrOpsRepo.saveEmployee(
            actor = superAdmin, employeeCode = "EMP-DEL-1", fullName = "Temporary Staff", photoUri = "",
            phone = "01710000001", department = "Packing", designation = "Helper",
            joiningDate = 1700000000000L, employmentStatus = "CONTRACT", active = true,
            basicSalary = 12000.0, houseAllowance = 0.0, medicalAllowance = 0.0,
            transportAllowance = 0.0, otherAllowance = 0.0, overtimeHourlyRate = 80.0
        ).getOrThrow()

        val protectedEmp = hrOpsRepo.saveEmployee(
            actor = superAdmin, employeeCode = "EMP-KEEP-1", fullName = "Senior Staff", photoUri = "",
            phone = "01710000002", department = "Stitching", designation = "Lead",
            joiningDate = 1700000000000L, employmentStatus = "FULL_TIME", active = true,
            basicSalary = 24000.0, houseAllowance = 0.0, medicalAllowance = 0.0,
            transportAllowance = 0.0, otherAllowance = 0.0, overtimeHourlyRate = 150.0
        ).getOrThrow()

        // Create attendance & draft payroll for removableEmp
        hrOpsRepo.markOrUpdateAttendance(superAdmin, removableEmp.id, "2026-06-01", "PRESENT", "09:00", "18:00", 0.0, "OK").getOrThrow()
        hrPayrollRepo.generateMonthlyPayroll(superAdmin, "2026-06", 0.0, 0.0, "Draft").getOrThrow()

        // Issue active advance to protectedEmp
        hrPayrollRepo.issueEmployeeAdvance(superAdmin, protectedEmp.id, 2000.0, "acct_cash", "Medical", "2026-06-02").getOrThrow()

        // Manager cannot delete any HR employee
        assertTrue(hrOpsRepo.deleteEmployee(mgr, removableEmp.id).exceptionOrNull() is ErpSecurityException)

        // Employee with active unrecovered advance cannot be deleted
        assertTrue(hrOpsRepo.deleteEmployee(superAdmin, protectedEmp.id).isFailure)

        // Employee with only attendance and DRAFT payroll CAN be deleted cleanly
        hrOpsRepo.deleteEmployee(superAdmin, removableEmp.id).getOrThrow()
        assertNull(db.hrDao().getEmployeeById(removableEmp.id))
        assertTrue(hrOpsRepo.observeAttendanceForMonth(superAdmin, "2026-06").first().none { it.employeeId == removableEmp.id })
        assertTrue(hrPayrollRepo.observePayrollRuns(superAdmin).first().none { it.employeeId == removableEmp.id })
        assertTrue(authRepo.observeAuditLogs(superAdmin).first().any { it.action == "DELETE_EMPLOYEE" && it.recordId == removableEmp.id })
    }
}
