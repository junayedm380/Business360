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
    fun `rbac financial confidentiality and privilege escalation checks`() = runTest {
        val superAdmin = authRepo.claimInitialSuperAdmin("Dhaka Mills", "Owner", "owner@mills.bd", "secret123").getOrThrow()
        assertEquals(UserRole.SUPER_ADMIN, superAdmin.role)

        val adminEntity = authRepo.createUser(superAdmin, "Admin User", "admin@mills.bd", "secret123", UserRole.ADMIN).getOrThrow()
        val modEntity = authRepo.createUser(superAdmin, "Floor Manager", "mod@mills.bd", "secret123", UserRole.MODERATOR_MANAGER).getOrThrow()
        val empEntity = authRepo.createUser(superAdmin, "Staff", "emp@mills.bd", "secret123", UserRole.EMPLOYEE).getOrThrow()

        val adminSession = authRepo.login("admin@mills.bd", "secret123").getOrThrow()
        val modSession = authRepo.login("mod@mills.bd", "secret123").getOrThrow()
        val empSession = authRepo.login("emp@mills.bd", "secret123").getOrThrow()

        // Moderator & Employee dashboard must NEVER contain financial summary
        assertNull(finRepo.observeRoleDashboard(modSession).first().financial)
        assertNull(finRepo.observeRoleDashboard(empSession).first().financial)
        assertNotNull(finRepo.observeRoleDashboard(adminSession).first().financial)

        // Moderator & Employee blocked from financial ledgers, cash accounts, accounting, and financial exports
        assertTrue(runCatching { finRepo.observeCashAccounts(modSession) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observeAccountingStatements(modSession) }.exceptionOrNull() is ErpSecurityException)
        assertTrue(runCatching { finRepo.observeCustomerLedger(empSession, "cust_1") }.exceptionOrNull() is ErpSecurityException)
        assertTrue(finRepo.generateReportCsv(modSession, ReportType.FINANCIAL_PROFIT_LOSS).exceptionOrNull() is ErpSecurityException)
        assertTrue(finRepo.generateReportCsv(empSession, ReportType.FINANCIAL_CUSTOMER_DUES).exceptionOrNull() is ErpSecurityException)

        // Users cannot escalate themselves or promote someone to Super Admin
        assertTrue(authRepo.updateUserAccess(empSession, empEntity.id, UserRole.ADMIN, empSession.permissions, true).exceptionOrNull() is ErpSecurityException)
        assertTrue(authRepo.updateUserAccess(adminSession, modEntity.id, UserRole.SUPER_ADMIN, adminSession.permissions, true).exceptionOrNull() is ErpSecurityException)
        assertNotNull(adminEntity)
    }

    @Test
    fun `production bom consumption, historical invoice price, delivery stock deduction, and payment approval workflow`() = runTest {
        val superAdmin = authRepo.claimInitialSuperAdmin("Bengal Crafts", "Owner", "owner@crafts.bd", "pass1234").getOrThrow()
        authRepo.createUser(superAdmin, "Sales Admin", "admin@crafts.bd", "pass1234", UserRole.ADMIN).getOrThrow()
        authRepo.createUser(superAdmin, "Collector", "emp@crafts.bd", "pass1234", UserRole.EMPLOYEE).getOrThrow()

        val admin = authRepo.login("admin@crafts.bd", "pass1234").getOrThrow()
        val emp = authRepo.login("emp@crafts.bd", "pass1234").getOrThrow()

        // 1. Setup product, model, raw material & BOM
        val prod = opsRepo.saveProduct(admin, "Cotton Polo", "Apparel", "Premium polo", "pcs").getOrThrow()
        // Edit product name & verify ID is preserved and Employee is blocked
        val editedProd = opsRepo.saveProduct(admin, "Premium Cotton Polo", "Apparel", "Premium polo", "pcs", "", prod.id).getOrThrow()
        assertEquals(prod.id, editedProd.id)
        assertEquals("Premium Cotton Polo", db.erpDao().getProductById(prod.id)!!.name)
        assertTrue(opsRepo.saveProduct(emp, "Hacked Name", "Apparel", "", "pcs", "", prod.id).exceptionOrNull() is ErpSecurityException)

        val model = opsRepo.saveProductModel(admin, null, prod.id, "Polo Classic", "PC-01", "", 200.0, 350.0, 500.0, 5.0).getOrThrow()
        val raw = opsRepo.saveRawMaterial(admin, "Cotton Yarn", "CY-01", "Fabric", "kg", 100.0, "wh_main", 50.0, 5.0).getOrThrow()
        opsRepo.saveBomItem(admin, model.id, raw.id, 0.5).getOrThrow()

        // 2. Production batch: 20 actual, 2 rejected => 18 good, consumes 10 kg raw material
        val batch = opsRepo.createProductionBatch(admin, model.id, "wh_main", 20.0, 20.0, 2.0, 0.0, 400.0, 0.0, 0.0, 0.0, "").getOrThrow()
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

        // 4. Employee Payment Collection Security
        val submission = finRepo.submitPaymentCollection(emp, customer.id, sale.id, 1500.0, "BKASH", "TRX9988", "", "Collected").getOrThrow()
        assertEquals("PENDING", submission.status)
        // Pending payment MUST NOT reduce official customer due or cash/bKash balance
        assertEquals(3500.0, db.erpDao().getCustomerById(customer.id)!!.currentDue, 0.01)
        assertEquals(0.0, db.erpDao().getCashAccountById("acct_bkash")!!.currentBalance, 0.01)

        // Employee CANNOT approve own payment
        assertTrue(finRepo.reviewPaymentSubmission(emp, submission.id, true, "").exceptionOrNull() is ErpSecurityException)

        // Admin approves payment => customer due reduces to 2000, bKash balance increases to 1500
        val approved = finRepo.reviewPaymentSubmission(admin, submission.id, true, "Verified TrxID").getOrThrow()
        assertEquals("APPROVED", approved.status)
        assertEquals(2000.0, db.erpDao().getCustomerById(customer.id)!!.currentDue, 0.01)
        assertEquals(1500.0, db.erpDao().getCashAccountById("acct_bkash")!!.currentBalance, 0.01)

        // 5. All double-entry journal entries must balance (Debit == Credit)
        val entries = db.erpDao().observeJournalEntries().first()
        assertTrue(entries.isNotEmpty())
        entries.forEach { je ->
            assertEquals(je.totalDebit, je.totalCredit, 0.01)
        }
    }
}
