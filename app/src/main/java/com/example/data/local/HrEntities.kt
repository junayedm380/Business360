package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "employees",
    indices = [Index(value = ["employeeCode"], unique = true)]
)
data class EmployeeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val employeeCode: String,
    val fullName: String,
    val photoUri: String = "",
    val phone: String,
    val department: String,
    val designation: String,
    val joiningDate: Long = System.currentTimeMillis(),
    val employmentStatus: String = "FULL_TIME", // FULL_TIME, PART_TIME, CONTRACT, PROBATION
    val active: Boolean = true,
    val linkedUserId: String = "",
    val basicSalary: Double = 0.0,
    val houseAllowance: Double = 0.0,
    val medicalAllowance: Double = 0.0,
    val transportAllowance: Double = 0.0,
    val otherAllowance: Double = 0.0,
    val overtimeHourlyRate: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attendance_records",
    indices = [
        Index(value = ["employeeId", "dateKey"], unique = true),
        Index(value = ["employeeId"]),
        Index(value = ["dateKey"])
    ]
)
data class AttendanceRecordEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val employeeId: String,
    val dateKey: String, // yyyy-MM-dd in Asia/Dhaka
    val status: String, // PRESENT, ABSENT, LATE, HALF_DAY, LEAVE, HOLIDAY
    val checkInTime: String = "",
    val checkOutTime: String = "",
    val overtimeHours: Double = 0.0,
    val leaveRequestId: String = "",
    val notes: String = "",
    val markedByUserId: String,
    val markedByName: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attendance_daily_closings",
    indices = [Index(value = ["dateKey"], unique = true)]
)
data class AttendanceDailyClosingEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val dateKey: String, // yyyy-MM-dd in Asia/Dhaka
    val totalEmployees: Int,
    val presentCount: Int,
    val absentCount: Int,
    val lateCount: Int,
    val halfDayCount: Int,
    val leaveCount: Int,
    val holidayCount: Int,
    val locked: Boolean = true,
    val closedByUserId: String,
    val closedByName: String,
    val notes: String = "",
    val closedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "leave_types",
    indices = [Index(value = ["code"], unique = true)]
)
data class LeaveTypeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val code: String,
    val name: String,
    val annualQuotaDays: Int,
    val isPaid: Boolean,
    val active: Boolean = true
)

@Entity(
    tableName = "leave_requests",
    indices = [Index(value = ["employeeId"]), Index(value = ["status"])]
)
data class LeaveRequestEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val employeeId: String,
    val leaveTypeId: String,
    val leaveTypeName: String,
    val isPaid: Boolean,
    val startDateKey: String, // yyyy-MM-dd Asia/Dhaka
    val endDateKey: String,   // yyyy-MM-dd Asia/Dhaka
    val totalDays: Int,
    val reason: String,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val requestedByUserId: String,
    val reviewedByUserId: String = "",
    val reviewNote: String = "",
    val syncedToAttendance: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long = 0L
)

@Entity(tableName = "payroll_rule_configs")
data class PayrollRuleConfigEntity(
    @PrimaryKey val id: String = "default",
    val workingDaysPerMonth: Int = 26,
    val absentDeductionRate: Double = 1.0,
    val halfDayDeductionRate: Double = 0.5,
    val lateDaysPerHalfDayDeduction: Int = 0,
    val overtimeHourlyMultiplier: Double = 1.5,
    val deductUnpaidLeave: Boolean = true,
    val updatedByUserId: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "employee_advances",
    indices = [Index(value = ["employeeId"])]
)
data class EmployeeAdvanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val employeeId: String,
    val amount: Double,
    val remainingBalance: Double,
    val cashAccountId: String = "acct_cash",
    val reason: String,
    val issueDateKey: String, // yyyy-MM-dd Asia/Dhaka
    val status: String = "ACTIVE", // ACTIVE, RECOVERED
    val issuedByUserId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payroll_runs",
    indices = [
        Index(value = ["employeeId", "monthKey"], unique = true),
        Index(value = ["employeeId"]),
        Index(value = ["monthKey"])
    ]
)
data class PayrollRunEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val slipNumber: String,
    val employeeId: String,
    val employeeCodeSnapshot: String,
    val employeeNameSnapshot: String,
    val departmentSnapshot: String,
    val designationSnapshot: String,
    val monthKey: String, // yyyy-MM in Asia/Dhaka
    val workingDays: Int,
    val presentDays: Int,
    val lateDays: Int,
    val halfDays: Int,
    val absentDays: Int,
    val paidLeaveDays: Int,
    val unpaidLeaveDays: Int,
    val holidayDays: Int,
    val overtimeHours: Double,
    val basicSalary: Double,
    val totalAllowances: Double,
    val bonus: Double,
    val overtimePay: Double,
    val grossSalary: Double,
    val absenceDeduction: Double,
    val halfDayDeduction: Double,
    val unpaidLeaveDeduction: Double,
    val advanceDeduction: Double,
    val otherDeduction: Double,
    val totalDeductions: Double,
    val netSalary: Double,
    val status: String = "DRAFT", // DRAFT, APPROVED, PAID
    val cashAccountId: String = "",
    val paidAt: Long = 0L,
    val generatedByUserId: String,
    val approvedByUserId: String = "",
    val paidByUserId: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
