package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.AttendanceDailyClosingEntity
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.LeaveRequestEntity
import com.example.data.local.LeaveTypeEntity
import com.example.data.repository.EmployeeSanitizedView
import com.example.data.repository.HrOperationsRepository
import com.example.data.repository.LeaveBalanceItem
import com.example.domain.AppPermission
import com.example.domain.AppStrings
import com.example.domain.DhakaTime
import com.example.domain.Formatters
import com.example.domain.SessionUser
import com.example.ui.components.EmptyStateNotice
import com.example.ui.components.RestrictedBanner
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusChip
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSecondary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.VioletAccent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttendanceTab(
    session: SessionUser,
    strings: AppStrings,
    employees: List<EmployeeSanitizedView>,
    selectedDateKey: String,
    selectedMonthKey: String,
    allAttendance: List<AttendanceRecordEntity>,
    monthlyAttendance: List<AttendanceRecordEntity>,
    closings: List<AttendanceDailyClosingEntity>,
    auditLogs: List<AuditLogEntity>,
    onSelectDateKey: (String) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onMarkAttendance: (String, String, String, String, String, Double, String) -> Unit,
    onMarkAllPresent: (String) -> Unit,
    onCloseDailyAttendance: (String, String) -> Unit,
    onReopenDailyAttendance: (String) -> Unit
) {
    var mode by remember { mutableStateOf("DAILY") } // DAILY, CALENDAR, HISTORY
    var editingTarget by remember { mutableStateOf<Pair<EmployeeSanitizedView, AttendanceRecordEntity?>?>(null) }
    var showClosingDialog by remember { mutableStateOf(false) }

    val activeEmployees = remember(employees) { employees.filter { it.active } }
    val dateRecords = remember(allAttendance, monthlyAttendance, selectedDateKey) {
        (monthlyAttendance + allAttendance)
            .filter { it.dateKey == selectedDateKey }
            .associateBy { it.employeeId }
    }
    val dateClosing = remember(closings, selectedDateKey) { closings.firstOrNull { it.dateKey == selectedDateKey } }
    val isLocked = dateClosing?.locked == true && !session.isFullAdmin

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "DAILY", onClick = { mode = "DAILY" }, label = { Text("Daily Sheet") })
                FilterChip(selected = mode == "CALENDAR", onClick = { mode = "CALENDAR" }, label = { Text("Monthly Calendar") })
                FilterChip(selected = mode == "HISTORY", onClick = { mode = "HISTORY" }, label = { Text("Closings & Logs") })
            }
        }

        when (mode) {
            "DAILY" -> {
                item {
                    SectionCard(
                        title = "Daily Attendance • $selectedDateKey (Asia/Dhaka)",
                        accentColor = EmeraldSecondary,
                        actionLabel = "Today",
                        onAction = { onSelectDateKey(DhakaTime.todayDateKey()) }
                    ) {
                        var dateInput by remember(selectedDateKey) { mutableStateOf(selectedDateKey) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = dateInput,
                                onValueChange = { dateInput = it },
                                label = { Text("Date (yyyy-MM-dd)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(onClick = {
                                if (runCatching { DhakaTime.parseDateKeyStartMillis(dateInput) }.isSuccess) {
                                    onSelectDateKey(dateInput.trim())
                                }
                            }) { Text("Load") }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (session.has(AppPermission.MARK_ATTENDANCE)) {
                                Button(
                                    onClick = { onMarkAllPresent(selectedDateKey) },
                                    enabled = !isLocked && activeEmployees.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    modifier = Modifier.weight(1f).testTag("btn_mark_all_present")
                                ) {
                                    Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Mark All Present")
                                }
                            }
                            if (session.has(AppPermission.MANAGE_ATTENDANCE_CLOSING)) {
                                if (dateClosing?.locked == true && session.isFullAdmin) {
                                    OutlinedButton(
                                        onClick = { onReopenDailyAttendance(selectedDateKey) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Reopen Day")
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = { showClosingDialog = true },
                                        enabled = dateClosing?.locked != true && activeEmployees.isNotEmpty(),
                                        modifier = Modifier.weight(1f).testTag("btn_close_attendance")
                                    ) {
                                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (dateClosing?.locked == true) "Closed" else "Daily Closing")
                                    }
                                }
                            }
                        }
                        if (dateClosing?.locked == true) {
                            Spacer(Modifier.height(8.dp))
                            RestrictedBanner("Day closed by ${dateClosing.closedByName}: Present ${dateClosing.presentCount}, Absent ${dateClosing.absentCount}, Late ${dateClosing.lateCount}, Leave ${dateClosing.leaveCount}.")
                        }
                    }
                }

                if (activeEmployees.isEmpty()) {
                    item { EmptyStateNotice("No active employees yet. Add employees first.") }
                } else {
                    items(activeEmployees, key = { it.id }) { emp ->
                        val rec = dateRecords[emp.id]
                        val canSaveThis = if (rec == null) {
                            session.has(AppPermission.MARK_ATTENDANCE) && !isLocked
                        } else {
                            session.has(AppPermission.EDIT_ATTENDANCE) && !isLocked
                        }
                        SectionCard(
                            title = "${emp.fullName} [${emp.employeeCode}]",
                            accentColor = statusColor(rec?.status),
                            actionLabel = if (canSaveThis) (if (rec == null) "Mark" else "Edit") else null,
                            onAction = if (canSaveThis) ({ editingTarget = emp to rec }) else null
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${emp.designation} • ${emp.department}", style = MaterialTheme.typography.bodySmall)
                                    if (rec != null) {
                                        val timeStr = if (rec.checkInTime.isNotBlank() || rec.checkOutTime.isNotBlank()) {
                                            "In: ${rec.checkInTime.ifBlank { "--" }} | Out: ${rec.checkOutTime.ifBlank { "--" }}"
                                        } else "No time log"
                                        Text("$timeStr • OT: ${rec.overtimeHours}h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                        if (rec.notes.isNotBlank()) {
                                            Text(rec.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        Text("Not marked yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                StatusChip(rec?.status ?: "UNMARKED")
                            }
                            if (canSaveThis) {
                                Spacer(Modifier.height(8.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("PRESENT", "ABSENT", "LATE", "HALF_DAY", "HOLIDAY").forEach { st ->
                                        FilterChip(
                                            selected = rec?.status == st,
                                            onClick = {
                                                val defaultIn = if (st in listOf("PRESENT", "LATE", "HALF_DAY")) (rec?.checkInTime?.ifBlank { "09:00" } ?: "09:00") else ""
                                                val defaultOut = if (st == "PRESENT") (rec?.checkOutTime?.ifBlank { "18:00" } ?: "18:00") else (rec?.checkOutTime ?: "")
                                                onMarkAttendance(emp.id, selectedDateKey, st, defaultIn, defaultOut, rec?.overtimeHours ?: 0.0, rec?.notes ?: "")
                                            },
                                            label = { Text(shortLabel(st), style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "CALENDAR" -> {
                item {
                    val monthDays = remember(selectedMonthKey) {
                        runCatching { DhakaTime.daysInMonth(selectedMonthKey) }.getOrDefault(emptyList())
                    }
                    val mapByEmpDate = remember(monthlyAttendance) {
                        monthlyAttendance.associateBy { "${it.employeeId}:${it.dateKey}" }
                    }
                    SectionCard(title = "Monthly Calendar ($selectedMonthKey)", accentColor = IndigoAccent) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onShiftMonth(-1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                            }
                            Text(selectedMonthKey, style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold))
                            IconButton(onClick = { onShiftMonth(1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (activeEmployees.isEmpty()) {
                            EmptyStateNotice("No employees to display")
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                // Day header row
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Employee", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    monthDays.forEach { dKey ->
                                        Text(
                                            text = dKey.takeLast(2),
                                            modifier = Modifier.width(30.dp),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
                                        )
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                activeEmployees.forEach { emp ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                        Text(
                                            "${emp.employeeCode} ${emp.fullName.substringBefore(" ")}",
                                            modifier = Modifier.width(120.dp),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1
                                        )
                                        monthDays.forEach { dKey ->
                                            val rec = mapByEmpDate["${emp.id}:$dKey"]
                                            val bg = statusColor(rec?.status).copy(alpha = if (rec == null) 0.08f else 0.22f)
                                            val fg = if (rec == null) MaterialTheme.colorScheme.onSurfaceVariant else statusColor(rec.status)
                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = 2.dp)
                                                    .size(26.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(bg)
                                                    .clickable {
                                                        onSelectDateKey(dKey)
                                                        val allow = if (rec == null) session.has(AppPermission.MARK_ATTENDANCE) else session.has(AppPermission.EDIT_ATTENDANCE)
                                                        if (allow) editingTarget = emp to rec
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = shortCode(rec?.status),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = fg
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "HISTORY" -> {
                item {
                    SectionCard(title = "Daily Attendance Closings (${closings.size})", accentColor = CyanAccent) {
                        if (closings.isEmpty()) {
                            Text("No daily attendance closings recorded yet.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            closings.take(15).forEach { cl ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${cl.dateKey} • By ${cl.closedByName}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text("P:${cl.presentCount} A:${cl.absentCount} L:${cl.lateCount} HD:${cl.halfDayCount} LV:${cl.leaveCount} H:${cl.holidayCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    StatusChip(if (cl.locked) "LOCKED" else "REOPENED")
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = "Attendance & Leave Audit Logs (${auditLogs.size})", accentColor = VioletAccent) {
                        if (auditLogs.isEmpty()) {
                            Text("No audit entries yet.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            auditLogs.take(30).forEach { log ->
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Text("${log.action} • ${log.userName}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                    if (log.previousValue.isNotBlank()) {
                                        Text("Prev: ${log.previousValue}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("New: ${log.newValue}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    Text(Formatters.formatDateTime(log.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    editingTarget?.let { (emp, existingRec) ->
        AttendanceEntryDialog(
            employee = emp,
            dateKey = existingRec?.dateKey ?: selectedDateKey,
            existing = existingRec,
            onDismiss = { editingTarget = null },
            onSave = { status, checkIn, checkOut, otHours, notes ->
                onMarkAttendance(emp.id, existingRec?.dateKey ?: selectedDateKey, status, checkIn, checkOut, otHours, notes)
                editingTarget = null
            }
        )
    }

    if (showClosingDialog) {
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showClosingDialog = false },
            title = { Text("Close Daily Attendance ($selectedDateKey)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Closing locks today's attendance summary for Manager edits and posts daily counts.")
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Closing Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCloseDailyAttendance(selectedDateKey, notes)
                    showClosingDialog = false
                }) { Text("Confirm Closing") }
            },
            dismissButton = { TextButton(onClick = { showClosingDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AttendanceEntryDialog(
    employee: EmployeeSanitizedView,
    dateKey: String,
    existing: AttendanceRecordEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, String) -> Unit
) {
    var status by remember(existing) { mutableStateOf(existing?.status ?: "PRESENT") }
    var checkIn by remember(existing) { mutableStateOf(existing?.checkInTime ?: "09:00") }
    var checkOut by remember(existing) { mutableStateOf(existing?.checkOutTime ?: "18:00") }
    var overtime by remember(existing) { mutableStateOf((existing?.overtimeHours ?: 0.0).toString()) }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${employee.fullName} • $dateKey") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status", style = MaterialTheme.typography.labelMedium)
                HrOperationsRepository.VALID_ATTENDANCE_STATUSES.forEach { st ->
                    FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = checkIn, onValueChange = { checkIn = it }, label = { Text("Check-In") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = checkOut, onValueChange = { checkOut = it }, label = { Text("Check-Out") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = overtime,
                    onValueChange = { overtime = it },
                    label = { Text("Overtime Hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(status, checkIn, checkOut, overtime.toDoubleOrNull() ?: 0.0, notes)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LeaveTab(
    session: SessionUser,
    strings: AppStrings,
    employees: List<EmployeeSanitizedView>,
    leaveTypes: List<LeaveTypeEntity>,
    leaveRequests: List<LeaveRequestEntity>,
    leaveBalances: List<LeaveBalanceItem>,
    onSaveLeaveType: (String?, String, String, Int, Boolean, Boolean) -> Unit,
    onSubmitLeaveRequest: (String, String, String, String, String) -> Unit,
    onReviewLeaveRequest: (String, Boolean, String) -> Unit
) {
    var mode by remember { mutableStateOf("REQUESTS") } // REQUESTS, BALANCES, TYPES
    var showNewRequestDialog by remember { mutableStateOf(false) }
    var showNewTypeDialog by remember { mutableStateOf(false) }
    val empById = remember(employees) { employees.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "REQUESTS", onClick = { mode = "REQUESTS" }, label = { Text("Requests (${leaveRequests.size})") })
                FilterChip(selected = mode == "BALANCES", onClick = { mode = "BALANCES" }, label = { Text("Balances") })
                FilterChip(selected = mode == "TYPES", onClick = { mode = "TYPES" }, label = { Text("Types") })
            }
        }

        when (mode) {
            "REQUESTS" -> {
                if (session.has(AppPermission.MANAGE_LEAVES)) {
                    item {
                        Button(
                            onClick = { showNewRequestDialog = true },
                            enabled = employees.any { it.active } && leaveTypes.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().testTag("btn_new_leave_request")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Submit Leave Request")
                        }
                    }
                }
                if (leaveRequests.isEmpty()) {
                    item { EmptyStateNotice(strings.noDataYet) }
                } else {
                    items(leaveRequests, key = { it.id }) { req ->
                        val emp = empById[req.employeeId]
                        SectionCard(title = "${emp?.fullName ?: req.employeeId} • ${req.leaveTypeName}") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${req.startDateKey} to ${req.endDateKey} (${req.totalDays} days)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                StatusChip(req.status)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${if (req.isPaid) "Paid Leave" else "Unpaid Leave"}${if (req.syncedToAttendance) " • Synced to Attendance" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (req.syncedToAttendance) SuccessGreen else MaterialTheme.colorScheme.secondary
                            )
                            Text("Reason: ${req.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (req.status == "PENDING" && session.has(AppPermission.MANAGE_LEAVES)) {
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onReviewLeaveRequest(req.id, true, "Approved") },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                        modifier = Modifier.weight(1f).testTag("btn_approve_leave_${req.id}")
                                    ) { Text(strings.approve) }
                                    OutlinedButton(
                                        onClick = { onReviewLeaveRequest(req.id, false, "Declined") },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(strings.reject) }
                                }
                            }
                        }
                    }
                }
            }

            "BALANCES" -> {
                if (leaveBalances.isEmpty()) {
                    item { EmptyStateNotice(strings.noDataYet) }
                } else {
                    val grouped = leaveBalances.groupBy { it.employeeId }
                    grouped.forEach { (empId, items) ->
                        item(key = empId) {
                            val first = items.first()
                            SectionCard(title = "${first.employeeName} [${first.employeeCode}]", accentColor = CyanAccent) {
                                items.forEach { bal ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${bal.leaveTypeName} (${if (bal.isPaid) "Paid" else "Unpaid"})", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "Used ${bal.usedDays} / ${bal.annualQuota} • Rem ${bal.remainingDays}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.SemiBold),
                                            color = IndigoAccent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "TYPES" -> {
                if (session.has(AppPermission.MANAGE_LEAVES)) {
                    item {
                        Button(onClick = { showNewTypeDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add Leave Type")
                        }
                    }
                }
                items(leaveTypes, key = { it.id }) { lt ->
                    SectionCard(title = "${lt.name} (${lt.code})") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Annual Quota: ${lt.annualQuotaDays} days", style = MaterialTheme.typography.bodyMedium)
                            StatusChip(if (lt.isPaid) "PAID" else "UNPAID")
                        }
                    }
                }
            }
        }
    }

    if (showNewRequestDialog) {
        LeaveRequestDialog(
            employees = employees.filter { it.active },
            leaveTypes = leaveTypes.filter { it.active },
            onDismiss = { showNewRequestDialog = false },
            onSubmit = { empId, typeId, startKey, endKey, reason ->
                onSubmitLeaveRequest(empId, typeId, startKey, endKey, reason)
                showNewRequestDialog = false
            }
        )
    }

    if (showNewTypeDialog) {
        LeaveTypeDialog(
            onDismiss = { showNewTypeDialog = false },
            onSave = { code, name, quota, paid ->
                onSaveLeaveType(null, code, name, quota, paid, true)
                showNewTypeDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveRequestDialog(
    employees: List<EmployeeSanitizedView>,
    leaveTypes: List<LeaveTypeEntity>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String) -> Unit
) {
    var selectedEmp by remember { mutableStateOf(employees.first()) }
    var empExpanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(leaveTypes.first()) }
    var typeExpanded by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(DhakaTime.todayDateKey()) }
    var endDate by remember { mutableStateOf(DhakaTime.todayDateKey()) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Leave Request") },
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
                        employees.forEach { e ->
                            DropdownMenuItem(text = { Text("${e.fullName} (${e.employeeCode})") }, onClick = { selectedEmp = e; empExpanded = false })
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                    OutlinedTextField(
                        value = "${selectedType.name} (${if (selectedType.isPaid) "Paid" else "Unpaid"})",
                        onValueChange = {}, readOnly = true, label = { Text("Leave Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        leaveTypes.forEach { lt ->
                            DropdownMenuItem(text = { Text("${lt.name} (${lt.annualQuotaDays}d, ${if (lt.isPaid) "Paid" else "Unpaid"})") }, onClick = { selectedType = lt; typeExpanded = false })
                        }
                    }
                }
                OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("Start Date (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("End Date (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason *") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(selectedEmp.id, selectedType.id, startDate, endDate, reason) }) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LeaveTypeDialog(onDismiss: () -> Unit, onSave: (String, String, Int, Boolean) -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var quota by remember { mutableStateOf("10") }
    var isPaid by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Leave Type") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code (e.g. PATERNITY)") })
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = quota, onValueChange = { quota = it }, label = { Text("Annual Quota Days") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Paid Leave")
                    Switch(checked = isPaid, onCheckedChange = { isPaid = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(code, name, quota.toIntOrNull() ?: 0, isPaid) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun statusColor(status: String?): Color = when (status?.uppercase()) {
    "PRESENT" -> SuccessGreen
    "ABSENT" -> ErrorRed
    "LATE" -> AmberTertiary
    "HALF_DAY" -> CyanAccent
    "LEAVE" -> IndigoAccent
    "HOLIDAY" -> EmeraldSecondary
    else -> Color.Gray
}

private fun shortLabel(status: String): String = when (status) {
    "PRESENT" -> "Present"
    "ABSENT" -> "Absent"
    "LATE" -> "Late"
    "HALF_DAY" -> "Half Day"
    "HOLIDAY" -> "Holiday"
    else -> status
}

private fun shortCode(status: String?): String = when (status?.uppercase()) {
    "PRESENT" -> "P"
    "ABSENT" -> "A"
    "LATE" -> "L"
    "HALF_DAY" -> "H"
    "LEAVE" -> "LV"
    "HOLIDAY" -> "HD"
    else -> "-"
}
