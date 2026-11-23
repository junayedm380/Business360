package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.AppSettingEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.UserEntity
import com.example.domain.AppLanguage
import com.example.domain.AppPermission
import com.example.domain.AppStrings
import com.example.domain.Formatters
import com.example.domain.PermissionPolicy
import com.example.domain.SessionUser
import com.example.domain.UserRole
import com.example.ui.components.EmptyStateNotice
import com.example.ui.components.RoleBadge
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusChip
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovernanceAndSystemScreen(
    session: SessionUser,
    strings: AppStrings,
    language: AppLanguage,
    themeMode: ThemeMode,
    settings: AppSettingEntity?,
    users: List<UserEntity>,
    auditLogs: List<AuditLogEntity>,
    notifications: List<NotificationEntity>,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onUpdateSettings: (AppSettingEntity) -> Unit,
    onCreateUser: (String, String, String, UserRole, Set<AppPermission>?) -> Unit,
    onUpdateUserAccess: (String, UserRole, Set<AppPermission>, Boolean) -> Unit,
    onDeleteUser: (String) -> Unit,
    onCreateTask: (String, String, String) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onLogout: () -> Unit,
    initialSubTab: String? = null,
    onClearInitialSubTab: () -> Unit = {}
) {
    val tabs = buildList {
        if (session.isFullAdmin) {
            add(strings.notifications)
            if (session.has(AppPermission.MANAGE_TASKS)) add("Assign Tasks")
            if (session.has(AppPermission.MANAGE_USERS)) add(strings.userManagement)
            if (session.has(AppPermission.VIEW_AUDIT_LOGS)) add(strings.auditLogs)
        }
        add(strings.settings)
    }

    var selectedIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(initialSubTab, tabs) {
        if (!initialSubTab.isNullOrBlank()) {
            val targetTitle = when (initialSubTab) {
                "NOTIFICATIONS" -> strings.notifications
                "TASKS" -> "Assign Tasks"
                "USERS" -> strings.userManagement
                "AUDIT" -> strings.auditLogs
                "SETTINGS" -> strings.settings
                else -> initialSubTab
            }
            val idx = tabs.indexOf(targetTitle)
            if (idx >= 0) selectedIdx = idx
            onClearInitialSubTab()
        }
    }
    val activeTab = tabs.getOrElse(selectedIdx) { tabs.first() }
    var showCreateUserDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<UserEntity?>(null) }
    var deletingUser by remember { mutableStateOf<UserEntity?>(null) }
    var showTaskDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = selectedIdx.coerceAtMost(tabs.lastIndex)) {
            tabs.forEachIndexed { idx, title ->
                Tab(selected = selectedIdx == idx, onClick = { selectedIdx = idx }, text = { Text(title) })
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeTab) {
                strings.notifications -> {
                    if (notifications.isEmpty()) {
                        item { EmptyStateNotice(strings.noDataYet) }
                    } else {
                        items(notifications, key = { it.id }) { notif ->
                            SectionCard(
                                title = notif.title,
                                actionLabel = if (!notif.read) "Mark Read" else null,
                                onAction = if (!notif.read) ({ onMarkNotificationRead(notif.id) }) else null
                            ) {
                                Text(notif.message, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                Text("${notif.category} • ${Formatters.formatDateTime(notif.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                "Assign Tasks" -> {
                    item {
                        Button(onClick = { showTaskDialog = true }, enabled = users.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Assign Employee Task")
                        }
                    }
                }

                strings.userManagement -> {
                    item {
                        Button(onClick = { showCreateUserDialog = true }, modifier = Modifier.fillMaxWidth().testTag("btn_create_user")) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Create User Account")
                        }
                    }
                    items(users, key = { it.id }) { u ->
                        val uRole = UserRole.fromString(u.role)
                        SectionCard(title = u.fullName) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(u.email, style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        RoleBadge(uRole)
                                        StatusChip(if (u.active) "ACTIVE" else "INACTIVE")
                                    }
                                }
                                if (u.id != session.id && (session.isSuperAdmin || (uRole != UserRole.SUPER_ADMIN && uRole.rank < session.role.rank))) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(
                                            onClick = { editingUser = u },
                                            modifier = Modifier.testTag("btn_edit_user_${u.id}")
                                        ) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit permissions")
                                        }
                                        IconButton(
                                            onClick = { deletingUser = u },
                                            modifier = Modifier.testTag("btn_delete_user_${u.id}")
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Remove user", tint = ErrorRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                strings.auditLogs -> {
                    if (auditLogs.isEmpty()) {
                        item { EmptyStateNotice(strings.noDataYet) }
                    } else {
                        items(auditLogs, key = { it.id }) { log ->
                            SectionCard(title = "${log.action} (${log.module})") {
                                Text("${log.userName} [${log.userRole}] • ${Formatters.formatDateTime(log.createdAt)}", style = MaterialTheme.typography.bodySmall)
                                if (log.newValue.isNotBlank()) Text("Value: ${log.newValue}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }

                strings.settings -> {
                    item {
                        SectionCard(title = "${strings.languageLabel} & ${strings.themeLabel}") {
                            Text(strings.languageLabel, style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppLanguage.entries.forEach { lang ->
                                    FilterChip(selected = language == lang, onClick = { onLanguageChange(lang) }, label = { Text(lang.label) })
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(strings.themeLabel, style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemeMode.entries.forEach { mode ->
                                    FilterChip(selected = themeMode == mode, onClick = { onThemeChange(mode) }, label = { Text(mode.name) })
                                }
                            }
                        }
                    }
                    if (session.has(AppPermission.MANAGE_SETTINGS) && settings != null) {
                        item {
                            var bizName by remember(settings) { mutableStateOf(settings.businessName) }
                            var phone by remember(settings) { mutableStateOf(settings.phone) }
                            var address by remember(settings) { mutableStateOf(settings.address) }
                            var invPrefix by remember(settings) { mutableStateOf(settings.invoicePrefix) }
                            SectionCard(title = "Business Configuration") {
                                OutlinedTextField(value = bizName, onValueChange = { bizName = it }, label = { Text(strings.businessNameLabel) }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = invPrefix, onValueChange = { invPrefix = it }, label = { Text("Invoice Prefix") }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(10.dp))
                                Button(onClick = {
                                    onUpdateSettings(settings.copy(businessName = bizName, phone = phone, address = address, invoicePrefix = invPrefix))
                                }) { Text(strings.save) }
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_logout")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("${strings.signOutBtn} (${session.fullName})")
                        }
                    }
                }
            }
        }
    }

    if (showCreateUserDialog) {
        CreateUserDialog(actor = session, onDismiss = { showCreateUserDialog = false }) { name, email, pass, role ->
            onCreateUser(name, email, pass, role, null)
            showCreateUserDialog = false
        }
    }

    editingUser?.let { target ->
        EditUserPermissionsDialog(target = target, actor = session, onDismiss = { editingUser = null }) { role, perms, active ->
            onUpdateUserAccess(target.id, role, perms, active)
            editingUser = null
        }
    }

    deletingUser?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingUser = null },
            title = { Text("Remove User Account?") },
            text = {
                Text("Are you sure you want to permanently remove ${target.fullName} (${target.email} • ${target.role})? They will no longer be able to sign in.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUser(target.id)
                        deletingUser = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    modifier = Modifier.testTag("confirm_delete_user_btn")
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingUser = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showTaskDialog && users.isNotEmpty()) {
        AssignTaskDialog(users = users, onDismiss = { showTaskDialog = false }) { title, desc, uid ->
            onCreateTask(title, desc, uid)
            showTaskDialog = false
        }
    }
}

@Composable
private fun CreateUserDialog(
    actor: SessionUser,
    onDismiss: () -> Unit,
    onSave: (String, String, String, UserRole) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val allowedRoles = UserRole.entries.filter {
        actor.isSuperAdmin || (actor.isFullAdmin && it != UserRole.SUPER_ADMIN) || it.rank < actor.role.rank
    }
    var selectedRole by remember { mutableStateOf(allowedRoles.lastOrNull() ?: UserRole.EMPLOYEE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create ERP Account") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") })
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email / Username") })
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (min 6)") }, visualTransformation = PasswordVisualTransformation())
                Text("Assign Role", style = MaterialTheme.typography.labelMedium)
                allowedRoles.forEach { role ->
                    FilterChip(selected = selectedRole == role, onClick = { selectedRole = role }, label = { Text(role.name) })
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, email, password, selectedRole) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditUserPermissionsDialog(
    target: UserEntity,
    actor: SessionUser,
    onDismiss: () -> Unit,
    onSave: (UserRole, Set<AppPermission>, Boolean) -> Unit
) {
    val allowedRoles = UserRole.entries.filter {
        actor.isSuperAdmin || (actor.isFullAdmin && it != UserRole.SUPER_ADMIN) || it.rank < actor.role.rank
    }
    var role by remember { mutableStateOf(UserRole.fromString(target.role)) }
    var perms by remember { mutableStateOf(PermissionPolicy.parsePermissions(target.permissionsCsv, role)) }
    var active by remember { mutableStateOf(target.active) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions: ${target.fullName}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Account Active")
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                Text("Role", style = MaterialTheme.typography.labelMedium)
                allowedRoles.forEach { r ->
                    FilterChip(selected = role == r, onClick = { role = r; perms = PermissionPolicy.defaultPermissionsFor(r) }, label = { Text(r.name) })
                }
                Text("Permissions", style = MaterialTheme.typography.labelMedium)
                val editablePerms = when (role) {
                    UserRole.EMPLOYEE -> PermissionPolicy.EMPLOYEE_ALLOWED_PERMISSIONS.toList()
                    UserRole.MODERATOR_MANAGER -> PermissionPolicy.MODERATOR_ALLOWED_PERMISSIONS.toList()
                    UserRole.ADMIN, UserRole.SUPER_ADMIN -> AppPermission.entries
                }
                editablePerms.forEach { perm ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = perms.contains(perm), onCheckedChange = { checked ->
                            perms = if (checked) perms + perm else perms - perm
                        })
                        Text(perm.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(role, perms, active) }) { Text("Save Access") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignTaskDialog(users: List<UserEntity>, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var assignee by remember { mutableStateOf(users.first()) }
    var expanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Employee Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(value = "${assignee.fullName} (${assignee.role})", onValueChange = {}, readOnly = true, label = { Text("Assign To") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        users.forEach { u -> DropdownMenuItem(text = { Text("${u.fullName} (${u.role})") }, onClick = { assignee = u; expanded = false }) }
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Title") })
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Instructions") })
            }
        },
        confirmButton = { Button(onClick = { onSave(title, desc, assignee.id) }) { Text("Assign") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
