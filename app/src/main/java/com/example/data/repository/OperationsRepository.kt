package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AccountTransactionEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.BomItemEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerLedgerEntity
import com.example.data.local.EmployeeTaskEntity
import com.example.data.local.ErpDatabase
import com.example.data.local.InventoryBalanceEntity
import com.example.data.local.JournalEntryEntity
import com.example.data.local.JournalEntryLineEntity
import com.example.data.local.MaterialLedgerEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PriceHistoryEntity
import com.example.data.local.ProductEntity
import com.example.data.local.ProductModelEntity
import com.example.data.local.ProductionBatchEntity
import com.example.data.local.RawMaterialEntity
import com.example.data.local.SaleEntity
import com.example.data.local.SaleItemEntity
import com.example.data.local.SalesReturnEntity
import com.example.data.local.StockMovementEntity
import com.example.data.local.WarehouseEntity
import com.example.domain.AppPermission
import com.example.domain.Formatters
import com.example.domain.SessionUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

data class SaleItemRequest(
    val modelId: String,
    val quantity: Double,
    val customUnitPrice: Double? = null,
    val discount: Double = 0.0
)

class OperationsRepository(private val db: ErpDatabase) {
    private val dao = db.erpDao()

    // --- Observers ---
    fun observeProducts(): Flow<List<ProductEntity>> = dao.observeProducts()
    fun observeModels(): Flow<List<ProductModelEntity>> = dao.observeModels()
    fun observeBomItems(): Flow<List<BomItemEntity>> = dao.observeAllBomItems()
    fun observeWarehouses(): Flow<List<WarehouseEntity>> = dao.observeWarehouses()
    fun observeRawMaterials(): Flow<List<RawMaterialEntity>> = dao.observeRawMaterials()
    fun observeMaterialLedger(): Flow<List<MaterialLedgerEntity>> = dao.observeMaterialLedger()
    fun observeProductionBatches(): Flow<List<ProductionBatchEntity>> = dao.observeProductionBatches()
    fun observeInventoryBalances(): Flow<List<InventoryBalanceEntity>> = dao.observeInventoryBalances()
    fun observeStockMovements(): Flow<List<StockMovementEntity>> = dao.observeStockMovements()
    fun observeSalesReturns(): Flow<List<SalesReturnEntity>> = dao.observeSalesReturns()
    fun observeAllSaleItems(): Flow<List<SaleItemEntity>> = dao.observeAllSaleItems()

    fun observeTasks(session: SessionUser): Flow<List<EmployeeTaskEntity>> =
        if (session.has(AppPermission.MANAGE_TASKS)) dao.observeAllTasks()
        else dao.observeMyTasks(session.id)

    fun observeCustomersSanitized(session: SessionUser): Flow<List<CustomerSanitizedView>> {
        if (!session.has(AppPermission.VIEW_CUSTOMERS)) {
            throw ErpSecurityException("Customer access denied")
        }
        val canSeeContact = session.has(AppPermission.VIEW_CUSTOMER_CONTACT)
        val canSeeDue = session.has(AppPermission.VIEW_CUSTOMER_LEDGER)
        return dao.observeCustomers().map { list ->
            list.map { c ->
                CustomerSanitizedView(
                    id = c.id,
                    name = c.name,
                    businessName = c.businessName,
                    phone = if (canSeeContact) c.phone else null,
                    address = if (canSeeContact) c.address else null,
                    customerType = c.customerType,
                    status = c.status,
                    currentDue = if (canSeeDue) c.currentDue else null
                )
            }
        }
    }

    fun observeSalesSanitized(session: SessionUser): Flow<List<SaleSanitizedView>> {
        val canSeePrices = session.has(AppPermission.VIEW_UNIT_PRICES) || session.canViewFinancials
        return combine(dao.observeSales(), dao.observeCustomers(), dao.observeAllSaleItems()) { sales, customers, items ->
            val customerNames = customers.associateBy({ it.id }, { it.name })
            val itemsBySale = items.groupBy { it.saleId }
            sales.map { sale ->
                val qtySum = itemsBySale[sale.id]?.sumOf { it.quantity } ?: 0.0
                SaleSanitizedView(
                    id = sale.id,
                    invoiceNumber = sale.invoiceNumber,
                    saleType = sale.saleType,
                    customerId = sale.customerId,
                    customerName = customerNames[sale.customerId] ?: "Walk-in Customer",
                    warehouseId = sale.warehouseId,
                    totalItemQty = Formatters.roundQty(qtySum),
                    deliveryStatus = sale.deliveryStatus,
                    saleDate = sale.saleDate,
                    subtotal = if (canSeePrices) sale.subtotal else null,
                    discount = if (canSeePrices) sale.discount else null,
                    grandTotal = if (canSeePrices) sale.grandTotal else null,
                    paidAmount = if (canSeePrices) sale.paidAmount else null,
                    dueAmount = if (canSeePrices) sale.dueAmount else null
                )
            }
        }
    }

