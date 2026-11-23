package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.AttendanceDailyClosingEntity
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.CashAccountEntity
import com.example.data.local.EmployeeAdvanceEntity
import com.example.data.local.LeaveRequestEntity
import com.example.data.local.LeaveTypeEntity
import com.example.data.local.PayrollRuleConfigEntity
import com.example.data.local.PayrollRunEntity
import com.example.data.repository.AttendanceMonthlySummaryRow
import com.example.data.repository.EmployeeSanitizedView
import com.example.data.repository.HrOperationsRepository
import com.example.data.repository.LeaveBalanceItem
import com.example.data.repository.ReportType
import com.example.domain.AppPermission
import com.example.domain.AppStrings
import com.example.domain.DhakaTime
import com.example.domain.Formatters
import com.example.domain.SessionUser
import com.example.ui.components.EmptyStateNotice
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusChip
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSecondary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HeroDeepStart
import com.example.ui.theme.HeroTealMid
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.JetBrainsMonoFamily

private enum class HrSubTab(val routeKey: String) {
    EMPLOYEES("employees"),
    ATTENDANCE("attendance"),
    LEAVES("leaves"),
    PAYROLL("payroll"),
    REPORTS("reports")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HrModuleScreen(
    session: SessionUser,
    strings: AppStrings,
    businessName: String,
    currencySymbol: String,
    initialSubTab: String?,
    onClearInitialSubTab: () -> Unit,
    employees: List<EmployeeSanitizedView>,
    selectedDateKey: String,
    selectedMonthKey: String,
    allAttendance: List<AttendanceRecordEntity>,
    monthlyAttendance: List<AttendanceRecordEntity>,
    monthlySummary: List<AttendanceMonthlySummaryRow>,
    attendanceClosings: List<AttendanceDailyClosingEntity>,
    attendanceAuditLogs: List<AuditLogEntity>,
    leaveTypes: List<LeaveTypeEntity>,
    leaveRequests: List<LeaveRequestEntity>,
    leaveBalances: List<LeaveBalanceItem>,
    payrollConfig: PayrollRuleConfigEntity?,
    employeeAdvances: List<EmployeeAdvanceEntity>,
    payrollRuns: List<PayrollRunEntity>,
    cashAccounts: List<CashAccountEntity>,
    onSelectDateKey: (String) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onSaveEmployee: (String?, String, String, String, String, String, String, Long, String, Boolean, Double, Double, Double, Double, Double, Double) -> Unit,
    onSetEmployeeActive: (String, Boolean) -> Unit,
    onDeleteEmployee: (String) -> Unit,
    onMarkAttendance: (String, String, String, String, String, Double, String) -> Unit,
    onMarkAllPresent: (String) -> Unit,
    onCloseDailyAttendance: (String, String) -> Unit,
    onReopenDailyAttendance: (String) -> Unit,
    onSaveLeaveType: (String?, String, String, Int, Boolean, Boolean) -> Unit,
    onSubmitLeaveRequest: (String, String, String, String, String) -> Unit,
    onReviewLeaveRequest: (String, Boolean, String) -> Unit,
    onUpdatePayrollConfig: (Int, Double, Double, Int, Double, Boolean) -> Unit,
    onIssueAdvance: (String, Double, String, String, String) -> Unit,
    onGeneratePayroll: (String, Double, Double, String) -> Unit,
    onUpdateDraftPayroll: (String, Double, Double, Double, String) -> Unit,
    onApprovePayroll: (String) -> Unit,
    onPayPayroll: (String, String) -> Unit,
    onOpenPrintableSlip: (String, String, String, String) -> Unit,
    onExportHrReport: (ReportType, String) -> Unit
) {
    val visibleTabs = remember(session) {
        buildList {
            if (session.has(AppPermission.VIEW_EMPLOYEES_BASIC) || session.has(AppPermission.MANAGE_EMPLOYEES)) {
                add(HrSubTab.EMPLOYEES)
            }
            if (session.has(AppPermission.MARK_ATTENDANCE) || session.has(AppPermission.EDIT_ATTENDANCE) ||
                session.has(AppPermission.VIEW_ATTENDANCE_REPORTS) || session.has(AppPermission.MANAGE_ATTENDANCE_CLOSING)
            ) {
                add(HrSubTab.ATTENDANCE)
            }
            if (session.has(AppPermission.VIEW_LEAVES) || session.has(AppPermission.MANAGE_LEAVES)) {
                add(HrSubTab.LEAVES)
            }
            if (session.canViewSalaryAndPayroll && (session.has(AppPermission.VIEW_PAYROLL) || session.has(AppPermission.MANAGE_PAYROLL))) {
                add(HrSubTab.PAYROLL)
            }
            if (session.has(AppPermission.VIEW_HR_REPORTS) || session.has(AppPermission.VIEW_ATTENDANCE_REPORTS) || session.canViewSalaryAndPayroll) {
                add(HrSubTab.REPORTS)
            }
        }
    }

    if (visibleTabs.isEmpty()) {
        EmptyStateNotice(strings.restrictedNotice)
        return
    }

    var activeTabKey by rememberSaveable(session.id) { mutableStateOf(visibleTabs.first().routeKey) }
    LaunchedEffect(initialSubTab) {
        if (!initialSubTab.isNullOrBlank() && visibleTabs.any { it.routeKey == initialSubTab }) {
            activeTabKey = initialSubTab
            onClearInitialSubTab()
        }
    }
    if (visibleTabs.none { it.routeKey == activeTabKey }) {
        activeTabKey = visibleTabs.first().routeKey
    }
    val selectedIdx = visibleTabs.indexOfFirst { it.routeKey == activeTabKey }.coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = selectedIdx) {
            visibleTabs.forEach { tab ->
                val label = when (tab) {
                    HrSubTab.EMPLOYEES -> strings.employeeManagement
                    HrSubTab.ATTENDANCE -> strings.attendanceManagement
                    HrSubTab.LEAVES -> strings.leaveManagement
                    HrSubTab.PAYROLL -> strings.payrollManagement
                    HrSubTab.REPORTS -> strings.hrReports
                }
                Tab(
                    selected = tab.routeKey == activeTabKey,
                    onClick = { activeTabKey = tab.routeKey },
                    text = { Text(label) },
                    modifier = Modifier.testTag("hr_tab_${tab.routeKey}")
                )
            }
        }

