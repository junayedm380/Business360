package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AttendanceDailyClosingEntity
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.EmployeeAdvanceEntity
import com.example.data.local.LeaveRequestEntity
import com.example.data.local.LeaveTypeEntity
import com.example.data.local.PayrollRuleConfigEntity
import com.example.data.local.PayrollRunEntity
import com.example.data.repository.AttendanceMonthlySummaryRow
import com.example.data.repository.EmployeeSanitizedView
import com.example.data.repository.HrOperationsRepository
import com.example.data.repository.HrPayrollRepository
import com.example.data.repository.LeaveBalanceItem
import com.example.data.repository.ReportType
import com.example.domain.AppPermission
import com.example.domain.DhakaTime
import com.example.domain.SessionUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HrViewModel(
    private val hrOpsRepo: HrOperationsRepository,
    private val hrPayrollRepo: HrPayrollRepository
) : ViewModel() {

    private val _session = MutableStateFlow<SessionUser?>(null)

    private val _selectedDateKey = MutableStateFlow(DhakaTime.todayDateKey())
    val selectedDateKey: StateFlow<String> = _selectedDateKey.asStateFlow()

    private val _selectedMonthKey = MutableStateFlow(DhakaTime.currentMonthKey())
    val selectedMonthKey: StateFlow<String> = _selectedMonthKey.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _exportedCsv = MutableStateFlow<Pair<String, String>?>(null)
    val exportedCsv: StateFlow<Pair<String, String>?> = _exportedCsv.asStateFlow()

    private val _printableSlipHtml = MutableStateFlow<Pair<String, String>?>(null)
    val printableSlipHtml: StateFlow<Pair<String, String>?> = _printableSlipHtml.asStateFlow()

    fun setSession(session: SessionUser?) {
        _session.value = session
    }

    fun clearFeedback() { _feedback.value = null }
    fun clearExportedCsv() { _exportedCsv.value = null }
    fun clearPrintableSlip() { _printableSlipHtml.value = null }

    fun setSelectedDateKey(dateKey: String) {
        _selectedDateKey.value = dateKey
        if (dateKey.length >= 7) {
            _selectedMonthKey.value = dateKey.take(7)
        }
    }

    fun setSelectedMonthKey(monthKey: String) {
        _selectedMonthKey.value = monthKey
    }

    fun shiftMonth(delta: Int) {
        _selectedMonthKey.value = DhakaTime.shiftMonthKey(_selectedMonthKey.value, delta)
    }

    private fun <T> hrSessionFlow(defaultValue: T, provider: (SessionUser) -> Flow<T>): StateFlow<T> =
        _session.flatMapLatest { s ->
            if (s == null || !s.hasAnyHrAccess) flowOf(defaultValue)
            else provider(s).catch { emit(defaultValue) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), defaultValue)

    val employees: StateFlow<List<EmployeeSanitizedView>> = hrSessionFlow(emptyList()) { s ->
        if (s.has(AppPermission.VIEW_EMPLOYEES_BASIC) || s.has(AppPermission.MANAGE_EMPLOYEES) ||
            s.has(AppPermission.MARK_ATTENDANCE) || s.has(AppPermission.VIEW_ATTENDANCE_REPORTS) ||
            s.has(AppPermission.VIEW_LEAVES) || s.has(AppPermission.VIEW_PAYROLL)
        ) hrOpsRepo.observeEmployeesSanitized(s) else flowOf(emptyList())
    }

    val allAttendance: StateFlow<List<AttendanceRecordEntity>> = hrSessionFlow(emptyList()) { s ->
        if (s.has(AppPermission.MARK_ATTENDANCE) || s.has(AppPermission.EDIT_ATTENDANCE) ||
            s.has(AppPermission.VIEW_ATTENDANCE_REPORTS) || s.has(AppPermission.MANAGE_ATTENDANCE_CLOSING)
        ) hrOpsRepo.observeAllAttendance(s) else flowOf(emptyList())
    }

    val monthlyAttendance: StateFlow<List<AttendanceRecordEntity>> =
        combine(_session, _selectedMonthKey) { s, month -> s to month }
            .flatMapLatest { (s, month) ->
                if (s != null && (s.has(AppPermission.MARK_ATTENDANCE) || s.has(AppPermission.EDIT_ATTENDANCE) ||
                        s.has(AppPermission.VIEW_ATTENDANCE_REPORTS) || s.has(AppPermission.MANAGE_ATTENDANCE_CLOSING))
                ) hrOpsRepo.observeAttendanceForMonth(s, month).catch { emit(emptyList()) }
                else flowOf(emptyList())
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val monthlyAttendanceSummary: StateFlow<List<AttendanceMonthlySummaryRow>> =
        combine(_session, _selectedMonthKey) { s, month -> s to month }
            .flatMapLatest { (s, month) ->
                if (s != null && (s.has(AppPermission.VIEW_ATTENDANCE_REPORTS) || s.has(AppPermission.VIEW_HR_REPORTS) || s.has(AppPermission.VIEW_PAYROLL))) {
                    hrOpsRepo.observeMonthlyAttendanceSummary(s, month).catch { emit(emptyList()) }
                } else flowOf(emptyList())
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val attendanceClosings: StateFlow<List<AttendanceDailyClosingEntity>> = hrSessionFlow(emptyList()) { s ->
        if (s.has(AppPermission.MARK_ATTENDANCE) || s.has(AppPermission.EDIT_ATTENDANCE) ||
            s.has(AppPermission.VIEW_ATTENDANCE_REPORTS) || s.has(AppPermission.MANAGE_ATTENDANCE_CLOSING)
        ) hrOpsRepo.observeAttendanceClosings(s) else flowOf(emptyList())
    }

    val attendanceAuditLogs: StateFlow<List<AuditLogEntity>> = hrSessionFlow(emptyList()) { s ->
        if (s.has(AppPermission.MARK_ATTENDANCE) || s.has(AppPermission.EDIT_ATTENDANCE) ||
            s.has(AppPermission.VIEW_ATTENDANCE_REPORTS) || s.has(AppPermission.MANAGE_ATTENDANCE_CLOSING) ||
            s.has(AppPermission.VIEW_AUDIT_LOGS)
        ) hrOpsRepo.observeAttendanceAuditLogs(s) else flowOf(emptyList())
    }

    val leaveTypes: StateFlow<List<LeaveTypeEntity>> = hrSessionFlow(emptyList()) { s ->
        if (s.has(AppPermission.VIEW_LEAVES) || s.has(AppPermission.MANAGE_LEAVES)) {
            hrOpsRepo.observeLeaveTypes(s)
        } else flowOf(emptyList())
    }

    val leaveRequests: StateFlow<List<LeaveRequestEntity>> = hrSessionFlow(emptyList()) { s ->
        if (s.has(AppPermission.VIEW_LEAVES) || s.has(AppPermission.MANAGE_LEAVES)) {
            hrOpsRepo.observeLeaveRequests(s)
        } else flowOf(emptyList())
    }

    val leaveBalances: StateFlow<List<LeaveBalanceItem>> = hrSessionFlow(emptyList()) { s ->
        if (s.has(AppPermission.VIEW_LEAVES) || s.has(AppPermission.MANAGE_LEAVES)) {
            hrOpsRepo.observeLeaveBalances(s, DhakaTime.currentYear())
        } else flowOf(emptyList())
    }

    val payrollConfig: StateFlow<PayrollRuleConfigEntity?> = hrSessionFlow(null) { s ->
        if (s.canViewSalaryAndPayroll && s.has(AppPermission.VIEW_PAYROLL)) {
            hrPayrollRepo.observePayrollConfig(s)
        } else flowOf(null)
    }

    val employeeAdvances: StateFlow<List<EmployeeAdvanceEntity>> = hrSessionFlow(emptyList()) { s ->
        if (s.canViewSalaryAndPayroll && s.has(AppPermission.VIEW_PAYROLL)) {
            hrPayrollRepo.observeEmployeeAdvances(s)
        } else flowOf(emptyList())
    }

    val payrollRuns: StateFlow<List<PayrollRunEntity>> = hrSessionFlow(emptyList()) { s ->
        if (s.canViewSalaryAndPayroll && s.has(AppPermission.VIEW_PAYROLL)) {
            hrPayrollRepo.observePayrollRuns(s)
        } else flowOf(emptyList())
    }

    private inline fun withHrSession(crossinline block: suspend (SessionUser) -> Result<Any?>, successMsg: String) {
        val s = _session.value ?: return
        viewModelScope.launch {
            block(s)
                .onSuccess { _feedback.value = successMsg }
                .onFailure { _feedback.value = "Error: ${it.message ?: "Operation failed"}" }
        }
    }

    fun saveEmployee(
        existingId: String?,
        employeeCode: String,
        fullName: String,
        photoUri: String,
        phone: String,
        department: String,
        designation: String,
        joiningDate: Long,
        employmentStatus: String,
        active: Boolean,
        basicSalary: Double,
        houseAllowance: Double,
        medicalAllowance: Double,
        transportAllowance: Double,
        otherAllowance: Double,
        overtimeHourlyRate: Double
    ) = withHrSession({
        hrOpsRepo.saveEmployee(
            actor = it,
            existingId = existingId,
            employeeCode = employeeCode,
            fullName = fullName,
            photoUri = photoUri,
            phone = phone,
            department = department,
            designation = designation,
            joiningDate = joiningDate,
            employmentStatus = employmentStatus,
            active = active,
            basicSalary = basicSalary,
            houseAllowance = houseAllowance,
            medicalAllowance = medicalAllowance,
            transportAllowance = transportAllowance,
            otherAllowance = otherAllowance,
            overtimeHourlyRate = overtimeHourlyRate
        )
    }, if (existingId == null) "Employee added" else "Employee updated")

    fun setEmployeeActive(employeeId: String, active: Boolean) =
        withHrSession({ hrOpsRepo.setEmployeeActive(it, employeeId, active) }, if (active) "Employee activated" else "Employee deactivated")

    fun markOrUpdateAttendance(
        employeeId: String,
        dateKey: String,
        status: String,
        checkInTime: String,
        checkOutTime: String,
        overtimeHours: Double,
        notes: String
    ) = withHrSession({
        hrOpsRepo.markOrUpdateAttendance(it, employeeId, dateKey, status, checkInTime, checkOutTime, overtimeHours, notes)
    }, "Attendance saved ($status)")

    fun markAllPresent(dateKey: String) =
        withHrSession({ hrOpsRepo.markAllPresent(it, dateKey) }, "All unmarked active employees marked PRESENT")

    fun closeDailyAttendance(dateKey: String, notes: String) =
        withHrSession({ hrOpsRepo.closeDailyAttendance(it, dateKey, notes) }, "Daily attendance closed for $dateKey")

    fun reopenDailyAttendance(dateKey: String) =
        withHrSession({ hrOpsRepo.reopenDailyAttendance(it, dateKey) }, "Attendance reopened for $dateKey")

    fun saveLeaveType(existingId: String?, code: String, name: String, quota: Int, isPaid: Boolean, active: Boolean) =
        withHrSession({ hrOpsRepo.saveLeaveType(it, existingId, code, name, quota, isPaid, active) }, "Leave type saved")

    fun submitLeaveRequest(employeeId: String, leaveTypeId: String, startDateKey: String, endDateKey: String, reason: String) =
        withHrSession({ hrOpsRepo.submitLeaveRequest(it, employeeId, leaveTypeId, startDateKey, endDateKey, reason) }, "Leave request submitted")

    fun reviewLeaveRequest(requestId: String, approve: Boolean, note: String) =
        withHrSession({ hrOpsRepo.reviewLeaveRequest(it, requestId, approve, note) }, if (approve) "Leave approved & synced to attendance" else "Leave rejected")

    fun updatePayrollConfig(
        workingDays: Int,
        absentRate: Double,
        halfDayRate: Double,
        lateDaysPerHalfDay: Int,
        otMultiplier: Double,
        deductUnpaidLeave: Boolean
    ) = withHrSession({
        hrPayrollRepo.updatePayrollConfig(it, workingDays, absentRate, halfDayRate, lateDaysPerHalfDay, otMultiplier, deductUnpaidLeave)
    }, "Salary rules updated")

    fun issueEmployeeAdvance(employeeId: String, amount: Double, cashAccountId: String, reason: String, issueDateKey: String) =
        withHrSession({ hrPayrollRepo.issueEmployeeAdvance(it, employeeId, amount, cashAccountId, reason, issueDateKey) }, "Advance salary recorded")

    fun generateMonthlyPayroll(monthKey: String, defaultBonus: Double, otherDeduction: Double, notes: String) =
        withHrSession({ hrPayrollRepo.generateMonthlyPayroll(it, monthKey, defaultBonus, otherDeduction, notes) }, "Monthly payroll generated for $monthKey")

    fun updateDraftPayroll(payrollId: String, bonus: Double, advanceDed: Double, otherDed: Double, notes: String) =
        withHrSession({ hrPayrollRepo.updateDraftPayrollAdjustments(it, payrollId, bonus, advanceDed, otherDed, notes) }, "Payroll slip recalculated")

    fun approvePayroll(payrollId: String) =
        withHrSession({ hrPayrollRepo.approvePayrollRun(it, payrollId) }, "Payroll approved")

    fun payPayroll(payrollId: String, cashAccountId: String) =
        withHrSession({ hrPayrollRepo.payPayrollRun(it, payrollId, cashAccountId) }, "Salary paid & posted to accounting")

    fun openPrintableSlip(payrollId: String, slipTitle: String, businessName: String, currencySymbol: String) {
        val s = _session.value ?: return
        viewModelScope.launch {
            hrPayrollRepo.generatePrintableSalarySlipHtml(s, payrollId, businessName, currencySymbol)
                .onSuccess { html -> _printableSlipHtml.value = slipTitle to html }
                .onFailure { _feedback.value = "Blocked: ${it.message}" }
        }
    }

    fun exportHrReport(type: ReportType, monthKey: String) {
        val s = _session.value ?: return
        viewModelScope.launch {
            val result = when (type) {
                ReportType.HR_PAYROLL_MONTHLY -> hrPayrollRepo.generatePayrollCsv(s, monthKey)
                ReportType.HR_ATTENDANCE_MONTHLY, ReportType.HR_LEAVE_SUMMARY -> hrOpsRepo.generateHrOperationalCsv(s, type, monthKey)
                else -> Result.failure(IllegalArgumentException("Invalid HR report type"))
            }
            result
                .onSuccess { _exportedCsv.value = it }
                .onFailure { _feedback.value = "Export blocked: ${it.message}" }
        }
    }
}
