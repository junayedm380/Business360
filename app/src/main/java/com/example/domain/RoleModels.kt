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
    APPROVE_PRODUCTION,
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
     * Confidential financial permissions strictly forbidden by default for Moderator/Manager and Employee.
     */
    val CONFIDENTIAL_FINANCIAL_PERMISSIONS: Set<AppPermission> = setOf(
        AppPermission.VIEW_CUSTOMER_LEDGER,
        AppPermission.APPROVE_PAYMENTS,
        AppPermission.VIEW_SUPPLIER_LEDGER,
        AppPermission.VIEW_EXPENSE_TOTALS,
        AppPermission.APPROVE_EXPENSES,
        AppPermission.VIEW_CASH_BANK,
        AppPermission.MANAGE_CASH_BANK,
        AppPermission.VIEW_ACCOUNTING,
        AppPermission.VIEW_PROFIT_LOSS,
        AppPermission.DAILY_CLOSING
    )

    fun defaultPermissionsFor(role: UserRole): Set<AppPermission> = when (role) {
        UserRole.SUPER_ADMIN -> AppPermission.entries.toSet()

        UserRole.ADMIN -> AppPermission.entries.toSet() - setOf(
            AppPermission.VIEW_AUDIT_LOGS
        )

        UserRole.MODERATOR_MANAGER -> setOf(
            AppPermission.VIEW_PRODUCTS,
            AppPermission.MANAGE_PRODUCTS,
            AppPermission.VIEW_RAW_MATERIALS,
            AppPermission.MANAGE_RAW_MATERIALS,
            AppPermission.VIEW_PRODUCTION,
            AppPermission.CREATE_PRODUCTION,
            AppPermission.APPROVE_PRODUCTION,
            AppPermission.VIEW_INVENTORY,
            AppPermission.TRANSFER_STOCK,
            AppPermission.ADJUST_STOCK,
            AppPermission.VIEW_CUSTOMERS,
            AppPermission.VIEW_CUSTOMER_CONTACT,
            AppPermission.CREATE_SALES,
            AppPermission.DISPATCH_DELIVERY,
            AppPermission.MANAGE_RETURNS,
            AppPermission.VIEW_SUPPLIERS,
            AppPermission.MANAGE_PURCHASES,
            AppPermission.SUBMIT_EXPENSE,
            AppPermission.MANAGE_TASKS,
            AppPermission.EXPORT_REPORTS
        )

        UserRole.EMPLOYEE -> setOf(
            AppPermission.VIEW_PRODUCTS,
            AppPermission.VIEW_RAW_MATERIALS,
            AppPermission.VIEW_PRODUCTION,
            AppPermission.CREATE_PRODUCTION,
            AppPermission.VIEW_INVENTORY,
            AppPermission.VIEW_CUSTOMERS,
            AppPermission.CREATE_SALES,
            AppPermission.DISPATCH_DELIVERY,
            AppPermission.SUBMIT_PAYMENT_COLLECTION,
            AppPermission.SUBMIT_EXPENSE
        )
    }

    fun parsePermissions(csv: String, role: UserRole): Set<AppPermission> {
        if (role == UserRole.SUPER_ADMIN) return AppPermission.entries.toSet()
        if (csv.isBlank()) return emptySet()
        val parsed = csv.split(",")
            .mapNotNull { token -> AppPermission.entries.firstOrNull { it.name == token.trim() } }
            .toSet()
        // Enforce strict confidentiality invariant: Moderator/Manager and Employee cannot receive confidential financial permissions
        return if (role == UserRole.MODERATOR_MANAGER || role == UserRole.EMPLOYEE) {
            parsed - CONFIDENTIAL_FINANCIAL_PERMISSIONS - setOf(AppPermission.MANAGE_USERS, AppPermission.MANAGE_SETTINGS, AppPermission.VIEW_AUDIT_LOGS)
        } else {
            parsed
        }
    }

    fun serializePermissions(perms: Set<AppPermission>, role: UserRole): String {
        val sanitized = if (role == UserRole.MODERATOR_MANAGER || role == UserRole.EMPLOYEE) {
            perms - CONFIDENTIAL_FINANCIAL_PERMISSIONS - setOf(AppPermission.MANAGE_USERS, AppPermission.MANAGE_SETTINGS, AppPermission.VIEW_AUDIT_LOGS)
        } else {
            perms
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
    fun has(permission: AppPermission): Boolean =
        role == UserRole.SUPER_ADMIN || permissions.contains(permission)

    val canViewFinancials: Boolean
        get() = role == UserRole.SUPER_ADMIN ||
            (role == UserRole.ADMIN && (has(AppPermission.VIEW_PROFIT_LOSS) || has(AppPermission.VIEW_ACCOUNTING) || has(AppPermission.VIEW_CASH_BANK)))
}
