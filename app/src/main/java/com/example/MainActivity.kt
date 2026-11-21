package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.ErpDatabase
import com.example.data.local.SessionPreferences
import com.example.data.repository.AuthAndGovernanceRepository
import com.example.data.repository.FinanceRepository
import com.example.data.repository.OperationsRepository
import com.example.domain.AppLanguage
import com.example.domain.AppPermission
import com.example.domain.SessionUser
import com.example.domain.UserRole
import com.example.ui.components.RoleBadge
import com.example.ui.screens.CatalogProductionScreen
import com.example.ui.screens.FinanceAndOpsScreen
import com.example.ui.screens.GovernanceAndSystemScreen
import com.example.ui.screens.InitialSuperAdminSetupScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.RoleBasedDashboardScreen
import com.example.ui.screens.SalesAndCustomersScreen
import com.example.ui.theme.CraftErpTheme
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.ErpViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ErpViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val db = ErpDatabase.getInstance(app)
                        val prefs = SessionPreferences(app)
                        ErpViewModel(
                            authRepo = AuthAndGovernanceRepository(db),
                            opsRepo = OperationsRepository(db),
                            finRepo = FinanceRepository(db),
                            prefs = prefs
                        )
                    }
                }
            )
            CraftErpApp(viewModel)
        }
    }
}

