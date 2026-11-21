package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AccountTransactionEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.EmployeeAdvanceEntity
import com.example.data.local.ErpDatabase
import com.example.data.local.JournalEntryEntity
import com.example.data.local.JournalEntryLineEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PayrollRuleConfigEntity
import com.example.data.local.PayrollRunEntity
import com.example.domain.AppPermission
import com.example.domain.Formatters
import com.example.domain.SessionUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class HrPayrollRepository(private val db: ErpDatabase) {
    private val dao = db.erpDao()
    private val hrDao = db.hrDao()

    private fun requirePayrollView(session: SessionUser) {
        if (!session.canViewSalaryAndPayroll || !session.has(AppPermission.VIEW_PAYROLL)) {
            throw ErpSecurityException("Confidential payroll and salary data is strictly restricted to authorized Admin/Super Admin")
        }
    }

    private fun requirePayrollManage(session: SessionUser) {
        if (!session.canViewSalaryAndPayroll || !session.has(AppPermission.MANAGE_PAYROLL)) {
            throw ErpSecurityException("Payroll management is strictly restricted to authorized Admin/Super Admin")
        }
    }

    fun observePayrollConfig(session: SessionUser): Flow<PayrollRuleConfigEntity?> {
        requirePayrollView(session)
        return hrDao.observePayrollConfig()
    }

    fun observeEmployeeAdvances(session: SessionUser): Flow<List<EmployeeAdvanceEntity>> {
        requirePayrollView(session)
        return hrDao.observeEmployeeAdvances()
    }

    fun observePayrollRuns(session: SessionUser): Flow<List<PayrollRunEntity>> {
        requirePayrollView(session)
        return hrDao.observePayrollRuns()
    }

    suspend fun updatePayrollConfig(
        actor: SessionUser,
        workingDaysPerMonth: Int,
        absentDeductionRate: Double,
        halfDayDeductionRate: Double,
        lateDaysPerHalfDayDeduction: Int,
        overtimeHourlyMultiplier: Double,
        deductUnpaidLeave: Boolean
    ): Result<PayrollRuleConfigEntity> = try {
        requirePayrollManage(actor)
        require(workingDaysPerMonth in 1..31) { "Working days per month must be 1 to 31" }
        require(absentDeductionRate >= 0.0 && halfDayDeductionRate >= 0.0 && overtimeHourlyMultiplier >= 0.0) {
            "Rates cannot be negative"
        }
        require(lateDaysPerHalfDayDeduction >= 0) { "Late deduction threshold cannot be negative" }
        val config = PayrollRuleConfigEntity(
            id = "default",
            workingDaysPerMonth = workingDaysPerMonth,
            absentDeductionRate = absentDeductionRate,
            halfDayDeductionRate = halfDayDeductionRate,
            lateDaysPerHalfDayDeduction = lateDaysPerHalfDayDeduction,
            overtimeHourlyMultiplier = overtimeHourlyMultiplier,
            deductUnpaidLeave = deductUnpaidLeave,
            updatedByUserId = actor.id,
            updatedAt = System.currentTimeMillis()
        )
        db.withTransaction {
            hrDao.upsertPayrollConfig(config)
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = "UPDATE_PAYROLL_RULES",
                    module = "HR_PAYROLL",
                    recordId = "default",
                    newValue = "WorkDays=$workingDaysPerMonth, AbsentRate=$absentDeductionRate, HalfRate=$halfDayDeductionRate, OTMult=$overtimeHourlyMultiplier"
                )
            )
        }
        Result.success(config)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun issueEmployeeAdvance(
        actor: SessionUser,
        employeeId: String,
        amount: Double,
        cashAccountId: String,
        reason: String,
        issueDateKey: String
    ): Result<EmployeeAdvanceEntity> = try {
        requirePayrollManage(actor)
        require(amount > 0.0) { "Advance amount must be greater than zero" }
        require(reason.isNotBlank()) { "Advance reason is required" }
        val emp = hrDao.getEmployeeById(employeeId) ?: throw IllegalArgumentException("Employee not found")
        val acct = dao.getCashAccountById(cashAccountId.ifBlank { "acct_cash" })
            ?: throw IllegalArgumentException("Cash/Bank account not found")
        val rounded = Formatters.roundMoney(amount)
        val advance = EmployeeAdvanceEntity(
            employeeId = employeeId,
            amount = rounded,
            remainingBalance = rounded,
            cashAccountId = acct.id,
            reason = reason.trim(),
            issueDateKey = issueDateKey.trim(),
            status = "ACTIVE",
            issuedByUserId = actor.id
        )
        db.withTransaction {
            val nextBal = Formatters.roundMoney(acct.currentBalance - rounded)
            dao.upsertCashAccount(acct.copy(currentBalance = nextBal))
            val ref = "ADV-${advance.id.take(6).uppercase()}"
            dao.insertAccountTransaction(
                AccountTransactionEntity(
                    cashAccountId = acct.id,
                    txType = "ADVANCE_SALARY",
                    amount = -rounded,
                    balanceAfter = nextBal,
                    referenceDoc = ref,
                    notes = "Advance salary to ${emp.fullName} (${emp.employeeCode}): ${advance.reason}",
                    createdByUserId = actor.id
                )
            )
            hrDao.upsertAdvance(advance)
            postBalancedEntry(
                referenceDoc = ref,
                description = "Employee advance issued to ${emp.fullName} (${emp.employeeCode})",
                lines = listOf(
                    Triple("coa_1150", "Employee Advances & Loans", rounded to 0.0),
                    Triple("coa_1001", "Cash & Bank Accounts", 0.0 to rounded)
                ),
                userId = actor.id
            )
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = "ISSUE_ADVANCE_SALARY",
                    module = "HR_PAYROLL",
                    recordId = advance.id,
                    newValue = "${emp.employeeCode}: ${Formatters.formatTaka(rounded)} from ${acct.name}"
                )
            )
        }
        Result.success(advance)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun generateMonthlyPayroll(
        actor: SessionUser,
        monthKey: String,
        defaultBonus: Double = 0.0,
        otherDeduction: Double = 0.0,
        notes: String = ""
    ): Result<List<PayrollRunEntity>> = try {
        requirePayrollManage(actor)
        require(monthKey.matches(Regex("^\\d{4}-\\d{2}$"))) { "Invalid month format (use yyyy-MM)" }
        require(defaultBonus >= 0.0 && otherDeduction >= 0.0) { "Bonus and deduction cannot be negative" }

        val generated = db.withTransaction {
            val config = hrDao.getPayrollConfig() ?: PayrollRuleConfigEntity().also { hrDao.upsertPayrollConfig(it) }
            val employees = hrDao.getAllEmployeesOnce().filter { it.active }
            val attendance = hrDao.getAttendanceForMonthOnce("$monthKey%").groupBy { it.employeeId }
            val approvedLeaves = hrDao.getAllApprovedLeavesOnce().associateBy { it.id }
            val resultList = mutableListOf<PayrollRunEntity>()

            employees.forEach { emp ->
                val existing = hrDao.getPayrollByEmployeeAndMonth(emp.id, monthKey)
                if (existing != null && existing.status != "DRAFT") {
                    // Preserve already APPROVED or PAID runs immutably
                    resultList.add(existing)
                    return@forEach
                }

                val recs = attendance[emp.id].orEmpty()
                val presentDays = recs.count { it.status == "PRESENT" }
                val lateDays = recs.count { it.status == "LATE" }
                val halfDays = recs.count { it.status == "HALF_DAY" }
                val absentDays = recs.count { it.status == "ABSENT" }
                val holidayDays = recs.count { it.status == "HOLIDAY" }
                var paidLeaveDays = 0
                var unpaidLeaveDays = 0
                recs.filter { it.status == "LEAVE" }.forEach { rec ->
                    val linked = approvedLeaves[rec.leaveRequestId]
                    if (linked != null && !linked.isPaid) unpaidLeaveDays++ else paidLeaveDays++
                }
                val otHours = Formatters.roundQty(recs.sumOf { it.overtimeHours })

                val perDayRate = emp.basicSalary / config.workingDaysPerMonth.coerceAtLeast(1)
                val effectiveOtRate = if (emp.overtimeHourlyRate > 0.0) {
                    emp.overtimeHourlyRate
                } else {
                    (perDayRate / 8.0) * config.overtimeHourlyMultiplier
                }
                val otPay = Formatters.roundMoney(otHours * effectiveOtRate)
                val totalAllowances = Formatters.roundMoney(
                    emp.houseAllowance + emp.medicalAllowance + emp.transportAllowance + emp.otherAllowance
                )
                val bonusVal = Formatters.roundMoney(existing?.bonus ?: defaultBonus)
                val grossSalary = Formatters.roundMoney(emp.basicSalary + totalAllowances + bonusVal + otPay)

                val absenceDed = Formatters.roundMoney(absentDays * perDayRate * config.absentDeductionRate)
                val latePenaltyHalfDays = if (config.lateDaysPerHalfDayDeduction > 0) {
                    lateDays / config.lateDaysPerHalfDayDeduction
                } else 0
                val halfDayDed = Formatters.roundMoney((halfDays + latePenaltyHalfDays) * perDayRate * config.halfDayDeductionRate)
                val unpaidLeaveDed = if (config.deductUnpaidLeave) {
                    Formatters.roundMoney(unpaidLeaveDays * perDayRate)
                } else 0.0

                val activeAdvances = hrDao.getActiveAdvancesForEmployee(emp.id)
                val activeAdvanceTotal = Formatters.roundMoney(activeAdvances.sumOf { it.remainingBalance })
                val otherDed = Formatters.roundMoney(existing?.otherDeduction ?: otherDeduction)
                val preAdvNet = (grossSalary - absenceDed - halfDayDed - unpaidLeaveDed - otherDed).coerceAtLeast(0.0)
                val advDed = Formatters.roundMoney((existing?.advanceDeduction ?: activeAdvanceTotal).coerceIn(0.0, minOf(activeAdvanceTotal, preAdvNet)))

                val totalDeductions = Formatters.roundMoney(absenceDed + halfDayDed + unpaidLeaveDed + advDed + otherDed)
                val netSalary = Formatters.roundMoney((grossSalary - totalDeductions).coerceAtLeast(0.0))

                val now = System.currentTimeMillis()
                val run = PayrollRunEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    slipNumber = existing?.slipNumber ?: "PAY-${monthKey.replace("-", "")}-${emp.employeeCode}",
                    employeeId = emp.id,
                    employeeCodeSnapshot = emp.employeeCode,
                    employeeNameSnapshot = emp.fullName,
                    departmentSnapshot = emp.department,
                    designationSnapshot = emp.designation,
                    monthKey = monthKey,
                    workingDays = config.workingDaysPerMonth,
                    presentDays = presentDays,
                    lateDays = lateDays,
                    halfDays = halfDays,
                    absentDays = absentDays,
                    paidLeaveDays = paidLeaveDays,
                    unpaidLeaveDays = unpaidLeaveDays,
                    holidayDays = holidayDays,
                    overtimeHours = otHours,
                    basicSalary = Formatters.roundMoney(emp.basicSalary),
                    totalAllowances = totalAllowances,
                    bonus = bonusVal,
                    overtimePay = otPay,
                    grossSalary = grossSalary,
                    absenceDeduction = absenceDed,
                    halfDayDeduction = halfDayDed,
                    unpaidLeaveDeduction = unpaidLeaveDed,
                    advanceDeduction = advDed,
                    otherDeduction = otherDed,
                    totalDeductions = totalDeductions,
                    netSalary = netSalary,
                    status = "DRAFT",
                    generatedByUserId = actor.id,
                    notes = notes.trim(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
                hrDao.upsertPayrollRun(run)
                resultList.add(run)
            }

            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = "GENERATE_PAYROLL",
                    module = "HR_PAYROLL",
                    recordId = monthKey,
                    newValue = "Generated/updated ${resultList.size} payroll slips for $monthKey"
                )
            )
            resultList
        }
        Result.success(generated)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateDraftPayrollAdjustments(
        actor: SessionUser,
        payrollId: String,
        bonus: Double,
        advanceDeduction: Double,
        otherDeduction: Double,
        notes: String
    ): Result<PayrollRunEntity> = try {
        requirePayrollManage(actor)
        require(bonus >= 0 && advanceDeduction >= 0 && otherDeduction >= 0) { "Amounts cannot be negative" }
        val run = hrDao.getPayrollById(payrollId) ?: throw IllegalArgumentException("Payroll run not found")
        if (run.status != "DRAFT") throw IllegalStateException("Only DRAFT payroll slips can be edited")

        val activeAdv = hrDao.getActiveAdvancesForEmployee(run.employeeId).sumOf { it.remainingBalance }
        val advCap = minOf(activeAdv, advanceDeduction)
        val newGross = Formatters.roundMoney(run.basicSalary + run.totalAllowances + bonus + run.overtimePay)
        val newTotalDed = Formatters.roundMoney(
            run.absenceDeduction + run.halfDayDeduction + run.unpaidLeaveDeduction + advCap + otherDeduction
        )
        val newNet = Formatters.roundMoney((newGross - newTotalDed).coerceAtLeast(0.0))
        val updated = run.copy(
            bonus = Formatters.roundMoney(bonus),
            advanceDeduction = Formatters.roundMoney(advCap),
            otherDeduction = Formatters.roundMoney(otherDeduction),
            grossSalary = newGross,
            totalDeductions = newTotalDed,
            netSalary = newNet,
            notes = notes.trim(),
            updatedAt = System.currentTimeMillis()
        )
        hrDao.upsertPayrollRun(updated)
        Result.success(updated)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun approvePayrollRun(actor: SessionUser, payrollId: String): Result<PayrollRunEntity> = try {
        requirePayrollManage(actor)
        val run = hrDao.getPayrollById(payrollId) ?: throw IllegalArgumentException("Payroll slip not found")
        if (run.status != "DRAFT") throw IllegalStateException("Payroll is already ${run.status}")
        val updated = run.copy(
            status = "APPROVED",
            approvedByUserId = actor.id,
            updatedAt = System.currentTimeMillis()
        )
        db.withTransaction {
            hrDao.upsertPayrollRun(updated)
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = "APPROVE_PAYROLL",
                    module = "HR_PAYROLL",
                    recordId = run.id,
                    previousValue = "DRAFT",
                    newValue = "${run.slipNumber} APPROVED (Net ${Formatters.formatTaka(run.netSalary)})"
                )
            )
        }
        Result.success(updated)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun payPayrollRun(
        actor: SessionUser,
        payrollId: String,
        cashAccountId: String
    ): Result<PayrollRunEntity> = try {
        requirePayrollManage(actor)
        val run = hrDao.getPayrollById(payrollId) ?: throw IllegalArgumentException("Payroll slip not found")
        if (run.status == "PAID") throw IllegalStateException("Salary has already been paid")
        if (run.status != "APPROVED") throw IllegalStateException("Payroll must be APPROVED before disbursement")

        val acct = dao.getCashAccountById(cashAccountId.ifBlank { "acct_cash" })
            ?: throw IllegalArgumentException("Cash/Bank account not found")
        val now = System.currentTimeMillis()

        val updated = db.withTransaction {
            // 1. Deduct Cash/Bank balance if netSalary > 0
            if (run.netSalary > 0.0) {
                val nextBal = Formatters.roundMoney(acct.currentBalance - run.netSalary)
                dao.upsertCashAccount(acct.copy(currentBalance = nextBal))
                dao.insertAccountTransaction(
                    AccountTransactionEntity(
                        cashAccountId = acct.id,
                        txType = "SALARY_PAYMENT",
                        amount = -run.netSalary,
                        balanceAfter = nextBal,
                        referenceDoc = run.slipNumber,
                        notes = "Salary paid to ${run.employeeNameSnapshot} (${run.monthKey})",
                        createdByUserId = actor.id
                    )
                )
            }

            // 2. Recover advance salary FIFO up to run.advanceDeduction
            var remainingToRecover = run.advanceDeduction
            if (remainingToRecover > 0.0) {
                val activeAdvs = hrDao.getActiveAdvancesForEmployee(run.employeeId)
                for (adv in activeAdvs) {
                    if (remainingToRecover <= 0.0) break
                    val apply = minOf(remainingToRecover, adv.remainingBalance)
                    val newRem = Formatters.roundMoney(adv.remainingBalance - apply)
                    hrDao.upsertAdvance(
                        adv.copy(
                            remainingBalance = newRem,
                            status = if (newRem <= 0.0) "RECOVERED" else "ACTIVE"
                        )
                    )
                    remainingToRecover = Formatters.roundMoney(remainingToRecover - apply)
                }
            }

            // 3. Post balanced Double-Entry Journal Entry
            val expenseTotal = Formatters.roundMoney(run.netSalary + run.advanceDeduction)
            if (expenseTotal > 0.0) {
                val lines = mutableListOf(
                    Triple("coa_6100", "Payroll & Salary Expenses", expenseTotal to 0.0)
                )
                if (run.netSalary > 0.0) {
                    lines.add(Triple("coa_1001", "Cash & Bank Accounts", 0.0 to run.netSalary))
                }
                if (run.advanceDeduction > 0.0) {
                    lines.add(Triple("coa_1150", "Employee Advances & Loans", 0.0 to run.advanceDeduction))
                }
                postBalancedEntry(
                    referenceDoc = run.slipNumber,
                    description = "Salary payment ${run.slipNumber} (${run.employeeNameSnapshot}, ${run.monthKey})",
                    lines = lines,
                    userId = actor.id
                )
            }

            val paidRun = run.copy(
                status = "PAID",
                cashAccountId = acct.id,
                paidAt = now,
                paidByUserId = actor.id,
                updatedAt = now
            )
            hrDao.upsertPayrollRun(paidRun)

            dao.insertNotification(
                NotificationEntity(
                    targetAudience = "ADMIN_ONLY",
                    title = "Salary Paid (${run.slipNumber})",
                    message = "${Formatters.formatTaka(run.netSalary)} paid to ${run.employeeNameSnapshot} via ${acct.name}.",
                    category = "PAYROLL",
                    referenceId = run.id
                )
            )
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = "PAY_SALARY",
                    module = "HR_PAYROLL",
                    recordId = run.id,
                    previousValue = "APPROVED",
                    newValue = "${run.slipNumber} PAID ${Formatters.formatTaka(run.netSalary)} via ${acct.name}"
                )
            )
            paidRun
        }
        Result.success(updated)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun generatePrintableSalarySlipHtml(
        actor: SessionUser,
        payrollId: String,
        businessName: String = "Business360",
        currencySymbol: String = "৳"
    ): Result<String> = try {
        requirePayrollView(actor)
        val r = hrDao.getPayrollById(payrollId) ?: throw IllegalArgumentException("Salary slip not found")
        fun String.escapeHtml(): String = buildString(length) {
            for (ch in this@escapeHtml) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(ch)
                }
            }
        }
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8" />
              <style>
                body { font-family: sans-serif; color: #0f172a; padding: 24px; }
                .header { border-bottom: 2px solid #0f2942; padding-bottom: 12px; margin-bottom: 16px; }
                .brand { font-size: 20px; font-weight: bold; color: #0f2942; }
                .sub { font-size: 12px; color: #475569; }
                table { width: 100%; border-collapse: collapse; margin-top: 12px; }
                th, td { border: 1px solid #cbd5e1; padding: 8px; text-align: left; font-size: 13px; }
                th { background-color: #f1f5f9; }
                .net { font-size: 16px; font-weight: bold; color: #0d9488; background: #ecfdf5; }
              </style>
            </head>
            <body>
              <div class="header">
                <div class="brand">${businessName.escapeHtml()} — Salary Slip</div>
                <div class="sub">Slip #: ${r.slipNumber.escapeHtml()} • Month: ${r.monthKey.escapeHtml()} • Status: ${r.status.escapeHtml()} • Timezone: Asia/Dhaka</div>
              </div>
              <table>
                <tr><th>Employee ID</th><td>${r.employeeCodeSnapshot.escapeHtml()}</td><th>Name</th><td>${r.employeeNameSnapshot.escapeHtml()}</td></tr>
                <tr><th>Department</th><td>${r.departmentSnapshot.escapeHtml()}</td><th>Designation</th><td>${r.designationSnapshot.escapeHtml()}</td></tr>
                <tr><th>Attendance</th><td colspan="3">Present: ${r.presentDays}, Late: ${r.lateDays}, Half-Day: ${r.halfDays}, Absent: ${r.absentDays}, Paid Leave: ${r.paidLeaveDays}, Unpaid Leave: ${r.unpaidLeaveDays}, Holiday: ${r.holidayDays}, OT: ${r.overtimeHours} hrs</td></tr>
              </table>
              <table>
                <tr><th>Earnings</th><th>Amount</th><th>Deductions</th><th>Amount</th></tr>
                <tr><td>Basic Salary</td><td>${Formatters.formatTaka(r.basicSalary, currencySymbol).escapeHtml()}</td><td>Absence Deduction</td><td>${Formatters.formatTaka(r.absenceDeduction, currencySymbol).escapeHtml()}</td></tr>
                <tr><td>Allowances</td><td>${Formatters.formatTaka(r.totalAllowances, currencySymbol).escapeHtml()}</td><td>Half-Day / Late Deduction</td><td>${Formatters.formatTaka(r.halfDayDeduction, currencySymbol).escapeHtml()}</td></tr>
                <tr><td>Overtime Pay</td><td>${Formatters.formatTaka(r.overtimePay, currencySymbol).escapeHtml()}</td><td>Unpaid Leave Deduction</td><td>${Formatters.formatTaka(r.unpaidLeaveDeduction, currencySymbol).escapeHtml()}</td></tr>
                <tr><td>Bonus</td><td>${Formatters.formatTaka(r.bonus, currencySymbol).escapeHtml()}</td><td>Advance Recovery & Other</td><td>${Formatters.formatTaka(r.advanceDeduction + r.otherDeduction, currencySymbol).escapeHtml()}</td></tr>
                <tr><th>Gross Salary</th><th>${Formatters.formatTaka(r.grossSalary, currencySymbol).escapeHtml()}</th><th>Total Deductions</th><th>${Formatters.formatTaka(r.totalDeductions, currencySymbol).escapeHtml()}</th></tr>
                <tr><td colspan="2" class="net">Net Payable Salary</td><td colspan="2" class="net">${Formatters.formatTaka(r.netSalary, currencySymbol).escapeHtml()}</td></tr>
              </table>
            </body>
            </html>
        """.trimIndent()
        Result.success(html)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun generatePayrollCsv(actor: SessionUser, monthKey: String): Result<Pair<String, String>> = try {
        requirePayrollView(actor)
        val runs = hrDao.observePayrollRuns().first().filter { monthKey.isBlank() || it.monthKey == monthKey }
        val sb = StringBuilder()
        sb.appendLine("SlipNumber,Month,EmployeeCode,Name,Department,Basic,Allowances,Bonus,OvertimePay,Gross,AbsenceDed,HalfDayDed,UnpaidLeaveDed,AdvanceDed,OtherDed,TotalDeductions,NetSalary,Status")
        runs.forEach { r ->
            sb.appendLine("${r.slipNumber},${r.monthKey},${r.employeeCodeSnapshot},${r.employeeNameSnapshot},${r.departmentSnapshot},${r.basicSalary},${r.totalAllowances},${r.bonus},${r.overtimePay},${r.grossSalary},${r.absenceDeduction},${r.halfDayDeduction},${r.unpaidLeaveDeduction},${r.advanceDeduction},${r.otherDeduction},${r.totalDeductions},${r.netSalary},${r.status}")
        }
        dao.insertAuditLog(
            AuditLogEntity(
                userId = actor.id,
                userName = actor.fullName,
                userRole = actor.role.name,
                action = "EXPORT_PAYROLL_REPORT",
                module = "HR_PAYROLL",
                recordId = monthKey.ifBlank { "ALL" },
                newValue = "payroll_report_${monthKey.ifBlank { "all" }}.csv"
            )
        )
        Result.success("payroll_report_${monthKey.ifBlank { "all" }}.csv" to sb.toString())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun postBalancedEntry(
        referenceDoc: String,
        description: String,
        lines: List<Triple<String, String, Pair<Double, Double>>>,
        userId: String
    ) {
        val totalDr = Formatters.roundMoney(lines.sumOf { it.third.first })
        val totalCr = Formatters.roundMoney(lines.sumOf { it.third.second })
        require(kotlin.math.abs(totalDr - totalCr) < 0.01) {
            "Double-entry imbalance: Debit ($totalDr) != Credit ($totalCr)"
        }
        val jeId = UUID.randomUUID().toString()
        dao.insertJournalEntry(
            JournalEntryEntity(
                id = jeId,
                entryNumber = "JE-${System.currentTimeMillis().toString().takeLast(6)}",
                referenceDoc = referenceDoc,
                description = description,
                totalDebit = totalDr,
                totalCredit = totalCr,
                createdByUserId = userId
            )
        )
        dao.insertJournalLines(
            lines.map { (acctId, acctName, amounts) ->
                JournalEntryLineEntity(
                    journalEntryId = jeId,
                    accountId = acctId,
                    accountName = acctName,
                    debit = Formatters.roundMoney(amounts.first),
                    credit = Formatters.roundMoney(amounts.second)
                )
            }
        )
    }
}
