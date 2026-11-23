package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.example.data.repository.HrOperationsRepository
import com.example.data.repository.HrPayrollRepository
import com.example.data.repository.OperationsRepository
import com.example.domain.AppLanguage
import com.example.domain.AppPermission
import com.example.domain.SessionUser
import com.example.ui.components.Business360BottomActionBar
import com.example.ui.components.Business360BrandTopBar
import com.example.ui.components.ErpNavDestination
import com.example.ui.components.GlobalSearchResultsOverlay
import com.example.ui.components.buildGlobalSearchResults
import com.example.ui.screens.CatalogProductionScreen
import com.example.ui.screens.FinanceAndOpsScreen
import com.example.ui.screens.GovernanceAndSystemScreen
import com.example.ui.screens.HrModuleScreen
import com.example.ui.screens.InitialSuperAdminSetupScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.RoleBasedDashboardScreen
import com.example.ui.screens.SalesAndCustomersScreen
import com.example.ui.theme.CraftErpTheme
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.ErpViewModel
import com.example.ui.viewmodel.HrViewModel
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
            val hrViewModel: HrViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val db = ErpDatabase.getInstance(app)
                        HrViewModel(
                            hrOpsRepo = HrOperationsRepository(db),
                            hrPayrollRepo = HrPayrollRepository(db)
                        )
                    }
                }
            )
            CraftErpApp(viewModel, hrViewModel)
        }
    }
}

