package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AccountTransactionEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.CashAccountEntity
import com.example.data.local.CustomerLedgerEntity
import com.example.data.local.DailyClosingEntity
import com.example.data.local.ErpDatabase
import com.example.data.local.ExpenseEntity
import com.example.data.local.JournalEntryEntity
import com.example.data.local.JournalEntryLineEntity
import com.example.data.local.MaterialLedgerEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PaymentSubmissionEntity
import com.example.data.local.PurchaseOrderEntity
import com.example.data.local.RawMaterialEntity
import com.example.data.local.SupplierEntity
import com.example.data.local.SupplierLedgerEntity
import com.example.domain.AppPermission
import com.example.domain.Formatters
import com.example.domain.SessionUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.UUID

class FinanceRepository(private val db: ErpDatabase) {
    private val dao = db.erpDao()

    // --- Dashboard Snapshot (Strictly Role-Sanitized) ---
    fun observeRoleDashboard(session: SessionUser): Flow<RoleDashboardSnapshot> {
        val baseFlow = combine(
            dao.observeProductionBatches(),
            dao.observeRawMaterials(),
            dao.observeInventoryBalances(),
            dao.observeSales(),
            dao.observeMyTasks(session.id)
        ) { batches, rawMaterials, invBalances, sales, myTasks ->
            val canSeeCost = session.canViewCosting
            val sanitizedBatches = if (canSeeCost) batches else batches.map {
                it.copy(materialCost = 0.0, laborCost = 0.0, machineCost = 0.0, electricityCost = 0.0, otherCost = 0.0, totalCost = 0.0)
            }
            val startOfDay = startOfDayMillis()
            val startOfMonth = startOfMonthMillis()
            val todayProd = sanitizedBatches.filter { it.createdAt >= startOfDay }.sumOf { it.goodQty }
            val monthProd = sanitizedBatches.filter { it.createdAt >= startOfMonth }.sumOf { it.goodQty }
            val totalGood = sanitizedBatches.filter { it.status == "COMPLETED" }.sumOf { it.goodQty }
            val totalRej = sanitizedBatches.sumOf { it.rejectedQty }
            val canSeeRaw = session.has(AppPermission.VIEW_RAW_MATERIALS) || session.isFullAdmin
            val canSeeSalesCount = session.has(AppPermission.CREATE_SALES) || session.isFullAdmin
            val canSeeInv = session.has(AppPermission.VIEW_INVENTORY)
            val lowRaw = if (canSeeRaw) {
                rawMaterials.filter { it.availableQuantity <= it.minimumStock }.map { mat ->
                    if (canSeeCost || session.has(AppPermission.VIEW_PURCHASE_PRICES)) mat else mat.copy(purchasePrice = 0.0)
                }
            } else emptyList()
            val pendingProd = sanitizedBatches.count { it.status in listOf("SUBMITTED", "REVIEWED", "APPROVED") }

            OperationalDashboardData(
                todayProductionQty = Formatters.roundQty(todayProd),
                monthlyProductionQty = Formatters.roundQty(monthProd),
                totalGoodQty = Formatters.roundQty(totalGood),
                totalRejectedQty = Formatters.roundQty(totalRej),
                rawMaterialCount = if (canSeeRaw) rawMaterials.size else 0,
                totalRawMaterialUnits = if (canSeeRaw) Formatters.roundQty(rawMaterials.sumOf { it.availableQuantity }) else 0.0,
                totalFinishedGoodsUnits = if (canSeeInv) Formatters.roundQty(invBalances.sumOf { it.goodQty }) else 0.0,
                lowStockRawMaterials = lowRaw,
                lowStockModelsCount = 0,
                todayOrdersCount = if (canSeeSalesCount) sales.count { it.saleDate >= startOfDay } else 0,
                pendingDeliveriesCount = if (canSeeInv) sales.count { it.deliveryStatus in listOf("PENDING", "PARTIAL") } else 0,
                pendingProductionApprovals = pendingProd,
                myPendingTasksCount = if (session.isFullAdmin || session.has(AppPermission.MANAGE_TASKS)) myTasks.count { it.status != "COMPLETED" } else 0,
                recentBatches = sanitizedBatches.take(8),
                warehouseBalances = if (canSeeInv) invBalances else emptyList()
            )
        }

        if (!session.canViewFinancials) {
            // Strictly never expose or compute FinancialDashboardData for Moderator/Manager or Employee
            return combine(baseFlow, dao.observeModels()) { ops, models ->
                val lowModels = if (session.has(AppPermission.VIEW_INVENTORY)) {
                    val stockByModel = ops.warehouseBalances.groupBy { it.modelId }.mapValues { entry -> entry.value.sumOf { it.goodQty } }
                    models.count { (stockByModel[it.id] ?: 0.0) <= it.minimumStock }
                } else 0
                RoleDashboardSnapshot(operational = ops.copy(lowStockModelsCount = lowModels), financial = null)
            }
        }

        val finFlowA = combine(
            dao.observeSales(),
            dao.observePayments(),
            dao.observeCustomers(),
            dao.observeSuppliers(),
            dao.observeExpenses()
        ) { sales, payments, customers, suppliers, expenses ->
            listOf(sales, payments, customers, suppliers, expenses)
        }

        val finFlowB = combine(
            dao.observeCashAccounts(),
            dao.observePaymentSubmissions(),
            dao.observeJournalLines(),
            dao.observeModels()
        ) { accounts, submissions, jLines, models ->
            listOf(accounts, submissions, jLines, models)
        }

        return combine(baseFlow, finFlowA, finFlowB) { ops, a, b ->
            @Suppress("UNCHECKED_CAST")
            val sales = a[0] as List<com.example.data.local.SaleEntity>
            @Suppress("UNCHECKED_CAST")
            val payments = a[1] as List<PaymentEntity>
            @Suppress("UNCHECKED_CAST")
            val customers = a[2] as List<com.example.data.local.CustomerEntity>
            @Suppress("UNCHECKED_CAST")
            val suppliers = a[3] as List<SupplierEntity>
            @Suppress("UNCHECKED_CAST")
            val expenses = a[4] as List<ExpenseEntity>

            @Suppress("UNCHECKED_CAST")
            val accounts = b[0] as List<CashAccountEntity>
            @Suppress("UNCHECKED_CAST")
            val submissions = b[1] as List<PaymentSubmissionEntity>
            @Suppress("UNCHECKED_CAST")
            val jLines = b[2] as List<JournalEntryLineEntity>
            @Suppress("UNCHECKED_CAST")
            val models = b[3] as List<com.example.data.local.ProductModelEntity>

            val stockByModel = ops.warehouseBalances.groupBy { it.modelId }.mapValues { e -> e.value.sumOf { it.goodQty } }
            val lowModels = models.count { (stockByModel[it.id] ?: 0.0) <= it.minimumStock }

            val startOfDay = startOfDayMillis()
            val startOfMonth = startOfMonthMillis()
            val validSales = sales.filter { it.deliveryStatus != "CANCELLED" }
            val todaySales = validSales.filter { it.saleDate >= startOfDay }.sumOf { it.grandTotal }
            val monthSales = validSales.filter { it.saleDate >= startOfMonth }.sumOf { it.grandTotal }
            val totalSales = validSales.sumOf { it.grandTotal }
            val totalPayments = payments.sumOf { it.amount } + validSales.sumOf { it.paidAmount }
            val totalDue = customers.sumOf { it.currentDue }
            val totalPayable = suppliers.sumOf { it.currentPayable }
            val approvedExpenses = expenses.filter { it.status == "APPROVED" }.sumOf { it.amount }

            val revenue = jLines.filter { it.accountId == "coa_4001" }.sumOf { it.credit - it.debit }
            val returns = jLines.filter { it.accountId == "coa_4100" }.sumOf { it.debit - it.credit }
            val cogs = jLines.filter { it.accountId == "coa_5001" }.sumOf { it.debit - it.credit }
            val grossProfit = Formatters.roundMoney((revenue - returns) - cogs)
            val netProfit = Formatters.roundMoney(grossProfit - approvedExpenses)

            val cashInHand = accounts.filter { it.accountType == "CASH" }.sumOf { it.currentBalance }
            val bankBalance = accounts.filter { it.accountType != "CASH" }.sumOf { it.currentBalance }

            RoleDashboardSnapshot(
                operational = ops.copy(lowStockModelsCount = lowModels),
                financial = FinancialDashboardData(
                    todaySalesAmount = Formatters.roundMoney(todaySales),
                    monthlySalesAmount = Formatters.roundMoney(monthSales),
                    totalSalesAmount = Formatters.roundMoney(totalSales),
                    totalPaymentsReceived = Formatters.roundMoney(totalPayments),
                    totalCustomerDue = Formatters.roundMoney(totalDue),
                    totalSupplierPayable = Formatters.roundMoney(totalPayable),
                    totalExpenses = Formatters.roundMoney(approvedExpenses),
                    totalCogs = Formatters.roundMoney(cogs),
                    grossProfit = grossProfit,
                    netProfit = netProfit,
                    cashInHand = Formatters.roundMoney(cashInHand),
                    bankAndMobileBalance = Formatters.roundMoney(bankBalance),
                    pendingPaymentApprovals = submissions.count { it.status == "PENDING" },
                    pendingExpenseApprovals = expenses.count { it.status == "PENDING" },
                    cashAccounts = accounts
                )
            )
        }
    }

