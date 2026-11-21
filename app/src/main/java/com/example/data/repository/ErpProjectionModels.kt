package com.example.data.repository

import com.example.data.local.CashAccountEntity
import com.example.data.local.DailyClosingEntity
import com.example.data.local.InventoryBalanceEntity
import com.example.data.local.ProductionBatchEntity
import com.example.data.local.RawMaterialEntity

class ErpSecurityException(message: String) : SecurityException(message)

data class CustomerSanitizedView(
    val id: String,
    val name: String,
    val businessName: String,
    val phone: String?, // Null if user lacks VIEW_CUSTOMER_CONTACT
    val address: String?,
    val customerType: String,
    val status: String,
    val currentDue: Double? // Strictly null unless user has VIEW_CUSTOMER_LEDGER
)

data class SaleSanitizedView(
    val id: String,
    val invoiceNumber: String,
    val saleType: String,
    val customerId: String,
    val customerName: String,
    val warehouseId: String,
    val totalItemQty: Double,
    val deliveryStatus: String,
    val saleDate: Long,
    val subtotal: Double?, // Null unless VIEW_UNIT_PRICES
    val discount: Double?,
    val grandTotal: Double?,
    val paidAmount: Double?,
    val dueAmount: Double?
)

data class OperationalDashboardData(
    val todayProductionQty: Double,
    val monthlyProductionQty: Double,
    val totalGoodQty: Double,
    val totalRejectedQty: Double,
    val rawMaterialCount: Int,
    val totalRawMaterialUnits: Double,
    val totalFinishedGoodsUnits: Double,
    val lowStockRawMaterials: List<RawMaterialEntity>,
    val lowStockModelsCount: Int,
    val todayOrdersCount: Int,
    val pendingDeliveriesCount: Int,
    val pendingProductionApprovals: Int,
    val myPendingTasksCount: Int,
    val recentBatches: List<ProductionBatchEntity>,
    val warehouseBalances: List<InventoryBalanceEntity>
)

data class FinancialDashboardData(
    val todaySalesAmount: Double,
    val monthlySalesAmount: Double,
    val totalSalesAmount: Double,
    val totalPaymentsReceived: Double,
    val totalCustomerDue: Double,
    val totalSupplierPayable: Double,
    val totalExpenses: Double,
    val totalCogs: Double,
    val grossProfit: Double,
    val netProfit: Double,
    val cashInHand: Double,
    val bankAndMobileBalance: Double,
    val pendingPaymentApprovals: Int,
    val pendingExpenseApprovals: Int,
    val cashAccounts: List<CashAccountEntity>
)

data class RoleDashboardSnapshot(
    val operational: OperationalDashboardData,
    val financial: FinancialDashboardData? // Strictly NULL for Moderator/Manager & Employee
)

data class TrialBalanceRow(
    val accountCode: String,
    val accountName: String,
    val category: String,
    val totalDebit: Double,
    val totalCredit: Double
)

data class ProfitLossStatement(
    val salesRevenue: Double,
    val salesReturns: Double,
    val netRevenue: Double,
    val costOfGoodsSold: Double,
    val grossProfit: Double,
    val operatingExpenses: Double,
    val netProfit: Double
)

data class BalanceSheetStatement(
    val cashAndBankAssets: Double,
    val accountsReceivable: Double,
    val inventoryAssets: Double,
    val totalAssets: Double,
    val accountsPayable: Double,
    val totalLiabilities: Double,
    val ownerEquityAndRetainedEarnings: Double
)

data class AccountingStatements(
    val trialBalance: List<TrialBalanceRow>,
    val profitAndLoss: ProfitLossStatement,
    val balanceSheet: BalanceSheetStatement
)

data class DailyClosingPreview(
    val openingCash: Double,
    val cashSales: Double,
    val cashCollections: Double,
    val bankCollections: Double,
    val totalExpenses: Double,
    val supplierPayments: Double,
    val cashWithdrawals: Double,
    val closingCash: Double,
    val bankBalance: Double,
    val previousClosings: List<DailyClosingEntity>
)

enum class ReportType {
    OPERATIONAL_PRODUCTION,
    OPERATIONAL_STOCK,
    SALES_ORDERS_OPERATIONAL,
    FINANCIAL_SALES_LEDGER,
    FINANCIAL_PROFIT_LOSS,
    FINANCIAL_CUSTOMER_DUES
}