@Composable
fun CraftErpApp(viewModel: ErpViewModel, hrViewModel: HrViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val strings by viewModel.strings.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val exportedCsv by viewModel.exportedCsv.collectAsStateWithLifecycle()
    val hrExportedCsv by hrViewModel.exportedCsv.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(authState) {
        val user = (authState as? AuthUiState.Authenticated)?.user
        hrViewModel.setSession(user)
    }

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

    LaunchedEffect(hrExportedCsv) {
        val exportPair = hrExportedCsv ?: return@LaunchedEffect
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
            context.startActivity(Intent.createChooser(shareIntent, "Export HR Report"))
        } catch (_: Exception) {
        } finally {
            hrViewModel.clearExportedCsv()
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
                AuthenticatedErpShell(session = state.user, viewModel = viewModel, hrViewModel = hrViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedErpShell(
    session: SessionUser,
    viewModel: ErpViewModel,
    hrViewModel: HrViewModel
) {
    val strings by viewModel.strings.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val hrFeedback by hrViewModel.feedback.collectAsStateWithLifecycle()
    val printableSlipHtml by hrViewModel.printableSlipHtml.collectAsStateWithLifecycle()
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

    val hrEmployees by hrViewModel.employees.collectAsStateWithLifecycle()
    val hrSelectedDateKey by hrViewModel.selectedDateKey.collectAsStateWithLifecycle()
    val hrSelectedMonthKey by hrViewModel.selectedMonthKey.collectAsStateWithLifecycle()
    val hrAllAttendance by hrViewModel.allAttendance.collectAsStateWithLifecycle()
    val hrMonthlyAttendance by hrViewModel.monthlyAttendance.collectAsStateWithLifecycle()
    val hrMonthlySummary by hrViewModel.monthlyAttendanceSummary.collectAsStateWithLifecycle()
    val hrAttendanceClosings by hrViewModel.attendanceClosings.collectAsStateWithLifecycle()
    val hrAttendanceAuditLogs by hrViewModel.attendanceAuditLogs.collectAsStateWithLifecycle()
    val hrLeaveTypes by hrViewModel.leaveTypes.collectAsStateWithLifecycle()
    val hrLeaveRequests by hrViewModel.leaveRequests.collectAsStateWithLifecycle()
    val hrLeaveBalances by hrViewModel.leaveBalances.collectAsStateWithLifecycle()
    val hrPayrollConfig by hrViewModel.payrollConfig.collectAsStateWithLifecycle()
    val hrAdvances by hrViewModel.employeeAdvances.collectAsStateWithLifecycle()
    val hrPayrollRuns by hrViewModel.payrollRuns.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        val msg = feedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearFeedback()
    }
    LaunchedEffect(hrFeedback) {
        val msg = hrFeedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        hrViewModel.clearFeedback()
    }

    val destinations = remember(session, strings, language) {
        buildList {
            add(ErpNavDestination("dashboard", strings.navDashboard, Icons.Filled.Dashboard))
            if (session.has(AppPermission.VIEW_PRODUCTION) || session.has(AppPermission.VIEW_PRODUCTS)) {
                add(ErpNavDestination("production", strings.navProduction, Icons.Filled.Factory))
            }
            if (session.has(AppPermission.VIEW_INVENTORY)) {
                add(ErpNavDestination("inventory", strings.navInventory, Icons.Filled.Inventory2))
            }
            if (session.has(AppPermission.CREATE_SALES)) {
                add(ErpNavDestination("sales", strings.navSales, Icons.Filled.PointOfSale))
            }
            if (session.has(AppPermission.MANAGE_PURCHASES) || session.canViewFinancials) {
                val finLabel = if (!session.canViewFinancials && session.has(AppPermission.MANAGE_PURCHASES)) {
                    if (language == AppLanguage.BN) "ক্রয়" else "Purchases"
                } else {
                    strings.navFinance
                }
                add(ErpNavDestination("finance", finLabel, Icons.Filled.AccountBalance))
            }
            if (session.hasAnyHrAccess) {
                add(ErpNavDestination("hr", strings.navHr, Icons.Filled.Badge))
            }
            add(ErpNavDestination("admin", strings.navAdmin, Icons.Filled.AdminPanelSettings))
        }
    }

    var currentRoute by rememberSaveable(session.id) { mutableStateOf("dashboard") }
    var productionInitialSubTab by rememberSaveable(session.id) { mutableStateOf<String?>(null) }
    var inventoryInitialSubTab by rememberSaveable(session.id) { mutableStateOf<String?>(null) }
    var salesInitialSubTab by rememberSaveable(session.id) { mutableStateOf<String?>(null) }
    var financeInitialSubTab by rememberSaveable(session.id) { mutableStateOf<String?>(null) }
    var hrInitialSubTab by rememberSaveable(session.id) { mutableStateOf<String?>(null) }
    var adminInitialSubTab by rememberSaveable(session.id) { mutableStateOf<String?>(null) }

    var searchQuery by rememberSaveable(session.id) { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val isSearchOverlayVisible = isSearchFocused || searchQuery.isNotBlank()
    val focusManager = LocalFocusManager.current

    if (destinations.none { it.route == currentRoute }) {
        currentRoute = "dashboard"
    }

    val closeSearch: () -> Unit = {
        searchQuery = ""
        isSearchFocused = false
        focusManager.clearFocus()
    }

    val navigateToTarget: (String) -> Unit = { target ->
        closeSearch()
        val parts = target.split(":", limit = 2)
        val mainRoute = parts[0]
        val subTab = parts.getOrNull(1)
        when (mainRoute) {
            "production" -> {
                productionInitialSubTab = subTab
                currentRoute = "production"
            }
            "inventory" -> {
                inventoryInitialSubTab = subTab
                currentRoute = "inventory"
            }
            "sales" -> {
                salesInitialSubTab = subTab
                currentRoute = "sales"
            }
            "finance" -> {
                financeInitialSubTab = subTab
                currentRoute = "finance"
            }
            "hr" -> {
                hrInitialSubTab = subTab
                currentRoute = "hr"
            }
            "admin" -> {
                adminInitialSubTab = subTab
                currentRoute = "admin"
            }
            else -> currentRoute = mainRoute
        }
    }

    val searchResults = remember(
        searchQuery, session, strings, language, products, models, rawMaterials, batches,
        warehouses, customers, sales, suppliers, purchases, expenses, cashAccounts,
        hrEmployees, hrLeaveRequests, hrPayrollRuns, users, tasks
    ) {
        buildGlobalSearchResults(
            query = searchQuery,
            session = session,
            strings = strings,
            language = language,
            products = products,
            models = models,
            rawMaterials = rawMaterials,
            batches = batches,
            warehouses = warehouses,
            customers = customers,
            sales = sales,
            suppliers = suppliers,
            purchases = purchases,
            expenses = expenses,
            cashAccounts = cashAccounts,
            hrEmployees = hrEmployees,
            hrLeaveRequests = hrLeaveRequests,
            hrPayrollRuns = hrPayrollRuns,
            users = users,
            tasks = tasks
        )
    }

    BackHandler(enabled = isSearchOverlayVisible) {
        closeSearch()
    }

    BackHandler(enabled = !isSearchOverlayVisible && currentRoute != "dashboard") {
        currentRoute = "dashboard"
    }

    val unreadCount = notifications.count { !it.read }
    val businessTitle = settings?.businessName?.takeIf { it.isNotBlank() } ?: strings.appTitle
    val currencySymbol = settings?.currencySymbol?.takeIf { it.isNotBlank() } ?: "৳"
    val context = LocalContext.current

    printableSlipHtml?.let { (slipTitle, slipHtml) ->
        var slipWebView by remember(slipTitle, slipHtml) { mutableStateOf<WebView?>(null) }
        AlertDialog(
            onDismissRequest = hrViewModel::clearPrintableSlip,
            title = { Text(slipTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                this.settings.javaScriptEnabled = false
                                this.settings.allowFileAccess = false
                                this.settings.allowContentAccess = false
                                this.settings.builtInZoomControls = true
                                this.settings.displayZoomControls = false
                                loadDataWithBaseURL(null, slipHtml, "text/html", "utf-8", null)
                                slipWebView = this
                            }
                        },
                        update = { view ->
                            slipWebView = view
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        try {
                            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                            val safeName = slipTitle.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".html"
                            val file = File(dir, safeName)
                            file.writeText(slipHtml)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/html"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, slipTitle)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Salary Slip"))
                        } catch (_: Exception) {
                        }
                    }) {
                        Text("Share")
                    }
                    Button(onClick = {
                        val wv = slipWebView
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                        if (wv != null && printManager != null) {
                            val adapter = wv.createPrintDocumentAdapter(slipTitle)
                            printManager.print(slipTitle, adapter, PrintAttributes.Builder().build())
                        }
                    }) {
                        Text("Print / PDF")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = hrViewModel::clearPrintableSlip) {
                    Text(strings.cancel)
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Business360BrandTopBar(
                businessTitle = businessTitle,
                session = session,
                strings = strings,
                destinations = destinations,
                currentRoute = currentRoute,
                unreadCount = unreadCount,
                onOpenNotifications = {
                    navigateToTarget("admin:NOTIFICATIONS")
                }
            )
        },
        bottomBar = {
            Business360BottomActionBar(
                session = session,
                strings = strings,
                language = language,
                destinations = destinations,
                currentRoute = currentRoute,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchFocusChange = { focused -> isSearchFocused = focused },
                onGoHome = {
                    closeSearch()
                    currentRoute = "dashboard"
                },
                onNavigateTarget = navigateToTarget
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (isSearchOverlayVisible) {
                GlobalSearchResultsOverlay(
                    query = searchQuery,
                    language = language,
                    strings = strings,
                    destinations = destinations,
                    results = searchResults,
                    onSelectResult = { res -> navigateToTarget(res.targetRoute) },
                    onQuickJumpRoute = { route -> navigateToTarget(route) },
                    onCloseSearch = closeSearch
                )
            } else {
                when (currentRoute) {
                    "dashboard" -> RoleBasedDashboardScreen(
                        session = session,
                        strings = strings,
                        snapshot = dashboardSnapshot,
                        tasks = tasks,
                        mySubmissions = paymentSubmissions,
                        onNavigateTab = navigateToTarget,
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
                        onEditBatch = viewModel::editProductionBatch,
                        onAdvanceBatch = viewModel::advanceProductionBatch,
                        initialSubTab = productionInitialSubTab,
                        onClearInitialSubTab = { productionInitialSubTab = null }
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
                        onExportStock = viewModel::exportReport,
                        initialSubTab = inventoryInitialSubTab,
                        onClearInitialSubTab = { inventoryInitialSubTab = null }
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
                                financeInitialSubTab = "Ledgers"
                                currentRoute = "finance"
                            }
                        },
                        initialSubTab = salesInitialSubTab,
                        onClearInitialSubTab = { salesInitialSubTab = null }
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
                        onExportReport = viewModel::exportReport,
                        initialSubTab = financeInitialSubTab,
                        onClearInitialSubTab = { financeInitialSubTab = null }
                    )
                    "hr" -> HrModuleScreen(
                        session = session,
                        strings = strings,
                        businessName = businessTitle,
                        currencySymbol = currencySymbol,
                        initialSubTab = hrInitialSubTab,
                        onClearInitialSubTab = { hrInitialSubTab = null },
                        employees = hrEmployees,
                        selectedDateKey = hrSelectedDateKey,
                        selectedMonthKey = hrSelectedMonthKey,
                        allAttendance = hrAllAttendance,
                        monthlyAttendance = hrMonthlyAttendance,
                        monthlySummary = hrMonthlySummary,
                        attendanceClosings = hrAttendanceClosings,
                        attendanceAuditLogs = hrAttendanceAuditLogs,
                        leaveTypes = hrLeaveTypes,
                        leaveRequests = hrLeaveRequests,
                        leaveBalances = hrLeaveBalances,
                        payrollConfig = hrPayrollConfig,
                        employeeAdvances = hrAdvances,
                        payrollRuns = hrPayrollRuns,
                        cashAccounts = cashAccounts,
                        onSelectDateKey = hrViewModel::setSelectedDateKey,
                        onShiftMonth = hrViewModel::shiftMonth,
                        onSaveEmployee = hrViewModel::saveEmployee,
                        onSetEmployeeActive = hrViewModel::setEmployeeActive,
                        onDeleteEmployee = hrViewModel::deleteEmployee,
                        onMarkAttendance = hrViewModel::markOrUpdateAttendance,
                        onMarkAllPresent = hrViewModel::markAllPresent,
                        onCloseDailyAttendance = hrViewModel::closeDailyAttendance,
                        onReopenDailyAttendance = hrViewModel::reopenDailyAttendance,
                        onSaveLeaveType = hrViewModel::saveLeaveType,
                        onSubmitLeaveRequest = hrViewModel::submitLeaveRequest,
                        onReviewLeaveRequest = hrViewModel::reviewLeaveRequest,
                        onUpdatePayrollConfig = hrViewModel::updatePayrollConfig,
                        onIssueAdvance = hrViewModel::issueEmployeeAdvance,
                        onGeneratePayroll = hrViewModel::generateMonthlyPayroll,
                        onUpdateDraftPayroll = hrViewModel::updateDraftPayroll,
                        onApprovePayroll = hrViewModel::approvePayroll,
                        onPayPayroll = hrViewModel::payPayroll,
                        onOpenPrintableSlip = hrViewModel::openPrintableSlip,
                        onExportHrReport = hrViewModel::exportHrReport
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
                        onDeleteUser = viewModel::deleteUser,
                        onCreateTask = viewModel::createTask,
                        onMarkNotificationRead = viewModel::markNotificationRead,
                        onLogout = viewModel::logout,
                        initialSubTab = adminInitialSubTab,
                        onClearInitialSubTab = { adminInitialSubTab = null }
                    )
                }
            }
        }
    }
}
