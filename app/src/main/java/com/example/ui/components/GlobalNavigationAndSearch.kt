package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CashAccountEntity
import com.example.data.local.EmployeeTaskEntity
import com.example.data.local.ExpenseEntity
import com.example.data.local.LeaveRequestEntity
import com.example.data.local.PayrollRunEntity
import com.example.data.local.ProductEntity
import com.example.data.local.ProductModelEntity
import com.example.data.local.ProductionBatchEntity
import com.example.data.local.PurchaseOrderEntity
import com.example.data.local.RawMaterialEntity
import com.example.data.local.SupplierEntity
import com.example.data.local.UserEntity
import com.example.data.local.WarehouseEntity
import com.example.data.repository.CustomerSanitizedView
import com.example.data.repository.EmployeeSanitizedView
import com.example.data.repository.SaleSanitizedView
import com.example.domain.AppLanguage
import com.example.domain.AppPermission
import com.example.domain.AppStrings
import com.example.domain.Formatters
import com.example.domain.SessionUser
import com.example.ui.theme.AmberTertiary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSecondary
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.RoseAccent
import com.example.ui.theme.VioletAccent

data class ErpNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

data class GlobalSearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val targetRoute: String,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
fun Business360BrandTopBar(
    businessTitle: String,
    session: SessionUser,
    strings: AppStrings,
    destinations: List<ErpNavDestination>,
    currentRoute: String,
    unreadCount: Int,
    onOpenNotifications: () -> Unit
) {
    val activeDest = destinations.firstOrNull { it.route == currentRoute }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.statusBars.union(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = Color(0xFF01184B),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.business360_logo),
                            contentDescription = businessTitle,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (businessTitle == "Business360") "Business360" else "$businessTitle • B360",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RoleBadge(session.role)
                            if (activeDest != null) {
                                Text(
                                    text = "• ${activeDest.label}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (session.isFullAdmin) {
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("btn_notifications")
                    ) {
                        BadgedBox(badge = { if (unreadCount > 0) Badge { Text(unreadCount.toString()) } }) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = strings.notifications,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        }
    }
}