    // --- Catalog & BOM ---
    suspend fun saveProduct(
        actor: SessionUser,
        name: String,
        category: String,
        description: String,
        unit: String,
        imageUri: String = ""
    ): Result<ProductEntity> = try {
        if (!actor.has(AppPermission.MANAGE_PRODUCTS)) throw ErpSecurityException("Product management permission required")
        require(name.isNotBlank()) { "Product name is required" }
        val product = ProductEntity(
            name = name.trim(),
            category = category.trim().ifBlank { "General" },
            description = description.trim(),
            unit = unit.trim().ifBlank { "pcs" },
            imageUri = imageUri
        )
        db.withTransaction {
            dao.upsertProduct(product)
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id, userName = actor.fullName, userRole = actor.role.name,
                    action = "CREATE_PRODUCT", module = "PRODUCTS", recordId = product.id, newValue = product.name
                )
            )
        }
        Result.success(product)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun saveProductModel(
        actor: SessionUser,
        existingId: String? = null,
        productId: String,
        modelName: String,
        modelCode: String,
        barcode: String,
        productionCost: Double,
        wholesalePrice: Double,
        retailPrice: Double,
        minimumStock: Double,
        imageUri: String = ""
    ): Result<ProductModelEntity> = try {
        if (!actor.has(AppPermission.MANAGE_PRODUCTS)) throw ErpSecurityException("Model management permission required")
        require(productId.isNotBlank() && modelName.isNotBlank() && modelCode.isNotBlank()) { "Model details required" }
        require(productionCost >= 0 && wholesalePrice >= 0 && retailPrice >= 0) { "Prices cannot be negative" }

        val existing = existingId?.let { dao.getModelById(it) }
        val model = ProductModelEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            productId = productId,
            modelName = modelName.trim(),
            modelCode = modelCode.trim().uppercase(),
            barcode = barcode.trim(),
            imageUri = imageUri.ifBlank { existing?.imageUri ?: "" },
            productionCost = Formatters.roundMoney(productionCost),
            wholesalePrice = Formatters.roundMoney(wholesalePrice),
            retailPrice = Formatters.roundMoney(retailPrice),
            minimumStock = Formatters.roundQty(minimumStock),
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        db.withTransaction {
            dao.upsertModel(model)
            if (existing != null && (existing.wholesalePrice != model.wholesalePrice || existing.retailPrice != model.retailPrice || existing.productionCost != model.productionCost)) {
                dao.insertPriceHistory(
                    PriceHistoryEntity(
                        modelId = model.id,
                        oldCost = existing.productionCost,
                        newCost = model.productionCost,
                        oldWholesale = existing.wholesalePrice,
                        newWholesale = model.wholesalePrice,
                        oldRetail = existing.retailPrice,
                        newRetail = model.retailPrice,
                        changedByUserId = actor.id
                    )
                )
            }
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id, userName = actor.fullName, userRole = actor.role.name,
                    action = if (existing == null) "CREATE_MODEL" else "UPDATE_MODEL",
                    module = "MODELS", recordId = model.id, newValue = "${model.modelName} (${model.modelCode})"
                )
            )
        }
        Result.success(model)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun saveBomItem(
        actor: SessionUser,
        modelId: String,
        rawMaterialId: String,
        quantityPerUnit: Double
    ): Result<Unit> = try {
        if (!actor.has(AppPermission.MANAGE_PRODUCTS)) throw ErpSecurityException("BOM permission required")
        require(quantityPerUnit > 0) { "BOM quantity per unit must be greater than zero" }
        dao.upsertBomItem(BomItemEntity(modelId = modelId, rawMaterialId = rawMaterialId, quantityPerUnit = Formatters.roundQty(quantityPerUnit)))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteBomItem(actor: SessionUser, bomId: String): Result<Unit> = try {
        if (!actor.has(AppPermission.MANAGE_PRODUCTS)) throw ErpSecurityException("BOM permission required")
        dao.deleteBomItem(bomId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- Warehouses & Raw Materials ---
    suspend fun createWarehouse(actor: SessionUser, name: String, location: String): Result<WarehouseEntity> = try {
        if (!actor.has(AppPermission.MANAGE_SETTINGS) && !actor.has(AppPermission.TRANSFER_STOCK)) {
            throw ErpSecurityException("Warehouse creation not permitted")
        }
        require(name.isNotBlank()) { "Warehouse name required" }
        val wh = WarehouseEntity(name = name.trim(), location = location.trim())
        dao.upsertWarehouse(wh)
        Result.success(wh)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun saveRawMaterial(
        actor: SessionUser,
        name: String,
        materialCode: String,
        category: String,
        unit: String,
        purchasePrice: Double,
        warehouseId: String,
        initialQty: Double,
        minimumStock: Double
    ): Result<RawMaterialEntity> = try {
        if (!actor.has(AppPermission.MANAGE_RAW_MATERIALS)) throw ErpSecurityException("Raw material permission required")
        require(name.isNotBlank() && materialCode.isNotBlank()) { "Material name and code required" }
        require(initialQty >= 0 && purchasePrice >= 0) { "Quantity and price must be non-negative" }
        val mat = RawMaterialEntity(
            name = name.trim(),
            materialCode = materialCode.trim().uppercase(),
            category = category.trim().ifBlank { "Raw Material" },
            unit = unit.trim().ifBlank { "kg" },
            purchasePrice = Formatters.roundMoney(purchasePrice),
            warehouseId = warehouseId,
            availableQuantity = Formatters.roundQty(initialQty),
            minimumStock = Formatters.roundQty(minimumStock)
        )
        db.withTransaction {
            dao.upsertRawMaterial(mat)
            if (initialQty > 0) {
                dao.insertMaterialLedger(
                    MaterialLedgerEntity(
                        rawMaterialId = mat.id,
                        warehouseId = warehouseId,
                        movementType = "OPENING",
                        quantityDelta = mat.availableQuantity,
                        balanceAfter = mat.availableQuantity,
                        referenceDoc = mat.materialCode,
                        userId = actor.id,
                        notes = "Opening stock"
                    )
                )
            }
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id, userName = actor.fullName, userRole = actor.role.name,
                    action = "CREATE_RAW_MATERIAL", module = "RAW_MATERIALS", recordId = mat.id, newValue = mat.name
                )
            )
        }
        Result.success(mat)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- Production Workflow (Draft -> Submitted -> Reviewed -> Approved -> Completed) ---
    suspend fun createProductionBatch(
        actor: SessionUser,
        modelId: String,
        warehouseId: String,
        plannedQty: Double,
        actualQty: Double,
        rejectedQty: Double,
        wastageQty: Double,
        laborCost: Double,
        machineCost: Double,
        electricityCost: Double,
        otherCost: Double,
        notes: String
    ): Result<ProductionBatchEntity> = try {
        if (!actor.has(AppPermission.CREATE_PRODUCTION)) throw ErpSecurityException("Production creation permission required")
        val model = dao.getModelById(modelId) ?: throw IllegalArgumentException("Model not found")
        require(plannedQty > 0 && actualQty >= 0 && rejectedQty >= 0) { "Invalid production quantities" }
        require(rejectedQty <= actualQty) { "Rejected quantity cannot exceed actual production quantity" }
        val goodQty = Formatters.roundQty(actualQty - rejectedQty)
        val bom = dao.getBomForModel(modelId)
        var estMaterialCost = 0.0
        for (item in bom) {
            val mat = dao.getRawMaterialById(item.rawMaterialId)
            if (mat != null) {
                estMaterialCost += item.quantityPerUnit * actualQty * mat.purchasePrice
            }
        }
        val totalCost = Formatters.roundMoney(estMaterialCost + laborCost + machineCost + electricityCost + otherCost)
        val batch = ProductionBatchEntity(
            batchNumber = "PB-${System.currentTimeMillis().toString().takeLast(6)}",
            productId = model.productId,
            modelId = modelId,
            warehouseId = warehouseId,
            plannedQty = Formatters.roundQty(plannedQty),
            actualQty = Formatters.roundQty(actualQty),
            rejectedQty = Formatters.roundQty(rejectedQty),
            goodQty = goodQty,
            wastageQty = Formatters.roundQty(wastageQty),
            materialCost = Formatters.roundMoney(estMaterialCost),
            laborCost = Formatters.roundMoney(laborCost),
            machineCost = Formatters.roundMoney(machineCost),
            electricityCost = Formatters.roundMoney(electricityCost),
            otherCost = Formatters.roundMoney(otherCost),
            totalCost = totalCost,
            status = "SUBMITTED",
            stockApplied = false,
            responsibleUserId = actor.id,
            notes = notes.trim()
        )
        db.withTransaction {
            dao.upsertProductionBatch(batch)
            dao.insertNotification(
                NotificationEntity(
                    targetAudience = "OPS_AND_ADMIN",
                    title = "Production Batch Submitted",
                    message = "Batch ${batch.batchNumber} for ${model.modelName} awaiting review (${Formatters.formatQty(batch.goodQty)} good).",
                    category = "PRODUCTION",
                    referenceId = batch.id
                )
            )
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id, userName = actor.fullName, userRole = actor.role.name,
                    action = "SUBMIT_PRODUCTION", module = "PRODUCTION", recordId = batch.id, newValue = batch.batchNumber
                )
            )
        }
        Result.success(batch)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun advanceProductionBatch(
        actor: SessionUser,
        batchId: String,
        targetStatus: String
    ): Result<ProductionBatchEntity> = try {
        val batch = dao.getProductionBatchById(batchId) ?: throw IllegalArgumentException("Batch not found")
        val requiresApprovalPerm = targetStatus == "APPROVED" || targetStatus == "COMPLETED"
        if (requiresApprovalPerm && !actor.has(AppPermission.APPROVE_PRODUCTION)) {
            throw ErpSecurityException("Production approval permission required")
        }
        if (batch.status == "COMPLETED" || (targetStatus == "COMPLETED" && batch.stockApplied)) {
            throw IllegalStateException("Batch is already completed and stock has been applied")
        }
        val model = dao.getModelById(batch.modelId) ?: throw IllegalArgumentException("Model not found")

        val updated = db.withTransaction {
            var computedMaterialCost = batch.materialCost
            var currentBatch = batch
            if (targetStatus == "COMPLETED") {
                if (batch.stockApplied) throw IllegalStateException("Duplicate stock application prevented")
                // 1. Deduct raw materials per BOM + wastage proportionally
                val bomItems = dao.getBomForModel(batch.modelId)
                var actualMatCost = 0.0
                for (bom in bomItems) {
                    val mat = dao.getRawMaterialById(bom.rawMaterialId)
                        ?: throw IllegalStateException("BOM raw material missing")
                    val requiredQty = Formatters.roundQty((bom.quantityPerUnit * batch.actualQty) + (batch.wastageQty / bomItems.size.coerceAtLeast(1)))
                    if (mat.availableQuantity < requiredQty) {
                        throw IllegalStateException("Insufficient raw material '${mat.name}': need $requiredQty ${mat.unit}, available ${mat.availableQuantity}")
                    }
                    val newMatBal = Formatters.roundQty(mat.availableQuantity - requiredQty)
                    dao.upsertRawMaterial(mat.copy(availableQuantity = newMatBal))
                    dao.insertMaterialLedger(
                        MaterialLedgerEntity(
                            rawMaterialId = mat.id,
                            warehouseId = batch.warehouseId,
                            movementType = "CONSUMPTION",
                            quantityDelta = -requiredQty,
                            balanceAfter = newMatBal,
                            referenceDoc = batch.batchNumber,
                            userId = actor.id,
                            notes = "Consumed in ${model.modelName}"
                        )
                    )
                    actualMatCost += requiredQty * mat.purchasePrice
                    if (newMatBal <= mat.minimumStock) {
                        dao.insertNotification(
                            NotificationEntity(
                                targetAudience = "OPS_AND_ADMIN",
                                title = "Low Raw Material Stock",
                                message = "${mat.name} (${mat.materialCode}) is at $newMatBal ${mat.unit} (min ${mat.minimumStock}).",
                                category = "LOW_STOCK",
                                referenceId = mat.id
                            )
                        )
                    }
                }
                computedMaterialCost = Formatters.roundMoney(actualMatCost)
                val finalTotalCost = Formatters.roundMoney(computedMaterialCost + batch.laborCost + batch.machineCost + batch.electricityCost + batch.otherCost)

                // 2. Update finished goods stock (goodQty = actual - rejected)
                val existingInv = dao.getInventoryBalance(batch.modelId, batch.warehouseId)
                    ?: InventoryBalanceEntity(id = "${batch.modelId}:${batch.warehouseId}", modelId = batch.modelId, warehouseId = batch.warehouseId)
                val newGoodQty = Formatters.roundQty(existingInv.goodQty + batch.goodQty)
                val newDamaged = Formatters.roundQty(existingInv.damagedQty + batch.rejectedQty)
                dao.upsertInventoryBalance(
                    existingInv.copy(goodQty = newGoodQty, damagedQty = newDamaged, updatedAt = System.currentTimeMillis())
                )
                dao.insertStockMovement(
                    StockMovementEntity(
                        modelId = batch.modelId,
                        warehouseId = batch.warehouseId,
                        quantityDelta = batch.goodQty,
                        movementType = "PRODUCTION_IN",
                        referenceDoc = batch.batchNumber,
                        responsibleUserId = actor.id,
                        notes = "Batch completed (Rejected: ${batch.rejectedQty})"
                    )
                )

                // 3. Weighted average production cost update on model
                if (batch.goodQty > 0 && finalTotalCost > 0) {
                    val prevStock = dao.getInventoryBalancesForModel(batch.modelId).sumOf { it.goodQty } - batch.goodQty
                    val prevValue = prevStock.coerceAtLeast(0.0) * model.productionCost
                    val newUnitCost = Formatters.roundMoney((prevValue + finalTotalCost) / (prevStock.coerceAtLeast(0.0) + batch.goodQty))
                    dao.upsertModel(model.copy(productionCost = newUnitCost))
                }

                // 4. Double-Entry Journal Entry for Completed Production
                if (finalTotalCost > 0) {
                    val jeId = UUID.randomUUID().toString()
                    dao.insertJournalEntry(
                        JournalEntryEntity(
                            id = jeId,
                            entryNumber = "JE-${System.currentTimeMillis().toString().takeLast(6)}",
                            referenceDoc = batch.batchNumber,
                            description = "Production completion: ${model.modelName} (${batch.goodQty} units)",
                            totalDebit = finalTotalCost,
                            totalCredit = finalTotalCost,
                            createdByUserId = actor.id
                        )
                    )
                    val lines = mutableListOf(
                        JournalEntryLineEntity(
                            journalEntryId = jeId,
                            accountId = "coa_1210",
                            accountName = "Finished Goods Inventory",
                            debit = finalTotalCost,
                            credit = 0.0
                        )
                    )
                    if (computedMaterialCost > 0) {
                        lines.add(
                            JournalEntryLineEntity(
                                journalEntryId = jeId,
                                accountId = "coa_1200",
                                accountName = "Raw Material Inventory",
                                debit = 0.0,
                                credit = computedMaterialCost
                            )
                        )
                    }
                    val conversionCost = Formatters.roundMoney(finalTotalCost - computedMaterialCost)
                    if (conversionCost > 0) {
                        lines.add(
                            JournalEntryLineEntity(
                                journalEntryId = jeId,
                                accountId = "coa_5100",
                                accountName = "Production Labor & Overhead",
                                debit = 0.0,
                                credit = conversionCost
                            )
                        )
                    }
                    dao.insertJournalLines(lines)
                }

                currentBatch = batch.copy(
                    materialCost = computedMaterialCost,
                    totalCost = finalTotalCost,
                    status = "COMPLETED",
                    stockApplied = true,
                    supervisorId = actor.id,
                    updatedAt = System.currentTimeMillis()
                )
                dao.insertNotification(
                    NotificationEntity(
                        targetAudience = "OPS_AND_ADMIN",
                        title = "Production Completed",
                        message = "Batch ${batch.batchNumber} added ${Formatters.formatQty(batch.goodQty)} of ${model.modelName} to inventory.",
                        category = "PRODUCTION",
                        referenceId = batch.id
                    )
                )
            } else {
                currentBatch = batch.copy(
                    status = targetStatus,
                    supervisorId = actor.id,
                    updatedAt = System.currentTimeMillis()
                )
            }
            dao.upsertProductionBatch(currentBatch)
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id, userName = actor.fullName, userRole = actor.role.name,
                    action = "PRODUCTION_$targetStatus", module = "PRODUCTION", recordId = batch.id,
                    previousValue = batch.status, newValue = currentBatch.status
                )
            )
            currentBatch
        }
        Result.success(updated)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- Stock Transfers & Adjustments ---
    suspend fun transferStock(
        actor: SessionUser,
        modelId: String,
        fromWarehouseId: String,
        toWarehouseId: String,
        quantity: Double,
        notes: String
    ): Result<Unit> = try {
        if (!actor.has(AppPermission.TRANSFER_STOCK)) throw ErpSecurityException("Stock transfer permission required")
        require(fromWarehouseId != toWarehouseId) { "Source and destination warehouses must differ" }
        require(quantity > 0) { "Transfer quantity must be positive" }

        db.withTransaction {
            val fromBal = dao.getInventoryBalance(modelId, fromWarehouseId)
                ?: throw IllegalStateException("No stock available in source warehouse")
            if (fromBal.goodQty < quantity) {
                throw IllegalStateException("Insufficient stock in source warehouse (available: ${fromBal.goodQty})")
            }
            val toBal = dao.getInventoryBalance(modelId, toWarehouseId)
                ?: InventoryBalanceEntity(id = "$modelId:$toWarehouseId", modelId = modelId, warehouseId = toWarehouseId)
            val now = System.currentTimeMillis()
            dao.upsertInventoryBalance(fromBal.copy(goodQty = Formatters.roundQty(fromBal.goodQty - quantity), updatedAt = now))
            dao.upsertInventoryBalance(toBal.copy(goodQty = Formatters.roundQty(toBal.goodQty + quantity), updatedAt = now))
            val ref = "TRF-${now.toString().takeLast(6)}"
            dao.insertStockMovement(
                StockMovementEntity(modelId = modelId, warehouseId = fromWarehouseId, quantityDelta = -quantity, movementType = "TRANSFER_OUT", referenceDoc = ref, responsibleUserId = actor.id, notes = notes)
            )
            dao.insertStockMovement(
                StockMovementEntity(modelId = modelId, warehouseId = toWarehouseId, quantityDelta = quantity, movementType = "TRANSFER_IN", referenceDoc = ref, responsibleUserId = actor.id, notes = notes)
            )
            dao.insertAuditLog(
                AuditLogEntity(userId = actor.id, userName = actor.fullName, userRole = actor.role.name, action = "STOCK_TRANSFER", module = "INVENTORY", recordId = ref, newValue = "$quantity units")
            )
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun adjustStock(
        actor: SessionUser,
        modelId: String,
        warehouseId: String,
        delta: Double,
        reason: String
    ): Result<Unit> = try {
        if (!actor.has(AppPermission.ADJUST_STOCK)) throw ErpSecurityException("Stock adjustment permission required")
        require(delta != 0.0) { "Adjustment delta cannot be zero" }
        require(reason.isNotBlank()) { "Adjustment reason is required" }

        db.withTransaction {
            val bal = dao.getInventoryBalance(modelId, warehouseId)
                ?: InventoryBalanceEntity(id = "$modelId:$warehouseId", modelId = modelId, warehouseId = warehouseId)
            val newQty = Formatters.roundQty(bal.goodQty + delta)
            if (newQty < 0) throw IllegalStateException("Negative stock is prevented (resulting stock would be $newQty)")
            dao.upsertInventoryBalance(bal.copy(goodQty = newQty, updatedAt = System.currentTimeMillis()))
            val ref = "ADJ-${System.currentTimeMillis().toString().takeLast(6)}"
            dao.insertStockMovement(
                StockMovementEntity(modelId = modelId, warehouseId = warehouseId, quantityDelta = delta, movementType = "ADJUSTMENT", referenceDoc = ref, responsibleUserId = actor.id, notes = reason)
            )
            dao.insertAuditLog(
                AuditLogEntity(userId = actor.id, userName = actor.fullName, userRole = actor.role.name, action = "STOCK_ADJUSTMENT", module = "INVENTORY", recordId = ref, newValue = "$delta ($reason)")
            )
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- Customers ---
    suspend fun createCustomer(
        actor: SessionUser,
        name: String,
        businessName: String,
        phone: String,
        address: String,
        customerType: String,
        openingBalance: Double,
        creditLimit: Double
    ): Result<CustomerEntity> = try {
        if (!actor.has(AppPermission.MANAGE_CUSTOMERS) && !actor.has(AppPermission.CREATE_SALES)) {
            throw ErpSecurityException("Customer creation permission required")
        }
        require(name.isNotBlank() && phone.isNotBlank()) { "Customer name and phone are required" }
        val safeOpening = if (actor.canViewFinancials) Formatters.roundMoney(openingBalance.coerceAtLeast(0.0)) else 0.0
        val customer = CustomerEntity(
            name = name.trim(),
            businessName = businessName.trim(),
            phone = phone.trim(),
            address = address.trim(),
            customerType = customerType,
            openingBalance = safeOpening,
            creditLimit = if (actor.canViewFinancials) Formatters.roundMoney(creditLimit.coerceAtLeast(0.0)) else 0.0,
            currentDue = safeOpening
        )
        db.withTransaction {
            dao.upsertCustomer(customer)
            if (safeOpening > 0) {
                dao.insertCustomerLedger(
                    CustomerLedgerEntity(
                        customerId = customer.id,
                        entryType = "OPENING",
                        referenceDoc = "OPENING",
                        debitAmount = safeOpening,
                        creditAmount = 0.0,
                        balanceAfter = safeOpening,
                        notes = "Opening balance",
                        createdByUserId = actor.id
                    )
                )
            }
            dao.insertNotification(
                NotificationEntity(
                    targetAudience = "OPS_AND_ADMIN",
                    title = "New Customer Registered",
                    message = "${customer.name} (${customer.customerType}) added.",
                    category = "CUSTOMER",
                    referenceId = customer.id
                )
            )
        }
        Result.success(customer)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- Wholesale & Retail Sales ---
    suspend fun createSale(
        actor: SessionUser,
        saleType: String, // WHOLESALE or RETAIL
        customerId: String,
        warehouseId: String,
        items: List<SaleItemRequest>,
        orderDiscount: Double,
        additionalCharges: Double,
        immediatePaidAmount: Double,
        paymentMethod: String,
        deliverImmediately: Boolean,
        notes: String
    ): Result<SaleEntity> = try {
        if (!actor.has(AppPermission.CREATE_SALES)) throw ErpSecurityException("Sales creation permission required")
        if (deliverImmediately && !actor.has(AppPermission.DISPATCH_DELIVERY)) {
            throw ErpSecurityException("Delivery dispatch permission required for immediate delivery")
        }
        require(items.isNotEmpty()) { "At least one product model is required" }
        val customer = dao.getCustomerById(customerId) ?: throw IllegalArgumentException("Customer not found")

        val saleResult = db.withTransaction {
            val settings = dao.getSettings()
            val prefix = settings?.invoicePrefix ?: "INV-"
            val invoiceNumber = "$prefix${System.currentTimeMillis().toString().takeLast(6)}"
            val saleId = UUID.randomUUID().toString()

            val saleItemEntities = mutableListOf<SaleItemEntity>()
            var subtotal = 0.0
            var totalCogs = 0.0

            for (req in items) {
                require(req.quantity > 0) { "Item quantity must be positive" }
                val model = dao.getModelById(req.modelId) ?: throw IllegalArgumentException("Model not found")
                val product = dao.getProductById(model.productId)
                val basePrice = if (saleType == "WHOLESALE") model.wholesalePrice else model.retailPrice
                val unitPrice = Formatters.roundMoney(req.customUnitPrice ?: basePrice)
                val lineTotal = Formatters.roundMoney((unitPrice * req.quantity) - req.discount)
                subtotal += lineTotal
                totalCogs += Formatters.roundMoney(model.productionCost * req.quantity)

                if (deliverImmediately) {
                    val bal = dao.getInventoryBalance(model.id, warehouseId)
                        ?: throw IllegalStateException("No stock in selected warehouse for ${model.modelName}")
                    if (bal.goodQty < req.quantity) {
                        throw IllegalStateException("Insufficient stock for ${model.modelName}: available ${bal.goodQty}, requested ${req.quantity}")
                    }
                    val newQty = Formatters.roundQty(bal.goodQty - req.quantity)
                    dao.upsertInventoryBalance(bal.copy(goodQty = newQty, updatedAt = System.currentTimeMillis()))
                    dao.insertStockMovement(
                        StockMovementEntity(
                            modelId = model.id,
                            warehouseId = warehouseId,
                            quantityDelta = -req.quantity,
                            movementType = "DISPATCH_OUT",
                            referenceDoc = invoiceNumber,
                            responsibleUserId = actor.id,
                            notes = "Immediate delivery ($saleType)"
                        )
                    )
                    if (newQty <= model.minimumStock) {
                        dao.insertNotification(
                            NotificationEntity(
                                targetAudience = "OPS_AND_ADMIN",
                                title = "Low Finished Stock",
                                message = "${model.modelName} (${model.modelCode}) is down to $newQty units.",
                                category = "LOW_STOCK",
                                referenceId = model.id
                            )
                        )
                    }
                }

                saleItemEntities.add(
                    SaleItemEntity(
                        saleId = saleId,
                        modelId = model.id,
                        productNameSnapshot = product?.name ?: "Product",
                        modelNameSnapshot = model.modelName,
                        quantity = Formatters.roundQty(req.quantity),
                        deliveredQty = if (deliverImmediately) Formatters.roundQty(req.quantity) else 0.0,
                        unitPriceSnapshot = unitPrice,
                        costPriceSnapshot = model.productionCost,
                        discount = Formatters.roundMoney(req.discount),
                        lineTotal = lineTotal
                    )
                )
            }

            val grandTotal = Formatters.roundMoney((subtotal - orderDiscount + additionalCharges).coerceAtLeast(0.0))
            val confirmedPaid = if (actor.canViewFinancials || saleType == "RETAIL") {
                Formatters.roundMoney(immediatePaidAmount.coerceIn(0.0, grandTotal))
            } else {
                0.0
            }
            val dueAmount = Formatters.roundMoney(grandTotal - confirmedPaid)

            val sale = SaleEntity(
                id = saleId,
                invoiceNumber = invoiceNumber,
                saleType = saleType,
                customerId = customerId,
                warehouseId = warehouseId,
                subtotal = Formatters.roundMoney(subtotal),
                discount = Formatters.roundMoney(orderDiscount),
                additionalCharges = Formatters.roundMoney(additionalCharges),
                grandTotal = grandTotal,
                paidAmount = confirmedPaid,
                dueAmount = dueAmount,
                deliveryStatus = if (deliverImmediately) "DELIVERED" else "PENDING",
                paymentMethod = paymentMethod,
                createdByUserId = actor.id,
                notes = notes.trim()
            )
            dao.upsertSale(sale)
            dao.upsertSaleItems(saleItemEntities)

            if (deliverImmediately) {
                // Record confirmed sale in customer ledger & accounting
                val newCustDue = Formatters.roundMoney(customer.currentDue + dueAmount)
                dao.upsertCustomer(customer.copy(currentDue = newCustDue))
                dao.insertCustomerLedger(
                    CustomerLedgerEntity(
                        customerId = customerId,
                        entryType = "SALE_INVOICE",
                        referenceDoc = invoiceNumber,
                        debitAmount = grandTotal,
                        creditAmount = confirmedPaid,
                        balanceAfter = newCustDue,
                        notes = "$saleType Sale ($paymentMethod)",
                        createdByUserId = actor.id
                    )
                )
                if (confirmedPaid > 0) {
                    val acctId = accountIdForMethod(paymentMethod)
                    val cashAcct = dao.getCashAccountById(acctId) ?: dao.getCashAccountById("acct_cash")
                    if (cashAcct != null) {
                        val nextBal = Formatters.roundMoney(cashAcct.currentBalance + confirmedPaid)
                        dao.upsertCashAccount(cashAcct.copy(currentBalance = nextBal))
                        dao.insertAccountTransaction(
                            AccountTransactionEntity(
                                cashAccountId = cashAcct.id,
                                txType = "SALE",
                                amount = confirmedPaid,
                                balanceAfter = nextBal,
                                referenceDoc = invoiceNumber,
                                notes = "Retail/Wholesale immediate payment",
                                createdByUserId = actor.id
                            )
                        )
                    }
                }
                postSaleJournal(invoiceNumber, grandTotal, confirmedPaid, dueAmount, totalCogs, actor.id)
            } else {
                dao.insertNotification(
                    NotificationEntity(
                        targetAudience = "OPS_AND_ADMIN",
                        title = "New Sales Order",
                        message = "Order $invoiceNumber queued for delivery (${saleItemEntities.sumOf { it.quantity }} units).",
                        category = "SALES",
                        referenceId = sale.id
                    )
                )
            }

            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id, userName = actor.fullName, userRole = actor.role.name,
                    action = "CREATE_SALE", module = "SALES", recordId = sale.id, newValue = invoiceNumber
                )
            )
            sale
        }
        Result.success(saleResult)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun confirmDelivery(
        actor: SessionUser,
        saleId: String,
        partialFraction: Double = 1.0
    ): Result<SaleEntity> = try {
        if (!actor.has(AppPermission.DISPATCH_DELIVERY)) throw ErpSecurityException("Delivery dispatch permission required")
        val sale = dao.getSaleById(saleId) ?: throw IllegalArgumentException("Sale order not found")
        if (sale.deliveryStatus == "DELIVERED" || sale.deliveryStatus == "CANCELLED") {
            throw IllegalStateException("Order is already ${sale.deliveryStatus}")
        }
        val items = dao.getSaleItems(saleId)
        val customer = dao.getCustomerById(sale.customerId) ?: throw IllegalArgumentException("Customer missing")
        val fraction = partialFraction.coerceIn(0.1, 1.0)

        val updatedSale = db.withTransaction {
            var deliveredValue = 0.0
            var deliveredCogs = 0.0
            val updatedItems = mutableListOf<SaleItemEntity>()

            for (item in items) {
                val remaining = Formatters.roundQty(item.quantity - item.deliveredQty)
                if (remaining <= 0) {
                    updatedItems.add(item)
                    continue
                }
                val qtyToShip = if (fraction >= 0.999) remaining else Formatters.roundQty((remaining * fraction).coerceAtLeast(1.0).coerceAtMost(remaining))
                val bal = dao.getInventoryBalance(item.modelId, sale.warehouseId)
                    ?: throw IllegalStateException("No stock for ${item.modelNameSnapshot} in warehouse")
                if (bal.goodQty < qtyToShip) {
                    throw IllegalStateException("Insufficient stock for ${item.modelNameSnapshot}: need $qtyToShip, available ${bal.goodQty}")
                }
                dao.upsertInventoryBalance(bal.copy(goodQty = Formatters.roundQty(bal.goodQty - qtyToShip), updatedAt = System.currentTimeMillis()))
                dao.insertStockMovement(
                    StockMovementEntity(
                        modelId = item.modelId,
                        warehouseId = sale.warehouseId,
                        quantityDelta = -qtyToShip,
                        movementType = "DISPATCH_OUT",
                        referenceDoc = sale.invoiceNumber,
                        responsibleUserId = actor.id,
                        notes = "Order delivery"
                    )
                )
                val ratio = if (item.quantity > 0) qtyToShip / item.quantity else 0.0
                deliveredValue += Formatters.roundMoney(item.lineTotal * ratio)
                deliveredCogs += Formatters.roundMoney(item.costPriceSnapshot * qtyToShip)
                updatedItems.add(item.copy(deliveredQty = Formatters.roundQty(item.deliveredQty + qtyToShip)))
            }
            dao.upsertSaleItems(updatedItems)

            val allDelivered = updatedItems.all { it.deliveredQty >= it.quantity - 0.001 }
            val newStatus = if (allDelivered) "DELIVERED" else "PARTIAL"
            val updated = sale.copy(deliveryStatus = newStatus)
            dao.upsertSale(updated)

            // Post customer ledger & accounting on first/incremental confirmed delivery
            if (deliveredValue > 0) {
                val effectiveDebit = if (sale.deliveryStatus == "PENDING" && allDelivered) sale.grandTotal else Formatters.roundMoney(deliveredValue)
                val newCustDue = Formatters.roundMoney(customer.currentDue + effectiveDebit)
                dao.upsertCustomer(customer.copy(currentDue = newCustDue))
                dao.insertCustomerLedger(
                    CustomerLedgerEntity(
                        customerId = customer.id,
                        entryType = "SALE_INVOICE",
                        referenceDoc = sale.invoiceNumber,
                        debitAmount = effectiveDebit,
                        creditAmount = 0.0,
                        balanceAfter = newCustDue,
                        notes = "Dispatched ($newStatus)",
                        createdByUserId = actor.id
                    )
                )
                postSaleJournal(sale.invoiceNumber, effectiveDebit, 0.0, effectiveDebit, deliveredCogs, actor.id)
            }

            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id, userName = actor.fullName, userRole = actor.role.name,
                    action = "CONFIRM_DELIVERY", module = "SALES", recordId = sale.id, newValue = newStatus
                )
            )
            updated
        }
        Result.success(updatedSale)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- Sales Returns & Credit Notes ---
    suspend fun processSalesReturn(
        actor: SessionUser,
        saleId: String,
        modelId: String,
        quantity: Double,
        classification: String, // GOOD, DAMAGED, SCRAP
        reason: String
    ): Result<SalesReturnEntity> = try {
        if (!actor.has(AppPermission.MANAGE_RETURNS)) throw ErpSecurityException("Returns permission required")
        require(quantity > 0) { "Return quantity must be positive" }
        val sale = dao.getSaleById(saleId) ?: throw IllegalArgumentException("Sale not found")
        val saleItems = dao.getSaleItems(saleId)
        val item = saleItems.firstOrNull { it.modelId == modelId } ?: throw IllegalArgumentException("Model not on invoice")
        require(quantity <= item.deliveredQty) { "Cannot return more than delivered quantity (${item.deliveredQty})" }

        val ret = db.withTransaction {
            val refundAmount = Formatters.roundMoney(item.unitPriceSnapshot * quantity)
            val costReversal = Formatters.roundMoney(item.costPriceSnapshot * quantity)
            val creditNote = "CN-${System.currentTimeMillis().toString().takeLast(6)}"
            val entity = SalesReturnEntity(
                saleId = saleId,
                customerId = sale.customerId,
                modelId = modelId,
                warehouseId = sale.warehouseId,
                quantity = Formatters.roundQty(quantity),
                classification = classification,
                refundAmount = refundAmount,
                creditNoteNumber = creditNote,
                reason = reason.trim().ifBlank { "Customer return" },
                processedByUserId = actor.id
            )
            dao.insertSalesReturn(entity)

            val bal = dao.getInventoryBalance(modelId, sale.warehouseId)
                ?: InventoryBalanceEntity(id = "$modelId:${sale.warehouseId}", modelId = modelId, warehouseId = sale.warehouseId)
            val updatedBal = when (classification) {
                "GOOD" -> bal.copy(goodQty = Formatters.roundQty(bal.goodQty + quantity), updatedAt = System.currentTimeMillis())
                "DAMAGED" -> bal.copy(damagedQty = Formatters.roundQty(bal.damagedQty + quantity), updatedAt = System.currentTimeMillis())
                else -> bal.copy(scrapQty = Formatters.roundQty(bal.scrapQty + quantity), updatedAt = System.currentTimeMillis())
            }
            dao.upsertInventoryBalance(updatedBal)
            dao.insertStockMovement(
                StockMovementEntity(
                    modelId = modelId,
                    warehouseId = sale.warehouseId,
                    quantityDelta = quantity,
                    movementType = "RETURN_$classification",
                    referenceDoc = creditNote,
                    responsibleUserId = actor.id,
                    notes = reason
                )
            )

            // Adjust customer due & ledger
            val customer = dao.getCustomerById(sale.customerId)
            if (customer != null) {
                val nextDue = Formatters.roundMoney((customer.currentDue - refundAmount).coerceAtLeast(0.0))
                dao.upsertCustomer(customer.copy(currentDue = nextDue))
                dao.insertCustomerLedger(
                    CustomerLedgerEntity(
                        customerId = customer.id,
                        entryType = "RETURN_CREDIT",
                        referenceDoc = creditNote,
                        debitAmount = 0.0,
                        creditAmount = refundAmount,
                        balanceAfter = nextDue,
                        notes = "Return ($classification) against ${sale.invoiceNumber}",
                        createdByUserId = actor.id
                    )
                )
            }

            // Accounting reversal
            val jeId = UUID.randomUUID().toString()
            val totalEntry = Formatters.roundMoney(refundAmount + if (classification == "GOOD") costReversal else 0.0)
            dao.insertJournalEntry(
                JournalEntryEntity(
                    id = jeId,
                    entryNumber = "JE-${System.currentTimeMillis().toString().takeLast(6)}",
                    referenceDoc = creditNote,
                    description = "Sales return for ${sale.invoiceNumber}",
                    totalDebit = totalEntry,
                    totalCredit = totalEntry,
                    createdByUserId = actor.id
                )
            )
            val lines = mutableListOf(
                JournalEntryLineEntity(journalEntryId = jeId, accountId = "coa_4100", accountName = "Sales Returns & Allowances", debit = refundAmount, credit = 0.0),
                JournalEntryLineEntity(journalEntryId = jeId, accountId = "coa_1100", accountName = "Accounts Receivable", debit = 0.0, credit = refundAmount)
            )
            if (classification == "GOOD" && costReversal > 0) {
                lines.add(JournalEntryLineEntity(journalEntryId = jeId, accountId = "coa_1210", accountName = "Finished Goods Inventory", debit = costReversal, credit = 0.0))
                lines.add(JournalEntryLineEntity(journalEntryId = jeId, accountId = "coa_5001", accountName = "Cost of Goods Sold (COGS)", debit = 0.0, credit = costReversal))
            }
            dao.insertJournalLines(lines)
            dao.insertAuditLog(
                AuditLogEntity(userId = actor.id, userName = actor.fullName, userRole = actor.role.name, action = "SALES_RETURN", module = "RETURNS", recordId = entity.id, newValue = "$creditNote ($classification)")
            )
            entity
        }
        Result.success(ret)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- Employee Tasks ---
    suspend fun createTask(
        actor: SessionUser,
        title: String,
        description: String,
        assignedToUserId: String
    ): Result<EmployeeTaskEntity> = try {
        if (!actor.has(AppPermission.MANAGE_TASKS)) throw ErpSecurityException("Task assignment permission required")
        require(title.isNotBlank()) { "Task title required" }
        val task = EmployeeTaskEntity(
            title = title.trim(),
            description = description.trim(),
            assignedToUserId = assignedToUserId,
            assignedByUserId = actor.id
        )
        dao.upsertTask(task)
        dao.insertNotification(
            NotificationEntity(
                targetAudience = "USER:$assignedToUserId",
                title = "New Assigned Task",
                message = task.title,
                category = "TASK",
                referenceId = task.id
            )
        )
        Result.success(task)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateTaskStatus(actor: SessionUser, taskId: String, status: String): Result<Unit> = try {
        val task = dao.getTaskById(taskId) ?: throw IllegalArgumentException("Task not found")
        if (task.assignedToUserId != actor.id && !actor.has(AppPermission.MANAGE_TASKS)) {
            throw ErpSecurityException("Cannot update another employee's task")
        }
        dao.upsertTask(task.copy(status = status))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun postSaleJournal(
        invoiceNumber: String,
        grandTotal: Double,
        paidAmount: Double,
        dueAmount: Double,
        cogs: Double,
        userId: String
    ) {
        if (grandTotal <= 0 && cogs <= 0) return
        val jeId = UUID.randomUUID().toString()
        val totalBalanced = Formatters.roundMoney(grandTotal + cogs)
        dao.insertJournalEntry(
            JournalEntryEntity(
                id = jeId,
                entryNumber = "JE-${System.currentTimeMillis().toString().takeLast(6)}",
                referenceDoc = invoiceNumber,
                description = "Confirmed sale & dispatch $invoiceNumber",
                totalDebit = totalBalanced,
                totalCredit = totalBalanced,
                createdByUserId = userId
            )
        )
        val lines = mutableListOf<JournalEntryLineEntity>()
        if (paidAmount > 0) {
            lines.add(JournalEntryLineEntity(journalEntryId = jeId, accountId = "coa_1001", accountName = "Cash & Bank Accounts", debit = paidAmount, credit = 0.0))
        }
        if (dueAmount > 0) {
            lines.add(JournalEntryLineEntity(journalEntryId = jeId, accountId = "coa_1100", accountName = "Accounts Receivable", debit = dueAmount, credit = 0.0))
        }
        if (grandTotal > 0) {
            lines.add(JournalEntryLineEntity(journalEntryId = jeId, accountId = "coa_4001", accountName = "Sales Revenue", debit = 0.0, credit = grandTotal))
        }
        if (cogs > 0) {
            lines.add(JournalEntryLineEntity(journalEntryId = jeId, accountId = "coa_5001", accountName = "Cost of Goods Sold (COGS)", debit = cogs, credit = 0.0))
            lines.add(JournalEntryLineEntity(journalEntryId = jeId, accountId = "coa_1210", accountName = "Finished Goods Inventory", debit = 0.0, credit = cogs))
        }
        dao.insertJournalLines(lines)
    }

    private fun accountIdForMethod(method: String): String = when (method.uppercase()) {
        "BANK" -> "acct_bank"
        "BKASH" -> "acct_bkash"
        "NAGAD" -> "acct_nagad"
        else -> "acct_cash"
    }
}
