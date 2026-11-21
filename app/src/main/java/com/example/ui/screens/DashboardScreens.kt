package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.EmployeeTaskEntity
import com.example.data.local.PaymentSubmissionEntity
import com.example.data.repository.ReportType
import com.example.data.repository.RoleDashboardSnapshot
import com.example.domain.AppPermission
import com.example.domain.AppStrings
import com.example.domain.Formatters
import com.example.domain.SessionUser
import com.example.domain.UserRole
import com.example.ui.components.BarChartPoint
import com.example.ui.components.EmptyStateNotice
import com.example.ui.components.KpiTile
import com.example.ui.components.RestrictedBanner
import com.example.ui.components.RoleBadge
import com.example.ui.components.SectionCard
import com.example.ui.components.SimpleBarChart
import com.example.ui.components.StatusChip
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.EmeraldSecondary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SuccessGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleBasedDashboardScreen(
    session: SessionUser,
    strings: AppStrings,
    snapshot: RoleDashboardSnapshot?,
    tasks: List<EmployeeTaskEntity>,
    mySubmissions: List<PaymentSubmissionEntity>,
    onNavigateTab: (String) -> Unit,
    onToggleTask: (String, String) -> Unit,
    onExportReport: (ReportType) -> Unit
) {
    val ops = snapshot?.operational
    val fin = snapshot?.financial

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.fullName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = session.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RoleBadge(session.role)
            }
        }

        // --- 1. SUPER ADMIN & AUTHORIZED ADMIN FINANCIAL OVERVIEW ---
        if ((session.role == UserRole.SUPER_ADMIN || session.role == UserRole.ADMIN) && fin != null) {
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().testTag("admin_financial_grid"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 2
                ) {
                    KpiTile(strings.todaySales, Formatters.formatTaka(fin.todaySalesAmount), Icons.Filled.PointOfSale, NavyPrimary, modifier = Modifier.weight(1f), testTag = "kpi_today_sales")
                    KpiTile(strings.monthlySales, Formatters.formatTaka(fin.monthlySalesAmount), Icons.Filled.TrendingUp, EmeraldSecondary, modifier = Modifier.weight(1f))
                    KpiTile(strings.cashInHand, Formatters.formatTaka(fin.cashInHand), Icons.Filled.AccountBalanceWallet, SuccessGreen, modifier = Modifier.weight(1f), testTag = "kpi_cash_hand")
                    KpiTile(strings.bankBalance, Formatters.formatTaka(fin.bankAndMobileBalance), Icons.Filled.AccountBalance, NavyPrimary, modifier = Modifier.weight(1f))
                    KpiTile(strings.customerDue, Formatters.formatTaka(fin.totalCustomerDue), Icons.Filled.Payments, AmberTertiary, modifier = Modifier.weight(1f), testTag = "kpi_customer_due")
                    KpiTile(strings.supplierPayable, Formatters.formatTaka(fin.totalSupplierPayable), Icons.Filled.AttachMoney, ErrorRed, modifier = Modifier.weight(1f))
                    if (session.has(AppPermission.VIEW_PROFIT_LOSS)) {
                        KpiTile(strings.grossProfit, Formatters.formatTaka(fin.grossProfit), Icons.Filled.TrendingUp, EmeraldSecondary, modifier = Modifier.weight(1f))
                        KpiTile(strings.netProfit, Formatters.formatTaka(fin.netProfit), Icons.Filled.TrendingUp, if (fin.netProfit >= 0) SuccessGreen else ErrorRed, modifier = Modifier.weight(1f), testTag = "kpi_net_profit")
                    }
                }
            }

            item {
                SectionCard(
                    title = "${strings.accountingAndReports} (${strings.totalSales}: ${Formatters.formatTaka(fin.totalSalesAmount)})",
                    actionLabel = strings.exportCsv,
                    onAction = { onExportReport(ReportType.FINANCIAL_PROFIT_LOSS) }
                ) {
                    SimpleBarChart(
                        points = listOf(
                            BarChartPoint("Sales", fin.totalSalesAmount, NavyPrimary),
                            BarChartPoint("COGS", fin.totalCogs, AmberTertiary),
                            BarChartPoint("Expenses", fin.totalExpenses, ErrorRed),
                            BarChartPoint("Net Profit", fin.netProfit.coerceAtLeast(0.0), SuccessGreen)
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { onNavigateTab("finance") },
                            modifier = Modifier.weight(1f).testTag("btn_pending_approvals")
                        ) {
                            Text("${strings.pendingApprovals}: ${fin.pendingPaymentApprovals + fin.pendingExpenseApprovals}")
                        }
                        OutlinedButton(
                            onClick = { onExportReport(ReportType.FINANCIAL_SALES_LEDGER) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Sales CSV")
                        }
                    }
                }
            }
        }

        // --- 2. MODERATOR / MANAGER STRICTLY OPERATIONAL DASHBOARD ---
        if (session.role == UserRole.MODERATOR_MANAGER) {
            item {
                RestrictedBanner(
                    message = strings.restrictedNotice,
                    modifier = Modifier.testTag("moderator_restricted_banner")
                )
            }
        }

        // --- 3. OPERATIONAL SUMMARY (ALL AUTHORIZED ROLES, EXCLUDES FINANCIALS FOR MOD/EMPLOYEE) ---
        if (ops != null && session.role != UserRole.EMPLOYEE) {
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().testTag("operational_kpi_grid"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 2
                ) {
                    KpiTile(strings.todayProduction, Formatters.formatQty(ops.todayProductionQty, "u"), Icons.Filled.Factory, EmeraldSecondary, modifier = Modifier.weight(1f))
                    KpiTile(strings.monthlyProduction, Formatters.formatQty(ops.monthlyProductionQty, "u"), Icons.Filled.PrecisionManufacturing, NavyPrimary, modifier = Modifier.weight(1f))
                    KpiTile(strings.rawMaterialStock, Formatters.formatQty(ops.totalRawMaterialUnits), Icons.Filled.Inventory2, AmberTertiary, subtitle = "${ops.rawMaterialCount} types", modifier = Modifier.weight(1f))
                    KpiTile(strings.finishedGoodsStock, Formatters.formatQty(ops.totalFinishedGoodsUnits, "u"), Icons.Filled.CheckCircle, SuccessGreen, modifier = Modifier.weight(1f))
                    KpiTile(strings.pendingDeliveries, ops.pendingDeliveriesCount.toString(), Icons.Filled.LocalShipping, AmberTertiary, subtitle = "${ops.todayOrdersCount} today", modifier = Modifier.weight(1f))
                    KpiTile(strings.lowStockAlerts, (ops.lowStockRawMaterials.size + ops.lowStockModelsCount).toString(), Icons.Filled.Warning, ErrorRed, modifier = Modifier.weight(1f))
                }
            }

            item {
                SectionCard(
                    title = "${strings.productionBatches} (${strings.goodProducts} vs ${strings.rejectedProducts})",
                    actionLabel = if (session.has(AppPermission.EXPORT_REPORTS)) strings.exportCsv else null,
                    onAction = { onExportReport(ReportType.OPERATIONAL_PRODUCTION) }
                ) {
                    SimpleBarChart(
                        points = listOf(
                            BarChartPoint(strings.goodProducts, ops.totalGoodQty, SuccessGreen),
                            BarChartPoint(strings.rejectedProducts, ops.totalRejectedQty, ErrorRed),
                            BarChartPoint(strings.finishedGoodsStock, ops.totalFinishedGoodsUnits, NavyPrimary)
                        )
                    )
                }
            }
        }

        // --- 4. EMPLOYEE DEDICATED DASHBOARD ---
        if (session.role == UserRole.EMPLOYEE) {
            item {
                RestrictedBanner(
                    message = strings.restrictedNotice,
                    modifier = Modifier.testTag("employee_restricted_banner")
                )
            }
            if (ops != null) {
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        maxItemsInEachRow = 2
                    ) {
                        KpiTile(strings.myTasks, ops.myPendingTasksCount.toString(), Icons.AutoMirrored.Filled.Assignment, NavyPrimary, modifier = Modifier.weight(1f))
                        KpiTile(strings.pendingDeliveries, ops.pendingDeliveriesCount.toString(), Icons.Filled.LocalShipping, AmberTertiary, modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                SectionCard(title = strings.quickActions) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (session.has(AppPermission.CREATE_PRODUCTION)) {
                            Button(onClick = { onNavigateTab("production") }, modifier = Modifier.testTag("emp_quick_prod")) {
                                Text(strings.productionBatches)
                            }
                        }
                        if (session.has(AppPermission.CREATE_SALES) || session.has(AppPermission.DISPATCH_DELIVERY)) {
                            FilledTonalButton(onClick = { onNavigateTab("sales") }, modifier = Modifier.testTag("emp_quick_sales")) {
                                Text(strings.wholesaleAndRetail)
                            }
                        }
                        if (session.has(AppPermission.SUBMIT_PAYMENT_COLLECTION)) {
                            OutlinedButton(onClick = { onNavigateTab("finance") }, modifier = Modifier.testTag("emp_quick_collect")) {
                                Text(strings.paymentCollection)
                            }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "${strings.paymentCollection} (${mySubmissions.size})") {
                    if (mySubmissions.isEmpty()) {
                        EmptyStateNotice(strings.noDataYet)
                    } else {
                        mySubmissions.take(5).forEach { sub ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("${sub.paymentMethod} • ${Formatters.formatTaka(sub.amount)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text(Formatters.formatDateTime(sub.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusChip(sub.status)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // --- 5. ASSIGNED TASKS & LOW STOCK ALERTS ---
        item {
            SectionCard(title = strings.myTasks) {
                if (tasks.isEmpty()) {
                    EmptyStateNotice(strings.noDataYet)
                } else {
                    tasks.take(6).forEach { task ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.status == "COMPLETED",
                                onCheckedChange = { checked ->
                                    onToggleTask(task.id, if (checked) "COMPLETED" else "IN_PROGRESS")
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                if (task.description.isNotBlank()) {
                                    Text(task.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            StatusChip(task.status)
                        }
                    }
                }
            }
        }

        if (ops != null && ops.lowStockRawMaterials.isNotEmpty()) {
            item {
                SectionCard(title = strings.lowStockAlerts) {
                    ops.lowStockRawMaterials.forEach { mat ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${mat.name} (${mat.materialCode})", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${Formatters.formatQty(mat.availableQuantity, mat.unit)} / min ${Formatters.formatQty(mat.minimumStock)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ErrorRed
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