        when (visibleTabs[selectedIdx]) {
            HrSubTab.EMPLOYEES -> EmployeesTab(
                session = session,
                strings = strings,
                currencySymbol = currencySymbol,
                employees = employees,
                onSaveEmployee = onSaveEmployee,
                onSetEmployeeActive = onSetEmployeeActive,
                onDeleteEmployee = onDeleteEmployee
            )
            HrSubTab.ATTENDANCE -> AttendanceTab(
                session = session,
                strings = strings,
                employees = employees,
                selectedDateKey = selectedDateKey,
                selectedMonthKey = selectedMonthKey,
                allAttendance = allAttendance,
                monthlyAttendance = monthlyAttendance,
                closings = attendanceClosings,
                auditLogs = attendanceAuditLogs,
                onSelectDateKey = onSelectDateKey,
                onShiftMonth = onShiftMonth,
                onMarkAttendance = onMarkAttendance,
                onMarkAllPresent = onMarkAllPresent,
                onCloseDailyAttendance = onCloseDailyAttendance,
                onReopenDailyAttendance = onReopenDailyAttendance
            )
            HrSubTab.LEAVES -> LeaveTab(
                session = session,
                strings = strings,
                employees = employees,
                leaveTypes = leaveTypes,
                leaveRequests = leaveRequests,
                leaveBalances = leaveBalances,
                onSaveLeaveType = onSaveLeaveType,
                onSubmitLeaveRequest = onSubmitLeaveRequest,
                onReviewLeaveRequest = onReviewLeaveRequest
            )
            HrSubTab.PAYROLL -> PayrollTab(
                session = session,
                strings = strings,
                businessName = businessName,
                currencySymbol = currencySymbol,
                employees = employees,
                selectedMonthKey = selectedMonthKey,
                payrollConfig = payrollConfig,
                advances = employeeAdvances,
                payrollRuns = payrollRuns,
                cashAccounts = cashAccounts,
                onShiftMonth = onShiftMonth,
                onUpdateConfig = onUpdatePayrollConfig,
                onIssueAdvance = onIssueAdvance,
                onGeneratePayroll = onGeneratePayroll,
                onUpdateDraftPayroll = onUpdateDraftPayroll,
                onApprovePayroll = onApprovePayroll,
                onPayPayroll = onPayPayroll,
                onOpenPrintableSlip = onOpenPrintableSlip
            )
            HrSubTab.REPORTS -> HrReportsTab(
                session = session,
                strings = strings,
                currencySymbol = currencySymbol,
                selectedMonthKey = selectedMonthKey,
                monthlySummary = monthlySummary,
                leaveRequests = leaveRequests,
                leaveBalances = leaveBalances,
                auditLogs = attendanceAuditLogs,
                payrollRuns = payrollRuns,
                onShiftMonth = onShiftMonth,
                onExportHrReport = onExportHrReport
            )
        }
    }
}