private data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun CraftErpApp(viewModel: ErpViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val strings by viewModel.strings.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val exportedCsv by viewModel.exportedCsv.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(exportedCsv) {
        val exportPair = exportedCsv ?: return@LaunchedEffect
        val (filename, content) = exportPair
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, filename)
            file.writeText(content)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, filename)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export Report"))
        } catch (_: Exception) {
        } finally {
            viewModel.clearExportedCsv()
        }
    }

    CraftErpTheme(themeMode = themeMode, isBengali = language == AppLanguage.BN) {
        when (val state = authState) {
            AuthUiState.Loading -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            AuthUiState.NeedsInitialSuperAdmin -> {
                InitialSuperAdminSetupScreen(
                    strings = strings,
                    language = language,
                    errorMessage = authError,
                    onLanguageChange = viewModel::setLanguage,
                    onCreateSuperAdmin = viewModel::claimInitialSuperAdmin
                )
            }
            AuthUiState.Unauthenticated -> {
                LoginScreen(
                    strings = strings,
                    language = language,
                    errorMessage = authError,
                    onLanguageChange = viewModel::setLanguage,
                    onLogin = viewModel::login
                )
            }
            is AuthUiState.Authenticated -> {
                AuthenticatedErpShell(session = state.user, viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedErpShell(
    session: SessionUser,
    viewModel: ErpViewModel
) {
    val strings by viewModel.strings.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val dashboardSnapshot by viewModel.dashboardSnapshot.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val bomItems by viewModel.bomItems.collectAsStateWithLifecycle()
    val warehouses by viewModel.warehouses.collectAsStateWithLifecycle()
    val rawMaterials by viewModel.rawMaterials.collectAsStateWithLifecycle()
    val batches by viewModel.productionBatches.collectAsStateWithLifecycle()
    val balances by viewModel.inventoryBalances.collectAsStateWithLifecycle()
    val movements by viewModel.stockMovements.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val returns by viewModel.salesReturns.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val paymentSubmissions by viewModel.paymentSubmissions.collectAsStateWithLifecycle()
    val selectedCustomerLedger by viewModel.selectedCustomerLedger.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val purchases by viewModel.purchaseOrders.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val cashAccounts by viewModel.cashAccounts.collectAsStateWithLifecycle()
    val accounting by viewModel.accountingStatements.collectAsStateWithLifecycle()
    val journalEntries by viewModel.journalEntries.collectAsStateWithLifecycle()
    val dailyClosing by viewModel.dailyClosingPreview.collectAsStateWithLifecycle()
    val users by viewModel.managedUsers.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        val msg = feedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearFeedback()
    }

    val destinations = remember(session, strings) {
        buildList {
            add(NavDestination("dashboard", strings.navDashboard, Icons.Filled.Dashboard))
            if (session.has(AppPermission.VIEW_PRODUCTION) || session.has(AppPermission.VIEW_PRODUCTS)) {
                add(NavDestination("production", strings.navProduction, Icons.Filled.Factory))
            }
            if (session.has(AppPermission.VIEW_INVENTORY)) {
                add(NavDestination("inventory", strings.navInventory, Icons.Filled.Inventory2))
            }
            if (session.has(AppPermission.CREATE_SALES) || session.has(AppPermission.VIEW_CUSTOMERS)) {
                add(NavDestination("sales", strings.navSales, Icons.Filled.PointOfSale))
            }
            if (session.has(AppPermission.SUBMIT_PAYMENT_COLLECTION) ||
                session.has(AppPermission.APPROVE_PAYMENTS) ||
                session.has(AppPermission.VIEW_SUPPLIERS) ||
                session.has(AppPermission.SUBMIT_EXPENSE) ||
                session.canViewFinancials
            ) {
                add(NavDestination("finance", strings.navFinance, Icons.Filled.AccountBalance))
            }
            add(NavDestination("admin", strings.navAdmin, Icons.Filled.AdminPanelSettings))
        }
    }

    var currentRoute by rememberSaveable(session.id) { mutableStateOf("dashboard") }
    if (destinations.none { it.route == currentRoute }) {
        currentRoute = "dashboard"
    }

    BackHandler(enabled = currentRoute != "dashboard") {
        currentRoute = "dashboard"
    }

    val unreadCount = notifications.count { !it.read }
    val businessTitle = settings?.businessName?.takeIf { it.isNotBlank() } ?: strings.appTitle

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.business360_logo),
                                contentDescription = businessTitle,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(businessTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                RoleBadge(session.role)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    actions = {
                        IconButton(onClick = { currentRoute = "admin" }, modifier = Modifier.testTag("btn_notifications")) {
                            BadgedBox(badge = { if (unreadCount > 0) Badge { Text(unreadCount.toString()) } }) {
                                Icon(Icons.Filled.Notifications, contentDescription = strings.notifications)
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = { currentRoute = dest.route },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (useRail) {
                    NavigationRail {
                        destinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = { currentRoute = dest.route },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }

                when (currentRoute) {
                    "dashboard" -> RoleBasedDashboardScreen(
                        session = session,
                        strings = strings,
                        snapshot = dashboardSnapshot,
                        tasks = tasks,
                        mySubmissions = paymentSubmissions,
                        onNavigateTab = { currentRoute = it },
                        onToggleTask = viewModel::updateTaskStatus,
                        onExportReport = viewModel::exportReport
                    )
                    "production" -> CatalogProductionScreen(
                        session = session,
                        strings = strings,
                        products = products,
                        models = models,
                        bomItems = bomItems,
                        rawMaterials = rawMaterials,
                        warehouses = warehouses,
                        batches = batches,
                        onSaveProduct = viewModel::saveProduct,
                        onSaveModel = viewModel::saveProductModel,
                        onSaveBom = viewModel::saveBomItem,
                        onDeleteBom = viewModel::deleteBomItem,
                        onSaveRawMaterial = viewModel::saveRawMaterial,
                        onCreateBatch = viewModel::createProductionBatch,
                        onAdvanceBatch = viewModel::advanceProductionBatch
                    )
                    "inventory" -> InventoryScreen(
                        session = session,
                        strings = strings,
                        models = models,
                        warehouses = warehouses,
                        balances = balances,
                        movements = movements,
                        onCreateWarehouse = viewModel::createWarehouse,
                        onTransferStock = viewModel::transferStock,
                        onAdjustStock = viewModel::adjustStock,
                        onExportStock = viewModel::exportReport
                    )
                    "sales" -> SalesAndCustomersScreen(
                        session = session,
                        strings = strings,
                        models = models,
                        warehouses = warehouses,
                        customers = customers,
                        sales = sales,
                        returns = returns,
                        onCreateCustomer = viewModel::createCustomer,
                        onCreateSale = viewModel::createSale,
                        onConfirmDelivery = viewModel::confirmDelivery,
                        onProcessReturn = viewModel::processSalesReturn,
                        onOpenCustomerLedger = { custId ->
                            if (session.has(AppPermission.VIEW_CUSTOMER_LEDGER)) {
                                viewModel.selectCustomerForLedger(custId)
                                currentRoute = "finance"
                            }
                        }
                    )
                    "finance" -> FinanceAndOpsScreen(
                        session = session,
                        strings = strings,
                        customers = customers,
                        paymentSubmissions = paymentSubmissions,
                        selectedCustomerLedger = selectedCustomerLedger,
                        suppliers = suppliers,
                        purchases = purchases,
                        rawMaterials = rawMaterials,
                        warehouses = warehouses,
                        expenses = expenses,
                        cashAccounts = cashAccounts,
                        accounting = accounting,
                        journalEntries = journalEntries,
                        dailyClosing = dailyClosing,
                        onSubmitPaymentCollection = viewModel::submitPaymentCollection,
                        onReviewPayment = viewModel::reviewPaymentSubmission,
                        onSelectCustomerLedger = viewModel::selectCustomerForLedger,
                        onCreateSupplier = viewModel::createSupplier,
                        onCreatePurchase = viewModel::createAndReceivePurchase,
                        onReceivePurchase = viewModel::receivePurchaseOrder,
                        onSubmitExpense = viewModel::submitExpense,
                        onReviewExpense = viewModel::reviewExpense,
                        onCashTx = viewModel::cashInOutOrTransfer,
                        onExecuteDailyClosing = viewModel::executeDailyClosing,
                        onExportReport = viewModel::exportReport
                    )
                    "admin" -> GovernanceAndSystemScreen(
                        session = session,
                        strings = strings,
                        language = language,
                        themeMode = themeMode,
                        settings = settings,
                        users = users,
                        auditLogs = auditLogs,
                        notifications = notifications,
                        onLanguageChange = viewModel::setLanguage,
                        onThemeChange = viewModel::setThemeMode,
                        onUpdateSettings = viewModel::updateSettings,
                        onCreateUser = viewModel::createUser,
                        onUpdateUserAccess = viewModel::updateUserAccess,
                        onCreateTask = viewModel::createTask,
                        onMarkNotificationRead = viewModel::markNotificationRead,
                        onLogout = viewModel::logout
                    )
                }
            }
        }
    }
}
