package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AttendanceDailyClosingEntity
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.ErpDatabase
import com.example.data.local.LeaveRequestEntity
import com.example.data.local.LeaveTypeEntity
import com.example.data.local.NotificationEntity
import com.example.domain.AppPermission
import com.example.domain.DhakaTime
import com.example.domain.Formatters
import com.example.domain.SessionUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class HrOperationsRepository(private val db: ErpDatabase) {
    private val dao = db.erpDao()
    private val hrDao = db.hrDao()

    companion object {
        val VALID_ATTENDANCE_STATUSES = setOf("PRESENT", "ABSENT", "LATE", "HALF_DAY", "LEAVE", "HOLIDAY")
        val VALID_EMPLOYMENT_STATUSES = setOf("FULL_TIME", "PART_TIME", "CONTRACT", "PROBATION")
    }

    private fun SessionUser.canReadEmployees(): Boolean =
        has(AppPermission.VIEW_EMPLOYEES_BASIC) ||
            has(AppPermission.MANAGE_EMPLOYEES) ||
            has(AppPermission.MARK_ATTENDANCE) ||
            has(AppPermission.VIEW_ATTENDANCE_REPORTS) ||
            has(AppPermission.VIEW_LEAVES) ||
            has(AppPermission.VIEW_PAYROLL)

    private fun SessionUser.canReadAttendance(): Boolean =
        has(AppPermission.MARK_ATTENDANCE) ||
            has(AppPermission.EDIT_ATTENDANCE) ||
            has(AppPermission.VIEW_ATTENDANCE_REPORTS) ||
            has(AppPermission.MANAGE_ATTENDANCE_CLOSING)

    private fun SessionUser.canReadLeaves(): Boolean =
        has(AppPermission.VIEW_LEAVES) || has(AppPermission.MANAGE_LEAVES)

    // --- Employees (Salary strictly redacted unless VIEW_EMPLOYEE_SALARY) ---
    fun observeEmployeesSanitized(session: SessionUser): Flow<List<EmployeeSanitizedView>> {
        if (!session.canReadEmployees()) {
            throw ErpSecurityException("Employee directory access denied")
        }
        val canSeeSalary = session.has(AppPermission.VIEW_EMPLOYEE_SALARY)
        return hrDao.observeEmployees().map { list ->
            list.map { emp -> emp.toSanitized(canSeeSalary) }
        }
    }

    suspend fun saveEmployee(
        actor: SessionUser,
        existingId: String? = null,
        employeeCode: String,
        fullName: String,
        photoUri: String,
        phone: String,
        department: String,
        designation: String,
        joiningDate: Long,
        employmentStatus: String,
        active: Boolean,
        linkedUserId: String = "",
        basicSalary: Double,
        houseAllowance: Double,
        medicalAllowance: Double,
        transportAllowance: Double,
        otherAllowance: Double,
        overtimeHourlyRate: Double
    ): Result<EmployeeSanitizedView> = try {
        if (!actor.has(AppPermission.MANAGE_EMPLOYEES)) {
            throw ErpSecurityException("Employee management permission required")
        }
        val cleanCode = employeeCode.trim().uppercase()
        require(cleanCode.isNotBlank()) { "Unique Employee ID is required" }
        require(fullName.isNotBlank()) { "Employee full name is required" }
        require(phone.isNotBlank()) { "Employee phone number is required" }
        require(department.isNotBlank() && designation.isNotBlank()) { "Department and designation are required" }
        val statusNorm = employmentStatus.trim().uppercase()
        require(statusNorm in VALID_EMPLOYMENT_STATUSES) { "Invalid employment status" }

        val existing = existingId?.let { hrDao.getEmployeeById(it) ?: throw IllegalArgumentException("Employee not found") }
        val codeCollision = hrDao.getEmployeeByCode(cleanCode)
        if (codeCollision != null && codeCollision.id != existing?.id) {
            throw IllegalArgumentException("Employee ID '$cleanCode' is already in use")
        }

        val canModifySalary = actor.has(AppPermission.VIEW_EMPLOYEE_SALARY)
        if (canModifySalary) {
            require(
                basicSalary >= 0 && houseAllowance >= 0 && medicalAllowance >= 0 &&
                    transportAllowance >= 0 && otherAllowance >= 0 && overtimeHourlyRate >= 0
            ) { "Salary and allowance amounts cannot be negative" }
        }

        val now = System.currentTimeMillis()
        val entity = EmployeeEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            employeeCode = cleanCode,
            fullName = fullName.trim(),
            photoUri = photoUri.ifBlank { existing?.photoUri ?: "" },
            phone = phone.trim(),
            department = department.trim(),
            designation = designation.trim(),
            joiningDate = joiningDate,
            employmentStatus = statusNorm,
            active = active,
            linkedUserId = linkedUserId.trim(),
            basicSalary = if (canModifySalary) Formatters.roundMoney(basicSalary) else (existing?.basicSalary ?: 0.0),
            houseAllowance = if (canModifySalary) Formatters.roundMoney(houseAllowance) else (existing?.houseAllowance ?: 0.0),
            medicalAllowance = if (canModifySalary) Formatters.roundMoney(medicalAllowance) else (existing?.medicalAllowance ?: 0.0),
            transportAllowance = if (canModifySalary) Formatters.roundMoney(transportAllowance) else (existing?.transportAllowance ?: 0.0),
            otherAllowance = if (canModifySalary) Formatters.roundMoney(otherAllowance) else (existing?.otherAllowance ?: 0.0),
            overtimeHourlyRate = if (canModifySalary) Formatters.roundMoney(overtimeHourlyRate) else (existing?.overtimeHourlyRate ?: 0.0),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        db.withTransaction {
            hrDao.upsertEmployee(entity)
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = if (existing == null) "CREATE_EMPLOYEE" else "UPDATE_EMPLOYEE",
                    module = "HR_EMPLOYEES",
                    recordId = entity.id,
                    previousValue = existing?.let { "${it.employeeCode} ${it.fullName} active=${it.active}" } ?: "",
                    newValue = "${entity.employeeCode} ${entity.fullName} (${entity.department} / ${entity.designation}) active=${entity.active}"
                )
            )
        }
        Result.success(entity.toSanitized(actor.has(AppPermission.VIEW_EMPLOYEE_SALARY)))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun setEmployeeActive(actor: SessionUser, employeeId: String, active: Boolean): Result<Unit> = try {
        if (!actor.has(AppPermission.MANAGE_EMPLOYEES)) {
            throw ErpSecurityException("Employee management permission required")
        }
        val existing = hrDao.getEmployeeById(employeeId) ?: throw IllegalArgumentException("Employee not found")
        val updated = existing.copy(active = active, updatedAt = System.currentTimeMillis())
        db.withTransaction {
            hrDao.upsertEmployee(updated)
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = if (active) "ACTIVATE_EMPLOYEE" else "DEACTIVATE_EMPLOYEE",
                    module = "HR_EMPLOYEES",
                    recordId = employeeId,
                    previousValue = "active=${existing.active}",
                    newValue = "active=$active"
                )
            )
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- Manual Attendance Management (Asia/Dhaka) ---
    fun observeAllAttendance(session: SessionUser): Flow<List<AttendanceRecordEntity>> {
        if (!session.canReadAttendance()) throw ErpSecurityException("Attendance access denied")
        return hrDao.observeAllAttendance()
    }

    fun observeAttendanceForMonth(session: SessionUser, monthKey: String): Flow<List<AttendanceRecordEntity>> {
        if (!session.canReadAttendance()) throw ErpSecurityException("Attendance access denied")
        return hrDao.observeAttendanceForMonth("$monthKey%")
    }

    fun observeAttendanceClosings(session: SessionUser): Flow<List<AttendanceDailyClosingEntity>> {
        if (!session.canReadAttendance()) throw ErpSecurityException("Attendance closing access denied")
        return hrDao.observeAttendanceClosings()
    }

    fun observeAttendanceAuditLogs(session: SessionUser): Flow<List<AuditLogEntity>> {
        if (!session.canReadAttendance() && !session.has(AppPermission.VIEW_AUDIT_LOGS)) {
            throw ErpSecurityException("Attendance audit log access denied")
        }
        return dao.observeAuditLogs().map { logs ->
            logs.filter { it.module == "ATTENDANCE" || it.module == "HR_LEAVES" }
        }
    }

    fun observeMonthlyAttendanceSummary(
        session: SessionUser,
        monthKey: String
    ): Flow<List<AttendanceMonthlySummaryRow>> {
        if (!session.has(AppPermission.VIEW_ATTENDANCE_REPORTS) && !session.has(AppPermission.VIEW_HR_REPORTS) && !session.has(AppPermission.VIEW_PAYROLL)) {
            throw ErpSecurityException("Attendance reports access denied")
        }
        return combine(
            hrDao.observeEmployees(),
            hrDao.observeAttendanceForMonth("$monthKey%"),
            hrDao.observeLeaveRequests()
        ) { employees, records, leaveRequests ->
            val leaveById = leaveRequests.associateBy { it.id }
            val byEmp = records.groupBy { it.employeeId }
            employees.map { emp ->
                val list = byEmp[emp.id].orEmpty()
                var paidLeave = 0
                var unpaidLeave = 0
                list.filter { it.status == "LEAVE" }.forEach { rec ->
                    val linked = leaveById[rec.leaveRequestId]
                    if (linked != null && !linked.isPaid) unpaidLeave++ else paidLeave++
                }
                AttendanceMonthlySummaryRow(
                    employeeId = emp.id,
                    employeeCode = emp.employeeCode,
                    employeeName = emp.fullName,
                    department = emp.department,
                    monthKey = monthKey,
                    presentDays = list.count { it.status == "PRESENT" },
                    lateDays = list.count { it.status == "LATE" },
                    halfDays = list.count { it.status == "HALF_DAY" },
                    absentDays = list.count { it.status == "ABSENT" },
                    paidLeaveDays = paidLeave,
                    unpaidLeaveDays = unpaidLeave,
                    holidayDays = list.count { it.status == "HOLIDAY" },
                    overtimeHours = Formatters.roundQty(list.sumOf { it.overtimeHours })
                )
            }
        }
    }

    suspend fun markOrUpdateAttendance(
        actor: SessionUser,
        employeeId: String,
        dateKey: String,
        status: String,
        checkInTime: String = "",
        checkOutTime: String = "",
        overtimeHours: Double = 0.0,
        notes: String = ""
    ): Result<AttendanceRecordEntity> = try {
        val statusNorm = status.trim().uppercase()
        require(statusNorm in VALID_ATTENDANCE_STATUSES) { "Invalid attendance status: $status" }
        DhakaTime.parseDateKeyStartMillis(dateKey)
        require(overtimeHours >= 0.0) { "Overtime hours cannot be negative" }

        val emp = hrDao.getEmployeeById(employeeId) ?: throw IllegalArgumentException("Employee not found")
        val existing = hrDao.getAttendanceByEmployeeAndDate(employeeId, dateKey)
        if (existing == null) {
            if (!actor.has(AppPermission.MARK_ATTENDANCE)) {
                throw ErpSecurityException("Attendance marking permission required")
            }
        } else {
            if (!actor.has(AppPermission.EDIT_ATTENDANCE)) {
                throw ErpSecurityException("Attendance edit permission required")
            }
        }
        val closing = hrDao.getAttendanceClosingByDate(dateKey)
        if (closing?.locked == true && !actor.isFullAdmin) {
            throw ErpSecurityException("Attendance for $dateKey is closed and locked. Contact Admin to reopen.")
        }

        val now = System.currentTimeMillis()
        val updated = AttendanceRecordEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            employeeId = employeeId,
            dateKey = dateKey.trim(),
            status = statusNorm,
            checkInTime = checkInTime.trim(),
            checkOutTime = checkOutTime.trim(),
            overtimeHours = Formatters.roundQty(overtimeHours),
            leaveRequestId = if (statusNorm == "LEAVE") (existing?.leaveRequestId ?: "") else "",
            notes = notes.trim(),
            markedByUserId = actor.id,
            markedByName = actor.fullName,
            updatedAt = now
        )
        db.withTransaction {
            hrDao.upsertAttendance(updated)
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = if (existing == null) "MARK_ATTENDANCE" else "EDIT_ATTENDANCE",
                    module = "ATTENDANCE",
                    recordId = updated.id,
                    previousValue = existing?.let { "${emp.employeeCode} ${it.dateKey}: ${it.status} (${it.checkInTime}-${it.checkOutTime}, OT=${it.overtimeHours})" } ?: "",
                    newValue = "${emp.employeeCode} ${updated.dateKey}: ${updated.status} (${updated.checkInTime}-${updated.checkOutTime}, OT=${updated.overtimeHours})"
                )
            )
        }
        Result.success(updated)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun markAllPresent(
        actor: SessionUser,
        dateKey: String,
        defaultCheckIn: String = "09:00",
        defaultCheckOut: String = "18:00"
    ): Result<Int> = try {
        if (!actor.has(AppPermission.MARK_ATTENDANCE)) {
            throw ErpSecurityException("Attendance marking permission required")
        }
        DhakaTime.parseDateKeyStartMillis(dateKey)
        val closing = hrDao.getAttendanceClosingByDate(dateKey)
        if (closing?.locked == true && !actor.isFullAdmin) {
            throw ErpSecurityException("Attendance for $dateKey is closed and locked.")
        }

        val count = db.withTransaction {
            val activeEmployees = hrDao.getAllEmployeesOnce().filter { it.active }
            val existingRecords = hrDao.getAttendanceForDateOnce(dateKey).associateBy { it.employeeId }
            var markedCount = 0
            val now = System.currentTimeMillis()
            activeEmployees.forEach { emp ->
                val existing = existingRecords[emp.id]
                // Preserve approved LEAVE/HOLIDAY/existing records unless absent
                if (existing == null) {
                    hrDao.upsertAttendance(
                        AttendanceRecordEntity(
                            employeeId = emp.id,
                            dateKey = dateKey,
                            status = "PRESENT",
                            checkInTime = defaultCheckIn,
                            checkOutTime = defaultCheckOut,
                            overtimeHours = 0.0,
                            markedByUserId = actor.id,
                            markedByName = actor.fullName,
                            updatedAt = now
                        )
                    )
                    markedCount++
                }
            }
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = "MARK_ALL_PRESENT",
                    module = "ATTENDANCE",
                    recordId = dateKey,
                    newValue = "Marked $markedCount active employees PRESENT on $dateKey"
                )
            )
            markedCount
        }
        Result.success(count)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun closeDailyAttendance(
        actor: SessionUser,
        dateKey: String,
        notes: String = ""
    ): Result<AttendanceDailyClosingEntity> = try {
        if (!actor.has(AppPermission.MANAGE_ATTENDANCE_CLOSING)) {
            throw ErpSecurityException("Daily attendance closing permission required")
        }
        DhakaTime.parseDateKeyStartMillis(dateKey)
        val closing = db.withTransaction {
            val existing = hrDao.getAttendanceClosingByDate(dateKey)
            val activeEmployees = hrDao.getAllEmployeesOnce().filter { it.active }
            val records = hrDao.getAttendanceForDateOnce(dateKey)
            val entity = AttendanceDailyClosingEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                dateKey = dateKey,
                totalEmployees = activeEmployees.size,
                presentCount = records.count { it.status == "PRESENT" },
                absentCount = records.count { it.status == "ABSENT" },
                lateCount = records.count { it.status == "LATE" },
                halfDayCount = records.count { it.status == "HALF_DAY" },
                leaveCount = records.count { it.status == "LEAVE" },
                holidayCount = records.count { it.status == "HOLIDAY" },
                locked = true,
                closedByUserId = actor.id,
                closedByName = actor.fullName,
                notes = notes.trim(),
                closedAt = System.currentTimeMillis()
            )
            hrDao.upsertAttendanceClosing(entity)
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = "ATTENDANCE_DAILY_CLOSING",
                    module = "ATTENDANCE",
                    recordId = dateKey,
                    newValue = "P:${entity.presentCount} A:${entity.absentCount} L:${entity.lateCount} HD:${entity.halfDayCount} LV:${entity.leaveCount} H:${entity.holidayCount}"
                )
            )
            dao.insertNotification(
                NotificationEntity(
                    targetAudience = "OPS_AND_ADMIN",
                    title = "Daily Attendance Closed ($dateKey)",
                    message = "Present: ${entity.presentCount}, Absent: ${entity.absentCount}, Late: ${entity.lateCount}, Leave: ${entity.leaveCount}.",
                    category = "ATTENDANCE",
                    referenceId = dateKey
                )
            )
            entity
        }
        Result.success(closing)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun reopenDailyAttendance(actor: SessionUser, dateKey: String): Result<Unit> = try {
        if (!actor.isFullAdmin || !actor.has(AppPermission.MANAGE_ATTENDANCE_CLOSING)) {
            throw ErpSecurityException("Only authorized Admin or Super Admin can reopen closed attendance")
        }
        val existing = hrDao.getAttendanceClosingByDate(dateKey)
            ?: throw IllegalArgumentException("No attendance closing found for $dateKey")
        db.withTransaction {
            hrDao.upsertAttendanceClosing(existing.copy(locked = false))
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = "ATTENDANCE_REOPEN",
                    module = "ATTENDANCE",
                    recordId = dateKey,
                    previousValue = "locked=true",
                    newValue = "locked=false"
                )
            )
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- Leave Management & Synchronization ---
    fun observeLeaveTypes(session: SessionUser): Flow<List<LeaveTypeEntity>> {
        if (!session.canReadLeaves()) throw ErpSecurityException("Leave access denied")
        return hrDao.observeLeaveTypes()
    }

    fun observeLeaveRequests(session: SessionUser): Flow<List<LeaveRequestEntity>> {
        if (!session.canReadLeaves()) throw ErpSecurityException("Leave access denied")
        return hrDao.observeLeaveRequests()
    }

    fun observeLeaveBalances(session: SessionUser, year: Int = DhakaTime.currentYear()): Flow<List<LeaveBalanceItem>> {
        if (!session.canReadLeaves()) throw ErpSecurityException("Leave balances access denied")
        val yearPrefix = "$year-"
        return combine(
            hrDao.observeEmployees(),
            hrDao.observeLeaveTypes(),
            hrDao.observeLeaveRequests()
        ) { employees, leaveTypes, requests ->
            val approvedThisYear = requests.filter {
                it.status == "APPROVED" && it.startDateKey.startsWith(yearPrefix)
            }
            val activeTypes = leaveTypes.filter { it.active }
            employees.filter { it.active }.flatMap { emp ->
                activeTypes.map { lt ->
                    val used = approvedThisYear
                        .filter { it.employeeId == emp.id && it.leaveTypeId == lt.id }
                        .sumOf { it.totalDays }
                    LeaveBalanceItem(
                        employeeId = emp.id,
                        employeeCode = emp.employeeCode,
                        employeeName = emp.fullName,
                        leaveTypeId = lt.id,
                        leaveTypeName = lt.name,
                        isPaid = lt.isPaid,
                        annualQuota = lt.annualQuotaDays,
                        usedDays = used,
                        remainingDays = (lt.annualQuotaDays - used).coerceAtLeast(0)
                    )
                }
            }
        }
    }

    suspend fun saveLeaveType(
        actor: SessionUser,
        existingId: String? = null,
        code: String,
        name: String,
        annualQuotaDays: Int,
        isPaid: Boolean,
        active: Boolean = true
    ): Result<LeaveTypeEntity> = try {
        if (!actor.has(AppPermission.MANAGE_LEAVES)) {
            throw ErpSecurityException("Leave management permission required")
        }
        require(code.isNotBlank() && name.isNotBlank()) { "Leave type code and name required" }
        require(annualQuotaDays >= 0) { "Quota days cannot be negative" }
        val lt = LeaveTypeEntity(
            id = existingId ?: UUID.randomUUID().toString(),
            code = code.trim().uppercase(),
            name = name.trim(),
            annualQuotaDays = annualQuotaDays,
            isPaid = isPaid,
            active = active
        )
        hrDao.upsertLeaveType(lt)
        Result.success(lt)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun submitLeaveRequest(
        actor: SessionUser,
        employeeId: String,
        leaveTypeId: String,
        startDateKey: String,
        endDateKey: String,
        reason: String
    ): Result<LeaveRequestEntity> = try {
        if (!actor.has(AppPermission.MANAGE_LEAVES)) {
            throw ErpSecurityException("Leave request submission permission required")
        }
        val emp = hrDao.getEmployeeById(employeeId) ?: throw IllegalArgumentException("Employee not found")
        val leaveType = hrDao.getLeaveTypeById(leaveTypeId) ?: throw IllegalArgumentException("Leave type not found")
        val dates = DhakaTime.datesBetweenKeys(startDateKey, endDateKey)
        require(dates.isNotEmpty() && reason.isNotBlank()) { "Valid leave dates and reason are required" }

        val yearPrefix = "${startDateKey.take(4)}-"
        val alreadyUsed = hrDao.getApprovedLeavesForEmployee(employeeId)
            .filter { it.leaveTypeId == leaveTypeId && it.startDateKey.startsWith(yearPrefix) }
            .sumOf { it.totalDays }
        if (leaveType.isPaid && (alreadyUsed + dates.size) > leaveType.annualQuotaDays) {
            throw IllegalStateException("Insufficient ${leaveType.name} balance: remaining ${(leaveType.annualQuotaDays - alreadyUsed).coerceAtLeast(0)} days, requested ${dates.size} days")
        }

        val req = LeaveRequestEntity(
            employeeId = employeeId,
            leaveTypeId = leaveType.id,
            leaveTypeName = leaveType.name,
            isPaid = leaveType.isPaid,
            startDateKey = startDateKey.trim(),
            endDateKey = endDateKey.trim(),
            totalDays = dates.size,
            reason = reason.trim(),
            status = "PENDING",
            requestedByUserId = actor.id
        )
        db.withTransaction {
            hrDao.upsertLeaveRequest(req)
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = "SUBMIT_LEAVE_REQUEST",
                    module = "HR_LEAVES",
                    recordId = req.id,
                    newValue = "${emp.employeeCode} ${leaveType.name} ($startDateKey to $endDateKey, ${dates.size}d)"
                )
            )
        }
        Result.success(req)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun reviewLeaveRequest(
        actor: SessionUser,
        requestId: String,
        approve: Boolean,
        reviewNote: String = ""
    ): Result<LeaveRequestEntity> = try {
        if (!actor.has(AppPermission.MANAGE_LEAVES)) {
            throw ErpSecurityException("Leave approval permission required")
        }
        val req = hrDao.getLeaveRequestById(requestId) ?: throw IllegalArgumentException("Leave request not found")
        if (req.status != "PENDING") {
            throw IllegalStateException("Leave request has already been ${req.status}")
        }
        val emp = hrDao.getEmployeeById(req.employeeId) ?: throw IllegalArgumentException("Employee not found")
        val now = System.currentTimeMillis()
        val updated = db.withTransaction {
            val finalReq = req.copy(
                status = if (approve) "APPROVED" else "REJECTED",
                reviewedByUserId = actor.id,
                reviewNote = reviewNote.trim(),
                syncedToAttendance = approve,
                reviewedAt = now
            )
            hrDao.upsertLeaveRequest(finalReq)

            if (approve) {
                val leaveDates = DhakaTime.datesBetweenKeys(req.startDateKey, req.endDateKey)
                leaveDates.forEach { dKey ->
                    // Reuse existing record ID if one exists on that (employeeId, dateKey) so no duplicate rows are ever created!
                    val existingAtt = hrDao.getAttendanceByEmployeeAndDate(req.employeeId, dKey)
                    hrDao.upsertAttendance(
                        AttendanceRecordEntity(
                            id = existingAtt?.id ?: UUID.randomUUID().toString(),
                            employeeId = req.employeeId,
                            dateKey = dKey,
                            status = "LEAVE",
                            checkInTime = "",
                            checkOutTime = "",
                            overtimeHours = 0.0,
                            leaveRequestId = req.id,
                            notes = "${req.leaveTypeName} (${if (req.isPaid) "Paid" else "Unpaid"}): ${req.reason}",
                            markedByUserId = actor.id,
                            markedByName = actor.fullName,
                            updatedAt = now
                        )
                    )
                }
            }

            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = if (approve) "APPROVE_LEAVE" else "REJECT_LEAVE",
                    module = "HR_LEAVES",
                    recordId = req.id,
                    previousValue = "PENDING",
                    newValue = "${emp.employeeCode} ${finalReq.status} (${req.startDateKey}..${req.endDateKey}, synced=$approve)"
                )
            )
            finalReq
        }
        Result.success(updated)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- Operational HR Reports (Attendance & Leave) ---
    suspend fun generateHrOperationalCsv(
        actor: SessionUser,
        type: ReportType,
        monthKey: String
    ): Result<Pair<String, String>> = try {
        val sb = StringBuilder()
        val filename = when (type) {
            ReportType.HR_ATTENDANCE_MONTHLY -> {
                if (!actor.has(AppPermission.VIEW_ATTENDANCE_REPORTS) && !actor.has(AppPermission.VIEW_HR_REPORTS)) {
                    throw ErpSecurityException("Attendance report permission denied")
                }
                val rows = observeMonthlyAttendanceSummary(actor, monthKey).first()
                sb.appendLine("EmployeeCode,Name,Department,Month,Present,Late,HalfDay,Absent,PaidLeave,UnpaidLeave,Holiday,OvertimeHours")
                rows.forEach { r ->
                    sb.appendLine("${r.employeeCode},${r.employeeName},${r.department},${r.monthKey},${r.presentDays},${r.lateDays},${r.halfDays},${r.absentDays},${r.paidLeaveDays},${r.unpaidLeaveDays},${r.holidayDays},${r.overtimeHours}")
                }
                "attendance_report_$monthKey.csv"
            }
            ReportType.HR_LEAVE_SUMMARY -> {
                if (!actor.has(AppPermission.VIEW_LEAVES) && !actor.has(AppPermission.VIEW_HR_REPORTS)) {
                    throw ErpSecurityException("Leave report permission denied")
                }
                val employees = hrDao.getAllEmployeesOnce().associateBy { it.id }
                val requests = hrDao.observeLeaveRequests().first()
                sb.appendLine("EmployeeCode,EmployeeName,LeaveType,Paid,StartDate,EndDate,Days,Status,SyncedToAttendance,Reason")
                requests.forEach { lr ->
                    val emp = employees[lr.employeeId]
                    sb.appendLine("${emp?.employeeCode ?: ""},${emp?.fullName ?: ""},${lr.leaveTypeName},${lr.isPaid},${lr.startDateKey},${lr.endDateKey},${lr.totalDays},${lr.status},${lr.syncedToAttendance},\"${lr.reason.replace("\"", "'")}\"")
                }
                "leave_summary_report.csv"
            }
            else -> throw IllegalArgumentException("Unsupported operational HR report type: $type")
        }
        dao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                userRole = actor.role.name,
                action = "EXPORT_HR_REPORT",
                module = "HR_REPORTS",
                recordId = type.name,
                newValue = filename
            )
        )
        Result.success(filename to sb.toString())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun EmployeeEntity.toSanitized(includeSalary: Boolean): EmployeeSanitizedView =
        EmployeeSanitizedView(
            id = id,
            employeeCode = employeeCode,
            fullName = fullName,
            photoUri = photoUri,
            phone = phone,
            department = department,
            designation = designation,
            joiningDate = joiningDate,
            employmentStatus = employmentStatus,
            active = active,
            linkedUserId = linkedUserId,
            basicSalary = if (includeSalary) basicSalary else null,
            houseAllowance = if (includeSalary) houseAllowance else null,
            medicalAllowance = if (includeSalary) medicalAllowance else null,
            transportAllowance = if (includeSalary) transportAllowance else null,
            otherAllowance = if (includeSalary) otherAllowance else null,
            overtimeHourlyRate = if (includeSalary) overtimeHourlyRate else null
        )
}