    // --- Employee Payment Collection & Admin Approval Security ---
    fun observePaymentSubmissions(session: SessionUser): Flow<List<PaymentSubmissionEntity>> =
        if (session.has(AppPermission.APPROVE_PAYMENTS)) dao.observePaymentSubmissions()
        else if (session.has(AppPermission.SUBMIT_PAYMENT_COLLECTION)) dao.observeMyPaymentSubmissions(session.id)
        else throw ErpSecurityException("Payment submission access denied")

    suspend fun submitPaymentCollection(
        actor: SessionUser,
        customerId: String,
        saleId: String = "",
        amount: Double,
        paymentMethod: String,
        transactionRef: String,
        proofUri: String,
        notes: String
    ): Result<PaymentSubmissionEntity> = try {
        if (!actor.has(AppPermission.SUBMIT_PAYMENT_COLLECTION) && !actor.has(AppPermission.APPROVE_PAYMENTS)) {
            throw ErpSecurityException("Payment collection submission permission required")
        }
        require(amount > 0) { "Collected amount must be greater than zero" }
        val customer = dao.getCustomerById(customerId) ?: throw IllegalArgumentException("Customer not found")

        val submission = PaymentSubmissionEntity(
            customerId = customerId,
            saleId = saleId,
            amount = Formatters.roundMoney(amount),
            paymentMethod = paymentMethod.uppercase(),
            transactionRef = transactionRef.trim(),
            proofUri = proofUri,
            notes = notes.trim(),
            submittedByUserId = actor.id,
            submittedByName = actor.fullName,
            status = "PENDING"
        )
        db.withTransaction {
            dao.upsertPaymentSubmission(submission)
            dao.insertNotification(
                NotificationEntity(
                    targetAudience = "ADMIN_ONLY",
                    title = "Payment Approval Required",
                    message = "${actor.fullName} submitted ${Formatters.formatTaka(submission.amount)} collection for ${customer.name} (${submission.paymentMethod}).",
                    category = "PAYMENT_APPROVAL",
                    referenceId = submission.id
                )
            )
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id, userName = actor.fullName, userRole = actor.role.name,
                    action = "SUBMIT_PAYMENT_COLLECTION", module = "PAYMENTS", recordId = submission.id,
                    newValue = "PENDING ${submission.amount} (${submission.paymentMethod})"
                )
            )
        }
        Result.success(submission)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun reviewPaymentSubmission(
        actor: SessionUser,
        submissionId: String,
        approve: Boolean,
        reviewNote: String = ""
    ): Result<PaymentSubmissionEntity> = try {
        if (!actor.has(AppPermission.APPROVE_PAYMENTS)) {
            throw ErpSecurityException("Only authorized Admin/Super Admin can approve or reject payment submissions")
        }
        val sub = dao.getPaymentSubmissionById(submissionId) ?: throw IllegalArgumentException("Submission not found")
        if (sub.status != "PENDING") {
            throw IllegalStateException("Payment submission has already been ${sub.status}")
        }
        // MANDATORY SECURITY RULE: Non-admin users cannot approve their own payment submissions
        if (sub.submittedByUserId == actor.id && !actor.isFullAdmin) {
            throw ErpSecurityException("Users cannot approve their own payment submissions")
        }

        val updated = db.withTransaction {
            val now = System.currentTimeMillis()
            val finalSub = sub.copy(
                status = if (approve) "APPROVED" else "REJECTED",
                reviewedByUserId = actor.id,
                reviewNote = reviewNote.trim(),
                reviewedAt = now
            )
            dao.upsertPaymentSubmission(finalSub)

            if (approve) {
                val acctId = accountIdForMethod(sub.paymentMethod)
                val acct = dao.getCashAccountById(acctId) ?: dao.getCashAccountById("acct_cash")
                    ?: throw IllegalStateException("Cash account missing")
                val nextAcctBal = Formatters.roundMoney(acct.currentBalance + sub.amount)
                dao.upsertCashAccount(acct.copy(currentBalance = nextAcctBal))

                val payment = PaymentEntity(
                    submissionId = sub.id,
                    customerId = sub.customerId,
                    saleId = sub.saleId,
                    cashAccountId = acct.id,
                    amount = sub.amount,
                    paymentMethod = sub.paymentMethod,
                    transactionRef = sub.transactionRef,
                    approvedByUserId = actor.id
                )
                dao.insertPayment(payment)
                dao.insertAccountTransaction(
                    AccountTransactionEntity(
                        cashAccountId = acct.id,
                        txType = "COLLECTION",
                        amount = sub.amount,
                        balanceAfter = nextAcctBal,
                        referenceDoc = sub.transactionRef.ifBlank { payment.id.take(8) },
                        notes = "Approved collection from ${sub.submittedByName}",
                        createdByUserId = actor.id
                    )
                )

                val customer = dao.getCustomerById(sub.customerId)
                    ?: throw IllegalArgumentException("Customer not found")
                val nextDue = Formatters.roundMoney((customer.currentDue - sub.amount).coerceAtLeast(0.0))
                dao.upsertCustomer(customer.copy(currentDue = nextDue))
                dao.insertCustomerLedger(
                    CustomerLedgerEntity(
                        customerId = customer.id,
                        entryType = "PAYMENT",
                        referenceDoc = sub.transactionRef.ifBlank { payment.id.take(8) },
                        debitAmount = 0.0,
                        creditAmount = sub.amount,
                        balanceAfter = nextDue,
                        notes = "Approved ${sub.paymentMethod} collection",
                        createdByUserId = actor.id
                    )
                )

                if (sub.saleId.isNotBlank()) {
                    val sale = dao.getSaleById(sub.saleId)
                    if (sale != null) {
                        val newPaid = Formatters.roundMoney(sale.paidAmount + sub.amount)
                        val newSaleDue = Formatters.roundMoney((sale.grandTotal - newPaid).coerceAtLeast(0.0))
                        dao.upsertSale(sale.copy(paidAmount = newPaid, dueAmount = newSaleDue))
                    }
                }

                // Balanced double-entry: Debit Cash/Bank, Credit Accounts Receivable
                postBalancedEntry(
                    referenceDoc = sub.transactionRef.ifBlank { payment.id.take(8) },
                    description = "Customer payment approved (${customer.name})",
                    lines = listOf(
                        Triple("coa_1001", "Cash & Bank Accounts", sub.amount to 0.0),
                        Triple("coa_1100", "Accounts Receivable", 0.0 to sub.amount)
                    ),
                    userId = actor.id
                )
            }

            dao.insertNotification(
                NotificationEntity(
                    targetAudience = "USER:${sub.submittedByUserId}",
                    title = if (approve) "Payment Submission Approved" else "Payment Submission Rejected",
                    message = if (approve) "Your payment collection submission was approved." else "Your payment collection submission was rejected${if (reviewNote.isNotBlank()) ": $reviewNote" else "."}",
                    category = "PAYMENT_STATUS",
                    referenceId = sub.id
                )
            )
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id, userName = actor.fullName, userRole = actor.role.name,
                    action = if (approve) "APPROVE_PAYMENT" else "REJECT_PAYMENT",
                    module = "PAYMENTS", recordId = sub.id, previousValue = "PENDING", newValue = finalSub.status
                )
            )
            finalSub
        }
        Result.success(updated)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- Restricted Financial Ledgers ---
    fun observeCustomerLedger(session: SessionUser, customerId: String): Flow<List<CustomerLedgerEntity>> {
        if (!session.has(AppPermission.VIEW_CUSTOMER_LEDGER)) {
            throw ErpSecurityException("Customer financial ledger is strictly restricted to authorized Admins")
        }
        return dao.observeCustomerLedger(customerId)
    }

    fun observeSuppliers(session: SessionUser): Flow<List<SupplierEntity>> {
        if (!session.has(AppPermission.VIEW_SUPPLIERS)) throw ErpSecurityException("Supplier access denied")
        return dao.observeSuppliers().map { list ->
            if (session.has(AppPermission.VIEW_SUPPLIER_LEDGER)) list
            else list.map { it.copy(openingBalance = 0.0, currentPayable = 0.0) }
        }
    }

    fun observeSupplierLedger(session: SessionUser, supplierId: String): Flow<List<SupplierLedgerEntity>> {
        if (!session.has(AppPermission.VIEW_SUPPLIER_LEDGER)) {
            throw ErpSecurityException("Supplier financial ledger is restricted to authorized Admins")
        }
        return dao.observeSupplierLedger(supplierId)
    }

    fun observePurchaseOrders(session: SessionUser): Flow<List<PurchaseOrderEntity>> {
        if (!session.has(AppPermission.VIEW_SUPPLIERS) && !session.has(AppPermission.MANAGE_PURCHASES)) {
            throw ErpSecurityException("Purchase order access denied")
        }
        return dao.observePurchaseOrders().map { list ->
            when {
                session.canViewFinancials -> list
                session.has(AppPermission.VIEW_PURCHASE_PRICES) -> list.map {
                    it.copy(paidAmount = 0.0, payableAmount = 0.0)
                }
                else -> list.map {
                    it.copy(unitPrice = 0.0, discount = 0.0, additionalCharges = 0.0, totalAmount = 0.0, paidAmount = 0.0, payableAmount = 0.0)
                }
            }
        }
    }

    // --- Suppliers & Purchases ---
    suspend fun createSupplier(
        actor: SessionUser,
        name: String,
        companyName: String,
        phone: String,
        address: String,
        openingBalance: Double
    ): Result<SupplierEntity> = try {
        if (!actor.has(AppPermission.MANAGE_PURCHASES) && !actor.has(AppPermission.VIEW_SUPPLIER_LEDGER)) {
            throw ErpSecurityException("Supplier creation not permitted")
        }
        require(name.isNotBlank() && phone.isNotBlank()) { "Supplier name and phone required" }
        val safeOpening = if (actor.canViewFinancials) Formatters.roundMoney(openingBalance.coerceAtLeast(0.0)) else 0.0
        val sup = SupplierEntity(
            name = name.trim(),
            companyName = companyName.trim(),
            phone = phone.trim(),
            address = address.trim(),
            openingBalance = safeOpening,
            currentPayable = safeOpening
        )
        db.withTransaction {
            dao.upsertSupplier(sup)
            if (safeOpening > 0) {
                dao.insertSupplierLedger(
                    SupplierLedgerEntity(
                        supplierId = sup.id, entryType = "OPENING", referenceDoc = "OPENING",
                        creditAmount = safeOpening, debitAmount = 0.0, balanceAfter = safeOpening,
                        notes = "Opening payable", createdByUserId = actor.id
                    )
                )
            }
        }
        Result.success(sup)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun createAndReceivePurchase(
        actor: SessionUser,
        supplierId: String,
        rawMaterialId: String,
        warehouseId: String,
        quantity: Double,
        unitPrice: Double,
        paidAmount: Double,
        receiveImmediately: Boolean
    ): Result<PurchaseOrderEntity> = try {
        if (!actor.has(AppPermission.MANAGE_PURCHASES)) throw ErpSecurityException("Purchase management permission required")
        require(quantity > 0 && unitPrice >= 0) { "Invalid purchase quantity or price" }
        val supplier = dao.getSupplierById(supplierId) ?: throw IllegalArgumentException("Supplier not found")
        val material = dao.getRawMaterialById(rawMaterialId) ?: throw IllegalArgumentException("Raw material not found")

        val po = db.withTransaction {
            val settings = dao.getSettings()
            val poNumber = "${settings?.purchasePrefix ?: "PO-"}${System.currentTimeMillis().toString().takeLast(6)}"
            val total = Formatters.roundMoney(quantity * unitPrice)
            val paid = if (actor.canViewFinancials) Formatters.roundMoney(paidAmount.coerceIn(0.0, total)) else 0.0
            val payable = Formatters.roundMoney(total - paid)
            val entity = PurchaseOrderEntity(
                poNumber = poNumber,
                supplierId = supplierId,
                warehouseId = warehouseId,
                rawMaterialId = rawMaterialId,
                quantity = Formatters.roundQty(quantity),
                unitPrice = Formatters.roundMoney(unitPrice),
                totalAmount = total,
                paidAmount = paid,
                payableAmount = payable,
                status = if (receiveImmediately) "RECEIVED" else "ORDERED",
                createdByUserId = actor.id
            )
            dao.upsertPurchaseOrder(entity)
            if (receiveImmediately) {
                applyPurchaseReceipt(actor, entity, supplier, material)
            }
            entity
        }
        Result.success(po)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun receivePurchaseOrder(actor: SessionUser, poId: String): Result<Unit> = try {
        if (!actor.has(AppPermission.MANAGE_PURCHASES)) throw ErpSecurityException("Purchase receiving permission required")
        val po = dao.getPurchaseOrderById(poId) ?: throw IllegalArgumentException("Purchase order not found")
        if (po.status == "RECEIVED") throw IllegalStateException("Purchase order already received")
        val supplier = dao.getSupplierById(po.supplierId) ?: throw IllegalArgumentException("Supplier not found")
        val material = dao.getRawMaterialById(po.rawMaterialId) ?: throw IllegalArgumentException("Raw material not found")

        db.withTransaction {
            val updated = po.copy(status = "RECEIVED")
            dao.upsertPurchaseOrder(updated)
            applyPurchaseReceipt(actor, updated, supplier, material)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun applyPurchaseReceipt(
        actor: SessionUser,
        po: PurchaseOrderEntity,
        supplier: SupplierEntity,
        material: RawMaterialEntity
    ) {
        val nextQty = Formatters.roundQty(material.availableQuantity + po.quantity)
        dao.upsertRawMaterial(material.copy(availableQuantity = nextQty, purchasePrice = po.unitPrice))
        dao.insertMaterialLedger(
            MaterialLedgerEntity(
                rawMaterialId = material.id,
                warehouseId = po.warehouseId,
                movementType = "PURCHASE",
                quantityDelta = po.quantity,
                balanceAfter = nextQty,
                referenceDoc = po.poNumber,
                userId = actor.id,
                notes = "Received from ${supplier.name}"
            )
        )
        val nextPayable = Formatters.roundMoney(supplier.currentPayable + po.payableAmount)
        dao.upsertSupplier(supplier.copy(currentPayable = nextPayable))
        dao.insertSupplierLedger(
            SupplierLedgerEntity(
                supplierId = supplier.id,
                entryType = "PURCHASE",
                referenceDoc = po.poNumber,
                creditAmount = po.totalAmount,
                debitAmount = po.paidAmount,
                balanceAfter = nextPayable,
                notes = "${material.name} (${po.quantity} ${material.unit})",
                createdByUserId = actor.id
            )
        )
        if (po.paidAmount > 0) {
            val cashAcct = dao.getCashAccountById("acct_cash")
            if (cashAcct != null) {
                val nextBal = Formatters.roundMoney(cashAcct.currentBalance - po.paidAmount)
                dao.upsertCashAccount(cashAcct.copy(currentBalance = nextBal))
                dao.insertAccountTransaction(
                    AccountTransactionEntity(
                        cashAccountId = cashAcct.id,
                        txType = "SUPPLIER_PAYMENT",
                        amount = -po.paidAmount,
                        balanceAfter = nextBal,
                        referenceDoc = po.poNumber,
                        notes = "Paid to ${supplier.name}",
                        createdByUserId = actor.id
                    )
                )
            }
        }
        if (po.totalAmount > 0) {
            val lines = mutableListOf(
                Triple("coa_1200", "Raw Material Inventory", po.totalAmount to 0.0)
            )
            if (po.paidAmount > 0) lines.add(Triple("coa_1001", "Cash & Bank Accounts", 0.0 to po.paidAmount))
            if (po.payableAmount > 0) lines.add(Triple("coa_2001", "Accounts Payable", 0.0 to po.payableAmount))
            postBalancedEntry(po.poNumber, "Raw material purchase ${po.poNumber}", lines, actor.id)
        }
        dao.insertNotification(
            NotificationEntity(
                targetAudience = "OPS_AND_ADMIN",
                title = "Purchase Received",
                message = "${po.poNumber}: ${po.quantity} ${material.unit} of ${material.name} added to inventory.",
                category = "PURCHASE",
                referenceId = po.id
            )
        )
    }

    // --- Expenses & Cash/Bank Accounts ---
    fun observeExpenses(session: SessionUser): Flow<List<ExpenseEntity>> = when {
        session.has(AppPermission.VIEW_EXPENSE_TOTALS) || session.has(AppPermission.APPROVE_EXPENSES) -> dao.observeExpenses()
        session.has(AppPermission.SUBMIT_EXPENSE) -> dao.observeMyExpenses(session.id)
        else -> throw ErpSecurityException("Expense access denied")
    }

    suspend fun submitExpense(
        actor: SessionUser,
        category: String,
        amount: Double,
        cashAccountId: String,
        description: String,
        receiptUri: String = ""
    ): Result<ExpenseEntity> = try {
        if (!actor.has(AppPermission.SUBMIT_EXPENSE) && !actor.has(AppPermission.APPROVE_EXPENSES)) {
            throw ErpSecurityException("Expense submission permission required")
        }
        require(amount > 0 && description.isNotBlank()) { "Valid amount and description required" }
        val autoApprove = actor.has(AppPermission.APPROVE_EXPENSES)
        val expense = ExpenseEntity(
            category = category,
            amount = Formatters.roundMoney(amount),
            cashAccountId = cashAccountId.ifBlank { "acct_cash" },
            description = description.trim(),
            receiptUri = receiptUri,
            submittedByUserId = actor.id,
            submittedByName = actor.fullName,
            status = if (autoApprove) "APPROVED" else "PENDING",
            approvedByUserId = if (autoApprove) actor.id else ""
        )
        db.withTransaction {
            dao.upsertExpense(expense)
            if (autoApprove) {
                postApprovedExpense(actor, expense)
            } else {
                dao.insertNotification(
                    NotificationEntity(
                        targetAudience = "ADMIN_ONLY",
                        title = "Expense Request Pending",
                        message = "${actor.fullName} submitted an expense request (${expense.category}).",
                        category = "EXPENSE",
                        referenceId = expense.id
                    )
                )
            }
        }
        Result.success(expense)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun reviewExpense(actor: SessionUser, expenseId: String, approve: Boolean): Result<Unit> = try {
        if (!actor.has(AppPermission.APPROVE_EXPENSES)) {
            throw ErpSecurityException("Only authorized Admins can approve expenses")
        }
        val exp = dao.getExpenseById(expenseId) ?: throw IllegalArgumentException("Expense not found")
        if (exp.status != "PENDING") throw IllegalStateException("Expense already ${exp.status}")
        db.withTransaction {
            val updated = exp.copy(status = if (approve) "APPROVED" else "REJECTED", approvedByUserId = actor.id)
            dao.upsertExpense(updated)
            if (approve) {
                postApprovedExpense(actor, updated)
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun postApprovedExpense(actor: SessionUser, expense: ExpenseEntity) {
        val acct = dao.getCashAccountById(expense.cashAccountId) ?: dao.getCashAccountById("acct_cash")
        if (acct != null) {
            val nextBal = Formatters.roundMoney(acct.currentBalance - expense.amount)
            dao.upsertCashAccount(acct.copy(currentBalance = nextBal))
            dao.insertAccountTransaction(
                AccountTransactionEntity(
                    cashAccountId = acct.id,
                    txType = "EXPENSE",
                    amount = -expense.amount,
                    balanceAfter = nextBal,
                    referenceDoc = "EXP-${expense.id.take(6)}",
                    notes = "${expense.category}: ${expense.description}",
                    createdByUserId = actor.id
                )
            )
        }
        postBalancedEntry(
            referenceDoc = "EXP-${expense.id.take(6)}",
            description = "Expense: ${expense.category} - ${expense.description}",
            lines = listOf(
                Triple("coa_6001", "Operating Expenses", expense.amount to 0.0),
                Triple("coa_1001", "Cash & Bank Accounts", 0.0 to expense.amount)
            ),
            userId = actor.id
        )
    }

    fun observeCashAccounts(session: SessionUser): Flow<List<CashAccountEntity>> {
        if (!session.has(AppPermission.VIEW_CASH_BANK)) {
            throw ErpSecurityException("Cash and bank balances are restricted to authorized Admins")
        }
        return dao.observeCashAccounts()
    }

    fun observeAccountTransactions(session: SessionUser): Flow<List<AccountTransactionEntity>> {
        if (!session.has(AppPermission.VIEW_CASH_BANK)) {
            throw ErpSecurityException("Account statements are restricted to authorized Admins")
        }
        return dao.observeAccountTransactions()
    }

    suspend fun recordCashInOutOrTransfer(
        actor: SessionUser,
        fromAccountId: String,
        toAccountId: String?, // non-null => internal transfer
        amount: Double,
        isCashIn: Boolean,
        notes: String
    ): Result<Unit> = try {
        if (!actor.has(AppPermission.MANAGE_CASH_BANK)) throw ErpSecurityException("Cash/bank management permission required")
        require(amount > 0) { "Amount must be positive" }
        db.withTransaction {
            val ref = "FIN-${System.currentTimeMillis().toString().takeLast(6)}"
            if (toAccountId != null) {
                require(fromAccountId != toAccountId) { "Source and destination accounts must differ" }
                val from = dao.getCashAccountById(fromAccountId) ?: throw IllegalArgumentException("Source account missing")
                val to = dao.getCashAccountById(toAccountId) ?: throw IllegalArgumentException("Target account missing")
                val fromNext = Formatters.roundMoney(from.currentBalance - amount)
                val toNext = Formatters.roundMoney(to.currentBalance + amount)
                dao.upsertCashAccount(from.copy(currentBalance = fromNext))
                dao.upsertCashAccount(to.copy(currentBalance = toNext))
                dao.insertAccountTransaction(
                    AccountTransactionEntity(cashAccountId = from.id, txType = "TRANSFER_OUT", amount = -amount, balanceAfter = fromNext, referenceDoc = ref, notes = "Transfer to ${to.name}: $notes", createdByUserId = actor.id)
                )
                dao.insertAccountTransaction(
                    AccountTransactionEntity(cashAccountId = to.id, txType = "TRANSFER_IN", amount = amount, balanceAfter = toNext, referenceDoc = ref, notes = "Transfer from ${from.name}: $notes", createdByUserId = actor.id)
                )
                // Internal transfer: Asset to Asset (NOT revenue or expense!)
                postBalancedEntry(
                    referenceDoc = ref,
                    description = "Internal transfer ${from.name} -> ${to.name}",
                    lines = listOf(
                        Triple("coa_1001", "Cash & Bank Accounts", amount to 0.0),
                        Triple("coa_1001", "Cash & Bank Accounts", 0.0 to amount)
                    ),
                    userId = actor.id
                )
            } else {
                val acct = dao.getCashAccountById(fromAccountId) ?: throw IllegalArgumentException("Account missing")
                val delta = if (isCashIn) amount else -amount
                val nextBal = Formatters.roundMoney(acct.currentBalance + delta)
                dao.upsertCashAccount(acct.copy(currentBalance = nextBal))
                dao.insertAccountTransaction(
                    AccountTransactionEntity(cashAccountId = acct.id, txType = if (isCashIn) "CASH_IN" else "CASH_OUT", amount = delta, balanceAfter = nextBal, referenceDoc = ref, notes = notes, createdByUserId = actor.id)
                )
                val lines = if (isCashIn) {
                    listOf(Triple("coa_1001", "Cash & Bank Accounts", amount to 0.0), Triple("coa_3001", "Owner Capital & Retained Equity", 0.0 to amount))
                } else {
                    listOf(Triple("coa_3001", "Owner Capital & Retained Equity", amount to 0.0), Triple("coa_1001", "Cash & Bank Accounts", 0.0 to amount))
                }
                postBalancedEntry(ref, notes.ifBlank { if (isCashIn) "Capital Cash In" else "Owner Withdrawal" }, lines, actor.id)
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- Double-Entry Accounting & Statements ---
    fun observeAccountingStatements(session: SessionUser): Flow<AccountingStatements> {
        if (!session.has(AppPermission.VIEW_ACCOUNTING) && !session.has(AppPermission.VIEW_PROFIT_LOSS)) {
            throw ErpSecurityException("Accounting statements are strictly restricted to authorized Admins")
        }
        return combine(
            dao.observeChartOfAccounts(),
            dao.observeJournalLines(),
            dao.observeCashAccounts(),
            dao.observeCustomers(),
            dao.observeSuppliers()
        ) { coa, lines, cashAccounts, customers, suppliers ->
            val linesByAcct = lines.groupBy { it.accountId }
            val trialBalance = coa.map { acct ->
                val acctLines = linesByAcct[acct.id].orEmpty()
                TrialBalanceRow(
                    accountCode = acct.code,
                    accountName = acct.name,
                    category = acct.category,
                    totalDebit = Formatters.roundMoney(acctLines.sumOf { it.debit }),
                    totalCredit = Formatters.roundMoney(acctLines.sumOf { it.credit })
                )
            }.filter { it.totalDebit > 0 || it.totalCredit > 0 }

            val salesRev = Formatters.roundMoney(linesByAcct["coa_4001"].orEmpty().sumOf { it.credit - it.debit })
            val salesRet = Formatters.roundMoney(linesByAcct["coa_4100"].orEmpty().sumOf { it.debit - it.credit })
            val netRev = Formatters.roundMoney(salesRev - salesRet)
            val cogs = Formatters.roundMoney(linesByAcct["coa_5001"].orEmpty().sumOf { it.debit - it.credit })
            val grossProfit = Formatters.roundMoney(netRev - cogs)
            val opExp = Formatters.roundMoney(linesByAcct["coa_6001"].orEmpty().sumOf { it.debit - it.credit })
            val netProfit = Formatters.roundMoney(grossProfit - opExp)

            val cashTotal = Formatters.roundMoney(cashAccounts.sumOf { it.currentBalance })
            val arTotal = Formatters.roundMoney(customers.sumOf { it.currentDue })
            val invRaw = Formatters.roundMoney(linesByAcct["coa_1200"].orEmpty().sumOf { it.debit - it.credit }.coerceAtLeast(0.0))
            val invFg = Formatters.roundMoney(linesByAcct["coa_1210"].orEmpty().sumOf { it.debit - it.credit }.coerceAtLeast(0.0))
            val invTotal = Formatters.roundMoney(invRaw + invFg)
            val totalAssets = Formatters.roundMoney(cashTotal + arTotal + invTotal)
            val apTotal = Formatters.roundMoney(suppliers.sumOf { it.currentPayable })
            val equity = Formatters.roundMoney(totalAssets - apTotal)

            AccountingStatements(
                trialBalance = trialBalance,
                profitAndLoss = ProfitLossStatement(salesRev, salesRet, netRev, cogs, grossProfit, opExp, netProfit),
                balanceSheet = BalanceSheetStatement(cashTotal, arTotal, invTotal, totalAssets, apTotal, apTotal, equity)
            )
        }
    }

    fun observeJournalEntries(session: SessionUser): Flow<List<JournalEntryEntity>> {
        if (!session.has(AppPermission.VIEW_ACCOUNTING)) {
            throw ErpSecurityException("General ledger is restricted to authorized Admins")
        }
        return dao.observeJournalEntries()
    }

    // --- Daily Business Closing ---
    fun observeDailyClosingPreview(session: SessionUser): Flow<DailyClosingPreview> {
        if (!session.has(AppPermission.DAILY_CLOSING)) {
            throw ErpSecurityException("Daily Business Closing is restricted to authorized Admins")
        }
        return combine(
            dao.observeCashAccounts(),
            dao.observeAccountTransactions(),
            dao.observeDailyClosings()
        ) { accounts, txs, closings ->
            val startOfDay = startOfDayMillis()
            val todayTxs = txs.filter { it.createdAt >= startOfDay }
            val cashAcctIds = accounts.filter { it.accountType == "CASH" }.map { it.id }.toSet()
            val bankAcctIds = accounts.filter { it.accountType != "CASH" }.map { it.id }.toSet()

            val cashSales = todayTxs.filter { it.txType == "SALE" && it.cashAccountId in cashAcctIds }.sumOf { it.amount }
            val cashCol = todayTxs.filter { it.txType == "COLLECTION" && it.cashAccountId in cashAcctIds }.sumOf { it.amount }
            val bankCol = todayTxs.filter { it.txType in listOf("SALE", "COLLECTION") && it.cashAccountId in bankAcctIds }.sumOf { it.amount }
            val expenses = todayTxs.filter { it.txType == "EXPENSE" }.sumOf { kotlin.math.abs(it.amount) }
            val supPay = todayTxs.filter { it.txType == "SUPPLIER_PAYMENT" }.sumOf { kotlin.math.abs(it.amount) }
            val withdrawals = todayTxs.filter { it.txType == "CASH_OUT" && it.cashAccountId in cashAcctIds }.sumOf { kotlin.math.abs(it.amount) }
            val closingCash = accounts.filter { it.id in cashAcctIds }.sumOf { it.currentBalance }
            val bankBal = accounts.filter { it.id in bankAcctIds }.sumOf { it.currentBalance }
            val openingCash = Formatters.roundMoney(closingCash - cashSales - cashCol + expenses + supPay + withdrawals)

            DailyClosingPreview(
                openingCash = openingCash,
                cashSales = Formatters.roundMoney(cashSales),
                cashCollections = Formatters.roundMoney(cashCol),
                bankCollections = Formatters.roundMoney(bankCol),
                totalExpenses = Formatters.roundMoney(expenses),
                supplierPayments = Formatters.roundMoney(supPay),
                cashWithdrawals = Formatters.roundMoney(withdrawals),
                closingCash = Formatters.roundMoney(closingCash),
                bankBalance = Formatters.roundMoney(bankBal),
                previousClosings = closings
            )
        }
    }

    suspend fun executeDailyClosing(actor: SessionUser, notes: String): Result<DailyClosingEntity> = try {
        if (!actor.has(AppPermission.DAILY_CLOSING)) {
            throw ErpSecurityException("Daily closing permission required")
        }
        val preview = observeDailyClosingPreview(actor).first()
        val entity = DailyClosingEntity(
            openingCash = preview.openingCash,
            cashSales = preview.cashSales,
            cashCollections = preview.cashCollections,
            bankCollections = preview.bankCollections,
            totalExpenses = preview.totalExpenses,
            supplierPayments = preview.supplierPayments,
            cashWithdrawals = preview.cashWithdrawals,
            closingCash = preview.closingCash,
            bankBalance = preview.bankBalance,
            closedByUserId = actor.id,
            notes = notes.trim()
        )
        db.withTransaction {
            dao.insertDailyClosing(entity)
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id, userName = actor.fullName, userRole = actor.role.name,
                    action = "DAILY_CLOSING", module = "CLOSING", recordId = entity.id,
                    newValue = "Closing Cash: ${entity.closingCash}"
                )
            )
        }
        Result.success(entity)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- Role-Protected CSV Report Export ---
    suspend fun generateReportCsv(actor: SessionUser, type: ReportType): Result<Pair<String, String>> = try {
        if (!actor.has(AppPermission.EXPORT_REPORTS)) {
            throw ErpSecurityException("Report export permission denied")
        }
        when (type) {
            ReportType.FINANCIAL_SALES_LEDGER,
            ReportType.FINANCIAL_PROFIT_LOSS,
            ReportType.FINANCIAL_CUSTOMER_DUES -> {
                if (!actor.canViewFinancials) {
                    throw ErpSecurityException("Financial exports are strictly prohibited for ${actor.role.name}")
                }
            }
            else -> Unit
        }
        val sb = StringBuilder()
        val filename = when (type) {
            ReportType.OPERATIONAL_PRODUCTION -> {
                sb.appendLine("Batch,Date,ModelId,Planned,Actual,Good,Rejected,Status")
                dao.observeProductionBatches().first().forEach {
                    sb.appendLine("${it.batchNumber},${Formatters.formatDate(it.productionDate)},${it.modelId},${it.plannedQty},${it.actualQty},${it.goodQty},${it.rejectedQty},${it.status}")
                }
                "production_report.csv"
            }
            ReportType.OPERATIONAL_STOCK -> {
                sb.appendLine("ModelCode,ModelName,WarehouseId,GoodQty,DamagedQty,ScrapQty")
                val models = dao.observeModels().first().associateBy { it.id }
                dao.observeInventoryBalances().first().forEach {
                    val m = models[it.modelId]
                    sb.appendLine("${m?.modelCode ?: it.modelId},${m?.modelName ?: ""},${it.warehouseId},${it.goodQty},${it.damagedQty},${it.scrapQty}")
                }
                "inventory_stock_report.csv"
            }
            ReportType.SALES_ORDERS_OPERATIONAL -> {
                sb.appendLine("Invoice,Type,Date,DeliveryStatus")
                dao.observeSales().first().forEach {
                    sb.appendLine("${it.invoiceNumber},${it.saleType},${Formatters.formatDate(it.saleDate)},${it.deliveryStatus}")
                }
                "operational_orders_report.csv"
            }
            ReportType.FINANCIAL_SALES_LEDGER -> {
                sb.appendLine("Invoice,Type,Date,Subtotal,Discount,GrandTotal,Paid,Due,Status")
                dao.observeSales().first().forEach {
                    sb.appendLine("${it.invoiceNumber},${it.saleType},${Formatters.formatDate(it.saleDate)},${it.subtotal},${it.discount},${it.grandTotal},${it.paidAmount},${it.dueAmount},${it.deliveryStatus}")
                }
                "financial_sales_report.csv"
            }
            ReportType.FINANCIAL_CUSTOMER_DUES -> {
                sb.appendLine("Customer,Type,Phone,OpeningBalance,CurrentDue")
                dao.observeCustomers().first().forEach {
                    sb.appendLine("${it.name},${it.customerType},${it.phone},${it.openingBalance},${it.currentDue}")
                }
                "customer_dues_report.csv"
            }
            ReportType.FINANCIAL_PROFIT_LOSS -> {
                val st = observeAccountingStatements(actor).first().profitAndLoss
                sb.appendLine("Metric,AmountBDT")
                sb.appendLine("Sales Revenue,${st.salesRevenue}")
                sb.appendLine("Sales Returns,${st.salesReturns}")
                sb.appendLine("Net Revenue,${st.netRevenue}")
                sb.appendLine("Cost of Goods Sold,${st.costOfGoodsSold}")
                sb.appendLine("Gross Profit,${st.grossProfit}")
                sb.appendLine("Operating Expenses,${st.operatingExpenses}")
                sb.appendLine("Net Profit,${st.netProfit}")
                "profit_and_loss_report.csv"
            }
        }
        dao.insertAuditLog(
            AuditLogEntity(userId = actor.id, userName = actor.fullName, userRole = actor.role.name, action = "EXPORT_REPORT", module = "REPORTS", recordId = type.name, newValue = filename)
        )
        Result.success(filename to sb.toString())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun postBalancedEntry(
        referenceDoc: String,
        description: String,
        lines: List<Triple<String, String, Pair<Double, Double>>>,
        userId: String
    ) {
        val totalDr = Formatters.roundMoney(lines.sumOf { it.third.first })
        val totalCr = Formatters.roundMoney(lines.sumOf { it.third.second })
        require(kotlin.math.abs(totalDr - totalCr) < 0.01) {
            "Double-entry imbalance: Debit ($totalDr) != Credit ($totalCr)"
        }
        val jeId = UUID.randomUUID().toString()
        dao.insertJournalEntry(
            JournalEntryEntity(
                id = jeId,
                entryNumber = "JE-${System.currentTimeMillis().toString().takeLast(6)}",
                referenceDoc = referenceDoc,
                description = description,
                totalDebit = totalDr,
                totalCredit = totalCr,
                createdByUserId = userId
            )
        )
        dao.insertJournalLines(
            lines.map { (acctId, acctName, amounts) ->
                JournalEntryLineEntity(
                    journalEntryId = jeId,
                    accountId = acctId,
                    accountName = acctName,
                    debit = Formatters.roundMoney(amounts.first),
                    credit = Formatters.roundMoney(amounts.second)
                )
            }
        )
    }

    private fun accountIdForMethod(method: String): String = when (method.uppercase()) {
        "BANK" -> "acct_bank"
        "BKASH" -> "acct_bkash"
        "NAGAD" -> "acct_nagad"
        else -> "acct_cash"
    }

    private fun startOfDayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfMonthMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private typealias ProductModelEntityTuplePlaceholder = com.example.data.local.ProductModelEntity
