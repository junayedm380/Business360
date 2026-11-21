package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppSettingEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.CashAccountEntity
import com.example.data.local.ChartOfAccountEntity
import com.example.data.local.ErpDatabase
import com.example.data.local.NotificationEntity
import com.example.data.local.UserEntity
import com.example.data.local.WarehouseEntity
import com.example.domain.AppPermission
import com.example.domain.PasswordHasher
import com.example.domain.PermissionPolicy
import com.example.domain.SessionUser
import com.example.domain.UserRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class AuthAndGovernanceRepository(private val db: ErpDatabase) {
    private val dao = db.erpDao()

    suspend fun ensureStructuralDefaults() {
        if (dao.getSettings() == null) {
            dao.upsertSettings(AppSettingEntity())
        }
        if (dao.getWarehouseCount() == 0) {
            dao.upsertWarehouse(
                WarehouseEntity(
                    id = "wh_main",
                    name = "Main Factory Warehouse",
                    location = "Dhaka HQ",
                    defaultWarehouse = true,
                    active = true
                )
            )
        }
        if (dao.getCashAccountCount() == 0) {
            dao.upsertCashAccount(
                CashAccountEntity(
                    id = "acct_cash",
                    name = "Cash in Hand",
                    accountType = "CASH",
                    openingBalance = 0.0,
                    currentBalance = 0.0
                )
            )
            dao.upsertCashAccount(
                CashAccountEntity(
                    id = "acct_bank",
                    name = "Primary Business Bank",
                    accountType = "BANK",
                    openingBalance = 0.0,
                    currentBalance = 0.0
                )
            )
            dao.upsertCashAccount(
                CashAccountEntity(
                    id = "acct_bkash",
                    name = "bKash Merchant",
                    accountType = "BKASH",
                    openingBalance = 0.0,
                    currentBalance = 0.0
                )
            )
            dao.upsertCashAccount(
                CashAccountEntity(
                    id = "acct_nagad",
                    name = "Nagad Merchant",
                    accountType = "NAGAD",
                    openingBalance = 0.0,
                    currentBalance = 0.0
                )
            )
        }
        if (dao.getChartOfAccountsCount() == 0) {
            dao.insertChartOfAccounts(
                listOf(
                    ChartOfAccountEntity("coa_1001", "1001", "Cash & Bank Accounts", "ASSET"),
                    ChartOfAccountEntity("coa_1100", "1100", "Accounts Receivable", "ASSET"),
                    ChartOfAccountEntity("coa_1200", "1200", "Raw Material Inventory", "ASSET"),
                    ChartOfAccountEntity("coa_1210", "1210", "Finished Goods Inventory", "ASSET"),
                    ChartOfAccountEntity("coa_2001", "2001", "Accounts Payable", "LIABILITY"),
                    ChartOfAccountEntity("coa_3001", "3001", "Owner Capital & Retained Equity", "EQUITY"),
                    ChartOfAccountEntity("coa_4001", "4001", "Sales Revenue", "REVENUE"),
                    ChartOfAccountEntity("coa_4100", "4100", "Sales Returns & Allowances", "REVENUE"),
                    ChartOfAccountEntity("coa_5001", "5001", "Cost of Goods Sold (COGS)", "COGS"),
                    ChartOfAccountEntity("coa_5100", "5100", "Production Labor & Overhead", "EXPENSE"),
                    ChartOfAccountEntity("coa_6001", "6001", "Operating Expenses", "EXPENSE")
                )
            )
        }
    }

    suspend fun hasAnyUsers(): Boolean = dao.getUserCount() > 0

    suspend fun loadSessionUser(userId: String): SessionUser? {
        val entity = dao.getUserById(userId) ?: return null
        if (!entity.active) return null
        return entity.toSessionUser()
    }

    suspend fun claimInitialSuperAdmin(
        businessName: String,
        fullName: String,
        email: String,
        password: String
    ): Result<SessionUser> = try {
        require(businessName.isNotBlank()) { "Business name is required" }
        require(fullName.isNotBlank()) { "Full name is required" }
        require(email.isNotBlank()) { "Email or username is required" }
        require(password.length >= 6) { "Password must be at least 6 characters" }

        ensureStructuralDefaults()
        val created = db.withTransaction {
            if (dao.getUserCount() > 0) {
                throw ErpSecurityException("Initial Super Admin has already been claimed.")
            }
            val allPerms = PermissionPolicy.defaultPermissionsFor(UserRole.SUPER_ADMIN)
            val user = UserEntity(
                id = UUID.randomUUID().toString(),
                fullName = fullName.trim(),
                email = email.trim(),
                passwordHash = PasswordHasher.hashPassword(password),
                role = UserRole.SUPER_ADMIN.name,
                permissionsCsv = PermissionPolicy.serializePermissions(allPerms, UserRole.SUPER_ADMIN),
                active = true
            )
            dao.insertUser(user)
            val currentSettings = dao.getSettings() ?: AppSettingEntity()
            dao.upsertSettings(
                currentSettings.copy(
                    businessName = businessName.trim(),
                    email = email.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = user.id,
                    userName = user.fullName,
                    userRole = user.role,
                    action = "CLAIM_SUPER_ADMIN",
                    module = "AUTH",
                    recordId = user.id,
                    newValue = "Initial Super Admin created for ${businessName.trim()}"
                )
            )
            user
        }
        Result.success(created.toSessionUser())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun login(identifier: String, password: String): Result<SessionUser> = try {
        require(identifier.isNotBlank() && password.isNotBlank()) { "Credentials are required" }
        ensureStructuralDefaults()
        val user = dao.findUserByEmail(identifier.trim())
            ?: return Result.failure(IllegalArgumentException("Invalid email/username or password"))
        if (!user.active) {
            return Result.failure(ErpSecurityException("Account is deactivated. Contact Super Admin."))
        }
        if (!PasswordHasher.verifyPassword(password, user.passwordHash)) {
            return Result.failure(IllegalArgumentException("Invalid email/username or password"))
        }
        dao.insertAuditLog(
            AuditLogEntity(
                userId = user.id,
                userName = user.fullName,
                userRole = user.role,
                action = "LOGIN",
                module = "AUTH",
                recordId = user.id
            )
        )
        Result.success(user.toSessionUser())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun logout(session: SessionUser) {
        try {
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = session.id,
                    userName = session.fullName,
                    userRole = session.role.name,
                    action = "LOGOUT",
                    module = "AUTH",
                    recordId = session.id
                )
            )
        } catch (_: Exception) {
        }
    }

    fun observeUsers(actor: SessionUser): Flow<List<UserEntity>> {
        if (!actor.has(AppPermission.MANAGE_USERS) && !actor.has(AppPermission.MANAGE_TASKS)) {
            throw ErpSecurityException("Access denied to user directory")
        }
        return dao.observeUsers().map { list ->
            // Sanitize password hashes before exposing to UI, and hide Super Admin editing for non-SuperAdmins
            list.map { it.copy(passwordHash = "") }
        }
    }

    suspend fun createUser(
        actor: SessionUser,
        fullName: String,
        email: String,
        password: String,
        targetRole: UserRole,
        customPermissions: Set<AppPermission>? = null
    ): Result<UserEntity> = try {
        if (!actor.has(AppPermission.MANAGE_USERS)) {
            throw ErpSecurityException("Only authorized Super Admin/Admin can create users")
        }
        if (!actor.isFullAdmin && targetRole.rank >= actor.role.rank) {
            throw ErpSecurityException("Cannot create an account with equal or higher authority")
        }
        require(fullName.isNotBlank()) { "Name is required" }
        require(email.isNotBlank()) { "Email/username is required" }
        require(password.length >= 6) { "Password must be at least 6 characters" }
        if (dao.findUserByEmail(email.trim()) != null) {
            throw IllegalArgumentException("An account with this email/username already exists")
        }
        val effectivePerms = customPermissions ?: PermissionPolicy.defaultPermissionsFor(targetRole)
        val serialized = PermissionPolicy.serializePermissions(effectivePerms, targetRole)
        val user = UserEntity(
            fullName = fullName.trim(),
            email = email.trim(),
            passwordHash = PasswordHasher.hashPassword(password),
            role = targetRole.name,
            permissionsCsv = serialized,
            active = true
        )
        db.withTransaction {
            dao.insertUser(user)
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = "CREATE_USER",
                    module = "SECURITY",
                    recordId = user.id,
                    newValue = "${user.email} (${user.role})"
                )
            )
        }
        Result.success(user.copy(passwordHash = ""))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateUserAccess(
        actor: SessionUser,
        targetUserId: String,
        newRole: UserRole,
        newPermissions: Set<AppPermission>,
        active: Boolean
    ): Result<Unit> = try {
        if (!actor.has(AppPermission.MANAGE_USERS)) {
            throw ErpSecurityException("Permission denied: MANAGE_USERS required")
        }
        if (actor.id == targetUserId) {
            throw ErpSecurityException("Users cannot modify their own role, permissions, or active status")
        }
        val target = dao.getUserById(targetUserId) ?: throw IllegalArgumentException("User not found")
        val targetCurrentRole = UserRole.fromString(target.role)
        if (!actor.isFullAdmin &&
            (targetCurrentRole.rank >= actor.role.rank || newRole.rank >= actor.role.rank)
        ) {
            throw ErpSecurityException("Cannot alter a user with equal or higher role authority")
        }
        val serialized = PermissionPolicy.serializePermissions(newPermissions, newRole)
        val updated = target.copy(
            role = newRole.name,
            permissionsCsv = serialized,
            active = active,
            updatedAt = System.currentTimeMillis()
        )
        db.withTransaction {
            dao.updateUser(updated)
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = "UPDATE_USER_ACCESS",
                    module = "SECURITY",
                    recordId = targetUserId,
                    previousValue = "${target.role} active=${target.active}",
                    newValue = "${updated.role} active=${updated.active}"
                )
            )
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun observeAuditLogs(actor: SessionUser): Flow<List<AuditLogEntity>> {
        if (!actor.isFullAdmin && !actor.has(AppPermission.VIEW_AUDIT_LOGS)) {
            throw ErpSecurityException("Audit logs are restricted to Admin and Super Admin")
        }
        return dao.observeAuditLogs()
    }

    fun observeNotifications(session: SessionUser): Flow<List<NotificationEntity>> =
        dao.observeAllNotifications().map { list ->
            list.filter { notif ->
                if (session.isFullAdmin) true
                else notif.targetAudience == "USER:${session.id}"
            }
        }

    suspend fun markNotificationRead(id: String) {
        dao.markNotificationRead(id)
    }

    fun observeSettings(): Flow<AppSettingEntity?> = dao.observeSettings()

    suspend fun updateSettings(actor: SessionUser, settings: AppSettingEntity): Result<Unit> = try {
        if (!actor.has(AppPermission.MANAGE_SETTINGS)) {
            throw ErpSecurityException("Settings management is restricted to authorized Admins")
        }
        db.withTransaction {
            dao.upsertSettings(settings.copy(id = "default", updatedAt = System.currentTimeMillis()))
            dao.insertAuditLog(
                AuditLogEntity(
                    userId = actor.id,
                    userName = actor.fullName,
                    userRole = actor.role.name,
                    action = "UPDATE_SETTINGS",
                    module = "SETTINGS",
                    recordId = "default",
                    newValue = settings.businessName
                )
            )
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun UserEntity.toSessionUser(): SessionUser {
        val parsedRole = UserRole.fromString(role)
        return SessionUser(
            id = id,
            fullName = fullName,
            email = email,
            role = parsedRole,
            permissions = PermissionPolicy.parsePermissions(permissionsCsv, parsedRole),
            active = active
        )
    }
}