@Composable
private fun EmployeesTab(
    session: SessionUser,
    strings: AppStrings,
    currencySymbol: String,
    employees: List<EmployeeSanitizedView>,
    onSaveEmployee: (String?, String, String, String, String, String, String, Long, String, Boolean, Double, Double, Double, Double, Double, Double) -> Unit,
    onSetEmployeeActive: (String, Boolean) -> Unit,
    onDeleteEmployee: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("ACTIVE") } // ALL, ACTIVE, INACTIVE
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEmployee by remember { mutableStateOf<EmployeeSanitizedView?>(null) }
    var viewingProfile by remember { mutableStateOf<EmployeeSanitizedView?>(null) }
    var deletingEmployee by remember { mutableStateOf<EmployeeSanitizedView?>(null) }

    val filtered = remember(employees, searchQuery, filterStatus) {
        employees.filter { emp ->
            val matchStatus = when (filterStatus) {
                "ACTIVE" -> emp.active
                "INACTIVE" -> !emp.active
                else -> true
            }
            val q = searchQuery.trim().lowercase()
            val matchQuery = q.isBlank() ||
                emp.fullName.lowercase().contains(q) ||
                emp.employeeCode.lowercase().contains(q) ||
                emp.department.lowercase().contains(q) ||
                emp.designation.lowercase().contains(q) ||
                emp.phone.contains(q)
            matchStatus && matchQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .background(Brush.linearGradient(listOf(HeroDeepStart, HeroTealMid)))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hr_workforce_banner_1795378902848),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().alpha(0.38f)
                    )
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.employeeManagement,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(color = Color.White.copy(alpha = 0.16f), shape = MaterialTheme.shapes.small) {
                                Text(
                                    text = "Active: ${employees.count { it.active }}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            Surface(color = Color.White.copy(alpha = 0.16f), shape = MaterialTheme.shapes.small) {
                                Text(
                                    text = "Total Staff: ${employees.size}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(strings.searchHint) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_emp_search")
                )
                if (session.has(AppPermission.MANAGE_EMPLOYEES)) {
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.height(52.dp).testTag("btn_add_employee")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ACTIVE" to "Active (${employees.count { it.active }})", "INACTIVE" to "Inactive (${employees.count { !it.active }})", "ALL" to "All (${employees.size})").forEach { (key, label) ->
                    FilterChip(selected = filterStatus == key, onClick = { filterStatus = key }, label = { Text(label) })
                }
            }
        }

        if (filtered.isEmpty()) {
            item { EmptyStateNotice(strings.noDataYet) }
        } else {
            items(filtered, key = { it.id }) { emp ->
                SectionCard(
                    title = "${emp.fullName} [${emp.employeeCode}]",
                    accentColor = if (emp.active) EmeraldSecondary else MaterialTheme.colorScheme.outline,
                    actionLabel = if (session.has(AppPermission.MANAGE_EMPLOYEES)) "Edit" else "Profile",
                    onAction = {
                        if (session.has(AppPermission.MANAGE_EMPLOYEES)) editingEmployee = emp
                        else viewingProfile = emp
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewingProfile = emp },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EmployeeAvatar(photoUri = emp.photoUri, fullName = emp.fullName, sizeDp = 52)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${emp.designation} • ${emp.department}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text("${emp.phone} • Joined ${Formatters.formatDate(emp.joiningDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusChip(emp.employmentStatus)
                                StatusChip(if (emp.active) "ACTIVE" else "INACTIVE")
                            }
                            if (emp.totalPackage != null && session.has(AppPermission.VIEW_EMPLOYEE_SALARY)) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Basic: ${Formatters.formatTaka(emp.basicSalary ?: 0.0, currencySymbol)} • Package: ${Formatters.formatTaka(emp.totalPackage!!, currencySymbol)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.SemiBold),
                                    color = IndigoAccent
                                )
                            }
                        }
                        if (session.has(AppPermission.MANAGE_EMPLOYEES)) {
                            IconButton(
                                onClick = { deletingEmployee = emp },
                                modifier = Modifier.testTag("btn_delete_emp_${emp.id}")
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove employee", tint = ErrorRed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        EmployeeFormDialog(
            session = session,
            existing = null,
            nextSuggestedCode = "EMP-${(employees.size + 1).toString().padStart(3, '0')}",
            onDismiss = { showAddDialog = false },
            onSave = { code, name, photo, phone, dept, desig, joinMs, empStatus, active, basic, house, med, trans, other, otRate ->
                onSaveEmployee(null, code, name, photo, phone, dept, desig, joinMs, empStatus, active, basic, house, med, trans, other, otRate)
                showAddDialog = false
            }
        )
    }

    editingEmployee?.let { target ->
        EmployeeFormDialog(
            session = session,
            existing = target,
            nextSuggestedCode = target.employeeCode,
            onDismiss = { editingEmployee = null },
            onSave = { code, name, photo, phone, dept, desig, joinMs, empStatus, active, basic, house, med, trans, other, otRate ->
                onSaveEmployee(target.id, code, name, photo, phone, dept, desig, joinMs, empStatus, active, basic, house, med, trans, other, otRate)
                editingEmployee = null
            }
        )
    }

    viewingProfile?.let { emp ->
        EmployeeProfileDialog(
            session = session,
            employee = emp,
            currencySymbol = currencySymbol,
            onDismiss = { viewingProfile = null },
            onToggleActive = { active ->
                onSetEmployeeActive(emp.id, active)
                viewingProfile = null
            },
            onEdit = {
                viewingProfile = null
                editingEmployee = emp
            }
        )
    }

    deletingEmployee?.let { emp ->
        AlertDialog(
            onDismissRequest = { deletingEmployee = null },
            title = { Text("Remove Employee Record?") },
            text = {
                Text("Are you sure you want to remove ${emp.fullName} [${emp.employeeCode}]? Employees with finalized payroll or active advances cannot be removed and must be set Inactive instead.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteEmployee(emp.id)
                        deletingEmployee = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    modifier = Modifier.testTag("confirm_delete_emp_btn")
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEmployee = null }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
fun EmployeeAvatar(photoUri: String, fullName: String, sizeDp: Int = 44) {
    if (photoUri.isNotBlank()) {
        AsyncImage(
            model = Uri.parse(photoUri),
            contentDescription = fullName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp.dp).clip(CircleShape)
        )
    } else {
        val initials = fullName.trim().split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.take(1).uppercase() }.ifBlank { "E" }
        Box(
            modifier = Modifier.size(sizeDp.dp).clip(CircleShape).background(CyanAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CyanAccent)
        }
    }
}

@Composable
private fun EmployeeProfileDialog(
    session: SessionUser,
    employee: EmployeeSanitizedView,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${employee.fullName} (${employee.employeeCode})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmployeeAvatar(employee.photoUri, employee.fullName, sizeDp = 56)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(employee.designation, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                        Text(employee.department, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusChip(employee.employmentStatus)
                            StatusChip(if (employee.active) "ACTIVE" else "INACTIVE")
                        }
                    }
                }
                HorizontalDivider()
                Text("Phone: ${employee.phone}", style = MaterialTheme.typography.bodyMedium)
                Text("Joining Date: ${Formatters.formatDate(employee.joiningDate)} (Asia/Dhaka)", style = MaterialTheme.typography.bodyMedium)
                if (employee.basicSalary != null && session.has(AppPermission.VIEW_EMPLOYEE_SALARY)) {
                    HorizontalDivider()
                    Text("Confidential Compensation", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = IndigoAccent)
                    Text("Basic Salary: ${Formatters.formatTaka(employee.basicSalary, currencySymbol)}", style = MaterialTheme.typography.bodySmall)
                    Text("House Allowance: ${Formatters.formatTaka(employee.houseAllowance ?: 0.0, currencySymbol)}", style = MaterialTheme.typography.bodySmall)
                    Text("Medical Allowance: ${Formatters.formatTaka(employee.medicalAllowance ?: 0.0, currencySymbol)}", style = MaterialTheme.typography.bodySmall)
                    Text("Transport Allowance: ${Formatters.formatTaka(employee.transportAllowance ?: 0.0, currencySymbol)}", style = MaterialTheme.typography.bodySmall)
                    Text("Other Allowance: ${Formatters.formatTaka(employee.otherAllowance ?: 0.0, currencySymbol)}", style = MaterialTheme.typography.bodySmall)
                    Text("Overtime Rate/hr: ${Formatters.formatTaka(employee.overtimeHourlyRate ?: 0.0, currencySymbol)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (session.has(AppPermission.MANAGE_EMPLOYEES)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onToggleActive(!employee.active) }) {
                        Text(if (employee.active) "Deactivate" else "Activate")
                    }
                    Button(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                }
            } else {
                Button(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (session.has(AppPermission.MANAGE_EMPLOYEES)) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeFormDialog(
    session: SessionUser,
    existing: EmployeeSanitizedView?,
    nextSuggestedCode: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, Long, String, Boolean, Double, Double, Double, Double, Double, Double) -> Unit
) {
    var code by remember(existing) { mutableStateOf(existing?.employeeCode ?: nextSuggestedCode) }
    var fullName by remember(existing) { mutableStateOf(existing?.fullName ?: "") }
    var photoUri by remember(existing) { mutableStateOf(existing?.photoUri ?: "") }
    var phone by remember(existing) { mutableStateOf(existing?.phone ?: "") }
    var department by remember(existing) { mutableStateOf(existing?.department ?: "Production") }
    var designation by remember(existing) { mutableStateOf(existing?.designation ?: "Operator") }
    var joiningDateKey by remember(existing) {
        mutableStateOf(
            existing?.let { DhakaTime.dateKeyFromMillis(it.joiningDate) } ?: DhakaTime.todayDateKey()
        )
    }
    var empStatus by remember(existing) { mutableStateOf(existing?.employmentStatus ?: "FULL_TIME") }
    var statusExpanded by remember { mutableStateOf(false) }
    var active by remember(existing) { mutableStateOf(existing?.active ?: true) }

    val canEditSalary = session.has(AppPermission.VIEW_EMPLOYEE_SALARY)
    var basic by remember(existing) { mutableStateOf((existing?.basicSalary ?: 12000.0).toString()) }
    var house by remember(existing) { mutableStateOf((existing?.houseAllowance ?: 0.0).toString()) }
    var medical by remember(existing) { mutableStateOf((existing?.medicalAllowance ?: 0.0).toString()) }
    var transport by remember(existing) { mutableStateOf((existing?.transportAllowance ?: 0.0).toString()) }
    var other by remember(existing) { mutableStateOf((existing?.otherAllowance ?: 0.0).toString()) }
    var otRate by remember(existing) { mutableStateOf((existing?.overtimeHourlyRate ?: 0.0).toString()) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Employee" else "Edit Employee") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EmployeeAvatar(photoUri, fullName.ifBlank { "New" }, sizeDp = 48)
                    OutlinedButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (photoUri.isBlank()) "Pick Photo" else "Change Photo")
                    }
                }
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Unique Employee ID *") }, modifier = Modifier.fillMaxWidth().testTag("input_emp_code"))
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name *") }, modifier = Modifier.fillMaxWidth().testTag("input_emp_name"))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth().testTag("input_emp_phone"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = department, onValueChange = { department = it }, label = { Text("Department *") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = designation, onValueChange = { designation = it }, label = { Text("Designation *") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = joiningDateKey, onValueChange = { joiningDateKey = it }, label = { Text("Joining Date (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth())

                ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = { statusExpanded = !statusExpanded }) {
                    OutlinedTextField(
                        value = empStatus,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Employment Status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                        HrOperationsRepository.VALID_EMPLOYMENT_STATUSES.forEach { st ->
                            DropdownMenuItem(text = { Text(st) }, onClick = { empStatus = st; statusExpanded = false })
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Active Employee", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }

                if (canEditSalary) {
                    HorizontalDivider()
                    Text("Salary Structure (Confidential)", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = IndigoAccent)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = basic, onValueChange = { basic = it }, label = { Text("Basic Salary") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = house, onValueChange = { house = it }, label = { Text("House Allow.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = medical, onValueChange = { medical = it }, label = { Text("Medical Allow.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = transport, onValueChange = { transport = it }, label = { Text("Transport Allow.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = other, onValueChange = { other = it }, label = { Text("Other Allow.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = otRate, onValueChange = { otRate = it }, label = { Text("OT Rate/hr (0=auto)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val joinMs = runCatching { DhakaTime.parseDateKeyStartMillis(joiningDateKey) }.getOrDefault(System.currentTimeMillis())
                    onSave(
                        code, fullName, photoUri, phone, department, designation, joinMs, empStatus, active,
                        basic.toDoubleOrNull() ?: 0.0,
                        house.toDoubleOrNull() ?: 0.0,
                        medical.toDoubleOrNull() ?: 0.0,
                        transport.toDoubleOrNull() ?: 0.0,
                        other.toDoubleOrNull() ?: 0.0,
                        otRate.toDoubleOrNull() ?: 0.0
                    )
                },
                modifier = Modifier.testTag("btn_save_employee")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
