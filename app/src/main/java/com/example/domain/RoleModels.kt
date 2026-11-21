package com.example.domain

enum class UserRole(val rank: Int) {
    SUPER_ADMIN(4),
    ADMIN(3),
    MODERATOR_MANAGER(2),
    EMPLOYEE(1);

    companion object {
        fun fromString(raw: String): UserRole =
            entries.firstOrNull { it.name == raw } ?: EMPLOYEE
    }
}

enum class AppPermission {
    VIEW_PRODUCTS,
    MANAGE_PRODUCTS,
    VIEW_RAW_MATERIALS,
    MANAGE_RAW_MATERIALS,
    VIEW_PRODUCTION,
    CREATE_PRODUCTION,
    EDIT_PRODUCTION,
    APPROVE_PRODUCTION,
    VIEW_COSTING,
    VIEW_INVENTORY,
    ADJUST_STOCK,
    TRANSFER_STOCK,
    VIEW_CUSTOMERS,
    VIEW_CUSTOMER_CONTACT,
    MANAGE_CUSTOMERS,
    VIEW_CUSTOMER_LEDGER,
    CREATE_SALES,
    DISPATCH_DELIVERY,
    MANAGE_RETURNS,
    VIEW_UNIT_PRICES,
    VIEW_PURCHASE_PRICES,
    SUBMIT_PAYMENT_COLLECTION,
    APPROVE_PAYMENTS,
    VIEW_SUPPLIERS,
    MANAGE_PURCHASES,
    VIEW_SUPPLIER_LEDGER,
    SUBMIT_EXPENSE,
    APPROVE_EXPENSES,
    VIEW_EXPENSE_TOTALS,
    VIEW_CASH_BANK,
    MANAGE_CASH_BANK,
    VIEW_ACCOUNTING,
    VIEW_PROFIT_LOSS,
    DAILY_CLOSING,
    MANAGE_TASKS,
    MANAGE_USERS,
    VIEW_AUDIT_LOGS,
    MANAGE_SETTINGS,
    EXPORT_REPORTS
}

object PermissionPolicy {

    /**
     * Confidential financial & costing permissions strictly forbidden for Moderator/Manager and Employee.
     */
    val CONFIDENTIAL_FINANCIAL_PERMISSIONS: Set<AppPermission> = setOf(
        AppPermission.VIEW_COSTING,
        AppPermission.VIEW_CUSTOMER_LEDGER,
        AppPermission.SUBMIT_PAYMENT_COLLECTION,
        AppPermission.APPROVE_PAYMENTS,
        AppPermission.VIEW_SUPPLIER_LEDGER,
        AppPermission.SUBMIT_EXPENSE,
        AppPermission.VIEW_EXPENSE_TOTALS,
        AppPermission.APPROVE_EXPENSES,
        AppPermission.VIEW_CASH_BANK,
        AppPermission.MANAGE_CASH_BANK,
        AppPermission.VIEW_ACCOUNTING,
        AppPermission.VIEW_PROFIT_LOSS,
        AppPermission.DAILY_CLOSING
    )

    val EMPLOYEE_ALLOWED_PERMISSIONS: Set<AppPermission> = setOf(
        AppPermission.VIEW_PRODUCTION,
        AppPermission.CREATE_PRODUCTION,
        AppPermission.EDIT_PRODUCTION
    )

    val MODERATOR_ALLOWED_PERMISSIONS: Set<AppPermission> = setOf(
        AppPermission.VIEW_PRODUCTION,
        AppPermission.CREATE_PRODUCTION,
        AppPermission.EDIT_PRODUCTION,
        AppPermission.VIEW_PRODUCTS,
        AppPermission.MANAGE_PRODUCTS,
        AppPermission.VIEW_RAW_MATERIALS,
        AppPermission.MANAGE_RAW_MATERIALS,
        AppPermission.VIEW_CUSTOMERS,
        AppPermission.VIEW_CUSTOMER_CONTACT,
        AppPermission.MANAGE_CUSTOMERS,
        AppPermission.CREATE_SALES,
        AppPermission.VIEW_UNIT_PRICES,
        AppPermission.VIEW_SUPPLIERS,
        AppPermission.MANAGE_PURCHASES,
        AppPermission.VIEW_PURCHASE_PRICES
    )

    fun defaultPermissionsFor(role: UserRole): Set<AppPermission> = when (role) {
        UserRole.SUPER_ADMIN, UserRole.ADMIN -> AppPermission.entries.toSet()
        UserRole.MODERATOR_MANAGER -> MODERATOR_ALLOWED_PERMISSIONS
        UserRole.EMPLOYEE -> EMPLOYEE_ALLOWED_PERMISSIONS
    }

    fun parsePermissions(csv: String, role: UserRole): Set<AppPermission> {
        if (role == UserRole.SUPER_ADMIN || role == UserRole.ADMIN) {
            return AppPermission.entries.toSet()
        }
        if (csv.isBlank()) return emptySet()
        val parsed = csv.split(",")
            .mapNotNull { token -> AppPermission.entries.firstOrNull { it.name == token.trim() } }
            .toSet()
        return when (role) {
            UserRole.MODERATOR_MANAGER -> parsed.intersect(MODERATOR_ALLOWED_PERMISSIONS)
            UserRole.EMPLOYEE -> parsed.intersect(EMPLOYEE_ALLOWED_PERMISSIONS)
            UserRole.SUPER_ADMIN, UserRole.ADMIN -> AppPermission.entries.toSet()
        }
    }

    fun serializePermissions(perms: Set<AppPermission>, role: UserRole): String {
        val sanitized = when (role) {
            UserRole.SUPER_ADMIN, UserRole.ADMIN -> AppPermission.entries.toSet()
            UserRole.MODERATOR_MANAGER -> perms.intersect(MODERATOR_ALLOWED_PERMISSIONS)
            UserRole.EMPLOYEE -> perms.intersect(EMPLOYEE_ALLOWED_PERMISSIONS)
        }
        return sanitized.joinToString(",") { it.name }
    }
}

data class SessionUser(
    val id: String,
    val fullName: String,
    val email: String,
    val role: UserRole,
    val permissions: Set<AppPermission>,
    val active: Boolean
) {
    val isFullAdmin: Boolean
        get() = role == UserRole.SUPER_ADMIN || role == UserRole.ADMIN

    fun has(permission: AppPermission): Boolean =
        isFullAdmin || permissions.contains(permission)

    val canViewFinancials: Boolean
        get() = isFullAdmin ||
            (has(AppPermission.VIEW_PROFIT_LOSS) || has(AppPermission.VIEW_ACCOUNTING) || has(AppPermission.VIEW_CASH_BANK))

    val canViewCosting: Boolean
        get() = isFullAdmin || has(AppPermission.VIEW_COSTING)

    val canViewProfit: Boolean
        get() = isFullAdmin || has(AppPermission.VIEW_PROFIT_LOSS)
}