@Composable
fun Business360BottomActionBar(
    session: SessionUser,
    strings: AppStrings,
    language: AppLanguage,
    destinations: List<ErpNavDestination>,
    currentRoute: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onGoHome: () -> Unit,
    onNavigateTarget: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .union(WindowInsets.ime)
                        .union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                )
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))

            // Bottom Control Bar: Left = Search Bar | Center = Home/Dashboard Button | Right = Three-Dot Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bottom Left Corner: Global Search Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = strings.searchHint,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = if (language == AppLanguage.BN) "খুঁজুন..." else "Search...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSearchFocusChange(true) }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { onSearchFocusChange(it.isFocused) }
                                    .testTag("global_search_input")
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = strings.cancel,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onSearchQueryChange("")
                                        onSearchFocusChange(false)
                                    }
                                    .testTag("btn_clear_search")
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Bottom Center: Home / Dashboard Pill Button
                val isHome = currentRoute == "dashboard" && searchQuery.isBlank()
                Surface(
                    onClick = onGoHome,
                    color = if (isHome) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                    contentColor = if (isHome) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(
                        width = if (isHome) 1.5.dp else 1.dp,
                        color = if (isHome) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_center_home")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.Home,
                            contentDescription = strings.navDashboard,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (language == AppLanguage.BN) "হোম" else "Home",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                }

                // Bottom Right Corner: Hamburger Menu containing Dashboard, HR, Production, and all modules
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_hamburger_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = if (language == AppLanguage.BN) "সব মডিউল মেনু" else "All Modules Menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.testTag("hamburger_dropdown_menu")
                    ) {
                        Text(
                            text = if (language == AppLanguage.BN) "সকল মডিউল" else "Business360 Modules",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        HorizontalDivider()

                        destinations.forEach { dest ->
                            val isSelected = currentRoute == dest.route
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = dest.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateTarget(dest.route)
                                },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }

                        if (session.hasAnyHrAccess) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = if (language == AppLanguage.BN) "এইচআর শর্টকাট" else "HR Quick Links",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            if (session.has(AppPermission.VIEW_EMPLOYEES_BASIC) || session.has(AppPermission.MANAGE_EMPLOYEES)) {
                                DropdownMenuItem(
                                    text = { Text(strings.employeeManagement) },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateTarget("hr:employees")
                                    }
                                )
                            }
                            if (session.has(AppPermission.MARK_ATTENDANCE) || session.has(AppPermission.VIEW_ATTENDANCE_REPORTS)) {
                                DropdownMenuItem(
                                    text = { Text(strings.attendanceManagement) },
                                    leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateTarget("hr:attendance")
                                    }
                                )
                            }
                            if (session.has(AppPermission.VIEW_LEAVES) || session.has(AppPermission.MANAGE_LEAVES)) {
                                DropdownMenuItem(
                                    text = { Text(strings.leaveManagement) },
                                    leadingIcon = { Icon(Icons.Filled.EventNote, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateTarget("hr:leaves")
                                    }
                                )
                            }
                            if (session.canViewSalaryAndPayroll) {
                                DropdownMenuItem(
                                    text = { Text(strings.payrollManagement) },
                                    leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateTarget("hr:payroll")
                                    }
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text(strings.settings) },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onNavigateTarget("admin:SETTINGS")
                            },
                            modifier = Modifier.testTag("nav_menu_settings")
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalSearchResultsOverlay(
    query: String,
    language: AppLanguage,
    strings: AppStrings,
    destinations: List<ErpNavDestination>,
    results: List<GlobalSearchResult>,
    onSelectResult: (GlobalSearchResult) -> Unit,
    onQuickJumpRoute: (String) -> Unit,
    onCloseSearch: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (query.isBlank()) {
                            if (language == AppLanguage.BN) "দ্রুত মডিউল ও অনুসন্ধান" else "Quick Search & Modules"
                        } else {
                            if (language == AppLanguage.BN) "অনুসন্ধানের ফলাফল (${results.size})" else "Search Results (${results.size})"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = onCloseSearch, modifier = Modifier.testTag("btn_close_search_overlay")) {
                        Text(strings.cancel)
                    }
                }
            }

            if (query.isBlank()) {
                item {
                    SectionCard(
                        title = if (language == AppLanguage.BN) "অ্যাপ জুড়ে যা খুঁজতে পারেন" else "Search Across Business360",
                        accentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = if (language == AppLanguage.BN) {
                                "পণ্য, মডেল, কাঁচামাল, উৎপাদন ব্যাচ, গ্রাহক, বিক্রয় ইনভয়েস, সরবরাহকারী, কর্মচারী, এইচআর, বা ড্যাশবোর্ড লিখুন।"
                            } else {
                                "Type any product, model, raw material, production batch, customer, invoice, supplier, HR employee, or module name."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            destinations.forEach { dest ->
                                AssistChip(
                                    onClick = { onQuickJumpRoute(dest.route) },
                                    label = { Text(dest.label) },
                                    leadingIcon = {
                                        Icon(dest.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            } else if (results.isEmpty()) {
                item {
                    EmptyStateNotice(
                        message = if (language == AppLanguage.BN) {
                            "\"$query\"-এর সাথে মিলে এমন কোনো তথ্য পাওয়া যায়নি।"
                        } else {
                            "No authorized records or modules matched \"$query\"."
                        }
                    )
                }
            } else {
                items(results, key = { it.id }) { res ->
                    Card(
                        onClick = { onSelectResult(res) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, res.accentColor.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_result_${res.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = res.accentColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(res.icon, contentDescription = null, tint = res.accentColor, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = res.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (res.subtitle.isNotBlank()) {
                                    Text(
                                        text = res.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = res.accentColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = res.category,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = res.accentColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun buildGlobalSearchResults(
    query: String,
    session: SessionUser,
    strings: AppStrings,
    language: AppLanguage,
    products: List<ProductEntity>,
    models: List<ProductModelEntity>,
    rawMaterials: List<RawMaterialEntity>,
    batches: List<ProductionBatchEntity>,
    warehouses: List<WarehouseEntity>,
    customers: List<CustomerSanitizedView>,
    sales: List<SaleSanitizedView>,
    suppliers: List<SupplierEntity>,
    purchases: List<PurchaseOrderEntity>,
    expenses: List<ExpenseEntity>,
    cashAccounts: List<CashAccountEntity>,
    hrEmployees: List<EmployeeSanitizedView>,
    hrLeaveRequests: List<LeaveRequestEntity>,
    hrPayrollRuns: List<PayrollRunEntity>,
    users: List<UserEntity>,
    tasks: List<EmployeeTaskEntity>
): List<GlobalSearchResult> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return emptyList()

    val out = mutableListOf<GlobalSearchResult>()
    fun matches(vararg fields: String?): Boolean =
        fields.any { !it.isNullOrBlank() && it.lowercase().contains(q) }

    // 1. Authorized Modules & Sub-sections (bilingual keywords)
    if (matches("dashboard", "home", "ড্যাশবোর্ড", "হোম", "overview")) {
        out.add(GlobalSearchResult("mod_dash", strings.navDashboard, strings.appTitle, "Module", "dashboard", Icons.Filled.Dashboard, IndigoAccent))
    }
    if (session.has(AppPermission.VIEW_PRODUCTION) && matches("production", "batch", "batches", "উৎপাদন", "প্রোডাকশন", "ব্যাচ")) {
        out.add(GlobalSearchResult("mod_prod_batches", strings.productionBatches, strings.navProduction, "Module", "production:BATCHES", Icons.Filled.PrecisionManufacturing, VioletAccent))
    }
    if (session.has(AppPermission.VIEW_PRODUCTS) && matches("product", "products", "catalog", "model", "পণ্য", "মডেল")) {
        out.add(GlobalSearchResult("mod_prod_catalog", strings.productsAndModels, strings.navProduction, "Module", "production:CATALOG", Icons.Filled.Factory, VioletAccent))
    }
    if (session.has(AppPermission.VIEW_RAW_MATERIALS) && matches("raw", "material", "materials", "কাঁচামাল")) {
        out.add(GlobalSearchResult("mod_prod_raw", strings.rawMaterials, strings.navProduction, "Module", "production:RAW", Icons.Filled.Inventory2, VioletAccent))
    }
    if (session.has(AppPermission.VIEW_INVENTORY) && matches("inventory", "stock", "warehouse", "মজুদ", "স্টক", "গুদাম")) {
        out.add(GlobalSearchResult("mod_inv", strings.navInventory, strings.warehouses, "Module", "inventory:BALANCES", Icons.Filled.Warehouse, CyanAccent))
    }
    if (session.has(AppPermission.CREATE_SALES) && matches("sales", "order", "invoice", "pos", "বিক্রয়", "সেলস")) {
        out.add(GlobalSearchResult("mod_sales", strings.navSales, strings.wholesaleAndRetail, "Module", "sales:ORDERS", Icons.Filled.PointOfSale, EmeraldSecondary))
    }
    if (session.has(AppPermission.VIEW_CUSTOMERS) && matches("customer", "customers", "client", "গ্রাহক")) {
        out.add(GlobalSearchResult("mod_cust", strings.customers, strings.navSales, "Module", "sales:CUSTOMERS", Icons.Filled.Storefront, EmeraldSecondary))
    }
    if (session.has(AppPermission.VIEW_CASH_BANK) && matches("cash", "bank", "finance", "accounting", "profit", "নগদ", "ব্যাংক", "অর্থ", "হিসাব", "লাভ")) {
        out.add(GlobalSearchResult("mod_fin_cash", strings.cashAndBank, strings.navFinance, "Module", "finance:Cash & Bank", Icons.Filled.AccountBalance, AmberTertiary))
    }
    if (session.has(AppPermission.MANAGE_PURCHASES) && matches("purchase", "supplier", "suppliers", "po", "ক্রয়", "সরবরাহকারী")) {
        out.add(GlobalSearchResult("mod_fin_po", strings.suppliersAndPurchases, strings.navFinance, "Module", "finance:Suppliers & PO", Icons.Filled.AccountBalanceWallet, AmberTertiary))
    }
    if ((session.has(AppPermission.VIEW_EMPLOYEES_BASIC) || session.has(AppPermission.MANAGE_EMPLOYEES)) &&
        matches("hr", "employee", "employees", "staff", "এইচআর", "কর্মী", "কর্মচারী")
    ) {
        out.add(GlobalSearchResult("mod_hr_emp", strings.employeeManagement, strings.navHr, "HR", "hr:employees", Icons.Filled.Person, IndigoAccent))
    }
    if ((session.has(AppPermission.MARK_ATTENDANCE) || session.has(AppPermission.VIEW_ATTENDANCE_REPORTS)) &&
        matches("hr", "attendance", "present", "absent", "হাজিরা", "উপস্থিতি")
    ) {
        out.add(GlobalSearchResult("mod_hr_att", strings.attendanceManagement, strings.navHr, "HR", "hr:attendance", Icons.Filled.Badge, IndigoAccent))
    }
    if ((session.has(AppPermission.VIEW_LEAVES) || session.has(AppPermission.MANAGE_LEAVES)) &&
        matches("hr", "leave", "leaves", "holiday", "ছুটি")
    ) {
        out.add(GlobalSearchResult("mod_hr_leave", strings.leaveManagement, strings.navHr, "HR", "hr:leaves", Icons.Filled.EventNote, IndigoAccent))
    }
    if (session.canViewSalaryAndPayroll && matches("hr", "payroll", "salary", "slip", "advance", "বেতন", "পেরোল", "স্লিপ")) {
        out.add(GlobalSearchResult("mod_hr_pay", strings.payrollManagement, strings.navHr, "HR", "hr:payroll", Icons.Filled.Payments, IndigoAccent))
    }
    if (session.has(AppPermission.MANAGE_USERS) && matches("user", "users", "role", "roles", "admin", "moderator", "ব্যবহারকারী", "অ্যাকাউন্ট", "মডারেটর")) {
        out.add(GlobalSearchResult("mod_admin_users", strings.userManagement, strings.navAdmin, "System", "admin:USERS", Icons.Filled.AdminPanelSettings, RoseAccent))
    }
    if (matches("settings", "language", "theme", "বাংলা", "english", "সেটিংস", "থিম")) {
        out.add(GlobalSearchResult("mod_settings", strings.settings, strings.languageLabel, "System", "admin:SETTINGS", Icons.Filled.Settings, RoseAccent))
    }

    // 2. Catalog Products & Models
    if (session.has(AppPermission.VIEW_PRODUCTS)) {
        products.filter { matches(it.name, it.category, it.description, it.unit) }.take(8).forEach { p ->
            out.add(GlobalSearchResult("prod_${p.id}", p.name, "${p.category} • ${p.unit}", strings.productsAndModels, "production:CATALOG", Icons.Filled.Factory, VioletAccent))
        }
        models.filter { matches(it.modelName, it.modelCode, it.barcode) }.take(8).forEach { m ->
            val sub = if (session.has(AppPermission.VIEW_UNIT_PRICES) || session.canViewFinancials) {
                "${m.modelCode} • Retail: ${Formatters.formatTaka(m.retailPrice)}"
            } else {
                m.modelCode
            }
            out.add(GlobalSearchResult("model_${m.id}", m.modelName, sub, "Model", "production:CATALOG", Icons.Filled.Factory, VioletAccent))
        }
    }

    // 3. Raw Materials
    if (session.has(AppPermission.VIEW_RAW_MATERIALS)) {
        val showPrice = session.has(AppPermission.VIEW_PURCHASE_PRICES) || session.canViewFinancials
        rawMaterials.filter { matches(it.name, it.materialCode, it.category, it.unit) }.take(8).forEach { rm ->
            val pricePart = if (showPrice) " • ${Formatters.formatTaka(rm.purchasePrice)}" else ""
            out.add(
                GlobalSearchResult(
                    "raw_${rm.id}",
                    "${rm.name} [${rm.materialCode}]",
                    "Stock: ${Formatters.formatQty(rm.availableQuantity, rm.unit)}$pricePart",
                    strings.rawMaterials,
                    "production:RAW",
                    Icons.Filled.Inventory2,
                    VioletAccent
                )
            )
        }
    }

    // 4. Production Batches
    if (session.has(AppPermission.VIEW_PRODUCTION)) {
        batches.filter { matches(it.batchNumber, it.status, it.notes) }.take(8).forEach { b ->
            out.add(
                GlobalSearchResult(
                    "batch_${b.id}",
                    "${b.batchNumber} (${b.status})",
                    "Good: ${Formatters.formatQty(b.goodQty)} • Planned: ${Formatters.formatQty(b.plannedQty)}",
                    strings.productionBatches,
                    "production:BATCHES",
                    Icons.Filled.PrecisionManufacturing,
                    VioletAccent
                )
            )
        }
    }

    // 5. Warehouses (Inventory)
    if (session.has(AppPermission.VIEW_INVENTORY)) {
        warehouses.filter { matches(it.name, it.location) }.take(5).forEach { wh ->
            out.add(GlobalSearchResult("wh_${wh.id}", wh.name, wh.location, strings.warehouses, "inventory:WAREHOUSES", Icons.Filled.Warehouse, CyanAccent))
        }
    }

    // 6. Customers & Sales (Sanitized)
    if (session.has(AppPermission.VIEW_CUSTOMERS)) {
        customers.filter { matches(it.name, it.businessName, it.phone, it.customerType) }.take(8).forEach { c ->
            val dueText = if (c.currentDue != null) " • Due: ${Formatters.formatTaka(c.currentDue)}" else ""
            out.add(
                GlobalSearchResult(
                    "cust_${c.id}",
                    c.name,
                    "${c.businessName.ifBlank { c.customerType }}$dueText",
                    strings.customers,
                    "sales:CUSTOMERS",
                    Icons.Filled.Storefront,
                    EmeraldSecondary
                )
            )
        }
    }
    if (session.has(AppPermission.CREATE_SALES)) {
        sales.filter { matches(it.invoiceNumber, it.customerName, it.deliveryStatus, it.saleType) }.take(8).forEach { s ->
            val totalPart = if (s.grandTotal != null) " • ${Formatters.formatTaka(s.grandTotal)}" else ""
            out.add(
                GlobalSearchResult(
                    "sale_${s.id}",
                    "${s.invoiceNumber} • ${s.customerName}",
                    "${s.deliveryStatus}$totalPart",
                    strings.navSales,
                    "sales:ORDERS",
                    Icons.Filled.ReceiptLong,
                    EmeraldSecondary
                )
            )
        }
    }

    // 7. Finance & Purchasing (Role-gated)
    if (session.has(AppPermission.VIEW_SUPPLIERS)) {
        suppliers.filter { matches(it.name, it.companyName, it.phone) }.take(6).forEach { sup ->
            val payPart = if (session.has(AppPermission.VIEW_SUPPLIER_LEDGER)) " • Payable: ${Formatters.formatTaka(sup.currentPayable)}" else ""
            out.add(
                GlobalSearchResult(
                    "sup_${sup.id}",
                    sup.name,
                    "${sup.companyName.ifBlank { sup.phone }}$payPart",
                    strings.suppliersAndPurchases,
                    "finance:Suppliers & PO",
                    Icons.Filled.AccountBalanceWallet,
                    AmberTertiary
                )
            )
        }
    }
    if (session.has(AppPermission.MANAGE_PURCHASES) || session.canViewFinancials) {
        purchases.filter { matches(it.poNumber, it.status) }.take(6).forEach { po ->
            out.add(GlobalSearchResult("po_${po.id}", "${po.poNumber} (${po.status})", "Qty: ${Formatters.formatQty(po.quantity)}", "PO", "finance:Suppliers & PO", Icons.Filled.AccountBalanceWallet, AmberTertiary))
        }
    }
    if (session.has(AppPermission.VIEW_EXPENSE_TOTALS) || session.has(AppPermission.APPROVE_EXPENSES) || session.has(AppPermission.SUBMIT_EXPENSE)) {
        expenses.filter { matches(it.category, it.description, it.submittedByName, it.status) }.take(6).forEach { exp ->
            out.add(
                GlobalSearchResult(
                    "exp_${exp.id}",
                    "${exp.category} • ${Formatters.formatTaka(exp.amount)}",
                    "${exp.status} • ${exp.description.ifBlank { exp.submittedByName }}",
                    strings.expenses,
                    "finance:Expenses",
                    Icons.Filled.Payments,
                    AmberTertiary
                )
            )
        }
    }
    if (session.has(AppPermission.VIEW_CASH_BANK)) {
        cashAccounts.filter { matches(it.name, it.accountType) }.take(5).forEach { acct ->
            out.add(
                GlobalSearchResult(
                    "acct_${acct.id}",
                    acct.name,
                    "${acct.accountType} • ${Formatters.formatTaka(acct.currentBalance)}",
                    strings.cashAndBank,
                    "finance:Cash & Bank",
                    Icons.Filled.AccountBalance,
                    AmberTertiary
                )
            )
        }
    }

    // 8. HR Employees, Leaves & Payroll (Role-gated)
    if (session.has(AppPermission.VIEW_EMPLOYEES_BASIC) || session.has(AppPermission.MANAGE_EMPLOYEES)) {
        hrEmployees.filter { matches(it.fullName, it.employeeCode, it.department, it.designation, it.phone) }.take(8).forEach { emp ->
            val salPart = if (emp.basicSalary != null) " • ${Formatters.formatTaka(emp.basicSalary)}" else ""
            out.add(
                GlobalSearchResult(
                    "hremp_${emp.id}",
                    "${emp.fullName} [${emp.employeeCode}]",
                    "${emp.designation} • ${emp.department}$salPart",
                    strings.employeeManagement,
                    "hr:employees",
                    Icons.Filled.Person,
                    IndigoAccent
                )
            )
        }
    }
    val empNameById = hrEmployees.associateBy({ it.id }, { it.fullName })
    if (session.has(AppPermission.VIEW_LEAVES) || session.has(AppPermission.MANAGE_LEAVES)) {
        hrLeaveRequests.filter { lv ->
            val empName = empNameById[lv.employeeId] ?: ""
            matches(empName, lv.leaveTypeName, lv.status, lv.reason, lv.startDateKey)
        }.take(6).forEach { lv ->
            val empName = empNameById[lv.employeeId] ?: strings.leaveManagement
            out.add(
                GlobalSearchResult(
                    "leave_${lv.id}",
                    "$empName • ${lv.leaveTypeName}",
                    "${lv.startDateKey} (${lv.totalDays}d) • ${lv.status}",
                    strings.leaveManagement,
                    "hr:leaves",
                    Icons.Filled.EventNote,
                    IndigoAccent
                )
            )
        }
    }
    if (session.canViewSalaryAndPayroll && (session.has(AppPermission.VIEW_PAYROLL) || session.has(AppPermission.MANAGE_PAYROLL))) {
        hrPayrollRuns.filter { matches(it.slipNumber, it.employeeNameSnapshot, it.employeeCodeSnapshot, it.monthKey, it.status) }.take(6).forEach { pr ->
            out.add(
                GlobalSearchResult(
                    "pay_${pr.id}",
                    "${pr.slipNumber} • ${pr.employeeNameSnapshot}",
                    "${pr.monthKey} • Net ${Formatters.formatTaka(pr.netSalary)} (${pr.status})",
                    strings.payrollManagement,
                    "hr:payroll",
                    Icons.Filled.Payments,
                    IndigoAccent
                )
            )
        }
    }

    // 9. System Users & Tasks
    if (session.has(AppPermission.MANAGE_USERS) || session.has(AppPermission.MANAGE_TASKS)) {
        users.filter { matches(it.fullName, it.email, it.role) }.take(6).forEach { u ->
            out.add(
                GlobalSearchResult(
                    "usr_${u.id}",
                    u.fullName,
                    "${u.email} • ${u.role}",
                    strings.userManagement,
                    "admin:USERS",
                    Icons.Filled.AdminPanelSettings,
                    RoseAccent
                )
            )
        }
    }
    tasks.filter {
        (session.isFullAdmin || it.assignedToUserId == session.id) && matches(it.title, it.description, it.status)
    }.take(5).forEach { t ->
        out.add(
            GlobalSearchResult(
                "task_${t.id}",
                t.title,
                "${t.status} • ${t.description}",
                strings.myTasks,
                "dashboard",
                Icons.AutoMirrored.Filled.Assignment,
                IndigoAccent
            )
        )
    }

    // Deduplicate and cap cleanly
    return out.distinctBy { it.id }.take(40)
}
