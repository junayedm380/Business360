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
    FINANCIAL_CUSTOMER_DUES,
    HR_ATTENDANCE_MONTHLY,
    HR_LEAVE_SUMMARY,
    HR_PAYROLL_MONTHLY
}

data class EmployeeSanitizedView(
    val id: String,
    val employeeCode: String,
    val fullName: String,
    val photoUri: String,
    val phone: String,
    val department: String,
    val designation: String,
    val joiningDate: Long,
    val employmentStatus: String,
    val active: Boolean,
    val linkedUserId: String,
    val basicSalary: Double?,
    val houseAllowance: Double?,
    val medicalAllowance: Double?,
    val transportAllowance: Double?,
    val otherAllowance: Double?,
    val overtimeHourlyRate: Double?
) {
    val totalPackage: Double?
        get() = if (basicSalary != null) {
            basicSalary + (houseAllowance ?: 0.0) + (medicalAllowance ?: 0.0) +
                (transportAllowance ?: 0.0) + (otherAllowance ?: 0.0)
        } else null
}

data class LeaveBalanceItem(
    val employeeId: String,
    val employeeCode: String,
    val employeeName: String,
    val leaveTypeId: String,
    val leaveTypeName: String,
    val isPaid: Boolean,
    val annualQuota: Int,
    val usedDays: Int,
    val remainingDays: Int
)

data class AttendanceMonthlySummaryRow(
    val employeeId: String,
    val employeeCode: String,
    val employeeName: String,
    val department: String,
    val monthKey: String,
    val presentDays: Int,
    val lateDays: Int,
    val halfDays: Int,
    val absentDays: Int,
    val paidLeaveDays: Int,
    val unpaidLeaveDays: Int,
    val holidayDays: Int,
    val overtimeHours: Double
)
