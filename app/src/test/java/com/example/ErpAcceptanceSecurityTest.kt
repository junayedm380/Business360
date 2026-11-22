package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ErpDatabase
import com.example.data.repository.AuthAndGovernanceRepository
import com.example.data.repository.ErpSecurityException
import com.example.data.repository.FinanceRepository
import com.example.data.repository.OperationsRepository
import com.example.data.repository.ReportType
import com.example.data.repository.SaleItemRequest
import com.example.domain.AppPermission
import com.example.domain.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ErpAcceptanceSecurityTest {

    private lateinit var db: ErpDatabase
    private lateinit var authRepo: AuthAndGovernanceRepository
    private lateinit var opsRepo: OperationsRepository
    private lateinit var finRepo: FinanceRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ErpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        authRepo = AuthAndGovernanceRepository(db)
        opsRepo = OperationsRepository(db)
        finRepo = FinanceRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `employee can only add and edit non-completed production without seeing costs`() = runTest {
        val superAdmin = authRepo.claimInitialSuperAdmin("Business360 Factory", "Owner", "owner@b360.bd", "secret123").getOrThrow()
        val empEntity = authRepo.createUser(superAdmin, "Production Staff", "emp@b360.bd", "secret123", UserRole.EMPLOYEE).getOrThrow()
        val emp = authRepo.login("emp@b360.bd", "secret123").getOrThrow()

        // Admin sets up product, model with secret cost, raw material, and BOM
        val prod = opsRepo.saveProduct(superAdmin, "Denim Jacket", "Apparel", "Classic", "pcs").getOrThrow()
        val model = opsRepo.saveProductModel(superAdmin, null, prod.id, "DJ Slim", "DJ-01", "", 450.0, 900.0, 1250.0, 10.0).getOrThrow()
        val raw = opsRepo.saveRawMaterial(superAdmin, "Denim Fabric", "DF-01", "Textile", "yd", 220.0, "wh_main", 100.0, 10.0).getOrThrow()
        opsRepo.saveBomItem(superAdmin, model.id, raw.id, 1.5).getOrThrow()

        // Employee CAN create & edit production batch, with all costing hidden
        val createdBatch = opsRepo.createProductionBatch(
            emp, model.id, "wh_main", 30.0, 28.0, 2.0, 1.0,
            999.0, 500.0, 300.0, 100.0, "Morning shift"
        ).getOrThrow()
        assertEquals(0.0, createdBatch.materialCost, 0.001)
        assertEquals(0.0, createdBatch.laborCost, 0.001)
        assertEquals(0.0, createdBatch.totalCost, 0.001)

        val editedBatch = opsRepo.editProductionBatch(
            emp, createdBatch.id, model.id, "wh_main", 35.0, 32.0, 1.0, 0.5,
            888.0, 444.0, 222.0, 111.0, "Updated shift count"
        ).getOrThrow()
        assertEquals(35.0, editedBatch.plannedQty, 0.001)
        assertEquals(31.0, editedBatch.goodQty, 0.001)
        assertEquals(0.0, editedBatch.totalCost, 0.001)

        val observedBatch = opsRepo.observeProductionBatchesSanitized(emp).first().first()
        assertEquals(0.0, observedBatch.totalCost, 0.001)
        assertEquals(0.0, observedBatch.materialCost, 0.001)

        // Employee dashboard hides all financials and non-production modules
        val empDash = finRepo.observeRoleDashboard(emp).first()
        assertNull(empDash.financial)
        assertEquals(0, empDash.operational.rawMaterialCount)
        assertEquals(0, empDash.operational.todayOrdersCount)
        assertEquals(0.0, empDash.operational.totalFinishedGoodsUnits, 0.001)

        // Employee CANNOT approve/complete production or do anything else
        assertTrue(opsRepo.advanceProductionBatch(emp, createdBatch.id, "COMPLETED").exceptionOrNull() is ErpSecurityException)
        assertTrue(opsRepo.saveProduct(emp, "Hack", "Cat", "", "pcs").exceptionOrNull() is ErpSecurityException)
        assertTrue(opsRepo.saveProductModel(emp, null, prod.id, "X", "X1", "", 10.0, 20.0, 30.0, 1.0).exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { opsRepo.observeBomItems(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(opsRepo.saveBomItem(emp, model.id, raw.id, 1.0).exceptionOrNull() is ErpSecurityException)
        assertTrue(opsRepo.saveRawMaterial(emp, "Thread", "TH-1", "Trim", "cone", 50.0, "wh_main", 10.0, 2.0).exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { opsRepo.observeRawMaterialsSanitized(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { opsRepo.observeInventoryBalances(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(opsRepo.transferStock(emp, model.id, "wh_main", "wh_other", 1.0, "").exceptionOrNull() is ErpSecurityException)
        assertTrue(opsRepo.adjustStock(emp, model.id, "wh_main", 1.0, "Test").exceptionOrNull() is ErpSecurityException)
        assertTrue(opsRepo.createCustomer(emp, "Cust", "", "017", "", "RETAIL", 0.0, 0.0).exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { opsRepo.observeCustomersSanitized(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { opsRepo.observeSalesSanitized(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(opsRepo.createSale(emp, "RETAIL", "c1", "wh_main", listOf(SaleItemRequest(model.id, 1.0)), 0.0, 0.0, 0.0, "CASH", false, "").exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { opsRepo.observeSalesReturns(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(finRepo.createSupplier(emp, "Sup", "", "018", "", 0.0).exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observeSuppliers(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observePurchaseOrders(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(finRepo.submitPaymentCollection(emp, "c1", "", 100.0, "CASH", "", "", "").exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observePaymentSubmissions(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(finRepo.submitExpense(emp, "Tea", 50.0, "acct_cash", "Snacks").exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observeExpenses(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observeCashAccounts(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observeAccountingStatements(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observeDailyClosingPreview(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { authRepo.observeAuditLogs(emp) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(authRepo.updateUserAccess(emp, empEntity.id, UserRole.ADMIN, AppPermission.entries.toSet(), true).exceptionOrNull() is ErpSecurityException)
        assertTrue(finRepo.generateReportCsv(emp, ReportType.FINANCIAL_PROFIT_LOSS).exceptionOrNull() is ErpSecurityException)

        // Once Admin completes the batch, Employee CANNOT edit it anymore
        opsRepo.advanceProductionBatch(superAdmin, createdBatch.id, "COMPLETED").getOrThrow()
        assertTrue(
            opsRepo.editProductionBatch(
                emp, createdBatch.id, model.id, "wh_main", 40.0, 40.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, "After completion"
            ).isFailure
        )
    }

    @Test
    fun `moderator can manage production products sales and purchase prices while profit and costing stay hidden`() = runTest {
        val superAdmin = authRepo.claimInitialSuperAdmin("Dhaka Mills", "Owner", "owner@mills.bd", "secret123").getOrThrow()
        val modEntity = authRepo.createUser(superAdmin, "Floor Manager", "mod@mills.bd", "secret123", UserRole.MODERATOR_MANAGER).getOrThrow()
        val mod = authRepo.login("mod@mills.bd", "secret123").getOrThrow()

        // 1. Moderator can create/edit products and models with selling prices, while productionCost is hidden/preserved
        val prod = opsRepo.saveProduct(mod, "Polo Shirt", "Apparel", "Cotton", "pcs").getOrThrow()
        val editedProd = opsRepo.saveProduct(mod, "Premium Polo Shirt", "Apparel", "Cotton", "pcs", "", prod.id).getOrThrow()
        assertEquals("Premium Polo Shirt", editedProd.name)

        // Admin creates model with hidden productionCost = 320.0
        val adminModel = opsRepo.saveProductModel(superAdmin, null, prod.id, "Polo V1", "PV-01", "", 320.0, 550.0, 750.0, 5.0).getOrThrow()
        // Moderator sees selling prices, but productionCost is redacted to 0.0
        val modSeenModel = opsRepo.observeModelsSanitized(mod).first().first { it.id == adminModel.id }
        assertEquals(0.0, modSeenModel.productionCost, 0.001)
        assertEquals(550.0, modSeenModel.wholesalePrice, 0.001)
        assertEquals(750.0, modSeenModel.retailPrice, 0.001)

        // Moderator edits selling price of model -> DB productionCost (320.0) stays preserved for Admin!
        val modEditedModel = opsRepo.saveProductModel(mod, adminModel.id, prod.id, "Polo V1", "PV-01", "", 0.0, 580.0, 790.0, 5.0).getOrThrow()
        assertEquals(0.0, modEditedModel.productionCost, 0.001)
        assertEquals(320.0, db.erpDao().getModelById(adminModel.id)!!.productionCost, 0.001)
        assertEquals(580.0, db.erpDao().getModelById(adminModel.id)!!.wholesalePrice, 0.001)

        // 2. Moderator can add & edit production batches (costing redacted)
        val batch = opsRepo.createProductionBatch(mod, adminModel.id, "wh_main", 50.0, 48.0, 2.0, 1.0, 500.0, 200.0, 100.0, 50.0, "Mod batch").getOrThrow()
        assertEquals(0.0, batch.totalCost, 0.001)
        val editedBatch = opsRepo.editProductionBatch(mod, batch.id, adminModel.id, "wh_main", 60.0, 55.0, 3.0, 1.0, 0.0, 0.0, 0.0, 0.0, "Adjusted").getOrThrow()
        assertEquals(52.0, editedBatch.goodQty, 0.001)
        assertEquals(0.0, editedBatch.totalCost, 0.001)

        // 3. Moderator can add raw materials, suppliers, and purchases with purchase price & total
        val raw = opsRepo.saveRawMaterial(mod, "Cotton Yarn", "CY-01", "Yarn", "kg", 250.0, "wh_main", 20.0, 5.0).getOrThrow()
        assertEquals(250.0, opsRepo.observeRawMaterialsSanitized(mod).first().first().purchasePrice, 0.001)
        val sup = finRepo.createSupplier(mod, "Yarn House", "Yarn Ltd", "01811111111", "Narayanganj", 5000.0).getOrThrow()
        assertEquals(0.0, finRepo.observeSuppliers(mod).first().first().currentPayable, 0.001)
        val po = finRepo.createAndReceivePurchase(mod, sup.id, raw.id, "wh_main", 10.0, 260.0, 1000.0, true).getOrThrow()
        val modSeenPo = finRepo.observePurchaseOrders(mod).first().first { it.id == po.id }
        assertEquals(260.0, modSeenPo.unitPrice, 0.001)
        assertEquals(2600.0, modSeenPo.totalAmount, 0.001)
        assertEquals(0.0, modSeenPo.paidAmount, 0.001)
        assertEquals(0.0, modSeenPo.payableAmount, 0.001)

        // 4. Moderator can create customers & sales (seeing sale prices & totals, NOT paid/due or immediate delivery)
        val cust = opsRepo.createCustomer(mod, "Karim Fashion", "Karim Store", "01711111111", "Dhaka", "WHOLESALE", 1000.0, 20000.0).getOrThrow()
        assertNull(opsRepo.observeCustomersSanitized(mod).first().first { it.id == cust.id }.currentDue)
        val sale = opsRepo.createSale(
            mod, "WHOLESALE", cust.id, "wh_main",
            listOf(SaleItemRequest(adminModel.id, 10.0)), 0.0, 0.0, 500.0, "CASH", deliverImmediately = false, "Order by Mod"
        ).getOrThrow()
        val modSeenSale = opsRepo.observeSalesSanitized(mod).first().first { it.id == sale.id }
        assertEquals(5800.0, modSeenSale.grandTotal!!, 0.001)
        assertNull(modSeenSale.paidAmount)
        assertNull(modSeenSale.dueAmount)

        // 5. Moderator CANNOT see profit, costing, BOM, inventory, delivery, expenses, payments, cash, accounting, users, or audit logs
        assertNull(finRepo.observeRoleDashboard(mod).first().financial)
        assertTrue(opsRepo.createSale(mod, "RETAIL", cust.id, "wh_main", listOf(SaleItemRequest(adminModel.id, 1.0)), 0.0, 0.0, 0.0, "CASH", deliverImmediately = true, "").exceptionOrNull() is ErpSecurityException)
        assertTrue(opsRepo.confirmDelivery(mod, sale.id, 1.0).exceptionOrNull() is ErpSecurityException)
        assertTrue(opsRepo.advanceProductionBatch(mod, batch.id, "COMPLETED").exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { opsRepo.observeBomItems(mod) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(opsRepo.saveBomItem(mod, adminModel.id, raw.id, 0.5).exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { opsRepo.observeInventoryBalances(mod) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { opsRepo.observeSalesReturns(mod) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observeCashAccounts(mod) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observeAccountingStatements(mod) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observeCustomerLedger(mod, cust.id) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observeSupplierLedger(mod, sup.id) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observeExpenses(mod) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(finRepo.submitExpense(mod, "Rent", 1000.0, "acct_cash", "Rent").exceptionOrNull() is ErpSecurityException)
        assertTrue(finRepo.submitPaymentCollection(mod, cust.id, sale.id, 500.0, "CASH", "", "", "").exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observeDailyClosingPreview(mod) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { authRepo.observeAuditLogs(mod) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(authRepo.updateUserAccess(mod, modEntity.id, UserRole.ADMIN, AppPermission.entries.toSet(), true).exceptionOrNull() is ErpSecurityException)
        assertTrue(finRepo.generateReportCsv(mod, ReportType.FINANCIAL_PROFIT_LOSS).exceptionOrNull() is ErpSecurityException)
    }

    @Test
    fun `admin and super admin have complete end-to-end A-to-Z permissions and balanced accounting`() = runTest {
        val superAdmin = authRepo.claimInitialSuperAdmin("Bengal Crafts", "Owner", "owner@crafts.bd", "pass1234").getOrThrow()
        val adminEntity = authRepo.createUser(superAdmin, "General Admin", "admin@crafts.bd", "pass1234", UserRole.ADMIN).getOrThrow()
        val admin = authRepo.login("admin@crafts.bd", "pass1234").getOrThrow()

        // Admin has full authority just like Super Admin: can create users (including Admin), view audit logs, edit settings
        val secondAdmin = authRepo.createUser(admin, "Second Admin", "admin2@crafts.bd", "pass1234", UserRole.ADMIN).getOrThrow()
        assertNotNull(secondAdmin)
        assertTrue(authRepo.observeAuditLogs(admin).first().isNotEmpty())
        assertTrue(authRepo.updateSettings(admin, db.erpDao().getSettings()!!.copy(address = "Dhaka HQ")).isSuccess)
        assertNotNull(finRepo.observeRoleDashboard(admin).first().financial)
        assertNotNull(adminEntity)

        // 1. Setup product, model, raw material & BOM
        val prod = opsRepo.saveProduct(admin, "Cotton Polo", "Apparel", "Premium polo", "pcs").getOrThrow()
        val editedProd = opsRepo.saveProduct(admin, "Premium Cotton Polo", "Apparel", "Premium polo", "pcs", "", prod.id).getOrThrow()
        assertEquals(prod.id, editedProd.id)
        assertEquals("Premium Cotton Polo", db.erpDao().getProductById(prod.id)!!.name)

        val model = opsRepo.saveProductModel(admin, null, prod.id, "Polo Classic", "PC-01", "", 200.0, 350.0, 500.0, 5.0).getOrThrow()
        val raw = opsRepo.saveRawMaterial(admin, "Cotton Yarn", "CY-01", "Fabric", "kg", 100.0, "wh_main", 50.0, 5.0).getOrThrow()
        opsRepo.saveBomItem(admin, model.id, raw.id, 0.5).getOrThrow()

        // 2. Production batch: create + edit + complete (20 actual, 2 rejected => 18 good, consumes 10 kg raw material)
        val batch = opsRepo.createProductionBatch(admin, model.id, "wh_main", 15.0, 15.0, 1.0, 0.0, 300.0, 0.0, 0.0, 0.0, "").getOrThrow()
        opsRepo.editProductionBatch(admin, batch.id, model.id, "wh_main", 20.0, 20.0, 2.0, 0.0, 400.0, 0.0, 0.0, 0.0, "Updated").getOrThrow()
        opsRepo.advanceProductionBatch(admin, batch.id, "COMPLETED").getOrThrow()

        // Prevent duplicate completion
        assertTrue(opsRepo.advanceProductionBatch(admin, batch.id, "COMPLETED").isFailure)

        val updatedRaw = db.erpDao().getRawMaterialById(raw.id)!!
        assertEquals(40.0, updatedRaw.availableQuantity, 0.001)
        val fgBal = db.erpDao().getInventoryBalance(model.id, "wh_main")!!
        assertEquals(18.0, fgBal.goodQty, 0.001)

        // 3. Wholesale order creation does NOT deduct stock until delivered
        val customer = opsRepo.createCustomer(admin, "Rahim Traders", "Rahim Store", "01700000000", "Dhaka", "WHOLESALE", 0.0, 50000.0).getOrThrow()
        val sale = opsRepo.createSale(
            admin, "WHOLESALE", customer.id, "wh_main",
            listOf(SaleItemRequest(model.id, 10.0)), 0.0, 0.0, 0.0, "CASH", deliverImmediately = false, ""
        ).getOrThrow()
        assertEquals(18.0, db.erpDao().getInventoryBalance(model.id, "wh_main")!!.goodQty, 0.001)

        // Confirm delivery => deducts 10 units and updates customer due to 3500
        opsRepo.confirmDelivery(admin, sale.id, 1.0).getOrThrow()
        assertEquals(8.0, db.erpDao().getInventoryBalance(model.id, "wh_main")!!.goodQty, 0.001)
        assertEquals(3500.0, db.erpDao().getCustomerById(customer.id)!!.currentDue, 0.01)

        // Updating model wholesale price later does NOT alter historical invoice price
        opsRepo.saveProductModel(admin, model.id, prod.id, "Polo Classic", "PC-01", "", 200.0, 450.0, 600.0, 5.0).getOrThrow()
        val historicalItem = db.erpDao().getSaleItems(sale.id).first()
        assertEquals(350.0, historicalItem.unitPriceSnapshot, 0.01)

        // 4. Payment Collection & SuperAdmin/Admin Approval + Profit/Loss & Accounting
        val submission = finRepo.submitPaymentCollection(admin, customer.id, sale.id, 1500.0, "BKASH", "TRX9988", "", "Collected").getOrThrow()
        assertEquals("PENDING", submission.status)
        assertEquals(3500.0, db.erpDao().getCustomerById(customer.id)!!.currentDue, 0.01)
        assertEquals(0.0, db.erpDao().getCashAccountById("acct_bkash")!!.currentBalance, 0.01)

        val approved = finRepo.reviewPaymentSubmission(superAdmin, submission.id, true, "Verified TrxID").getOrThrow()
        assertEquals("APPROVED", approved.status)
        assertEquals(2000.0, db.erpDao().getCustomerById(customer.id)!!.currentDue, 0.01)
        assertEquals(1500.0, db.erpDao().getCashAccountById("acct_bkash")!!.currentBalance, 0.01)

        // Admin & Super Admin can both view P&L statements and export reports
        assertNotNull(finRepo.observeAccountingStatements(admin).first().profitAndLoss)
        assertTrue(finRepo.generateReportCsv(admin, ReportType.FINANCIAL_PROFIT_LOSS).isSuccess)
        assertTrue(finRepo.generateReportCsv(superAdmin, ReportType.FINANCIAL_PROFIT_LOSS).isSuccess)

        // 5. All double-entry journal entries must balance (Debit == Credit)
        val entries = db.erpDao().observeJournalEntries().first()
        assertTrue(entries.isNotEmpty())
        entries.forEach { je ->
            assertEquals(je.totalDebit, je.totalCredit, 0.01)
        }
    }

    @Test
    fun `raw material editing updates fields adjusts material ledger and prevents duplicate code`() = runTest {
        val superAdmin = authRepo.claimInitialSuperAdmin("Business360", "Owner", "owner@b360.bd", "secret123").getOrThrow()
        authRepo.createUser(superAdmin, "Prod Staff", "emp@b360.bd", "secret123", UserRole.EMPLOYEE).getOrThrow()
        val emp = authRepo.login("emp@b360.bd", "secret123").getOrThrow()

        val raw1 = opsRepo.saveRawMaterial(superAdmin, "Cotton Thread", "RM-101", "Trim", "cone", 80.0, "wh_main", 50.0, 10.0).getOrThrow()
        opsRepo.saveRawMaterial(superAdmin, "Zipper 8in", "RM-102", "Hardware", "pcs", 15.0, "wh_main", 200.0, 20.0).getOrThrow()

        // Edit existing raw material: update name, code, category, unit, price, quantity (50 -> 75), minStock
        val updated = opsRepo.saveRawMaterial(
            superAdmin, "Organic Cotton Thread", "RM-101A", "Organic Trim", "spool",
            95.0, "wh_main", 75.0, 15.0, raw1.id
        ).getOrThrow()
        assertEquals(raw1.id, updated.id)
        assertEquals("Organic Cotton Thread", updated.name)
        assertEquals("RM-101A", updated.materialCode)
        assertEquals(75.0, updated.availableQuantity, 0.001)
        assertEquals(95.0, updated.purchasePrice, 0.001)
        assertEquals(15.0, updated.minimumStock, 0.001)

        val ledger = db.erpDao().observeMaterialLedger().first().filter { it.rawMaterialId == raw1.id }
        assertTrue(ledger.any { it.movementType == "ADJUSTMENT" && kotlin.math.abs(it.quantityDelta - 25.0) < 0.001 })

        // Duplicate code collision rejected cleanly
        val dupResult = opsRepo.saveRawMaterial(superAdmin, "Organic Cotton Thread", "RM-102", "Organic Trim", "spool", 95.0, "wh_main", 75.0, 15.0, raw1.id)
        assertTrue(dupResult.isFailure)

        // Employee cannot edit raw materials
        assertTrue(
            opsRepo.saveRawMaterial(emp, "Hack", "RM-101A", "Trim", "spool", 1.0, "wh_main", 999.0, 1.0, raw1.id)
                .exceptionOrNull() is ErpSecurityException
        )
    }
}
