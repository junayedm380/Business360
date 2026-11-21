package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HrDao {
    // --- Employees ---
    @Query("SELECT * FROM employees ORDER BY active DESC, fullName ASC")
    fun observeEmployees(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees ORDER BY active DESC, fullName ASC")
    suspend fun getAllEmployeesOnce(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun getEmployeeById(id: String): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE LOWER(employeeCode) = LOWER(:code) LIMIT 1")
    suspend fun getEmployeeByCode(code: String): EmployeeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmployee(employee: EmployeeEntity)

    // --- Attendance & Daily Closing ---
    @Query("SELECT * FROM attendance_records ORDER BY dateKey DESC, updatedAt DESC LIMIT 500")
    fun observeAllAttendance(): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE dateKey = :dateKey ORDER BY updatedAt DESC")
    fun observeAttendanceForDate(dateKey: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE dateKey LIKE :monthPrefix ORDER BY dateKey ASC")
    fun observeAttendanceForMonth(monthPrefix: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE dateKey LIKE :monthPrefix ORDER BY dateKey ASC")
    suspend fun getAttendanceForMonthOnce(monthPrefix: String): List<AttendanceRecordEntity>

    @Query("SELECT * FROM attendance_records WHERE employeeId = :employeeId AND dateKey = :dateKey LIMIT 1")
    suspend fun getAttendanceByEmployeeAndDate(employeeId: String, dateKey: String): AttendanceRecordEntity?

    @Query("SELECT * FROM attendance_records WHERE dateKey = :dateKey")
    suspend fun getAttendanceForDateOnce(dateKey: String): List<AttendanceRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendance(record: AttendanceRecordEntity)

    @Query("SELECT * FROM attendance_daily_closings ORDER BY dateKey DESC")
    fun observeAttendanceClosings(): Flow<List<AttendanceDailyClosingEntity>>

    @Query("SELECT * FROM attendance_daily_closings WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getAttendanceClosingByDate(dateKey: String): AttendanceDailyClosingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendanceClosing(closing: AttendanceDailyClosingEntity)

    // --- Leave Management ---
    @Query("SELECT * FROM leave_types ORDER BY isPaid DESC, name ASC")
    fun observeLeaveTypes(): Flow<List<LeaveTypeEntity>>

    @Query("SELECT COUNT(*) FROM leave_types")
    suspend fun getLeaveTypeCount(): Int

    @Query("SELECT * FROM leave_types WHERE id = :id LIMIT 1")
    suspend fun getLeaveTypeById(id: String): LeaveTypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLeaveType(leaveType: LeaveTypeEntity)

    @Query("SELECT * FROM leave_requests ORDER BY createdAt DESC")
    fun observeLeaveRequests(): Flow<List<LeaveRequestEntity>>

    @Query("SELECT * FROM leave_requests WHERE id = :id LIMIT 1")
    suspend fun getLeaveRequestById(id: String): LeaveRequestEntity?

    @Query("SELECT * FROM leave_requests WHERE employeeId = :employeeId AND status = 'APPROVED'")
    suspend fun getApprovedLeavesForEmployee(employeeId: String): List<LeaveRequestEntity>

    @Query("SELECT * FROM leave_requests WHERE status = 'APPROVED'")
    suspend fun getAllApprovedLeavesOnce(): List<LeaveRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLeaveRequest(request: LeaveRequestEntity)

    // --- Payroll Rules, Advances, and Payroll Runs ---
    @Query("SELECT * FROM payroll_rule_configs WHERE id = 'default' LIMIT 1")
    fun observePayrollConfig(): Flow<PayrollRuleConfigEntity?>

    @Query("SELECT * FROM payroll_rule_configs WHERE id = 'default' LIMIT 1")
    suspend fun getPayrollConfig(): PayrollRuleConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayrollConfig(config: PayrollRuleConfigEntity)

    @Query("SELECT * FROM employee_advances ORDER BY createdAt DESC")
    fun observeEmployeeAdvances(): Flow<List<EmployeeAdvanceEntity>>

    @Query("SELECT * FROM employee_advances WHERE employeeId = :employeeId AND status = 'ACTIVE' ORDER BY createdAt ASC")
    suspend fun getActiveAdvancesForEmployee(employeeId: String): List<EmployeeAdvanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAdvance(advance: EmployeeAdvanceEntity)

    @Query("SELECT * FROM payroll_runs ORDER BY monthKey DESC, employeeCodeSnapshot ASC")
    fun observePayrollRuns(): Flow<List<PayrollRunEntity>>

    @Query("SELECT * FROM payroll_runs WHERE monthKey = :monthKey ORDER BY employeeCodeSnapshot ASC")
    suspend fun getPayrollForMonthOnce(monthKey: String): List<PayrollRunEntity>

    @Query("SELECT * FROM payroll_runs WHERE employeeId = :employeeId AND monthKey = :monthKey LIMIT 1")
    suspend fun getPayrollByEmployeeAndMonth(employeeId: String, monthKey: String): PayrollRunEntity?

    @Query("SELECT * FROM payroll_runs WHERE id = :id LIMIT 1")
    suspend fun getPayrollById(id: String): PayrollRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayrollRun(run: PayrollRunEntity)
}
