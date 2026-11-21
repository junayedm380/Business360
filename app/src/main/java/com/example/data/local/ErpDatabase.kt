package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        AuditLogEntity::class,
        AppSettingEntity::class,
        NotificationEntity::class,
        ProductEntity::class,
        ProductModelEntity::class,
        BomItemEntity::class,
        PriceHistoryEntity::class,
        WarehouseEntity::class,
        RawMaterialEntity::class,
        MaterialLedgerEntity::class,
        ProductionBatchEntity::class,
        InventoryBalanceEntity::class,
        StockMovementEntity::class,
        CustomerEntity::class,
        CustomerLedgerEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        SalesReturnEntity::class,
        PaymentSubmissionEntity::class,
        PaymentEntity::class,
        SupplierEntity::class,
        PurchaseOrderEntity::class,
        SupplierLedgerEntity::class,
        CashAccountEntity::class,
        AccountTransactionEntity::class,
        ExpenseEntity::class,
        ChartOfAccountEntity::class,
        JournalEntryEntity::class,
        JournalEntryLineEntity::class,
        DailyClosingEntity::class,
        EmployeeTaskEntity::class,
        // HR, Attendance, Leave & Payroll Entities
        EmployeeEntity::class,
        AttendanceRecordEntity::class,
        AttendanceDailyClosingEntity::class,
        LeaveTypeEntity::class,
        LeaveRequestEntity::class,
        PayrollRuleConfigEntity::class,
        EmployeeAdvanceEntity::class,
        PayrollRunEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ErpDatabase : RoomDatabase() {
    abstract fun erpDao(): ErpDao
    abstract fun hrDao(): HrDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `employees` (
                        `id` TEXT NOT NULL,
                        `employeeCode` TEXT NOT NULL,
                        `fullName` TEXT NOT NULL,
                        `photoUri` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        `department` TEXT NOT NULL,
                        `designation` TEXT NOT NULL,
                        `joiningDate` INTEGER NOT NULL,
                        `employmentStatus` TEXT NOT NULL,
                        `active` INTEGER NOT NULL,
                        `linkedUserId` TEXT NOT NULL,
                        `basicSalary` REAL NOT NULL,
                        `houseAllowance` REAL NOT NULL,
                        `medicalAllowance` REAL NOT NULL,
                        `transportAllowance` REAL NOT NULL,
                        `otherAllowance` REAL NOT NULL,
                        `overtimeHourlyRate` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_employees_employeeCode` ON `employees` (`employeeCode`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `attendance_records` (
                        `id` TEXT NOT NULL,
                        `employeeId` TEXT NOT NULL,
                        `dateKey` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `checkInTime` TEXT NOT NULL,
                        `checkOutTime` TEXT NOT NULL,
                        `overtimeHours` REAL NOT NULL,
                        `leaveRequestId` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `markedByUserId` TEXT NOT NULL,
                        `markedByName` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_records_employeeId_dateKey` ON `attendance_records` (`employeeId`, `dateKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_employeeId` ON `attendance_records` (`employeeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_dateKey` ON `attendance_records` (`dateKey`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `attendance_daily_closings` (
                        `id` TEXT NOT NULL,
                        `dateKey` TEXT NOT NULL,
                        `totalEmployees` INTEGER NOT NULL,
                        `presentCount` INTEGER NOT NULL,
                        `absentCount` INTEGER NOT NULL,
                        `lateCount` INTEGER NOT NULL,
                        `halfDayCount` INTEGER NOT NULL,
                        `leaveCount` INTEGER NOT NULL,
                        `holidayCount` INTEGER NOT NULL,
                        `locked` INTEGER NOT NULL,
                        `closedByUserId` TEXT NOT NULL,
                        `closedByName` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `closedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_daily_closings_dateKey` ON `attendance_daily_closings` (`dateKey`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `leave_types` (
                        `id` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `annualQuotaDays` INTEGER NOT NULL,
                        `isPaid` INTEGER NOT NULL,
                        `active` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_leave_types_code` ON `leave_types` (`code`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `leave_requests` (
                        `id` TEXT NOT NULL,
                        `employeeId` TEXT NOT NULL,
                        `leaveTypeId` TEXT NOT NULL,
                        `leaveTypeName` TEXT NOT NULL,
                        `isPaid` INTEGER NOT NULL,
                        `startDateKey` TEXT NOT NULL,
                        `endDateKey` TEXT NOT NULL,
                        `totalDays` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `requestedByUserId` TEXT NOT NULL,
                        `reviewedByUserId` TEXT NOT NULL,
                        `reviewNote` TEXT NOT NULL,
                        `syncedToAttendance` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `reviewedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_leave_requests_employeeId` ON `leave_requests` (`employeeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_leave_requests_status` ON `leave_requests` (`status`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payroll_rule_configs` (
                        `id` TEXT NOT NULL,
                        `workingDaysPerMonth` INTEGER NOT NULL,
                        `absentDeductionRate` REAL NOT NULL,
                        `halfDayDeductionRate` REAL NOT NULL,
                        `lateDaysPerHalfDayDeduction` INTEGER NOT NULL,
                        `overtimeHourlyMultiplier` REAL NOT NULL,
                        `deductUnpaidLeave` INTEGER NOT NULL,
                        `updatedByUserId` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `employee_advances` (
                        `id` TEXT NOT NULL,
                        `employeeId` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `remainingBalance` REAL NOT NULL,
                        `cashAccountId` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `issueDateKey` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `issuedByUserId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_employee_advances_employeeId` ON `employee_advances` (`employeeId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payroll_runs` (
                        `id` TEXT NOT NULL,
                        `slipNumber` TEXT NOT NULL,
                        `employeeId` TEXT NOT NULL,
                        `employeeCodeSnapshot` TEXT NOT NULL,
                        `employeeNameSnapshot` TEXT NOT NULL,
                        `departmentSnapshot` TEXT NOT NULL,
                        `designationSnapshot` TEXT NOT NULL,
                        `monthKey` TEXT NOT NULL,
                        `workingDays` INTEGER NOT NULL,
                        `presentDays` INTEGER NOT NULL,
                        `lateDays` INTEGER NOT NULL,
                        `halfDays` INTEGER NOT NULL,
                        `absentDays` INTEGER NOT NULL,
                        `paidLeaveDays` INTEGER NOT NULL,
                        `unpaidLeaveDays` INTEGER NOT NULL,
                        `holidayDays` INTEGER NOT NULL,
                        `overtimeHours` REAL NOT NULL,
                        `basicSalary` REAL NOT NULL,
                        `totalAllowances` REAL NOT NULL,
                        `bonus` REAL NOT NULL,
                        `overtimePay` REAL NOT NULL,
                        `grossSalary` REAL NOT NULL,
                        `absenceDeduction` REAL NOT NULL,
                        `halfDayDeduction` REAL NOT NULL,
                        `unpaidLeaveDeduction` REAL NOT NULL,
                        `advanceDeduction` REAL NOT NULL,
                        `otherDeduction` REAL NOT NULL,
                        `totalDeductions` REAL NOT NULL,
                        `netSalary` REAL NOT NULL,
                        `status` TEXT NOT NULL,
                        `cashAccountId` TEXT NOT NULL,
                        `paidAt` INTEGER NOT NULL,
                        `generatedByUserId` TEXT NOT NULL,
                        `approvedByUserId` TEXT NOT NULL,
                        `paidByUserId` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payroll_runs_employeeId_monthKey` ON `payroll_runs` (`employeeId`, `monthKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payroll_runs_employeeId` ON `payroll_runs` (`employeeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payroll_runs_monthKey` ON `payroll_runs` (`monthKey`)")

                // Forward-migrate existing v1 Admin & Moderator accounts with their default HR permissions
                db.execSQL(
                    "UPDATE users SET permissionsCsv = permissionsCsv || ',VIEW_EMPLOYEES_BASIC,MANAGE_EMPLOYEES,VIEW_EMPLOYEE_SALARY,MARK_ATTENDANCE,EDIT_ATTENDANCE,MANAGE_ATTENDANCE_CLOSING,VIEW_ATTENDANCE_REPORTS,VIEW_LEAVES,MANAGE_LEAVES,VIEW_PAYROLL,MANAGE_PAYROLL,VIEW_HR_REPORTS' WHERE role = 'ADMIN' AND permissionsCsv NOT LIKE '%VIEW_EMPLOYEES_BASIC%'"
                )
                db.execSQL(
                    "UPDATE users SET permissionsCsv = permissionsCsv || ',VIEW_EMPLOYEES_BASIC,MARK_ATTENDANCE,EDIT_ATTENDANCE,MANAGE_ATTENDANCE_CLOSING,VIEW_ATTENDANCE_REPORTS,VIEW_LEAVES,VIEW_HR_REPORTS' WHERE role = 'MODERATOR_MANAGER' AND permissionsCsv NOT LIKE '%VIEW_EMPLOYEES_BASIC%'"
                )
            }
        }

        @Volatile
        private var INSTANCE: ErpDatabase? = null

        fun getInstance(context: Context): ErpDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ErpDatabase::class.java,
                    "crafterp_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
