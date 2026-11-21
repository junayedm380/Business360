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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.AuditLogEntity
import com.example.data.local.CashAccountEntity
import com.example.data.local.EmployeeAdvanceEntity
import com.example.data.local.LeaveRequestEntity
import com.example.data.local.PayrollRuleConfigEntity
import com.example.data.local.PayrollRunEntity
import com.example.data.repository.AttendanceMonthlySummaryRow
import com.example.data.repository.EmployeeSanitizedView
import com.example.data.repository.LeaveBalanceItem
import com.example.data.repository.ReportType
import com.example.domain.AppPermission
import com.example.domain.AppStrings
import com.example.domain.DhakaTime
import com.example.domain.Formatters
import com.example.domain.SessionUser
import com.example.ui.components.EmptyStateNotice
import com.example.ui.components.KpiTile
import com.example.ui.components.RestrictedBanner
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusChip
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSecondary
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseAccent
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.VioletAccent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayrollTab(
    session: SessionUser,
    strings: AppStrings,
    businessName: String,
    currencySymbol: String,
    employees: List<EmployeeSanitizedView>,
    selectedMonthKey: String,
    payrollConfig: PayrollRuleConfigEntity?,
    advances: List<EmployeeAdvanceEntity>,
    payrollRuns: List<PayrollRunEntity>,
    cashAccounts: List<CashAccountEntity>,
    onShiftMonth: (Int) -> Unit,
    onUpdateConfig: (Int, Double, Double, Int, Double, Boolean) -> Unit,
    onIssueAdvance: (String, Double, String, String, String) -> Unit,
    onGeneratePayroll: (String, Double, Double, String) -> Unit,
    onUpdateDraftPayroll: (String, Double, Double, Double, String) -> Unit,
    onApprovePayroll: (String) -> Unit,
    onPayPayroll: (String, String) -> Unit,
    onOpenPrintableSlip: (String, String, String, String) -> Unit
) {
    if (!session.canViewSalaryAndPayroll || !session.has(AppPermission.VIEW_PAYROLL)) {
        RestrictedBanner(strings.restrictedNotice, modifier = Modifier.padding(16.dp))
        return
    }

    var mode by remember { mutableStateOf("PAYROLL") } // PAYROLL, ADVANCES, RULES
    var showGenerateDialog by remember { mutableStateOf(false) }
    var editingDraftRun by remember { mutableStateOf<PayrollRunEntity?>(null) }
    var payingRun by remember { mutableStateOf<PayrollRunEntity?>(null) }
    var showAdvanceDialog by remember { mutableStateOf(false) }

    val monthRuns = remember(payrollRuns, selectedMonthKey) {
        payrollRuns.filter { it.monthKey == selectedMonthKey }
    }
    val empById = remember(employees) { employees.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "PAYROLL", onClick = { mode = "PAYROLL" }, label = { Text("Monthly Payroll") })
                FilterChip(selected = mode == "ADVANCES", onClick = { mode = "ADVANCES" }, label = { Text("Advances (${advances.count { it.status == "ACTIVE" }})") })
                FilterChip(selected = mode == "RULES", onClick = { mode = "RULES" }, label = { Text("Salary Rules") })
            }
        }

        when (mode) {
            "PAYROLL" -> {
                item {
                    SectionCard(title = "Payroll Month • $selectedMonthKey", accentColor = IndigoAccent) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onShiftMonth(-1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                            }
                            Text(selectedMonthKey, style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold))
                            IconButton(onClick = { onShiftMonth(1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                            }
                        }
                        if (session.has(AppPermission.MANAGE_PAYROLL)) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { showGenerateDialog = true },
                                enabled = employees.any { it.active },
                                modifier = Modifier.fillMaxWidth().testTag("btn_generate_payroll")
                            ) {
                                Icon(Icons.Filled.Payments, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Generate / Recalculate Payroll ($selectedMonthKey)")
                            }
                        }
                    }
                }

                if (monthRuns.isNotEmpty()) {
                    val totalGross = monthRuns.sumOf { it.grossSalary }
                    val totalDed = monthRuns.sumOf { it.totalDeductions }
                    val totalNet = monthRuns.sumOf { it.netSalary }
                    val paidCount = monthRuns.count { it.status == "PAID" }
                    item {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            maxItemsInEachRow = 2
                        ) {
                            KpiTile("Total Gross", Formatters.formatTaka(totalGross, currencySymbol), Icons.Filled.AccountBalanceWallet, IndigoAccent, modifier = Modifier.weight(1f))
                            KpiTile("Total Deductions", Formatters.formatTaka(totalDed, currencySymbol), Icons.Filled.Payments, RoseAccent, modifier = Modifier.weight(1f))
                            KpiTile("Net Payable", Formatters.formatTaka(totalNet, currencySymbol), Icons.Filled.CheckCircle, EmeraldSecondary, modifier = Modifier.weight(1f))
                            KpiTile("Slips Paid", "$paidCount / ${monthRuns.size}", Icons.Filled.Print, SuccessGreen, modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (monthRuns.isEmpty()) {
                    item { EmptyStateNotice("No payroll runs generated for $selectedMonthKey yet.") }
                } else {
                    items(monthRuns, key = { it.id }) { run ->
                        SectionCard(
                            title = "${run.employeeNameSnapshot} [${run.employeeCodeSnapshot}]",
                            accentColor = if (run.status == "PAID") SuccessGreen else IndigoAccent,
                            actionLabel = if (run.status == "DRAFT" && session.has(AppPermission.MANAGE_PAYROLL)) "Adjust" else null,
                            onAction = if (run.status == "DRAFT" && session.has(AppPermission.MANAGE_PAYROLL)) ({ editingDraftRun = run }) else null
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${run.slipNumber} • ${run.designationSnapshot}", style = MaterialTheme.typography.bodySmall)
                                StatusChip(run.status)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Attendance: P:${run.presentDays} L:${run.lateDays} HD:${run.halfDays} A:${run.absentDays} PaidLv:${run.paidLeaveDays} UnpaidLv:${run.unpaidLeaveDays} OT:${run.overtimeHours}h",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Basic: ${Formatters.formatTaka(run.basicSalary, currencySymbol)} | Allow: ${Formatters.formatTaka(run.totalAllowances, currencySymbol)} | Bonus: ${Formatters.formatTaka(run.bonus, currencySymbol)} | OT: ${Formatters.formatTaka(run.overtimePay, currencySymbol)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Deductions: Absence ${Formatters.formatTaka(run.absenceDeduction, currencySymbol)}, HalfDay ${Formatters.formatTaka(run.halfDayDeduction, currencySymbol)}, UnpaidLv ${Formatters.formatTaka(run.unpaidLeaveDeduction, currencySymbol)}, Adv ${Formatters.formatTaka(run.advanceDeduction, currencySymbol)}, Other ${Formatters.formatTaka(run.otherDeduction, currencySymbol)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoseAccent
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Net Salary: ${Formatters.formatTaka(run.netSalary, currencySymbol)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                color = EmeraldSecondary
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (run.status == "DRAFT" && session.has(AppPermission.MANAGE_PAYROLL)) {
                                    Button(
                                        onClick = { onApprovePayroll(run.id) },
                                        modifier = Modifier.weight(1f).testTag("btn_approve_payroll_${run.id}")
                                    ) { Text(strings.approve) }
                                }
                                if (run.status == "APPROVED" && session.has(AppPermission.MANAGE_PAYROLL)) {
                                    Button(
                                        onClick = { payingRun = run },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                        modifier = Modifier.weight(1f).testTag("btn_pay_payroll_${run.id}")
                                    ) { Text("Pay Salary") }
                                }
                                OutlinedButton(
                                    onClick = { onOpenPrintableSlip(run.id, run.slipNumber, businessName, currencySymbol) },
                                    modifier = Modifier.weight(1f).testTag("btn_print_slip_${run.id}")
                                ) {
                                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Salary Slip")
                                }
                            }
                        }
                    }
                }
            }

            "ADVANCES" -> {
                if (session.has(AppPermission.MANAGE_PAYROLL)) {
                    item {
                        Button(
                            onClick = { showAdvanceDialog = true },
                            enabled = employees.any { it.active } && cashAccounts.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().testTag("btn_issue_advance")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Issue Advance Salary / Loan")
                        }
                    }
                }
                if (advances.isEmpty()) {
                    item { EmptyStateNotice("No advance salary records yet.") }
                } else {
                    items(advances, key = { it.id }) { adv ->
                        val emp = empById[adv.employeeId]
                        SectionCard(title = "${emp?.fullName ?: adv.employeeId} (${emp?.employeeCode ?: ""})", accentColor = AmberTertiary) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Issued: ${Formatters.formatTaka(adv.amount, currencySymbol)} on ${adv.issueDateKey}", style = MaterialTheme.typography.bodyMedium)
                                StatusChip(adv.status)
                            }
                            Text(
                                "Remaining Balance: ${Formatters.formatTaka(adv.remainingBalance, currencySymbol)}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                color = IndigoAccent
                            )
                            Text("Reason: ${adv.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            "RULES" -> {
                item {
                    val cfg = payrollConfig ?: PayrollRuleConfigEntity()
                    var workDays by remember(cfg) { mutableStateOf(cfg.workingDaysPerMonth.toString()) }
                    var absRate by remember(cfg) { mutableStateOf(cfg.absentDeductionRate.toString()) }
                    var halfRate by remember(cfg) { mutableStateOf(cfg.halfDayDeductionRate.toString()) }
                    var lateThreshold by remember(cfg) { mutableStateOf(cfg.lateDaysPerHalfDayDeduction.toString()) }
                    var otMult by remember(cfg) { mutableStateOf(cfg.overtimeHourlyMultiplier.toString()) }
                    var deductUnpaid by remember(cfg) { mutableStateOf(cfg.deductUnpaidLeave) }

                    SectionCard(title = "Configurable Salary Calculation Rules", accentColor = VioletAccent) {
                        OutlinedTextField(value = workDays, onValueChange = { workDays = it }, label = { Text("Working Days / Month") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = absRate, onValueChange = { absRate = it }, label = { Text("Absent Ded. Rate (days)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                            OutlinedTextField(value = halfRate, onValueChange = { halfRate = it }, label = { Text("Half-Day Ded. Rate (days)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = lateThreshold, onValueChange = { lateThreshold = it }, label = { Text("Late Days = 1 Half-Day (0=off)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(value = otMult, onValueChange = { otMult = it }, label = { Text("OT Multiplier (e.g. 1.5)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Deduct Salary for Unpaid Leave")
                            Switch(checked = deductUnpaid, onCheckedChange = { deductUnpaid = it })
                        }
                        if (session.has(AppPermission.MANAGE_PAYROLL)) {
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    onUpdateConfig(
                                        workDays.toIntOrNull() ?: 26,
                                        absRate.toDoubleOrNull() ?: 1.0,
                                        halfRate.toDoubleOrNull() ?: 0.5,
                                        lateThreshold.toIntOrNull() ?: 0,
                                        otMult.toDoubleOrNull() ?: 1.5,
                                        deductUnpaid
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag("btn_save_payroll_rules")
                            ) { Text(strings.save) }
                        }
                    }
                }
            }
        }
    }

    if (showGenerateDialog) {
        var bonus by remember { mutableStateOf("0") }
        var otherDed by remember { mutableStateOf("0") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGenerateDialog = false },
            title = { Text("Generate Payroll • $selectedMonthKey") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Calculates basic salary, allowances, overtime, absence, half-day, unpaid leave deductions, and active advance recovery from attendance logs.")
                    OutlinedTextField(value = bonus, onValueChange = { bonus = it }, label = { Text("Default Bonus") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = otherDed, onValueChange = { otherDed = it }, label = { Text("Default Other Deduction") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    onGeneratePayroll(selectedMonthKey, bonus.toDoubleOrNull() ?: 0.0, otherDed.toDoubleOrNull() ?: 0.0, notes)
                    showGenerateDialog = false
                }) { Text("Generate") }
            },
            dismissButton = { TextButton(onClick = { showGenerateDialog = false }) { Text("Cancel") } }
        )
    }

    editingDraftRun?.let { run ->
        var bonus by remember(run) { mutableStateOf(run.bonus.toString()) }
        var advDed by remember(run) { mutableStateOf(run.advanceDeduction.toString()) }
        var otherDed by remember(run) { mutableStateOf(run.otherDeduction.toString()) }
        var notes by remember(run) { mutableStateOf(run.notes) }
        AlertDialog(
            onDismissRequest = { editingDraftRun = null },
            title = { Text("Adjust ${run.employeeNameSnapshot}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = bonus, onValueChange = { bonus = it }, label = { Text("Bonus") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = advDed, onValueChange = { advDed = it }, label = { Text("Advance Recovery") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = otherDed, onValueChange = { otherDed = it }, label = { Text("Other Deduction") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateDraftPayroll(run.id, bonus.toDoubleOrNull() ?: 0.0, advDed.toDoubleOrNull() ?: 0.0, otherDed.toDoubleOrNull() ?: 0.0, notes)
                    editingDraftRun = null
                }) { Text("Recalculate") }
            },
            dismissButton = { TextButton(onClick = { editingDraftRun = null }) { Text("Cancel") } }
        )
    }

    payingRun?.let { run ->
        PaySalaryDialog(
            run = run,
            currencySymbol = currencySymbol,
            cashAccounts = cashAccounts,
            onDismiss = { payingRun = null },
            onPay = { accountId ->
                onPayPayroll(run.id, accountId)
                payingRun = null
            }
        )
    }

    if (showAdvanceDialog) {
        IssueAdvanceDialog(
            employees = employees.filter { it.active },
            cashAccounts = cashAccounts,
            onDismiss = { showAdvanceDialog = false },
            onIssue = { empId, amount, acctId, reason, dateKey ->
                onIssueAdvance(empId, amount, acctId, reason, dateKey)
                showAdvanceDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySalaryDialog(
    run: PayrollRunEntity,
    currencySymbol: String,
    cashAccounts: List<CashAccountEntity>,
    onDismiss: () -> Unit,
    onPay: (String) -> Unit
) {
    var selectedAcct by remember { mutableStateOf(cashAccounts.firstOrNull() ?: CashAccountEntity("acct_cash", "Cash in Hand", "CASH")) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disburse Salary • ${run.slipNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pay ${Formatters.formatTaka(run.netSalary, currencySymbol)} to ${run.employeeNameSnapshot}?", style = MaterialTheme.typography.bodyMedium)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = "${selectedAcct.name} (${Formatters.formatTaka(selectedAcct.currentBalance, currencySymbol)})",
                        onValueChange = {}, readOnly = true, label = { Text("Payment Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        cashAccounts.forEach { a ->
                            DropdownMenuItem(
                                text = { Text("${a.name} (${Formatters.formatTaka(a.currentBalance, currencySymbol)})") },
                                onClick = { selectedAcct = a; expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPay(selectedAcct.id) }, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)) { Text("Confirm Pay") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueAdvanceDialog(
    employees: List<EmployeeSanitizedView>,
    cashAccounts: List<CashAccountEntity>,
    onDismiss: () -> Unit,
    onIssue: (String, Double, String, String, String) -> Unit
) {
    var selectedEmp by remember { mutableStateOf(employees.first()) }
    var empExpanded by remember { mutableStateOf(false) }
    var selectedAcct by remember { mutableStateOf(cashAccounts.first()) }
    var acctExpanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var dateKey by remember { mutableStateOf(DhakaTime.todayDateKey()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Issue Advance Salary / Loan") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = empExpanded, onExpandedChange = { empExpanded = !empExpanded }) {
                    OutlinedTextField(
                        value = "${selectedEmp.fullName} (${selectedEmp.employeeCode})",
                        onValueChange = {}, readOnly = true, label = { Text("Employee") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(empExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = empExpanded, onDismissRequest = { empExpanded = false }) {
                        employees.forEach { e -> DropdownMenuItem(text = { Text("${e.fullName} (${e.employeeCode})") }, onClick = { selectedEmp = e; empExpanded = false }) }
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Advance Amount *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = acctExpanded, onExpandedChange = { acctExpanded = !acctExpanded }) {
                    OutlinedTextField(
                        value = selectedAcct.name,
                        onValueChange = {}, readOnly = true, label = { Text("Disburse From") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(acctExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = acctExpanded, onDismissRequest = { acctExpanded = false }) {
                        cashAccounts.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { selectedAcct = a; acctExpanded = false }) }
                    }
                }
                OutlinedTextField(value = dateKey, onValueChange = { dateKey = it }, label = { Text("Issue Date (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason *") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onIssue(selectedEmp.id, amount.toDoubleOrNull() ?: 0.0, selectedAcct.id, reason, dateKey) }) { Text("Issue Advance") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun HrReportsTab(
    session: SessionUser,
    strings: AppStrings,
    currencySymbol: String,
    selectedMonthKey: String,
    monthlySummary: List<AttendanceMonthlySummaryRow>,
    leaveRequests: List<LeaveRequestEntity>,
    leaveBalances: List<LeaveBalanceItem>,
    auditLogs: List<AuditLogEntity>,
    payrollRuns: List<PayrollRunEntity>,
    onShiftMonth: (Int) -> Unit,
    onExportHrReport: (ReportType, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(title = "HR Reports Period • $selectedMonthKey", accentColor = CyanAccent) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onShiftMonth(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                    }
                    Text(selectedMonthKey, style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold))
                    IconButton(onClick = { onShiftMonth(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                    }
                }
            }
        }

        // 1. Attendance Monthly Summary Report (Manager & Admin)
        if (session.has(AppPermission.VIEW_ATTENDANCE_REPORTS) || session.has(AppPermission.VIEW_HR_REPORTS)) {
            item {
                SectionCard(
                    title = "Monthly Attendance Report ($selectedMonthKey)",
                    accentColor = EmeraldSecondary,
                    actionLabel = strings.exportCsv,
                    onAction = { onExportHrReport(ReportType.HR_ATTENDANCE_MONTHLY, selectedMonthKey) }
                ) {
                    if (monthlySummary.isEmpty()) {
                        EmptyStateNotice("No attendance summary available")
                    } else {
                        monthlySummary.forEach { row ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text("${row.employeeName} [${row.employeeCode}] • ${row.department}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "P:${row.presentDays} | L:${row.lateDays} | HD:${row.halfDays} | A:${row.absentDays} | PaidLv:${row.paidLeaveDays} | UnpaidLv:${row.unpaidLeaveDays} | H:${row.holidayDays} | OT:${row.overtimeHours}h",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // 2. Leave Summary Report (Manager & Admin)
        if (session.has(AppPermission.VIEW_LEAVES) || session.has(AppPermission.VIEW_HR_REPORTS)) {
            item {
                SectionCard(
                    title = "Leave Summary & Balances",
                    accentColor = IndigoAccent,
                    actionLabel = strings.exportCsv,
                    onAction = { onExportHrReport(ReportType.HR_LEAVE_SUMMARY, selectedMonthKey) }
                ) {
                    val approvedCount = leaveRequests.count { it.status == "APPROVED" }
                    val pendingCount = leaveRequests.count { it.status == "PENDING" }
                    Text("Total Requests: ${leaveRequests.size} • Approved: $approvedCount • Pending: $pendingCount", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    leaveBalances.take(10).forEach { bal ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${bal.employeeName} (${bal.leaveTypeName})", style = MaterialTheme.typography.bodySmall)
                            Text("Rem ${bal.remainingDays}/${bal.annualQuota}d", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }

        // 3. Confidential Payroll Report (STRICTLY Admin / Super Admin with VIEW_PAYROLL)
        if (session.canViewSalaryAndPayroll && session.has(AppPermission.VIEW_PAYROLL)) {
            val monthPayroll = payrollRuns.filter { it.monthKey == selectedMonthKey }
            item {
                SectionCard(
                    title = "Payroll Disbursement Report ($selectedMonthKey)",
                    accentColor = VioletAccent,
                    actionLabel = strings.exportCsv,
                    onAction = { onExportHrReport(ReportType.HR_PAYROLL_MONTHLY, selectedMonthKey) }
                ) {
                    if (monthPayroll.isEmpty()) {
                        Text("No payroll generated for $selectedMonthKey yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Total Gross: ${Formatters.formatTaka(monthPayroll.sumOf { it.grossSalary }, currencySymbol)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Total Deductions: ${Formatters.formatTaka(monthPayroll.sumOf { it.totalDeductions }, currencySymbol)}", style = MaterialTheme.typography.bodyMedium, color = RoseAccent)
                        Text("Total Net Payroll: ${Formatters.formatTaka(monthPayroll.sumOf { it.netSalary }, currencySymbol)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = SuccessGreen)
                    }
                }
            }
        } else {
            item {
                RestrictedBanner("Confidential payroll & salary reports are hidden for your role.")
            }
        }

        // 4. Recent Attendance Audit Logs
        item {
            SectionCard(title = "Recent Attendance Audit Trail (${auditLogs.size})", accentColor = AmberTertiary) {
                if (auditLogs.isEmpty()) {
                    Text("No attendance changes logged yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    auditLogs.take(15).forEach { log ->
                        Text("${log.action}: ${log.newValue} (${log.userName})", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
