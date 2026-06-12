package com.codex.suishouledger

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codex.suishouledger.data.local.AccountEntity
import com.codex.suishouledger.data.local.CategoryEntity
import com.codex.suishouledger.data.local.CategoryTotal
import com.codex.suishouledger.data.local.IngestionState
import com.codex.suishouledger.data.local.LedgerEntryEntity
import com.codex.suishouledger.data.local.PeriodSummary
import com.codex.suishouledger.data.local.ReviewState
import com.codex.suishouledger.data.local.TransactionType
import com.codex.suishouledger.domain.PaymentParser
import com.codex.suishouledger.monitoring.PaymentNotificationListenerService
import com.codex.suishouledger.ui.MainUiState
import com.codex.suishouledger.ui.MainViewModel
import com.codex.suishouledger.ui.auth.AuthScreen
import com.codex.suishouledger.ui.chat.ChatScreen
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.BLACK
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContent {
            MoneyNoteTheme {
                LedgerApp()
            }
        }
    }
}

private enum class AppTab(val label: String) {
    Home("首页"),
    Bills("账单"),
    Charts("图表"),
    My("我的")
}

private enum class AppPanel {
    BillDetail,
    Budget,
    DraftReview,
    DraftDetail,
    Chat,
    Settings,
    PersonalInfo,
    Help,
    About,
    Account,
    Category,
    Search,
    OcrPreview,
    Sync,
    Profile
}

private val CashFlowGreen = Color(0xFF2F8E4C)
private val CashFlowGreenSoft = Color(0xFFD8F2DE)
private val CashFlowTextMuted = Color(0xFF6C726D)
private val CategoryColorOptions = listOf(
    "#EF4444",
    "#F97316",
    "#F59E0B",
    "#84CC16",
    "#14B8A6",
    "#0EA5E9",
    "#2563EB",
    "#8B5CF6",
    "#DB2777",
    "#64748B"
)

private data class GlyphOption(val key: String, val glyph: String)

private const val DefaultCategoryIconKey = "label"
private const val DefaultIncomeCategoryIconKey = "payments"
private const val DefaultAccountIconKey = "account_balance_wallet"

private val CategoryIconOptions = listOf(
    GlyphOption("label", "\uD83C\uDFF7\uFE0F"),
    GlyphOption("restaurant", "\uD83C\uDF7D\uFE0F"),
    GlyphOption("local_cafe", "\u2615"),
    GlyphOption("delivery_dining", "\uD83D\uDEF5"),
    GlyphOption("directions_transit", "\uD83D\uDE8C"),
    GlyphOption("local_taxi", "\uD83D\uDE95"),
    GlyphOption("shopping_bag", "\uD83D\uDECD\uFE0F"),
    GlyphOption("receipt_long", "\uD83E\uDDFE"),
    GlyphOption("movie", "\uD83C\uDFAC"),
    GlyphOption("home", "\uD83C\uDFE0"),
    GlyphOption("local_hospital", "\uD83C\uDFE5"),
    GlyphOption("school", "\uD83D\uDCDA"),
    GlyphOption("wifi", "\uD83D\uDCF1"),
    GlyphOption("flight_takeoff", "\u2708\uFE0F"),
    GlyphOption("checkroom", "\uD83D\uDC55"),
    GlyphOption("devices", "\uD83D\uDCBB"),
    GlyphOption("bolt", "\uD83D\uDCA1"),
    GlyphOption("swap_horiz", "\uD83D\uDD01"),
    GlyphOption("payments", "\uD83D\uDCB0"),
    GlyphOption("show_chart", "\uD83D\uDCC8"),
    GlyphOption("redeem", "\uD83C\uDF81"),
    GlyphOption("work", "\uD83D\uDCBC"),
    GlyphOption("undo", "\u21A9\uFE0F"),
    GlyphOption("savings", "\uD83D\uDCB0"),
    GlyphOption("more_horiz", "\u2022\u2022\u2022")
)

private val AccountIconOptions = listOf(
    GlyphOption("account_balance_wallet", "\uD83D\uDC5B"),
    GlyphOption("wechat", "\u5FAE"),
    GlyphOption("alipay", "\u652F"),
    GlyphOption("credit_card", "\uD83D\uDCB3"),
    GlyphOption("payments", "\uD83D\uDCB5"),
    GlyphOption("account_balance", "\uD83C\uDFE6"),
    GlyphOption("savings", "\uD83D\uDCB0"),
    GlyphOption("cash", "\u73B0"),
    GlyphOption("card", "\u5361")
)

@Composable
private fun MoneyNoteTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = CashFlowGreen,
            secondary = Color(0xFFB7E5C4),
            tertiary = Color(0xFFFFB74D),
            background = Color(0xFF161917),
            surface = Color(0xFF1D211E),
            surfaceVariant = Color(0xFF262A27),
            onPrimary = Color.White,
            onSecondary = Color(0xFF0F2417),
            onSurface = Color(0xFFF3F4F2),
            onSurfaceVariant = Color(0xFFBCC2BC),
            outline = Color(0xFF3A403B),
            error = Color(0xFFFF8A80)
        )
    } else {
        lightColorScheme(
            primary = CashFlowGreen,
            onPrimary = Color.White,
            primaryContainer = CashFlowGreenSoft,
            onPrimaryContainer = Color(0xFF0F4A24),
            secondary = Color(0xFF6E7C74),
            tertiary = Color(0xFFFFA726),
            background = Color(0xFFF7F9FB),
            surface = Color.White,
            surfaceVariant = Color(0xFFF2F5F7),
            onSurface = Color(0xFF1F2320),
            onSurfaceVariant = CashFlowTextMuted,
            outline = Color(0xFFE3E8ED),
            error = Color(0xFFE85D5D)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private fun missingScreenshotMonitorPermissions(context: Context): Array<String> {
    return screenshotMonitorPermissions().filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()
}

private fun screenshotMonitorPermissions(): List<String> {
    return buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ).orEmpty()
    val component = ComponentName(context, PaymentNotificationListenerService::class.java)
    return enabledListeners.split(':').any { enabled ->
        enabled.equals(component.flattenToString(), ignoreCase = true) ||
            enabled.equals(component.flattenToShortString(), ignoreCase = true)
    }
}

private fun requestNotificationListenerRebind(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, PaymentNotificationListenerService::class.java)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(AppTab.Home) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showAuthScreen by remember { mutableStateOf(false) }
    var showLocalPreviewNotice by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf<AppPanel?>(null) }
    var selectedEntryId by remember { mutableStateOf<String?>(null) }
    var editingEntry by remember { mutableStateOf<LedgerEntryEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.handlePickedImage(uri)
            activePanel = AppPanel.OcrPreview
        }
    }
    val screenshotPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (missingScreenshotMonitorPermissions(context).isEmpty()) {
            viewModel.toggleScreenshotMonitoring(true)
        } else {
            viewModel.toggleScreenshotMonitoring(false)
            Toast.makeText(context, "需要允许读取图片/通知权限后才能开启截图监听", Toast.LENGTH_LONG).show()
        }
    }
    fun requestEnableScreenshotMonitoring() {
        val missingPermissions = missingScreenshotMonitorPermissions(context)
        if (missingPermissions.isNotEmpty()) {
            screenshotPermissionLauncher.launch(missingPermissions)
        } else {
            viewModel.toggleScreenshotMonitoring(true)
        }
    }

    fun requestDisableScreenshotMonitoring() {
        viewModel.toggleScreenshotMonitoring(false)
    }

    fun openNotificationListenerSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Toast.makeText(context, "无法打开通知监听设置页", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestNotificationParsingChange(enabled: Boolean) {
        if (!enabled) {
            viewModel.toggleNotificationParsing(false)
            return
        }
        viewModel.toggleNotificationParsing(true)
        if (isNotificationListenerEnabled(context)) {
            requestNotificationListenerRebind(context)
            Toast.makeText(context, "通知监听已授权，等待微信/支付宝支付通知", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "请在系统页开启 CashFlow 通知监听权限", Toast.LENGTH_LONG).show()
            openNotificationListenerSettings()
        }
    }

    fun openAppSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data = "package:${context.packageName}".toUri()
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "无法打开应用设置", Toast.LENGTH_SHORT).show()
        }
    }

    val authToken by ServiceLocator.authTokenProvider.tokenFlow.collectAsState(initial = null)
    val isLoggedIn = !BuildConfig.LOCAL_PREVIEW && !authToken.isNullOrBlank()
    fun showLocalPreviewUnavailable() {
        showLocalPreviewNotice = true
    }
    fun requestAuthOrPreview() {
        if (BuildConfig.LOCAL_PREVIEW) {
            showLocalPreviewUnavailable()
        } else {
            showAuthScreen = true
        }
    }
    fun openPanelOrPreview(panel: AppPanel) {
        val blocked = panel == AppPanel.Chat ||
            panel == AppPanel.Sync ||
            panel == AppPanel.PersonalInfo ||
            panel == AppPanel.Profile
        if (BuildConfig.LOCAL_PREVIEW && blocked) {
            showLocalPreviewUnavailable()
        } else {
            activePanel = panel
        }
    }
    if (showAuthScreen && !BuildConfig.LOCAL_PREVIEW) {
        AuthScreen(
            tokenProvider = ServiceLocator.authTokenProvider,
            onAuthSuccess = {
                showAuthScreen = false
            },
            onDismiss = { showAuthScreen = false }
        )
        return
    }

    LaunchedEffect(Unit) {
        viewModel.startScreenshotMonitoringIfNeeded()
    }

    fun openEntryDetail(entry: LedgerEntryEntity) {
        selectedEntryId = entry.id
        activePanel = if (entry.reviewState == ReviewState.DRAFT) AppPanel.DraftDetail else AppPanel.BillDetail
    }

    val selectedEntry = remember(state.drafts, state.confirmed, selectedEntryId) {
        (state.drafts + state.confirmed).firstOrNull { it.id == selectedEntryId }
    }

    activePanel?.let { panel ->
        BackHandler {
            activePanel = null
            selectedEntryId = null
        }
        AppPanelHost(
            panel = panel,
            state = state,
            viewModel = viewModel,
            selectedEntry = selectedEntry,
            isLoggedIn = isLoggedIn,
            onBack = {
                activePanel = null
                selectedEntryId = null
            },
            onOpenPanel = { nextPanel -> openPanelOrPreview(nextPanel) },
            onOpenEntry = { entry -> openEntryDetail(entry) },
            onEditEntry = { editingEntry = it },
            onRequestAuth = { requestAuthOrPreview() },
            onLogout = {
                tab = AppTab.Home
                activePanel = null
                selectedEntryId = null
                requestAuthOrPreview()
            },
            onNotificationChange = { enabled -> requestNotificationParsingChange(enabled) },
            onOpenNotificationSettings = { openNotificationListenerSettings() },
            onOpenAppSettings = { openAppSettings() },
            onRequestScreenshotMonitoringEnable = { requestEnableScreenshotMonitoring() },
            onRequestScreenshotMonitoringDisable = { requestDisableScreenshotMonitoring() }
        )
        editingEntry?.let { entry ->
            EditEntrySheet(
                entry = entry,
                categories = state.categories,
                accounts = state.accounts,
                onDismiss = { editingEntry = null },
                onSave = { updated ->
                    viewModel.saveEditedEntry(updated)
                    editingEntry = null
                },
                onConfirm = { updated ->
                    viewModel.confirmEditedEntry(updated)
                    editingEntry = null
                    activePanel = null
                    selectedEntryId = null
                },
                onRetryOcr = { viewModel.retryOcr(entry) }
            )
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            ReferenceBottomBar(
                selectedTab = tab,
                onSelect = { tab = it },
                onAddClick = { showAddSheet = true }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (tab) {
                AppTab.Home -> HomeScreen(
                    state = state,
                    onViewBills = { tab = AppTab.Bills },
                    onViewCharts = { tab = AppTab.Charts },
                    onOpenBudget = { openPanelOrPreview(AppPanel.Budget) },
                    onOpenSearch = { openPanelOrPreview(AppPanel.Search) },
                    onOpenChat = { openPanelOrPreview(AppPanel.Chat) },
                    onOpenDraftReview = { openPanelOrPreview(AppPanel.DraftReview) },
                    onOpenOcrPreview = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onOpenQuickEntry = { showAddSheet = true },
                    onOpenDetail = { entry -> openEntryDetail(entry) }
                )

                AppTab.Bills -> BillsScreen(
                    state = state,
                    viewModel = viewModel,
                    onOpenDetail = { entry -> openEntryDetail(entry) }
                )
                AppTab.Charts -> ChartsScreen(
                    state = state
                )
                AppTab.My -> MyScreen(
                    state = state,
                    isLoggedIn = isLoggedIn,
                    onRequestAuth = { requestAuthOrPreview() },
                    onLogout = {
                        tab = AppTab.Home
                        activePanel = null
                        selectedEntryId = null
                        requestAuthOrPreview()
                    },
                    onOpenPanel = { openPanelOrPreview(it) }
                )
            }
        }
    }

    if (showAddSheet) {
        AddTransactionSheet(
            state = state,
            onDismiss = { showAddSheet = false },
            onCreateCategory = { name, isIncome -> viewModel.saveCategory(name, isIncome) },
            onDeleteCategory = { category -> viewModel.removeCategory(category) },
            onSave = { amountCents, merchant, categoryCode, accountCode, type, note ->
                viewModel.createManualEntry(amountCents, merchant, categoryCode, accountCode, type, note)
            }
        )
    }

    if (showLocalPreviewNotice) {
        LocalPreviewNoticeDialog(onDismiss = { showLocalPreviewNotice = false })
    }
}

@Composable
private fun LocalPreviewNoticeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本地体验版") },
        text = {
            Text("本地体验版暂不开放登录、AI 和云同步；当前可使用本地记账、账单、图表和识别草稿。")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}

@Composable
private fun ReferenceBottomBar(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
    onAddClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomItem(AppTab.Home, selectedTab, Icons.Filled.Home) { onSelect(it) }
            BottomItem(AppTab.Bills, selectedTab, Icons.AutoMirrored.Filled.ReceiptLong) { onSelect(it) }
            Surface(
                onClick = onAddClick,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = CashFlowGreen,
                shadowElevation = 7.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "记账",
                        tint = Color.White,
                        modifier = Modifier.size(27.dp)
                    )
                }
            }
            BottomItem(AppTab.Charts, selectedTab, Icons.Filled.Analytics) { onSelect(it) }
            BottomItem(AppTab.My, selectedTab, Icons.Filled.AccountCircle) { onSelect(it) }
        }
    }
}

@Composable
private fun RowScope.BottomItem(
    tab: AppTab,
    selected: AppTab,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSelect: (AppTab) -> Unit
) {
    val selectedColor = CashFlowGreen
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val color = if (selected == tab) selectedColor else unselectedColor
    TextButton(
        onClick = { onSelect(tab) },
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Text(tab.label, color = color, maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HomeScreen(
    state: MainUiState,
    onViewBills: () -> Unit,
    onViewCharts: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenDraftReview: () -> Unit,
    onOpenOcrPreview: () -> Unit,
    onOpenQuickEntry: () -> Unit,
    onOpenDetail: (LedgerEntryEntity) -> Unit
) {
    var selectedTopCategory by remember { mutableStateOf<CategoryTotal?>(null) }
    var selectedMonth by remember { mutableStateOf(monthStartMillis()) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val monthEntries = remember(state.confirmed, selectedMonth) {
        state.confirmed.filter { isSameMonth(it.occurredAt, selectedMonth) }
    }
    val monthIncome = remember(monthEntries) {
        monthEntries.filter { it.transactionType == TransactionType.INCOME }.sumOf { it.amountCents }
    }
    val monthExpense = remember(monthEntries) {
        monthEntries.filter { it.transactionType == TransactionType.EXPENSE }.sumOf { it.amountCents }
    }
    val monthCategoryTotals = remember(monthEntries, state.categories) {
        buildCategoryTotalsForMonth(monthEntries, state.categories, TransactionType.EXPENSE)
    }
    val monthBudgetLeft = (state.monthlyBudgetCents - monthExpense).coerceAtLeast(0L)
    val monthBudgetUsage = if (state.monthlyBudgetCents > 0L) {
        monthExpense.toFloat() / state.monthlyBudgetCents.toFloat()
    } else {
        0f
    }
    val selectedTopCategoryEntries = remember(state.confirmed, selectedTopCategory, selectedMonth) {
        selectedTopCategory?.let { total ->
            filterMonthCategoryExpenses(state.confirmed, total.categoryCode, selectedMonth)
        }.orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HomeHeader(
                onSearch = onOpenSearch,
                onChat = onOpenChat
            )
        }
        item {
            MonthSelectorLabel(monthLabel(selectedMonth), onClick = { showMonthPicker = true })
        }
        item {
            OverviewCard(
                monthLabel = monthLabel(selectedMonth),
                expense = monthExpense,
                income = monthIncome,
                monthlyBudgetCents = state.monthlyBudgetCents,
                monthlyBudgetLeftCents = monthBudgetLeft,
                monthlyBudgetUsage = monthBudgetUsage,
                expenseCategoryTotals = monthCategoryTotals,
                categories = state.categories
            )
        }
        item {
            BudgetOverviewCard(
                monthlyBudgetCents = state.monthlyBudgetCents,
                monthlyBudgetLeftCents = monthBudgetLeft,
                monthlyBudgetUsage = monthBudgetUsage,
                onOpenBudget = onOpenBudget
            )
        }
        item {
            CategoryPreviewCard(
                categoryTotals = monthCategoryTotals,
                monthlyTotal = monthExpense,
                categories = state.categories,
                onViewCharts = onViewCharts,
                onOpenCategory = { selectedTopCategory = it }
            )
        }
        item {
            DraftSummaryCard(state, onOpen = onOpenDraftReview, onOpenQuickEntry = onOpenQuickEntry)
        }
        item {
            OcrPreviewEntryCard(onClick = onOpenOcrPreview)
        }
    }

    selectedTopCategory?.let { total ->
        TopCategoryDetailSheet(
            total = total,
            entries = selectedTopCategoryEntries,
            categories = state.categories,
            onDismiss = { selectedTopCategory = null },
            onOpenDetail = onOpenDetail
        )
    }

    if (showMonthPicker) {
        YearMonthPickerDialog(
            title = "选择首页月份",
            selectedMonth = selectedMonth,
            entries = state.confirmed,
            onDismiss = { showMonthPicker = false },
            onConfirm = {
                selectedMonth = it
                selectedTopCategory = null
                showMonthPicker = false
            }
        )
    }
}

@Composable
private fun OverviewCard(
    monthLabel: String,
    expense: Long,
    income: Long,
    monthlyBudgetCents: Long,
    monthlyBudgetLeftCents: Long,
    monthlyBudgetUsage: Float,
    expenseCategoryTotals: List<CategoryTotal>,
    categories: List<CategoryEntity>
) {
    val budgetUsage = monthlyBudgetUsage.coerceAtMost(1.5f)
    val budgetColor = when {
        monthlyBudgetCents == 0L -> Color.White
        monthlyBudgetUsage >= 1f -> Color.White
        monthlyBudgetUsage >= 0.8f -> Color.White.copy(alpha = 0.95f)
        else -> Color.White
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF2B9A56), Color(0xFF1F7F45))
                    )
                )
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${monthLabel}支出（已确认）", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = formatMoney(expense),
                    style = MaterialTheme.typography.displaySmall,
                    color = budgetColor,
                    fontWeight = FontWeight.Bold
                )
                if (monthlyBudgetCents > 0L) {
                    Box(modifier = Modifier.fillMaxWidth().height(9.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(99.dp))) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(budgetUsage.coerceIn(0f, 1f))
                                .height(9.dp)
                                .background(Color.White, RoundedCornerShape(99.dp))
                        )
                    }
                    Text(
                        "预算 ${formatMoney(monthlyBudgetCents)}   剩余 ${formatMoney(monthlyBudgetLeftCents)}",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("设置月预算后可查看剩余额度", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("收入 ${formatMoney(income)}", color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
                    Text("结余 ${formatMoney(income - expense)}", color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
                }
            }
            DonutChart(
                totals = expenseCategoryTotals.take(6),
                categories = categories,
                modifier = Modifier.size(110.dp),
                ringColor = Color.White.copy(alpha = 0.94f)
            )
        }
    }
}

@Composable
private fun HomeHeader(
    onSearch: () -> Unit,
    onChat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                shadowElevation = 4.dp
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_cashflow),
                    contentDescription = "CashFlow",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("CashFlow", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("AI Money Ledger", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "搜索")
            }
            IconButton(onClick = onChat) {
                Icon(Icons.Filled.ChatBubble, contentDescription = "AI 对话")
            }
        }
    }
}

@Composable
private fun MonthSelectorLabel(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(1.dp))
    }
}

@Composable
private fun YearMonthPickerDialog(
    title: String,
    selectedMonth: Long,
    entries: List<LedgerEntryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var selectedYear by remember(selectedMonth) { mutableStateOf(uiYearOf(selectedMonth)) }
    var selectedMonthNumber by remember(selectedMonth) { mutableStateOf(uiMonthOf(selectedMonth)) }
    val yearOptions = remember(entries, selectedMonth) {
        buildYearPickerOptions(entries, selectedMonth)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth().height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PickerColumn(
                    title = "年份",
                    options = yearOptions,
                    selected = selectedYear,
                    label = { "${it}年" },
                    onSelect = { selectedYear = it },
                    modifier = Modifier.weight(1f)
                )
                PickerColumn(
                    title = "月份",
                    options = (1..12).toList(),
                    selected = selectedMonthNumber,
                    label = { "${it}月" },
                    onSelect = { selectedMonthNumber = it },
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(uiMonthStartMillis(selectedYear, selectedMonthNumber))
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun PickerColumn(
    title: String,
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options, key = { "$title-$it" }) { option ->
                val isSelected = selected == option
                Surface(
                    onClick = { onSelect(option) },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            label(option),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetOverviewCard(
    monthlyBudgetCents: Long,
    monthlyBudgetLeftCents: Long,
    monthlyBudgetUsage: Float,
    onOpenBudget: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("预算进度", fontWeight = FontWeight.SemiBold)
                Text(
                    if (monthlyBudgetCents > 0L) formatMoney(monthlyBudgetLeftCents) else "未设置",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("预算 ${formatMoney(monthlyBudgetCents)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(monthlyBudgetUsage * 100).toInt().coerceAtMost(100)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(modifier = Modifier.fillMaxWidth().height(7.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(monthlyBudgetUsage.coerceIn(0f, 1f))
                        .height(7.dp)
                        .background(
                            when {
                                monthlyBudgetUsage >= 1f -> Color(0xFFEA4335)
                                monthlyBudgetUsage >= 0.8f -> Color(0xFFFBBC04)
                                else -> CashFlowGreen
                            },
                            RoundedCornerShape(999.dp)
                        )
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenBudget) {
                    Text("预算管理")
                }
            }
        }
    }
}

@Composable
private fun DraftSummaryCard(
    state: MainUiState,
    onOpen: () -> Unit,
    onOpenQuickEntry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("待确认草稿", fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier.background(Color(0xFFFFF0EE), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(state.draftCount.toString(), color = Color(0xFFE85D5D), fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "本月识别 ${state.draftCount} 笔，合计 ${formatMoney(state.drafts.sumOf { it.amountCents })} 元",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "来自 微信支付 ${state.drafts.count { it.sourceAppName.contains("微信") }} 笔，支付宝 ${state.drafts.count { it.sourceAppName.contains("支付宝") }} 笔，银行卡 ${state.drafts.count { it.accountNameSnapshot.contains("银行") }} 笔",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text("查看草稿") }
                TextButton(onClick = onOpenQuickEntry) { Text("继续记账") }
            }
        }
    }
}

@Composable
private fun DraftReminderCard() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "自动识别结果先进入草稿，确认后才入账",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "统计仅使用已确认流水，OCR 失败也会保留可见草稿。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryPreviewCard(
    categoryTotals: List<CategoryTotal>,
    monthlyTotal: Long,
    categories: List<CategoryEntity>,
    onViewCharts: () -> Unit,
    onOpenCategory: (CategoryTotal) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title = "本月 Top 3 支出分类", action = "更多", onAction = onViewCharts)
        if (categoryTotals.isEmpty()) {
            EmptyState("确认流水后会显示本月消费最高的分类。")
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                categoryTotals.take(3).forEach { total ->
                    TopCategoryCard(
                        total = total,
                        monthlyTotal = monthlyTotal,
                        categories = categories,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenCategory(total) }
                    )
                }
                repeat(3 - categoryTotals.take(3).size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BillsScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenDetail: (LedgerEntryEntity) -> Unit
) {
    var selectedMonth by remember { mutableStateOf(monthStartMillis()) }
    var selectedDayKey by remember { mutableStateOf<String?>(null) }
    var selectedCategoryCode by remember { mutableStateOf<String?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val monthEntries = state.confirmed.filter { isSameMonth(it.occurredAt, selectedMonth) }
    val filteredEntries = monthEntries
        .filter { selectedCategoryCode == null || it.categoryCode == selectedCategoryCode }
        .filter { selectedDayKey == null || formatDateGroup(it.occurredAt) == selectedDayKey }
    val groupedConfirmed = filteredEntries.groupBy { formatDateGroup(it.occurredAt) }
    val income = filteredEntries
        .filter { it.transactionType == TransactionType.INCOME }
        .sumOf { it.amountCents }
    val monthExpense = filteredEntries
        .filter { it.transactionType == TransactionType.EXPENSE }
        .sumOf { it.amountCents }
    val activeFilterCount = listOfNotNull(
        selectedDayKey,
        selectedCategoryCode
    ).size
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item {
            ReferencePageHeader(
                title = "账单列表",
                subtitle = "查看所有账单，支持筛选和编辑",
                icon = Icons.AutoMirrored.Filled.ReceiptLong
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.52f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { showMonthPicker = true },
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(monthLabel(selectedMonth), fontWeight = FontWeight.SemiBold)
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            if (activeFilterCount > 0) "${activeFilterCount} 项筛选" else "全部账单",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (activeFilterCount > 0) CashFlowGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = if (activeFilterCount > 0) {
                            "已筛选 ${filteredEntries.size} / ${monthEntries.size} 笔账单"
                        } else {
                            "当前月份共 ${monthEntries.size} 笔账单"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile("支出", formatMoney(monthExpense), Color(0xFFE85D5D))
                        StatTile("收入", formatMoney(income), Color(0xFF2F8E4C))
                        StatTile("总笔数", filteredEntries.size.toString(), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        item {
            MonthlyCalendarCard(
                entries = state.confirmed,
                categories = state.categories,
                selectedMonth = selectedMonth,
                selectedDayKey = selectedDayKey,
                selectedCategoryCode = selectedCategoryCode,
                onPreviousMonth = {
                    selectedMonth = shiftMonth(selectedMonth, -1)
                    selectedDayKey = null
                },
                onNextMonth = {
                    selectedMonth = shiftMonth(selectedMonth, 1)
                    selectedDayKey = null
                },
                onSelectDay = { selectedDayKey = if (selectedDayKey == it) null else it },
                onSelectCategory = {
                    selectedCategoryCode = it
                    selectedDayKey = null
                }
            )
        }
        if (state.drafts.isNotEmpty()) {
            item { SectionTitle("待确认", "${state.drafts.size} 笔") }
            items(state.drafts, key = { it.id }) { entry ->
                TransactionRow(
                    entry = entry,
                    categories = state.categories,
                    showActions = true,
                    onConfirm = { viewModel.confirm(entry.id) },
                    onIgnore = { viewModel.ignore(entry.id) },
                    onDelete = { viewModel.delete(entry.id) },
                    onEdit = { onOpenDetail(entry) }
                )
            }
        }
        item {
            SectionTitle(
                title = selectedDayKey?.let { "${it} 账单" } ?: "${monthLabel(selectedMonth)} 账单",
                action = "${filteredEntries.size} 笔"
            )
        }
        groupedConfirmed.forEach { (dateLabel, entries) ->
            item(key = "date-$dateLabel") {
                DayHeader(dateLabel, entries)
            }
            items(entries, key = { it.id }) { entry ->
                TransactionRow(
                    entry = entry,
                    categories = state.categories,
                    showActions = true,
                    onDelete = { viewModel.delete(entry.id) },
                    onEdit = { onOpenDetail(entry) }
                )
            }
        }
        if (filteredEntries.isEmpty()) {
            item { EmptyState("当前月份或筛选条件下暂无账单。") }
        }
    }

    if (showMonthPicker) {
        YearMonthPickerDialog(
            title = "选择账单月份",
            selectedMonth = selectedMonth,
            entries = state.confirmed,
            onDismiss = { showMonthPicker = false },
            onConfirm = {
                selectedMonth = it
                selectedDayKey = null
                showMonthPicker = false
            }
        )
    }
}

@Composable
private fun BillsFilterCard(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    accounts: List<AccountEntity>,
    selectedAccountCode: String?,
    onSelectAccount: (String?) -> Unit,
    minAmountInput: String,
    maxAmountInput: String,
    onMinAmountChange: (String) -> Unit,
    onMaxAmountChange: (String) -> Unit,
    activeFilterCount: Int,
    onResetFilters: () -> Unit
) {
    val minAmountCents = minAmountInput.trim().toPositiveCentsOrNull()
    val maxAmountCents = maxAmountInput.trim().toPositiveCentsOrNull()
    val hasInvalidRange = minAmountCents != null && maxAmountCents != null && minAmountCents > maxAmountCents
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("搜索与筛选", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (activeFilterCount > 0) "已启用 $activeFilterCount 个条件" else "支持按关键词、账户、金额区间快速缩小范围",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (activeFilterCount > 0) {
                    TextButton(onClick = onResetFilters) { Text("重置") }
                }
            }
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索商户、备注、分类、账户") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("账户筛选", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AccountFilterChips(
                    accounts = accounts,
                    selectedCode = selectedAccountCode,
                    onSelect = onSelectAccount
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minAmountInput,
                    onValueChange = onMinAmountChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("最低金额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = maxAmountInput,
                    onValueChange = onMaxAmountChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("最高金额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            Text(
                text = if (hasInvalidRange) "最高金额需大于或等于最低金额" else "金额按元输入，例如 25 或 25.80",
                style = MaterialTheme.typography.bodySmall,
                color = if (hasInvalidRange) Color(0xFFB3261E) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OcrPreviewEntryCard(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("OCR 识别预览", fontWeight = FontWeight.SemiBold)
                    Text("选择支付截图，识别结果会进入预览队列。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CashFlowGreen.copy(alpha = 0.12f)
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = CashFlowGreen, modifier = Modifier.padding(8.dp))
                }
            }
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("OCR 识别")
            }
        }
    }
}

@Composable
private fun AccountFilterChips(
    accounts: List<AccountEntity>,
    selectedCode: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AccountFilterChip(
            label = "全部账户",
            selected = selectedCode == null,
            onClick = { onSelect(null) }
        )
        accounts.forEach { account ->
            AccountFilterChip(
                label = account.name,
                selected = selectedCode == account.code,
                onClick = { onSelect(account.code) },
                account = account
            )
        }
    }
}

@Composable
private fun AccountFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    account: AccountEntity? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) CashFlowGreen else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (account != null) {
                AccountIconImage(
                    account = account,
                    size = 18.dp,
                    fallbackStyle = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }

}

@Composable
private fun StatTile(title: String, value: String, valueColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun DayHeader(dateLabel: String, entries: List<LedgerEntryEntity>) {
    val expense = entries
        .filter { it.transactionType == TransactionType.EXPENSE }
        .sumOf { it.amountCents }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(dateLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("日支出 ${formatMoney(expense)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthlyCalendarCard(
    entries: List<LedgerEntryEntity>,
    categories: List<CategoryEntity>,
    selectedMonth: Long,
    selectedDayKey: String?,
    selectedCategoryCode: String?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (String) -> Unit,
    onSelectCategory: (String?) -> Unit
) {
    val monthEntries = entries.filter { isSameMonth(it.occurredAt, selectedMonth) }
    val filteredMonthEntries = monthEntries.filter { selectedCategoryCode == null || it.categoryCode == selectedCategoryCode }
    val dailyExpense = filteredMonthEntries
        .filter { it.transactionType == TransactionType.EXPENSE }
        .groupBy { formatDateGroup(it.occurredAt) }
        .mapValues { (_, dayEntries) -> dayEntries.sumOf { it.amountCents } }
    val maxDailyExpense = dailyExpense.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val monthCategories = monthEntries
        .filter { it.transactionType == TransactionType.EXPENSE }
        .mapNotNull { it.categoryCode }
        .distinct()
    val categoryOptions = categories.filter { category -> !category.isIncome && category.code in monthCategories }
    val cells = remember(selectedMonth) { monthCalendarCells(selectedMonth) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) { Text("‹", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                Text(monthLabel(selectedMonth), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) { Text("›", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { cell ->
                        if (cell == null) {
                            Spacer(modifier = Modifier.weight(1f).height(48.dp))
                        } else {
                            val dayKey = cell.dateKey
                            val amount = dailyExpense[dayKey] ?: 0L
                            CalendarDayCell(
                                day = cell.dayOfMonth,
                                amountCents = amount,
                                selected = selectedDayKey == dayKey,
                                intensity = amount.toFloat() / maxDailyExpense.toFloat(),
                                onClick = { onSelectDay(dayKey) },
                                modifier = Modifier.weight(1f).height(48.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    CalendarCategoryChips(
        categories = categoryOptions,
        selectedCode = selectedCategoryCode,
        onSelect = onSelectCategory
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarCategoryChips(
    categories: List<CategoryEntity>,
    selectedCode: String?,
    onSelect: (String?) -> Unit
) {
    val chips = listOf<CategoryEntity?>(null) + categories.take(7)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { category ->
            val selected = category?.code == selectedCode || (category == null && selectedCode == null)
            Surface(
                onClick = { onSelect(category?.code) },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = category?.name ?: "全部",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDayCell(
    day: Int,
    amountCents: Long,
    selected: Boolean,
    intensity: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasExpense = amountCents > 0L
    val baseColor = when {
        selected -> Color(0xFF2F8E4C).copy(alpha = 0.14f)
        hasExpense -> Color(0xFFEA4335).copy(alpha = 0.05f + 0.18f * intensity.coerceIn(0f, 1f))
        else -> Color.Transparent
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = baseColor
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(18.dp).background(if (selected) Color(0xFF2F8E4C) else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    day.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                if (hasExpense) formatCompactAmount(amountCents) else "",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (hasExpense) FontWeight.SemiBold else FontWeight.Normal,
                color = if (hasExpense) Color(0xFF6F6F6F) else Color.Transparent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChartsScreen(state: MainUiState) {
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var selectedMonth by remember { mutableStateOf(monthStartMillis()) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val isIncomeSelected = selectedType == TransactionType.INCOME
    val monthEntries = remember(state.confirmed, selectedMonth) {
        state.confirmed.filter { isSameMonth(it.occurredAt, selectedMonth) }
    }
    val chartTotals = remember(monthEntries, state.categories, selectedType) {
        buildCategoryTotalsForMonth(monthEntries, state.categories, selectedType).take(8)
    }
    val chartTotalCents = chartTotals.sumOf { it.amountCents }
    val recentMonths = remember(state.monthlySummaries, selectedMonth) {
        recentMonthlySummariesUntil(state.monthlySummaries, selectedMonth, 6)
    }
    val previousMonthCents = remember(state.monthlySummaries, monthEntries, selectedType, selectedMonth) {
        monthTotalFromSummariesOrEntries(
            summaries = state.monthlySummaries,
            entries = state.confirmed,
            selectedMonth = shiftMonth(selectedMonth, -1),
            type = selectedType
        )
    }
    val accountStats = remember(state.confirmed, state.accounts, selectedMonth) {
        buildAccountStatsForMonth(state.confirmed, state.accounts, selectedMonth)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            ReferencePageHeader(
                title = "图表分析",
                subtitle = "多维度图表分析，洞察消费趋势",
                icon = Icons.Filled.Analytics
            )
        }
        item {
            ChartMonthSelector(
                label = monthLabel(selectedMonth),
                onPrevious = { selectedMonth = shiftMonth(selectedMonth, -1) },
                onNext = { selectedMonth = shiftMonth(selectedMonth, 1) },
                onOpenPicker = { showMonthPicker = true }
            )
        }
        item {
            TypeSegmentedControl(selectedType = selectedType, onSelect = { selectedType = it })
        }
        item {
            ChartSummaryCard(
                title = if (isIncomeSelected) "本月收入" else "本月支出",
                amountCents = chartTotalCents,
                deltaText = chartMonthDeltaText(
                    current = chartTotalCents,
                    previous = previousMonthCents
                ),
                deltaColor = chartDeltaColor(
                    current = chartTotalCents,
                    previous = previousMonthCents,
                    isIncome = isIncomeSelected
                ),
                monthLabel = monthLabel(selectedMonth)
            )
        }
        item {
            ChartBreakdownCard(
                totals = chartTotals,
                totalCents = chartTotalCents,
                isIncome = isIncomeSelected,
                categories = state.categories
            )
        }
        item {
            MonthlyComparisonTrendCard(
                items = recentMonths,
                selectedType = selectedType
            )
        }
        item {
            AccountStatisticsCard(accountStats = accountStats)
        }
    }

    if (showMonthPicker) {
        YearMonthPickerDialog(
            title = "选择图表月份",
            selectedMonth = selectedMonth,
            entries = state.confirmed,
            onDismiss = { showMonthPicker = false },
            onConfirm = {
                selectedMonth = it
                showMonthPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onNotificationChange: (Boolean) -> Unit,
    onScreenshotChange: (Boolean) -> Unit,
    onRequestAuth: () -> Unit,
    onLogout: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SettingsGroupTitle("功能设置")
        }
        item {
            SettingSwitchGroup(
                state = state,
                onNotificationChange = onNotificationChange,
                onScreenshotChange = onScreenshotChange,
                onAutoCategoryChange = viewModel::toggleAutoCategory
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsClickableRow(
                        title = "通知监听权限",
                        subtitle = "前往系统设置开启通知监听服务",
                        value = "去开启",
                        onClick = onOpenNotificationSettings
                    )
                    ThinDivider()
                    SettingsClickableRow(
                        title = "应用设置",
                        subtitle = "检查截图、通知和存储权限",
                        value = "查看",
                        onClick = onOpenAppSettings
                    )
                }
            }
        }
        item {
            SettingsGroupTitle("其他设置")
        }
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsValueRow("版本号", appVersionName(), showChevron = false)
                    ThinDivider()
                    SettingsValueRow("数据模式", "本地优先", showChevron = false)
                }
            }
        }
    }
}

@Composable
private fun AppPanelHost(
    panel: AppPanel,
    state: MainUiState,
    viewModel: MainViewModel,
    selectedEntry: LedgerEntryEntity?,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onOpenPanel: (AppPanel) -> Unit,
    onOpenEntry: (LedgerEntryEntity) -> Unit,
    onEditEntry: (LedgerEntryEntity) -> Unit,
    onRequestAuth: () -> Unit,
    onLogout: () -> Unit,
    onNotificationChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRequestScreenshotMonitoringEnable: () -> Unit,
    onRequestScreenshotMonitoringDisable: () -> Unit
) {
    if (panel == AppPanel.Chat) {
        ChatScreen(
            isLoggedIn = isLoggedIn,
            onBack = onBack,
            onRequestAuth = onRequestAuth
        )
        return
    }

    val meta = panelMeta(panel)
    PanelChrome(
        title = meta.title,
        subtitle = meta.subtitle,
        icon = meta.icon,
        onBack = onBack
    ) {
        when (panel) {
            AppPanel.BillDetail,
            AppPanel.DraftDetail -> EntryDetailScreen(
                entry = selectedEntry,
                state = state,
                viewModel = viewModel,
                onBack = onBack,
                onEdit = onEditEntry
            )

            AppPanel.Budget -> BudgetManagementScreen(state = state, viewModel = viewModel)
            AppPanel.DraftReview -> DraftReviewScreen(state = state, viewModel = viewModel, onOpenEntry = onOpenEntry)

            AppPanel.Account -> AccountManagementScreen(state = state, viewModel = viewModel)
            AppPanel.Category -> CategoryManagementScreen(state = state, viewModel = viewModel)
            AppPanel.Search -> SearchScreenPanel(state = state, onOpenEntry = onOpenEntry)
            AppPanel.OcrPreview -> OcrPreviewScreen(state = state, viewModel = viewModel, onOpenEntry = onOpenEntry)
            AppPanel.Sync -> SyncScreenPanelV2(isLoggedIn = isLoggedIn, onRequestAuth = onRequestAuth)
            AppPanel.PersonalInfo,
            AppPanel.Profile -> PersonalInfoScreen(
                state = state,
                isLoggedIn = isLoggedIn,
                onRequestAuth = onRequestAuth,
                onLogout = onLogout
            )
            AppPanel.Settings -> SettingsScreen(
                state = state,
                viewModel = viewModel,
                onScreenshotChange = { enabled ->
                    if (enabled) onRequestScreenshotMonitoringEnable() else onRequestScreenshotMonitoringDisable()
                },
                onRequestAuth = onRequestAuth,
                onLogout = onLogout,
                onNotificationChange = onNotificationChange,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenAppSettings = onOpenAppSettings
            )
            AppPanel.Help -> HelpScreen()
            AppPanel.About -> AboutScreen()
            AppPanel.Chat -> error("Chat panel should be handled separately")
        }
    }
}

private data class PanelMeta(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun panelMeta(panel: AppPanel): PanelMeta = when (panel) {
    AppPanel.BillDetail -> PanelMeta("账单详情", "查看并修改单笔流水", Icons.AutoMirrored.Filled.ReceiptLong)
    AppPanel.Budget -> PanelMeta("预算管理", "总预算、分类预算和提醒", Icons.Filled.Analytics)
    AppPanel.DraftReview -> PanelMeta("草稿审核", "逐条确认自动识别结果", Icons.Filled.Notifications)
    AppPanel.DraftDetail -> PanelMeta("草稿详情", "复核 OCR 与通知识别结果", Icons.Filled.Edit)
    AppPanel.Chat -> PanelMeta("AI 对话", "自然语言记账和查询", Icons.Filled.ChatBubble)
    AppPanel.Account -> PanelMeta("账户管理", "管理支付账户和余额", Icons.Filled.Home)
    AppPanel.Category -> PanelMeta("分类管理", "维护收支分类和颜色", Icons.Filled.Palette)
    AppPanel.Search -> PanelMeta("搜索筛选", "跨账单搜索和组合筛选", Icons.Filled.Search)
    AppPanel.OcrPreview -> PanelMeta("识别预览", "查看截图识别草稿", Icons.Filled.Image)
    AppPanel.Sync -> PanelMeta("数据同步", "账号同步与状态检查", Icons.Filled.Refresh)
    AppPanel.PersonalInfo -> PanelMeta("个人信息", "登录状态、账号资料和本地模式", Icons.Filled.AccountCircle)
    AppPanel.Settings -> PanelMeta("设置", "功能设置和其他设置", Icons.Filled.Settings)
    AppPanel.Help -> PanelMeta("帮助与反馈", "常见问题和意见反馈", Icons.Filled.Info)
    AppPanel.About -> PanelMeta("关于 CashFlow", "版本信息与产品说明", Icons.Filled.Info)
    AppPanel.Profile -> PanelMeta("个人信息", "登录状态、账号资料和本地模式", Icons.Filled.AccountCircle)
}

@Composable
private fun PanelChrome(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Box(modifier = Modifier.weight(1f)) {
                    ReferencePageHeader(title = title, subtitle = subtitle, icon = icon)
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun EntryDetailScreen(
    entry: LedgerEntryEntity?,
    state: MainUiState,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onEdit: (LedgerEntryEntity) -> Unit
) {
    if (entry == null) {
        EmptyState("这条记录已不存在。")
        return
    }
    val category = state.categories.firstOrNull { it.code == entry.categoryCode }
    val color = category?.colorHex?.toColor() ?: CashFlowGreen
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = color),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(category?.name ?: entry.categoryNameSnapshot ?: "未分类", color = Color.White.copy(alpha = 0.86f))
                    Text(
                        text = (if (entry.transactionType == TransactionType.EXPENSE) "-" else "+") + formatMoney(entry.amountCents),
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(formatDateGroup(entry.occurredAt) + " " + formatDayTime(entry.occurredAt), color = Color.White.copy(alpha = 0.82f))
                }
            }
        }
        item {
            DetailInfoCard(entry)
        }
        if (entry.rawText.isNotBlank() || entry.imageUri != null) {
            item {
                OcrInfoCard(entry)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onEdit(entry) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("编辑")
                }
                if (entry.reviewState == ReviewState.DRAFT) {
                    Button(
                        onClick = {
                            viewModel.confirm(entry.id)
                            onBack()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("确认")
                    }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (entry.reviewState == ReviewState.DRAFT) {
                    TextButton(
                        onClick = {
                            viewModel.ignore(entry.id)
                            onBack()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("忽略")
                    }
                }
                TextButton(
                    onClick = {
                        viewModel.delete(entry.id)
                        onBack()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                if (entry.imageUri != null) {
                    TextButton(onClick = { viewModel.retryOcr(entry) }, modifier = Modifier.weight(1f)) {
                        Text("重识别")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoCard(entry: LedgerEntryEntity) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailLine("状态", if (entry.reviewState == ReviewState.DRAFT) "待确认" else "已确认")
            DetailLine("账户", entry.accountNameSnapshot.ifBlank { "现金" })
            DetailLine("商户", entry.merchant.ifBlank { "未填写" })
            DetailLine("备注", entry.note.ifBlank { "无" })
            DetailLine("来源", entry.sourceAppName.ifBlank { entry.sourceType.name })
            DetailLine("识别状态", entry.ingestionState.name)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OcrInfoCard(entry: LedgerEntryEntity) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("识别凭证", fontWeight = FontWeight.SemiBold)
            entry.imageUri?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (entry.rawText.isNotBlank()) {
                Text(entry.rawText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DraftReviewScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenEntry: (LedgerEntryEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("待确认草稿", "${state.drafts.size} 笔") }
        items(state.drafts, key = { it.id }) { entry ->
            TransactionRow(
                entry = entry,
                categories = state.categories,
                showActions = true,
                onConfirm = { viewModel.confirm(entry.id) },
                onIgnore = { viewModel.ignore(entry.id) },
                onDelete = { viewModel.delete(entry.id) },
                onEdit = { onOpenEntry(entry) }
            )
        }
        if (state.drafts.isEmpty()) {
            item { EmptyState("所有草稿已处理完毕。") }
        }
    }
}

@Composable
private fun BudgetManagementScreen(state: MainUiState, viewModel: MainViewModel) {
    val expenseCategories = state.categories.filter { !it.isIncome }
    var monthlyBudgetInput by remember(state.monthlyBudgetCents) { mutableStateOf(centsToInput(state.monthlyBudgetCents)) }
    var budgetEditing by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            BudgetCompactCard(
                monthlyBudgetInput = monthlyBudgetInput,
                monthlyBudgetCents = state.monthlyBudgetCents,
                categoryBudgetCount = expenseCategories.count { (it.monthlyBudgetCents ?: 0L) > 0L },
                budgetAlertEnabled = state.budgetAlertEnabled,
                editing = budgetEditing,
                onInputChange = { monthlyBudgetInput = it.filter { char -> char.isDigit() || char == '.' }.take(10) },
                onEditChange = { budgetEditing = it },
                onBudgetAlertChange = viewModel::toggleBudgetAlert,
                onSave = {
                    viewModel.setMonthlyBudget(monthlyBudgetInput.toCentsOrNull() ?: 0L)
                    budgetEditing = false
                }
            )
        }
        item { SectionTitle("分类预算", "${expenseCategories.count { (it.monthlyBudgetCents ?: 0L) > 0L }} 项") }
        items(expenseCategories, key = { it.code }) { category ->
            CategoryBudgetRow(category = category) { cents -> viewModel.setCategoryBudget(category.code, cents) }
        }
    }
}

@Composable
private fun AccountManagementScreen(state: MainUiState, viewModel: MainViewModel) {
    var newAccountName by remember { mutableStateOf("") }
    var newAccountColor by remember { mutableStateOf(CategoryColorOptions[4]) }
    var newAccountIcon by remember { mutableStateOf(DefaultAccountIconKey) }
    var accountAdding by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AccountManageCard(
                accounts = state.accounts,
                confirmedEntries = state.confirmed,
                newAccountName = newAccountName,
                newAccountColor = newAccountColor,
                newAccountIcon = newAccountIcon,
                adding = accountAdding,
                onNameChange = { newAccountName = it },
                onColorChange = { newAccountColor = it },
                onIconChange = { newAccountIcon = it },
                onAddingChange = { accountAdding = it },
                onAdd = {
                    viewModel.saveAccount(newAccountName, colorHex = newAccountColor, iconKey = newAccountIcon)
                    newAccountName = ""
                    newAccountColor = CategoryColorOptions[4]
                    newAccountIcon = DefaultAccountIconKey
                    accountAdding = false
                },
                onSaveAccount = viewModel::updateAccount,
                onMoveAccount = viewModel::moveAccount,
                onDelete = viewModel::removeAccount
            )
        }
    }
}

@Composable
private fun CategoryManagementScreen(state: MainUiState, viewModel: MainViewModel) {
    val expenseCategories = state.categories.filter { !it.isIncome }
    val incomeCategories = state.categories.filter { it.isIncome }
    var showingIncomeCategories by remember { mutableStateOf(false) }
    var newExpenseCategoryName by remember { mutableStateOf("") }
    var newIncomeCategoryName by remember { mutableStateOf("") }
    var newExpenseCategoryColor by remember { mutableStateOf(CategoryColorOptions.first()) }
    var newIncomeCategoryColor by remember { mutableStateOf(CategoryColorOptions.first()) }
    var newExpenseCategoryIcon by remember { mutableStateOf(DefaultCategoryIconKey) }
    var newIncomeCategoryIcon by remember { mutableStateOf(DefaultIncomeCategoryIconKey) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            CategoryManageCard(
                title = if (showingIncomeCategories) "收入分类" else "支出分类",
                categories = if (showingIncomeCategories) incomeCategories else expenseCategories,
                newCategoryName = if (showingIncomeCategories) newIncomeCategoryName else newExpenseCategoryName,
                newCategoryColor = if (showingIncomeCategories) newIncomeCategoryColor else newExpenseCategoryColor,
                newCategoryIcon = if (showingIncomeCategories) newIncomeCategoryIcon else newExpenseCategoryIcon,
                selectedSecondary = showingIncomeCategories,
                onPrimaryClick = { showingIncomeCategories = false },
                onSecondaryClick = { showingIncomeCategories = true },
                primaryTitle = "支出分类",
                secondaryTitle = "收入分类",
                onNameChange = {
                    if (showingIncomeCategories) newIncomeCategoryName = it else newExpenseCategoryName = it
                },
                onColorChange = {
                    if (showingIncomeCategories) newIncomeCategoryColor = it else newExpenseCategoryColor = it
                },
                onIconChange = {
                    if (showingIncomeCategories) newIncomeCategoryIcon = it else newExpenseCategoryIcon = it
                },
                onAdd = {
                    if (showingIncomeCategories) {
                        viewModel.saveCategory(
                            newIncomeCategoryName,
                            isIncome = true,
                            colorHex = newIncomeCategoryColor,
                            iconKey = newIncomeCategoryIcon
                        )
                        newIncomeCategoryName = ""
                        newIncomeCategoryColor = CategoryColorOptions.first()
                        newIncomeCategoryIcon = DefaultIncomeCategoryIconKey
                    } else {
                        viewModel.saveCategory(
                            newExpenseCategoryName,
                            isIncome = false,
                            colorHex = newExpenseCategoryColor,
                            iconKey = newExpenseCategoryIcon
                        )
                        newExpenseCategoryName = ""
                        newExpenseCategoryColor = CategoryColorOptions.first()
                        newExpenseCategoryIcon = DefaultCategoryIconKey
                    }
                },
                onSaveCategory = viewModel::updateCategory,
                onMoveCategory = viewModel::moveCategory,
                onMoveCategoryToIndex = viewModel::moveCategoryToIndex,
                onDelete = viewModel::removeCategory
            )
        }
    }
}

@Composable
private fun SearchScreenPanel(state: MainUiState, onOpenEntry: (LedgerEntryEntity) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var selectedAccountCode by remember { mutableStateOf<String?>(null) }
    var minAmountInput by remember { mutableStateOf("") }
    var maxAmountInput by remember { mutableStateOf("") }
    val query = keyword.trim()
    val minAmountCents = minAmountInput.trim().toPositiveCentsOrNull()
    val maxAmountCents = maxAmountInput.trim().toPositiveCentsOrNull()
    val results = (state.drafts + state.confirmed)
        .filter { query.isBlank() || it.matchesBillsKeyword(query) }
        .filter { selectedAccountCode == null || it.accountCode == selectedAccountCode }
        .filter { minAmountCents == null || it.amountCents >= minAmountCents }
        .filter { maxAmountCents == null || it.amountCents <= maxAmountCents }
        .sortedByDescending { it.occurredAt }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            BillsFilterCard(
                keyword = keyword,
                onKeywordChange = { keyword = it },
                accounts = state.accounts,
                selectedAccountCode = selectedAccountCode,
                onSelectAccount = { selectedAccountCode = it },
                minAmountInput = minAmountInput,
                maxAmountInput = maxAmountInput,
                onMinAmountChange = { minAmountInput = it },
                onMaxAmountChange = { maxAmountInput = it },
                activeFilterCount = listOfNotNull(query.takeIf { it.isNotBlank() }, selectedAccountCode, minAmountCents, maxAmountCents).size,
                onResetFilters = {
                    keyword = ""
                    selectedAccountCode = null
                    minAmountInput = ""
                    maxAmountInput = ""
                }
            )
        }
        item { SectionTitle("搜索结果", "${results.size} 条") }
        items(results, key = { it.id }) { entry ->
            TransactionRow(
                entry = entry,
                categories = state.categories,
                showActions = true,
                onEdit = { onOpenEntry(entry) }
            )
        }
        if (results.isEmpty()) {
            item { EmptyState("没有找到匹配的账单。") }
        }
    }
}

@Composable
private fun OcrPreviewScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenEntry: (LedgerEntryEntity) -> Unit
) {
    val ocrDrafts = state.drafts
        .filter { it.imageUri != null || it.ingestionState == IngestionState.OCR_PENDING || it.ingestionState == IngestionState.OCR_FAILED }
        .sortedByDescending { it.updatedAt }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("截图识别队列", fontWeight = FontWeight.Bold)
                    Text("共 ${ocrDrafts.size} 条来自截图或相册导入的待处理结果。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(ocrDrafts, key = { it.id }) { entry ->
            TransactionRow(
                entry = entry,
                categories = state.categories,
                showActions = true,
                onConfirm = { viewModel.confirm(entry.id) },
                onIgnore = { viewModel.ignore(entry.id) },
                onDelete = { viewModel.delete(entry.id) },
                onEdit = { onOpenEntry(entry) }
            )
        }
        if (ocrDrafts.isEmpty()) {
            item { EmptyState("暂无截图识别草稿。") }
        }
    }
}

@Composable
private fun OcrDiagnosticsScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenEntry: (LedgerEntryEntity) -> Unit
) {
    var diagnosticText by remember { mutableStateOf("") }
    val normalizedDiagnosticText = diagnosticText.trim()
    val diagnosticEntries = remember(normalizedDiagnosticText, state.categories) {
        if (normalizedDiagnosticText.isBlank()) {
            emptyList()
        } else {
            PaymentParser.parseTextEntries(
                text = normalizedDiagnosticText,
                categories = state.categories,
                allowSingleListEntry = true
            )
        }
    }
    val ocrDrafts = state.drafts
        .filter { it.imageUri != null || it.ingestionState == IngestionState.OCR_PENDING || it.ingestionState == IngestionState.OCR_FAILED }
        .sortedByDescending { it.updatedAt }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OcrDiagnosticSummaryCard(
                total = ocrDrafts.size,
                pending = ocrDrafts.count { it.ingestionState == IngestionState.OCR_PENDING },
                done = ocrDrafts.count { it.ingestionState == IngestionState.OCR_DONE },
                failed = ocrDrafts.count { it.ingestionState == IngestionState.OCR_FAILED }
            )
        }
        item {
            OcrTextDiagnosticCard(
                text = diagnosticText,
                parsedEntries = diagnosticEntries,
                categories = state.categories,
                onTextChange = { diagnosticText = it.take(5000) },
                onClear = { diagnosticText = "" }
            )
        }
        item { SectionTitle("识别草稿队列", "${ocrDrafts.size} 条") }
        items(ocrDrafts, key = { it.id }) { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OcrDraftDiagnosticCard(entry)
                TransactionRow(
                    entry = entry,
                    categories = state.categories,
                    showActions = true,
                    onConfirm = { viewModel.confirm(entry.id) },
                    onIgnore = { viewModel.ignore(entry.id) },
                    onDelete = { viewModel.delete(entry.id) },
                    onEdit = { onOpenEntry(entry) }
                )
            }
        }
        if (ocrDrafts.isEmpty()) {
            item { EmptyState("暂无截图识别草稿。导入支付截图后，会在这里看到识别状态和诊断信息。") }
        }
    }
}

@Composable
private fun OcrDiagnosticSummaryCard(
    total: Int,
    pending: Int,
    done: Int,
    failed: Int
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("OCR 识别诊断", fontWeight = FontWeight.Bold)
            Text(
                "用于真机验证截图监听、OCR 原文解析和多笔拆分结果。所有自动来源仍只生成待确认草稿。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile("草稿", total.toString(), CashFlowGreen)
                StatTile("待识别", pending.toString(), Color(0xFFF59E0B))
                StatTile("已完成", done.toString(), Color(0xFF16834A))
                StatTile("失败", failed.toString(), MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun OcrTextDiagnosticCard(
    text: String,
    parsedEntries: List<com.codex.suishouledger.domain.ParsedDraft>,
    categories: List<CategoryEntity>,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val hasInput = text.trim().isNotBlank()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("原文解析测试", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onClear, enabled = hasInput) {
                    Text("清空")
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                placeholder = { Text("粘贴 Huawei OCR 原文或截图识别文本") },
                minLines = 5
            )
            if (hasInput) {
                Text(
                    "解析出 ${parsedEntries.size} 条候选；金额、商户、分类、时间和指纹如下。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                parsedEntries.forEachIndexed { index, draft ->
                    ParsedDraftDiagnosticRow(
                        index = index + 1,
                        draft = draft,
                        rawText = text,
                        categories = categories
                    )
                }
            } else {
                Text(
                    "真机测试时可先导入截图生成草稿，也可以直接把 OCR 原文粘到这里复盘解析规则。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ParsedDraftDiagnosticRow(
    index: Int,
    draft: com.codex.suishouledger.domain.ParsedDraft,
    rawText: String,
    categories: List<CategoryEntity>
) {
    val categoryColor = categoryColor(draft.categoryCode.orEmpty(), categories)
    val fingerprint = PaymentParser.buildOcrFingerprint(
        rawText = draft.normalizedText.ifBlank { rawText },
        amountCents = draft.amountCents ?: 0L,
        merchant = draft.merchant
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("候选 $index", fontWeight = FontWeight.SemiBold)
                Text(formatConfidence(draft.confidence), style = MaterialTheme.typography.labelMedium, color = CashFlowGreen)
            }
            DetailLine("金额", draft.amountCents?.let { formatMoney(it) } ?: "未识别")
            DetailLine("商户", draft.merchant.ifBlank { "未识别" })
            DetailLine("分类", draft.categoryName ?: draft.categoryCode ?: "未分类")
            DetailLine("类型", transactionTypeLabel(draft.transactionType))
            DetailLine("时间", draft.occurredAt?.let { formatDateGroup(it) + " " + formatDayTime(it) } ?: "未识别")
            DetailLine("指纹", fingerprint)
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp).background(categoryColor.copy(alpha = 0.32f), RoundedCornerShape(999.dp))
            )
        }
    }
}

@Composable
private fun OcrDraftDiagnosticCard(entry: LedgerEntryEntity) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("诊断信息", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Text(entry.ingestionState.name, style = MaterialTheme.typography.labelSmall, color = ingestionStateColor(entry.ingestionState))
            }
            DetailLine("来源", entry.sourceAppName.ifBlank { entry.sourceType.name })
            DetailLine("置信度", formatConfidence(entry.confidence))
            DetailLine("指纹", entry.sourceFingerprint.ifBlank { "无" })
            DetailLine("图片", entry.imageUri ?: "无")
            if (entry.rawText.isNotBlank()) {
                DetailLine("原文长度", "${entry.rawText.length} 字符")
            }
        }
    }
}

@Composable
private fun SyncScreenPanel(isLoggedIn: Boolean, onRequestAuth: () -> Unit) {
    val scope = rememberCoroutineScope()
    val email by ServiceLocator.authTokenProvider.emailFlow.collectAsState(initial = null)
    val lastSyncTimestamp by ServiceLocator.settings.lastSyncTimestampMillis.collectAsState(initial = 0L)
    val persistedStatus by ServiceLocator.settings.lastSyncStatus.collectAsState(initial = "等待同步")
    val persistedError by ServiceLocator.settings.lastSyncError.collectAsState(initial = "")
    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("等待同步") }

    LaunchedEffect(Unit) {
        message = ""
    }

    if (!isLoggedIn) {
        AuthRequiredPanel(
            title = "登录后启用云同步",
            subtitle = "本地账单会优先写入手机，登录后可手动上传和下载远程变更。",
            onRequestAuth = onRequestAuth
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("同步状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(email.orEmpty().ifBlank { "已登录账号" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(message, color = if (message.contains("失败")) MaterialTheme.colorScheme.error else CashFlowGreen)
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val statusText = message.ifBlank {
                        if (persistedError.isBlank()) persistedStatus else "$persistedStatus：$persistedError"
                    }
                    val lastSyncText = if (lastSyncTimestamp > 0L) {
                        "${formatDateGroup(lastSyncTimestamp)} ${formatDayTime(lastSyncTimestamp)}"
                    } else {
                        "尚未同步"
                    }
                    Text("同步详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("最近同步：$lastSyncText", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        statusText,
                        color = if (statusText.contains("失败") || persistedError.isNotBlank()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            CashFlowGreen
                        }
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    syncing = true
                    message = "同步中"
                    scope.launch {
                        val result = ServiceLocator.syncManager.syncNow()
                        message = result.fold(
                            onSuccess = { "同步完成：上传 ${it.uploaded} 条，下载 ${it.downloaded} 条" },
                            onFailure = { "同步失败：${syncUserMessage(it)}" }
                        )
                        syncing = false
                    }
                },
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (syncing) "同步中" else "立即同步")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        syncing = true
                        scope.launch {
                            val result = ServiceLocator.syncManager.uploadLocalChanges()
                            message = result.fold(
                                onSuccess = { "上传完成：$it 条变更" },
                                onFailure = { "上传失败：${it.localizedMessage ?: "未知错误"}" }
                            )
                            syncing = false
                        }
                    },
                    enabled = !syncing,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("上传本地")
                }
                Button(
                    onClick = {
                        syncing = true
                        scope.launch {
                            val result = ServiceLocator.syncManager.downloadRemoteChanges()
                            message = result.fold(
                                onSuccess = { "下载完成：$it 条变更" },
                                onFailure = { "下载失败：${it.localizedMessage ?: "未知错误"}" }
                            )
                            syncing = false
                        }
                    },
                    enabled = !syncing,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("下载远程")
                }
            }
        }
    }
}

@Composable
private fun SyncScreenPanelV2(isLoggedIn: Boolean, onRequestAuth: () -> Unit) {
    val scope = rememberCoroutineScope()
    val email by ServiceLocator.authTokenProvider.emailFlow.collectAsState(initial = null)
    val lastSyncTimestamp by ServiceLocator.settings.lastSyncTimestampMillis.collectAsState(initial = 0L)
    val persistedStatus by ServiceLocator.settings.lastSyncStatus.collectAsState(initial = "等待同步")
    val persistedError by ServiceLocator.settings.lastSyncError.collectAsState(initial = "")
    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    if (!isLoggedIn) {
        AuthRequiredPanel(
            title = "登录后启用云同步",
            subtitle = "本地账单会优先写入手机，登录后可上传和下载远程变更。",
            onRequestAuth = onRequestAuth
        )
        return
    }

    val statusText = message.ifBlank {
        if (persistedError.isBlank()) persistedStatus else "$persistedStatus：${syncUserMessageText(persistedError)}"
    }
    val isError = statusText.contains("失败") || persistedError.isNotBlank()
    val lastSyncText = if (lastSyncTimestamp > 0L) {
        "${formatDateGroup(lastSyncTimestamp)} ${formatDayTime(lastSyncTimestamp)}"
    } else {
        "尚未同步"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("同步状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(email.orEmpty().ifBlank { "已登录账号" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("最近同步：$lastSyncText", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        statusText.ifBlank { "等待同步" },
                        color = if (isError) MaterialTheme.colorScheme.error else CashFlowGreen
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    syncing = true
                    message = "同步中"
                    scope.launch {
                        val result = ServiceLocator.syncManager.syncNow()
                        message = result.fold(
                            onSuccess = { "同步完成：上传 ${it.uploaded} 条，下载 ${it.downloaded} 条" },
                            onFailure = { "同步失败：${syncUserMessage(it)}" }
                        )
                        syncing = false
                    }
                },
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (syncing) "同步中" else "立即同步")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        syncing = true
                        message = "上传中"
                        scope.launch {
                            val result = ServiceLocator.syncManager.uploadLocalChanges()
                            message = result.fold(
                                onSuccess = { "上传完成：$it 条变更" },
                                onFailure = { "上传失败：${syncUserMessage(it)}" }
                            )
                            syncing = false
                        }
                    },
                    enabled = !syncing,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("上传本地")
                }
                Button(
                    onClick = {
                        syncing = true
                        message = "下载中"
                        scope.launch {
                            val result = ServiceLocator.syncManager.downloadRemoteChanges()
                            message = result.fold(
                                onSuccess = { "下载完成：$it 条变更" },
                                onFailure = { "下载失败：${syncUserMessage(it)}" }
                            )
                            syncing = false
                        }
                    },
                    enabled = !syncing,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("下载远程")
                }
            }
        }
    }
}

@Composable
private fun MyScreen(
    state: MainUiState,
    isLoggedIn: Boolean,
    onRequestAuth: () -> Unit,
    onLogout: () -> Unit,
    onOpenPanel: (AppPanel) -> Unit
) {
    val email by ServiceLocator.authTokenProvider.emailFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CashFlowGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("CashFlow", color = Color.White.copy(alpha = 0.78f))
                    Text(
                        text = if (isLoggedIn) {
                            email.orEmpty().ifBlank { "已登录用户" }
                        } else {
                            "本地模式"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "已确认 ${state.confirmed.size} 笔 · 待确认 ${state.drafts.size} 笔",
                        color = Color.White.copy(alpha = 0.82f)
                    )
                }
            }
        }
        item { ProfileMenuRow("设置", "功能设置和其他设置") { onOpenPanel(AppPanel.Settings) } }
        item { ProfileMenuRow("帮助与反馈", "常见问题和使用反馈") { onOpenPanel(AppPanel.Help) } }
        item { ProfileMenuRow("关于 CashFlow", "版本号、隐私与产品说明") { onOpenPanel(AppPanel.About) } }
        item {
            if (isLoggedIn) {
                Button(
                    onClick = {
                        scope.launch {
                            ServiceLocator.authTokenProvider.clearAuth()
                            onLogout()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("退出登录")
                }
            } else {
                Button(
                    onClick = onRequestAuth,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("登录 / 注册")
                }
            }
        }
    }
}

@Composable
private fun PersonalInfoScreen(
    state: MainUiState,
    isLoggedIn: Boolean,
    onRequestAuth: () -> Unit,
    onLogout: () -> Unit
) {
    val email by ServiceLocator.authTokenProvider.emailFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CashFlowGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("个人信息", color = Color.White.copy(alpha = 0.78f))
                    Text(
                        if (isLoggedIn) email.orEmpty().ifBlank { "已登录用户" } else "本地模式",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("已确认 ${state.confirmed.size} 笔 · 待确认 ${state.drafts.size} 笔", color = Color.White.copy(alpha = 0.82f))
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("账号状态", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isLoggedIn) {
                            "已登录后可使用 AI 对话和云同步。"
                        } else {
                            "当前为本地模式，登录后可启用 AI 对话和云同步。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isLoggedIn) {
                        Button(
                            onClick = {
                                scope.launch {
                                    ServiceLocator.authTokenProvider.clearAuth()
                                    onLogout()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("退出登录")
                        }
                    } else {
                        Button(
                            onClick = onRequestAuth,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("登录 / 注册")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("常见问题", fontWeight = FontWeight.SemiBold)
                    Text("1. AI 不出卡片时，请先确认账号已登录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("2. OCR 草稿会先进入待确认队列，确认后才会计入统计。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("3. 真机问题可以优先检查权限、网络和版本号。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { DraftReminderCard() }
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("意见反馈", fontWeight = FontWeight.SemiBold)
                    Text("如果你希望补充某个入口或优化某个页面，可以直接把真机截图发给我，我会按当前 App 结构继续收口。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AboutScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CashFlowGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI Money Ledger", color = Color.White.copy(alpha = 0.78f))
                    Text("CashFlow", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("版本 ${appVersionName()}", color = Color.White.copy(alpha = 0.82f))
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("产品说明", fontWeight = FontWeight.SemiBold)
                    Text("本地优先 · 草稿先行 · 确认后入账。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("所有自动识别结果都会先保持可见草稿，再由你决定是否入账。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ProfileScreenPanel(
    state: MainUiState,
    isLoggedIn: Boolean,
    onOpenPanel: (AppPanel) -> Unit,
    onRequestAuth: () -> Unit,
    onLogout: () -> Unit
) {
    val email by ServiceLocator.authTokenProvider.emailFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CashFlowGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CashFlow", color = Color.White.copy(alpha = 0.78f))
                    Text(if (isLoggedIn) email.orEmpty().ifBlank { "已登录用户" } else "本地模式", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("已确认 ${state.confirmed.size} 笔 · 待确认 ${state.drafts.size} 笔", color = Color.White.copy(alpha = 0.82f))
                }
            }
        }
        item { ProfileMenuRow("AI 对话助手", "自然语言记账和查询") { onOpenPanel(AppPanel.Chat) } }
        item { ProfileMenuRow("数据同步", "上传和下载远程账单") { onOpenPanel(AppPanel.Sync) } }
        item { ProfileMenuRow("账户管理", "余额与账户排序") { onOpenPanel(AppPanel.Account) } }
        item { ProfileMenuRow("分类管理", "支出/收入分类维护") { onOpenPanel(AppPanel.Category) } }
        item {
            if (isLoggedIn) {
                Button(
                    onClick = {
                        scope.launch {
                            ServiceLocator.authTokenProvider.clearAuth()
                            onLogout()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("退出登录")
                }
            } else {
                Button(onClick = onRequestAuth, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("登录 / 注册")
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun AuthRequiredPanel(title: String, subtitle: String, onRequestAuth: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onRequestAuth, shape = RoundedCornerShape(14.dp)) {
                    Text("登录 / 注册")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartSummaryCard(
    title: String,
    amountCents: Long,
    deltaText: String,
    deltaColor: Color,
    monthLabel: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(999.dp), color = CashFlowGreen.copy(alpha = 0.1f)) {
                    Text(
                        monthLabel,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = CashFlowGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = formatMoney(amountCents),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("较上月 $deltaText", style = MaterialTheme.typography.labelLarge, color = deltaColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ChartMonthSelector(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPicker: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.height(40.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                Text("‹", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Surface(
                onClick = onOpenPicker,
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                Text("›", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ChartBreakdownCard(
    totals: List<CategoryTotal>,
    totalCents: Long,
    isIncome: Boolean,
    categories: List<CategoryEntity>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isIncome) "收入分类权重" else "支出分类权重", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(formatMoney(totalCents), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                DonutChart(
                    totals = totals,
                    categories = categories,
                    modifier = Modifier.fillMaxWidth().height(if (totals.isEmpty()) 112.dp else 146.dp),
                    showLeaderLabels = true
                )
                DonutTotalCenter(label = if (isIncome) "总收入" else "总支出", totalCents = totalCents)
            }
            if (totals.isEmpty()) {
                Text(
                    if (isIncome) "确认收入流水后会显示收入分类权重。" else "确认支出流水后会显示消费分类权重。",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                totals.take(6).forEach { total ->
                    CompactCategoryWeightRow(
                        total = total,
                        monthlyTotal = totalCents,
                        categoryBudgetCents = if (isIncome) null else categories.firstOrNull { it.code == total.categoryCode }?.monthlyBudgetCents,
                        categories = categories
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactCategoryWeightRow(
    total: CategoryTotal,
    monthlyTotal: Long,
    categoryBudgetCents: Long?,
    categories: List<CategoryEntity>
) {
    val percent = if (monthlyTotal > 0L) total.amountCents.toFloat() / monthlyTotal.toFloat() else 0f
    val budgetUsage = if ((categoryBudgetCents ?: 0L) > 0L) {
        total.amountCents.toFloat() / (categoryBudgetCents ?: 1L).toFloat()
    } else {
        percent
    }
    val color = categoryColor(total.categoryCode, categories)
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
        Text(total.categoryName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text(formatCompactMoney(total.amountCents), style = MaterialTheme.typography.labelSmall)
        Text(formatPercent(total.amountCents, monthlyTotal), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(modifier = Modifier.width(76.dp).height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(99.dp))) {
            Box(modifier = Modifier.fillMaxWidth(budgetUsage.coerceIn(0f, 1f)).height(4.dp).background(color, RoundedCornerShape(99.dp)))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSegmentedControl(selectedType: TransactionType, onSelect: (TransactionType) -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
                val selected = selectedType == type
                Surface(
                    onClick = { onSelect(type) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) CashFlowGreen else Color.Transparent
                ) {
                    Text(
                        text = if (type == TransactionType.EXPENSE) "支出" else "收入",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DonutTotalCenter(label: String, totalCents: Long) {
    Column(
        modifier = Modifier.width(112.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatCompactMoney(totalCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MonthlyComparisonTrendCard(
    items: List<PeriodSummary>,
    selectedType: TransactionType
) {
    val isIncome = selectedType == TransactionType.INCOME
    val current = items.lastOrNull()?.let { if (isIncome) it.incomeCents else it.expenseCents } ?: 0L
    val previous = items.dropLast(1).lastOrNull()?.let { if (isIncome) it.incomeCents else it.expenseCents } ?: 0L
    val delta = current - previous
    val percentText = if (previous > 0L) {
        "${if (delta >= 0L) "+" else ""}${((delta.toFloat() / previous.toFloat()) * 100).toInt()}%"
    } else if (current > 0L) {
        "+100%"
    } else {
        "0%"
    }
    val deltaColor = when {
        delta > 0L && isIncome -> Color(0xFF16834A)
        delta > 0L -> Color(0xFFB3261E)
        delta < 0L && isIncome -> Color(0xFFB3261E)
        delta < 0L -> Color(0xFF16834A)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val lineColor = if (isIncome) Color(0xFF16834A) else MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "月度趋势曲线",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (isIncome) "本月收入" else "本月支出", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(current), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = lineColor)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(percentText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = deltaColor)
                    Text("${if (delta >= 0L) "+" else "-"}${formatMoney(kotlin.math.abs(delta))}", color = deltaColor)
                }
            }
            TrendChart(
                items = items,
                valueSelector = { if (isIncome) it.incomeCents else it.expenseCents },
                lineColor = lineColor,
                modifier = Modifier.fillMaxWidth().height(96.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryManageCard(
    title: String,
    categories: List<CategoryEntity>,
    newCategoryName: String,
    newCategoryColor: String,
    newCategoryIcon: String,
    selectedSecondary: Boolean = false,
    onPrimaryClick: (() -> Unit)? = null,
    onSecondaryClick: (() -> Unit)? = null,
    primaryTitle: String = title,
    secondaryTitle: String = if (title == "支出分类") "收入分类" else "支出分类",
    onNameChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onAdd: () -> Unit,
    onSaveCategory: (CategoryEntity, String, String, String) -> Unit,
    onMoveCategory: (CategoryEntity, Int) -> Unit,
    onMoveCategoryToIndex: (CategoryEntity, Int) -> Unit,
    onDelete: (CategoryEntity) -> Unit
) {
    var sortMode by remember(title) { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                CategoryHeaderTab(
                    title = primaryTitle,
                    selected = !selectedSecondary,
                    onClick = onPrimaryClick,
                    modifier = Modifier.weight(1f)
                )
                CategoryHeaderTab(
                    title = secondaryTitle,
                    selected = selectedSecondary,
                    onClick = onSecondaryClick,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (sortMode) "排序模式" else "分类列表",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { sortMode = !sortMode }) {
                    Text(if (sortMode) "完成排序" else "调整顺序")
                }
            }
            if (categories.isEmpty()) {
                Text("暂无分类", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                categories.forEachIndexed { index, category ->
                    if (sortMode) {
                        CategorySortRow(
                            category = category,
                            index = index,
                            lastIndex = categories.lastIndex,
                            onMoveUp = { onMoveCategory(category, -1) },
                            onMoveDown = { onMoveCategory(category, 1) },
                            onMoveToTop = { onMoveCategoryToIndex(category, 0) },
                            onMoveToBottom = { onMoveCategoryToIndex(category, categories.lastIndex) }
                        )
                    } else {
                        CategoryManageRow(
                            category = category,
                            canMoveUp = index > 0,
                            canMoveDown = index < categories.lastIndex,
                            onSave = { newName, newColor, newIcon -> onSaveCategory(category, newName, newColor, newIcon) },
                            onMoveUp = { onMoveCategory(category, -1) },
                            onMoveDown = { onMoveCategory(category, 1) },
                            onDelete = { onDelete(category) }
                        )
                    }
                    if (index < categories.lastIndex) ThinDivider()
                }
            }
            ThinDivider()
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { onNameChange(it.take(16)) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    singleLine = true,
                    placeholder = { Text("添加自定义分类") }
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(categoryIconGlyph(newCategoryIcon), style = MaterialTheme.typography.labelMedium)
                        Text("Icon", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    GlyphPicker(
                        options = CategoryIconOptions,
                        selectedKey = newCategoryIcon,
                        onSelect = onIconChange
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text("新分类颜色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ColorPalettePicker(selectedColor = newCategoryColor, onSelect = onColorChange)
                }
                Button(
                    onClick = onAdd,
                    enabled = newCategoryName.isNotBlank(),
                    modifier = Modifier.align(Alignment.End).height(40.dp),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AccountStatisticsCard(accountStats: List<AccountMonthlyStat>) {
    val totalIncome = accountStats.sumOf { it.incomeCents }
    val totalExpense = accountStats.sumOf { it.expenseCents }
    val visibleStats = accountStats
        .sortedByDescending { it.activityCents }
        .take(6)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("账户维度统计", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("仅统计本月已确认流水", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile("本月支出", formatMoney(totalExpense), Color(0xFFE85D5D))
                StatTile("本月收入", formatMoney(totalIncome), Color(0xFF2F8E4C))
                StatTile("净流入", formatSignedMoney(totalIncome - totalExpense), if (totalIncome - totalExpense >= 0L) Color(0xFF16834A) else Color(0xFFE85D5D))
            }
            if (visibleStats.isEmpty()) {
                Text(
                    "确认流水后会在这里看到各账户的本月收支概览。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                visibleStats.forEachIndexed { index, stat ->
                    AccountStatisticRow(stat)
                    if (index < visibleStats.lastIndex) ThinDivider()
                }
            }
        }
    }
}

@Composable
private fun AccountStatisticRow(stat: AccountMonthlyStat) {
    val account = stat.account
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountIconImage(account = account, size = 30.dp, fallbackStyle = MaterialTheme.typography.labelLarge)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(account.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(accountMonthSummaryText(stat), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                formatSignedMoney(stat.netCents),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (stat.netCents >= 0L) Color(0xFF16834A) else Color(0xFFE85D5D)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryHeaderTab(
    title: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onClick?.invoke() },
        modifier = modifier,
        color = Color.Transparent
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(if (selected) CashFlowGreen else Color.Transparent)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryManageRow(
    category: CategoryEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSave: (String, String, String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember(category.code) { mutableStateOf(false) }
    var nameInput by remember(category.code, category.name) { mutableStateOf(category.name) }
    var colorInput by remember(category.code, category.colorHex) { mutableStateOf(category.colorHex) }
    var iconInput by remember(category.code, category.iconKey) { mutableStateOf(category.iconKey.ifBlank { DefaultCategoryIconKey }) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(category.colorHex.toColor().copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(categoryEmoji(category), style = MaterialTheme.typography.labelLarge)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (editing) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(16) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("分类名称") }
                )
                GlyphPicker(
                    options = CategoryIconOptions,
                    selectedKey = iconInput,
                    onSelect = { iconInput = it }
                )
                ColorPalettePicker(selectedColor = colorInput, onSelect = { colorInput = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            onSave(nameInput, colorInput, iconInput)
                            editing = false
                        },
                        enabled = nameInput.isNotBlank()
                    ) {
                        Text("保存")
                    }
                    TextButton(onClick = { editing = false }) {
                        Text("取消")
                    }
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(category.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (category.isSystem) "系统分类" else "自定义分类",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
            }
        }
        if (!category.isSystem && !editing) {
            IconButton(onClick = { editing = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑")
            }
        }
    }
}

@Composable
private fun CategorySortRow(
    category: CategoryEntity,
    index: Int,
    lastIndex: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(30.dp).background(category.colorHex.toColor().copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(categoryEmoji(category), style = MaterialTheme.typography.labelLarge)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(category.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "第 ${index + 1} 位",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onMoveToTop, enabled = index > 0) {
            Text("置顶")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onMoveUp, enabled = index > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
            }
            IconButton(onClick = onMoveDown, enabled = index < lastIndex) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
            }
        }
        TextButton(onClick = onMoveToBottom, enabled = index < lastIndex) {
            Text("置底")
        }
    }
}

@Composable
private fun ColorPalettePicker(
    selectedColor: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryColorOptions.forEach { colorHex ->
            val color = colorHex.toColor()
            Surface(
                onClick = { onSelect(colorHex) },
                shape = CircleShape,
                color = color,
                border = BorderStroke(
                    width = if (selectedColor == colorHex) 2.dp else 1.dp,
                    color = if (selectedColor == colorHex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                )
            ) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedColor == colorHex) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlyphPicker(
    options: List<GlyphOption>,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val selected = selectedKey == option.key
            Surface(
                onClick = { onSelect(option.key) },
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            ) {
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(option.glyph, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SettingSwitchGroup(
    state: MainUiState,
    onNotificationChange: (Boolean) -> Unit,
    onScreenshotChange: (Boolean) -> Unit,
    onAutoCategoryChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingSwitchRow("通知识别", "识别支付通知，自动提取金额、商户等", state.notificationParsingEnabled, onNotificationChange)
            ThinDivider()
            SettingSwitchRow("截图监听", "监听支付截图，自动提取信息", state.screenshotMonitoringEnabled, onScreenshotChange)
            ThinDivider()
            SettingSwitchRow("自动分类", "通知、OCR、AI 和手动输入都可自动建议分类", state.autoCategoryEnabled, onAutoCategoryChange)
            ThinDivider()
            SettingSwitchRow("OCR 失败提醒", "识别失败时提醒我处理", true, {})
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiniSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.width(38.dp).height(22.dp),
        shape = RoundedCornerShape(999.dp),
        color = if (checked) CashFlowGreen else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(modifier = Modifier.size(18.dp).background(Color.White, CircleShape))
        }
    }
}

@Composable
private fun BudgetCompactCard(
    monthlyBudgetInput: String,
    monthlyBudgetCents: Long,
    categoryBudgetCount: Int,
    budgetAlertEnabled: Boolean,
    editing: Boolean,
    onInputChange: (String) -> Unit,
    onEditChange: (Boolean) -> Unit,
    onBudgetAlertChange: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (editing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("本月总预算", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = monthlyBudgetInput,
                        onValueChange = onInputChange,
                        modifier = Modifier.width(112.dp).height(46.dp),
                        prefix = { Text("¥") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    TextButton(onClick = onSave) { Text("保存") }
                }
            } else {
                Surface(onClick = { onEditChange(true) }, color = Color.Transparent) {
                    SettingsValueRow("本月总预算", if (monthlyBudgetCents > 0L) formatPlainMoney(monthlyBudgetCents) else "未设置")
                }
            }
            ThinDivider()
            SettingsValueRow("分类预算", if (categoryBudgetCount > 0) "已设置 $categoryBudgetCount 项" else "未设置")
            ThinDivider()
            SettingSwitchRow(
                title = "预算预警",
                subtitle = "本月预算达到 80% 和 100% 时发送提醒",
                checked = budgetAlertEnabled,
                onCheckedChange = onBudgetAlertChange
            )
        }
    }
}

@Composable
private fun SettingsValueRow(title: String, value: String, showChevron: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (showChevron) {
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsClickableRow(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(value, color = CashFlowGreen, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsAddRow(title: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).background(CashFlowGreen.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = CashFlowGreen, fontWeight = FontWeight.Bold)
                }
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccountPortfolioSummaryRow(
    totalTrackedBalance: Long,
    trackedBalanceCount: Int,
    totalAccounts: Int,
    totalExpense: Long,
    totalIncome: Long
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("账户总览", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("余额合计", if (totalTrackedBalance > 0L) formatMoney(totalTrackedBalance) else "未设置", MaterialTheme.colorScheme.onSurface)
            StatTile("已录余额", "$trackedBalanceCount/$totalAccounts", CashFlowGreen)
            StatTile("本月净流", formatSignedMoney(totalIncome - totalExpense), if (totalIncome - totalExpense >= 0L) Color(0xFF16834A) else Color(0xFFE85D5D))
        }
    }
}

@Composable
private fun ThinDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    )
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryBudgetRow(category: CategoryEntity, onSave: (Long?) -> Unit) {
    var input by remember(category.code, category.monthlyBudgetCents) {
        mutableStateOf(centsToInput(category.monthlyBudgetCents ?: 0L))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(category.colorHex.toColor().copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(categoryEmoji(category))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(category.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if ((category.monthlyBudgetCents ?: 0L) > 0L) "预算 ${formatMoney(category.monthlyBudgetCents ?: 0L)}" else "未设置分类预算",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.filter { char -> char.isDigit() || char == '.' }.take(9) },
            modifier = Modifier.width(118.dp),
            prefix = { Text("¥") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        TextButton(onClick = { onSave(input.toCentsOrNull()) }) {
            Text("保存")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountManageCard(
    accounts: List<AccountEntity>,
    confirmedEntries: List<LedgerEntryEntity>,
    newAccountName: String,
    newAccountColor: String,
    newAccountIcon: String,
    adding: Boolean,
    onNameChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onAddingChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onSaveAccount: (AccountEntity, String, String, Long?, String) -> Unit,
    onMoveAccount: (AccountEntity, Int) -> Unit,
    onDelete: (String) -> Unit
) {
    val accountStats = remember(accounts, confirmedEntries) {
        buildCurrentMonthAccountStats(confirmedEntries, accounts)
    }
    val totalTrackedBalance = accountStats.sumOf { it.account.balanceCents ?: 0L }
    val trackedBalanceCount = accountStats.count { (it.account.balanceCents ?: 0L) > 0L }
    val totalExpense = accountStats.sumOf { it.expenseCents }
    val totalIncome = accountStats.sumOf { it.incomeCents }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AccountPortfolioSummaryRow(
                totalTrackedBalance = totalTrackedBalance,
                trackedBalanceCount = trackedBalanceCount,
                totalAccounts = accounts.size,
                totalExpense = totalExpense,
                totalIncome = totalIncome
            )
            ThinDivider()
            if (accounts.isEmpty()) {
                Text("暂无账户", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                accountStats.forEachIndexed { index, stat ->
                    AccountManageRow(
                        account = stat.account,
                        stat = stat,
                        canMoveUp = index > 0,
                        canMoveDown = index < accountStats.lastIndex,
                        onSave = { newName, newColor, balance, newIcon ->
                            onSaveAccount(stat.account, newName, newColor, balance, newIcon)
                        },
                        onMoveUp = { onMoveAccount(stat.account, -1) },
                        onMoveDown = { onMoveAccount(stat.account, 1) },
                        onDelete = { onDelete(stat.account.code) }
                    )
                    if (index < accountStats.lastIndex) ThinDivider()
                }
            }
            ThinDivider()
            if (adding) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newAccountName,
                        onValueChange = { onNameChange(it.take(16)) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        singleLine = true,
                        placeholder = { Text("新账户名称") }
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Text("新账户颜色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        GlyphPicker(
                            options = AccountIconOptions,
                            selectedKey = newAccountIcon,
                            onSelect = onIconChange
                        )
                        ColorPalettePicker(selectedColor = newAccountColor, onSelect = onColorChange)
                    }
                    Button(
                        onClick = onAdd,
                        enabled = newAccountName.isNotBlank(),
                        modifier = Modifier.align(Alignment.End).height(40.dp),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("添加")
                    }
                }
            } else {
                SettingsAddRow("添加账户", onClick = { onAddingChange(true) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountManageRow(
    account: AccountEntity,
    stat: AccountMonthlyStat,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSave: (String, String, Long?, String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember(account.code) { mutableStateOf(false) }
    var nameInput by remember(account.code, account.name) { mutableStateOf(account.name) }
    var colorInput by remember(account.code, account.colorHex) { mutableStateOf(account.colorHex) }
    var iconInput by remember(account.code, account.iconKey) { mutableStateOf(account.iconKey.ifBlank { DefaultAccountIconKey }) }
    var balanceInput by remember(account.code, account.balanceCents) {
        mutableStateOf(centsToInput(account.balanceCents ?: 0L))
    }
    Surface(
        onClick = { editing = !editing },
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccountIconImage(account = account, size = 28.dp, fallbackStyle = MaterialTheme.typography.labelLarge)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(account.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (account.isSystem) "系统账户" else "自定义账户",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        accountMonthSummaryText(stat),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(accountBalancePreview(account), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "净值 ${formatSignedMoney(stat.netCents)}",
                        color = if (stat.netCents >= 0L) Color(0xFF16834A) else Color(0xFFE85D5D),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                    }
                }
                Text(if (editing) "⌃" else "›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            if (editing) {
                ThinDivider()
                if (!account.isSystem) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it.take(16) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("账户名称") }
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("账户颜色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GlyphPicker(
                        options = AccountIconOptions,
                        selectedKey = iconInput,
                        onSelect = { iconInput = it }
                    )
                    ColorPalettePicker(selectedColor = colorInput, onSelect = { colorInput = it })
                }
                OutlinedTextField(
                    value = balanceInput,
                    onValueChange = { balanceInput = it.filter { char -> char.isDigit() || char == '.' }.take(12) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("账户余额") },
                    prefix = { Text("¥") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text(
                    if (account.isSystem) "系统账户名称固定，仅支持维护余额。" else "自定义账户可同时修改名称和余额；清空余额可恢复为未设置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { editing = false }) { Text("收起") }
                    if (!account.isSystem) {
                        TextButton(onClick = onDelete) { Text("删除") }
                    }
                    Button(
                        onClick = {
                            onSave(nameInput, colorInput, balanceInput.toPositiveCentsOrNull(), iconInput)
                            editing = false
                        },
                        enabled = account.isSystem || nameInput.isNotBlank()
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionSheet(
    state: MainUiState,
    onDismiss: () -> Unit,
    onCreateCategory: (String, Boolean) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onSave: (Long, String, String?, String?, TransactionType, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var selectedCategory by remember { mutableStateOf(state.categories.firstOrNull { !it.isIncome }?.code) }
    var selectedAccount by remember(state.accounts) { mutableStateOf(state.accounts.firstOrNull()?.code ?: "cash") }
    var showCustomCategoryDialog by remember { mutableStateOf(false) }
    var customCategoryName by remember { mutableStateOf("") }
    var pendingCustomCategoryName by remember { mutableStateOf<String?>(null) }
    var pendingCustomCategoryIsIncome by remember { mutableStateOf<Boolean?>(null) }
    val amountFocusRequester = remember { FocusRequester() }
    val categories = state.categories.filter { it.isIncome == (selectedType == TransactionType.INCOME) }
    val amountCents = remember(amount) { evaluateAmountExpressionCents(amount) }
    val suggestedCategoryCode = remember(note, amount, selectedType, categories) {
        suggestCategoryCodeForManualEntry(
            note = note,
            amountText = amount,
            selectedType = selectedType,
            categories = categories
        )
    }

    LaunchedEffect(Unit) {
        yield()
        amountFocusRequester.requestFocus()
    }

    LaunchedEffect(categories, selectedType, suggestedCategoryCode) {
        if (selectedCategory == null || categories.none { it.code == selectedCategory }) {
            selectedCategory = suggestedCategoryCode ?: categories.firstOrNull()?.code
        }
    }

    LaunchedEffect(categories, pendingCustomCategoryName, pendingCustomCategoryIsIncome) {
        val pendingName = pendingCustomCategoryName ?: return@LaunchedEffect
        val pendingIsIncome = pendingCustomCategoryIsIncome ?: return@LaunchedEffect
        val created = categories.firstOrNull { category ->
            category.name == pendingName && category.isIncome == pendingIsIncome && !category.isSystem
        }
        if (created != null) {
            selectedCategory = created.code
            pendingCustomCategoryName = null
            pendingCustomCategoryIsIncome = null
        }
    }

    fun saveCurrent() {
        val cents = amountCents ?: return
        onSave(cents, note.ifBlank { "手动记录" }, selectedCategory, selectedAccount, selectedType, note)
        amount = ""
        note = ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("快速记账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = {
                        saveCurrent()
                    }, enabled = amountCents != null) {
                        Text("保存", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("金额（元）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it.filter { char -> char.isDigit() || char in ".+-*/ " }.take(32) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(amountFocusRequester),
                            prefix = { Text("¥", style = MaterialTheme.typography.headlineMedium) },
                            textStyle = MaterialTheme.typography.displaySmall,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            keyboardActions = KeyboardActions(onDone = {
                                saveCurrent()
                            }),
                            placeholder = { Text("68.00") },
                            shape = RoundedCornerShape(16.dp)
                        )
                        Text(
                            text = when {
                                amount.isBlank() -> "支持 12+8.5+3 这类多笔小额合计"
                                amountCents != null -> "计算结果 ${formatMoney(amountCents)}"
                                else -> "请输入有效金额表达式"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (amount.isNotBlank() && amountCents == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
                                val selected = selectedType == type
                Surface(
                    onClick = {
                        selectedType = type
                        selectedCategory = suggestCategoryCodeForManualEntry(
                            note = note,
                            amountText = amount,
                            selectedType = type,
                            categories = state.categories.filter { it.isIncome == (type == TransactionType.INCOME) }
                        )
                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (selected) Color(0xFF2F8E4C) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = if (type == TransactionType.EXPENSE) "支出" else "收入",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                if (suggestedCategoryCode != null && selectedCategory != suggestedCategoryCode) {
                    SuggestionChip(
                        suggestedCategory = categories.firstOrNull { it.code == suggestedCategoryCode },
                        onApply = { selectedCategory = suggestedCategoryCode }
                    )
                }
            }
            item {
                CategoryGrid(
                    categories = categories,
                    selectedCode = selectedCategory,
                    customCategoryCodes = setOf(if (selectedType == TransactionType.INCOME) "other_income" else "other"),
                    onCustomCategory = {
                        customCategoryName = ""
                        showCustomCategoryDialog = true
                    },
                    onDeleteCategory = onDeleteCategory,
                    onSelect = { selectedCategory = it }
                )
            }
            item {
                AccountSelector(
                    accounts = state.accounts,
                    selectedCode = selectedAccount,
                    onSelect = { selectedAccount = it }
                )
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("备注或商户") },
                    shape = RoundedCornerShape(16.dp)
                )
            }
            item {
                Button(
                    onClick = {
                        saveCurrent()
                    },
                    enabled = amountCents != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("保存记录")
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showCustomCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCustomCategoryDialog = false },
            title = { Text(if (selectedType == TransactionType.INCOME) "自定义收入分类" else "自定义支出分类") },
            text = {
                OutlinedTextField(
                    value = customCategoryName,
                    onValueChange = { customCategoryName = it.take(12) },
                    singleLine = true,
                    label = { Text("分类名称") },
                    placeholder = { Text("例如：早餐、兼职") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = customCategoryName.trim()
                        if (name.isNotBlank()) {
                            val isIncome = selectedType == TransactionType.INCOME
                            pendingCustomCategoryName = name
                            pendingCustomCategoryIsIncome = isIncome
                            onCreateCategory(name, isIncome)
                            showCustomCategoryDialog = false
                            customCategoryName = ""
                        }
                    },
                    enabled = customCategoryName.trim().isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomCategoryDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SuggestionChip(
    suggestedCategory: CategoryEntity?,
    onApply: () -> Unit
) {
    if (suggestedCategory == null) return
    Surface(
        onClick = onApply,
        shape = RoundedCornerShape(999.dp),
        color = CashFlowGreen.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, CashFlowGreen.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("推荐分类", color = CashFlowGreen, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(suggestedCategory.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSelector(
    accounts: List<AccountEntity>,
    selectedCode: String?,
    onSelect: (String) -> Unit
) {
    if (accounts.isEmpty()) return
    val columns = 2
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("账户", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        accounts.chunked(columns).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { account ->
                    val selected = selectedCode == account.code
                    Surface(
                        onClick = { onSelect(account.code) },
                        modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AccountIconImage(
                                account = account,
                                size = 30.dp,
                                fallbackStyle = MaterialTheme.typography.labelMedium,
                                fallbackWeight = FontWeight.Normal
                            )
                            Text(
                                account.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedCode: String?,
    customCategoryCodes: Set<String> = emptySet(),
    onCustomCategory: ((CategoryEntity) -> Unit)? = null,
    onDeleteCategory: ((CategoryEntity) -> Unit)? = null,
    onSelect: (String) -> Unit
) {
    val columns = if (LocalConfiguration.current.screenWidthDp < 360) 3 else 4
    var deleteVisibleCode by remember(categories) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        categories.chunked(columns).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { category ->
                    val selected = selectedCode == category.code
                    val isCustomEntry = category.code in customCategoryCodes && onCustomCategory != null
                    val canDelete = onDeleteCategory != null && !category.isSystem && !isCustomEntry
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = {
                                    deleteVisibleCode = null
                                    if (isCustomEntry) {
                                        onCustomCategory?.invoke(category)
                                    } else {
                                        onSelect(category.code)
                                    }
                                },
                                onLongClick = {
                                    if (canDelete) {
                                        deleteVisibleCode = category.code
                                    }
                                }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(if (isCustomEntry) "+" else categoryEmoji(category), style = MaterialTheme.typography.titleLarge)
                                Text(
                                    if (isCustomEntry) "自定义" else category.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            if (deleteVisibleCode == category.code && canDelete) {
                                Surface(
                                    onClick = {
                                        onDeleteCategory?.invoke(category)
                                        deleteVisibleCode = null
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(24.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.error
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("×", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AccountSyncCard(
    onRequestAuth: () -> Unit,
    onLogout: () -> Unit
) {
    val email by ServiceLocator.authTokenProvider.emailFlow.collectAsState(initial = null)
    val token by ServiceLocator.authTokenProvider.tokenFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val isLoggedIn = !token.isNullOrBlank()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(title = "账号与同步")
            if (isLoggedIn) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "已登录账号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = email.orEmpty().ifBlank { "未显示邮箱" },
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "数据保持本地优先，退出后会返回登录页。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        scope.launch {
                            ServiceLocator.authTokenProvider.clearAuth()
                            onLogout()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text("退出登录", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "当前为本地模式",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "登录后可启用云同步与 AI 对话",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "未登录时仍可继续使用本地记账、账单、图表和设置功能。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onRequestAuth,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("登录 / 注册", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEntrySheet(
    entry: LedgerEntryEntity,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (LedgerEntryEntity) -> Unit,
    onConfirm: (LedgerEntryEntity) -> Unit,
    onRetryOcr: () -> Unit
) {
    var amount by remember(entry.id) { mutableStateOf(centsToInput(entry.amountCents)) }
    var merchant by remember(entry.id) { mutableStateOf(entry.merchant.ifBlank { entry.note }) }
    var note by remember(entry.id) { mutableStateOf(entry.note) }
    var selectedType by remember(entry.id) { mutableStateOf(entry.transactionType) }
    var selectedCategory by remember(entry.id) { mutableStateOf(entry.categoryCode) }
    var selectedAccount by remember(entry.id, accounts) { mutableStateOf(entry.accountCode.ifBlank { accounts.firstOrNull()?.code ?: "cash" }) }
    val visibleCategories = categories.filter { it.isIncome == (selectedType == TransactionType.INCOME) }
    val ocrText = entry.rawText.trim()

    LaunchedEffect(visibleCategories, selectedType) {
        if (selectedCategory == null || visibleCategories.none { it.code == selectedCategory }) {
            selectedCategory = visibleCategories.firstOrNull()?.code
        }
    }

    fun buildUpdated(): LedgerEntryEntity? {
        val cents = amount.toCentsOrNull() ?: return null
        val category = categories.firstOrNull { it.code == selectedCategory }
        val account = accounts.firstOrNull { it.code == selectedAccount }
        return entry.copy(
            transactionType = selectedType,
            amountCents = cents,
            merchant = merchant.trim(),
            categoryCode = selectedCategory,
            categoryNameSnapshot = category?.name,
            accountCode = account?.code ?: selectedAccount,
            accountNameSnapshot = account?.name ?: entry.accountNameSnapshot,
            note = note.trim(),
            ingestionState = if (entry.ingestionState == IngestionState.OCR_PENDING) IngestionState.OCR_PENDING else entry.ingestionState,
            needsReview = true,
            updatedAt = System.currentTimeMillis()
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("编辑流水", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (entry.imageUri != null) {
                        TextButton(onClick = onRetryOcr) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("重识别")
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
                        val selected = selectedType == type
                        Surface(
                            onClick = {
                                selectedType = type
                                selectedCategory = categories.firstOrNull { it.isIncome == (type == TransactionType.INCOME) }?.code
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) CashFlowGreen else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (type == TransactionType.EXPENSE) "支出" else "收入",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' }.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("¥") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("金额") }
                )
            }
            item {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("商户") }
                )
            }
            item {
                CategoryGrid(visibleCategories, selectedCategory) { selectedCategory = it }
            }
            item {
                AccountSelector(
                    accounts = accounts,
                    selectedCode = selectedAccount,
                    onSelect = { selectedAccount = it }
                )
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") }
                )
            }
            if (ocrText.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("OCR 原文", fontWeight = FontWeight.SemiBold)
                            Text(
                                ocrText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { buildUpdated()?.let(onSave) },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text("保存")
                    }
                    Button(
                        onClick = { buildUpdated()?.let(onConfirm) },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text("保存并确认")
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TransactionRow(
    entry: LedgerEntryEntity,
    categories: List<CategoryEntity>,
    showActions: Boolean,
    onConfirm: (() -> Unit)? = null,
    onIgnore: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    val category = categories.firstOrNull { it.code == entry.categoryCode }
    val color = category?.colorHex?.toColor() ?: MaterialTheme.colorScheme.primary
    val displayTitle = when {
        entry.ingestionState == IngestionState.OCR_PENDING -> "正在识别支付截图"
        entry.ingestionState == IngestionState.OCR_FAILED -> "支付截图识别失败"
        else -> category?.name ?: entry.categoryNameSnapshot ?: "其他"
    }
    val displaySubtitle = when {
        entry.ingestionState == IngestionState.OCR_PENDING -> entry.sourceAppName.ifBlank { "相册导入" }
        entry.ingestionState == IngestionState.OCR_FAILED -> "可删除后重新导入，或等待自动重试"
        else -> listOf(
            entry.accountNameSnapshot.ifBlank { "现金" },
            entry.merchant.ifBlank { entry.note.ifBlank { entry.sourceAppName.ifBlank { "未命名" } } }
        ).joinToString(" · ")
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(42.dp).background(color.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(categoryEmoji(category))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayTitle, fontWeight = FontWeight.SemiBold)
                    Text(
                        displaySubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        (if (entry.transactionType == TransactionType.EXPENSE) "-" else "+") + formatMoney(entry.amountCents),
                        color = if (entry.transactionType == TransactionType.EXPENSE) Color(0xFFB3261E) else Color(0xFF16834A),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(formatDayTime(entry.occurredAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showActions && (onEdit != null || onConfirm != null || onIgnore != null || onDelete != null)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onEdit != null) {
                        TextButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("详情")
                        }
                    }
                    if (entry.reviewState == ReviewState.DRAFT && onConfirm != null) {
                        TextButton(onClick = onConfirm) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("确认")
                        }
                    }
                    if (entry.reviewState == ReviewState.DRAFT && onIgnore != null) {
                        TextButton(onClick = onIgnore) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("忽略")
                        }
                    }
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryWeightRow(
    total: CategoryTotal,
    monthlyTotal: Long,
    categoryBudgetCents: Long? = null,
    categories: List<CategoryEntity> = emptyList(),
    large: Boolean = false
) {
    val percent = if (monthlyTotal > 0) total.amountCents.toFloat() / monthlyTotal.toFloat() else 0f
    val budgetUsage = if ((categoryBudgetCents ?: 0L) > 0L) {
        total.amountCents.toFloat() / (categoryBudgetCents ?: 1L).toFloat()
    } else {
        percent
    }
    val color = categoryColor(total.categoryCode, categories)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${categoryEmoji(total.categoryCode)} ${total.categoryName}",
                modifier = Modifier.weight(1f),
                fontWeight = if (large) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${(percent * 100).toInt()}%  ${formatMoney(total.amountCents)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
            Box(modifier = Modifier.fillMaxWidth(budgetUsage.coerceIn(0f, 1f)).height(8.dp).background(color, RoundedCornerShape(4.dp)))
        }
        if ((categoryBudgetCents ?: 0L) > 0L) {
            val left = ((categoryBudgetCents ?: 0L) - total.amountCents).coerceAtLeast(0L)
            Text(
                text = "分类预算 ${formatMoney(categoryBudgetCents ?: 0L)}  剩余 ${formatMoney(left)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TopCategoryCard(
    total: CategoryTotal,
    monthlyTotal: Long,
    categories: List<CategoryEntity>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val percent = if (monthlyTotal > 0) total.amountCents.toFloat() / monthlyTotal.toFloat() else 0f
    val color = categoryColor(total.categoryCode, categories)
    Card(
        onClick = onClick,
        modifier = modifier.height(106.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(28.dp).background(color.copy(alpha = 0.13f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(categoryEmoji(total.categoryCode), style = MaterialTheme.typography.labelMedium)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        total.categoryName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${(percent * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    formatMoney(total.amountCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(percent.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(color, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopCategoryDetailSheet(
    total: CategoryTotal,
    entries: List<LedgerEntryEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onOpenDetail: (LedgerEntryEntity) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${total.categoryName}明细", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "本月 ${entries.size} 笔 · 合计 ${formatMoney(entries.sumOf { it.amountCents })}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
            if (entries.isEmpty()) {
                item { EmptyState("本月暂无该分类账单。") }
            } else {
                items(entries, key = { "top-detail-${it.id}" }) { entry ->
                    TransactionRow(
                        entry = entry,
                        categories = categories,
                        showActions = true,
                        onEdit = {
                            onDismiss()
                            onOpenDetail(entry)
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private data class DonutLeaderLabel(
    val text: String,
    val color: Color,
    val edge: Offset,
    val bend: Offset,
    val targetY: Float,
    val side: Int
)

@Composable
private fun DonutChart(
    totals: List<CategoryTotal>,
    categories: List<CategoryEntity> = emptyList(),
    modifier: Modifier = Modifier,
    showLeaderLabels: Boolean = false,
    ringColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val sum = totals.sumOf { it.amountCents }.takeIf { it > 0 } ?: 1L
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val outerSize = if (showLeaderLabels) size.minDimension * 0.58f else size.minDimension
        val stroke = Stroke(width = outerSize * (if (showLeaderLabels) 0.14f else 0.18f), cap = StrokeCap.Round)
        val center = Offset(size.width / 2f, size.height / 2f)
        val arcTopLeft = Offset(
            x = center.x - outerSize / 2f + stroke.width / 2f,
            y = center.y - outerSize / 2f + stroke.width / 2f
        )
        val arcSize = Size(outerSize - stroke.width, outerSize - stroke.width)
        val slices = mutableListOf<Triple<CategoryTotal, Float, Float>>()
        var start = -90f
        drawCircle(
            color = ringColor.copy(alpha = 0.18f),
            radius = arcSize.width / 2f,
            center = center,
            style = stroke
        )
        if (totals.isEmpty()) {
            drawCircle(
                ringColor.copy(alpha = 0.36f),
                radius = arcSize.width / 2f,
                center = center,
                style = stroke
            )
        } else {
            totals.forEach { total ->
                val sweep = 360f * total.amountCents.toFloat() / sum.toFloat()
                slices += Triple(total, start, sweep)
                drawArc(
                    color = categoryColor(total.categoryCode, categories),
                    startAngle = start,
                    sweepAngle = max(2f, sweep),
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = stroke
                )
                start += sweep
            }
            if (showLeaderLabels) {
                val leaderLength = 18.dp.toPx()
                val horizontalLength = 46.dp.toPx()
                val minY = 12.dp.toPx()
                val maxY = size.height - 12.dp.toPx()
                val spacing = 24.dp.toPx()
                val labels = slices.take(6).map { (total, startAngle, sweepAngle) ->
                    val angle = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
                    val cosValue = cos(angle).toFloat()
                    val sinValue = sin(angle).toFloat()
                    val side = if (cosValue >= 0f) 1 else -1
                    val edge = Offset(
                        x = center.x + cosValue * outerSize / 2f,
                        y = center.y + sinValue * outerSize / 2f
                    )
                    val bend = Offset(
                        x = center.x + cosValue * (outerSize / 2f + leaderLength),
                        y = center.y + sinValue * (outerSize / 2f + leaderLength)
                    )
                    DonutLeaderLabel(
                        text = "${shortCategoryLabel(total.categoryName)} ${formatPercent(total.amountCents, sum)}",
                        color = categoryColor(total.categoryCode, categories),
                        edge = edge,
                        bend = bend,
                        targetY = bend.y,
                        side = side
                    )
                }
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = labelColor.toArgb()
                    textSize = 12.dp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                listOf(-1, 1).forEach { side ->
                    var lastY = minY - spacing
                    val textRoom = 74.dp.toPx()
                    labels.filter { it.side == side }
                        .sortedBy { it.targetY }
                        .forEach { label ->
                            val labelY = min(max(label.targetY, lastY + spacing), maxY)
                            lastY = labelY
                            val endX = if (side > 0) {
                                min(size.width - textRoom, label.bend.x + horizontalLength)
                            } else {
                                max(textRoom, label.bend.x - horizontalLength)
                            }
                            val bend = Offset(label.bend.x, labelY)
                            val end = Offset(endX, labelY)
                            drawLine(label.color.copy(alpha = 0.42f), label.edge, bend, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(label.color.copy(alpha = 0.42f), bend, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            drawContext.canvas.nativeCanvas.drawText(
                                label.text,
                                endX + if (side > 0) 4.dp.toPx() else -4.dp.toPx(),
                                labelY + 4.dp.toPx(),
                                textPaint.apply {
                                    textAlign = if (side > 0) Paint.Align.LEFT else Paint.Align.RIGHT
                                }
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun TrendChart(
    items: List<PeriodSummary>,
    valueSelector: (PeriodSummary) -> Long = { it.expenseCents },
    lineColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val guideColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        if (items.isEmpty()) return@Canvas
        val left = 10.dp.toPx()
        val right = 10.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 28.dp.toPx()
        val chartHeight = (size.height - top - bottom).coerceAtLeast(1f)
        val chartWidth = (size.width - left - right).coerceAtLeast(1f)
        val maxValue = items.maxOf { valueSelector(it) }.coerceAtLeast(1L)
        val stepX = if (items.size == 1) 0f else chartWidth / (items.size - 1)
        val points = items.mapIndexed { index, item ->
            val x = left + stepX * index
            val y = top + chartHeight - (chartHeight * valueSelector(item).toFloat() / maxValue.toFloat())
            Offset(x, y)
        }
        repeat(3) { index ->
            val y = top + chartHeight * index / 2f
            drawLine(guideColor, Offset(left, y), Offset(size.width - right, y), strokeWidth = 1.dp.toPx())
        }
        for (i in 0 until points.lastIndex) {
            drawLine(lineColor, points[i], points[i + 1], strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
        points.forEach { drawCircle(lineColor, radius = 5.dp.toPx(), center = it) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.toArgb()
            textSize = 11.dp.toPx()
            textAlign = Paint.Align.CENTER
        }
        items.forEachIndexed { index, item ->
            val label = item.label.takeLast(2).trimStart('0') + "月"
            drawContext.canvas.nativeCanvas.drawText(label, left + stepX * index, size.height - 6.dp.toPx(), paint)
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String = "", onAction: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (action.isNotBlank()) {
            if (onAction != null) TextButton(onClick = onAction) { Text(action) } else Text(action, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PageTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ReferencePageHeader(
    index: String = "",
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(12.dp),
            color = CashFlowGreen.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = CashFlowGreen, modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatConfidence(confidence: Float): String {
    return "${(confidence.coerceIn(0f, 1f) * 100).toInt()}%"
}

private fun transactionTypeLabel(type: TransactionType): String = when (type) {
    TransactionType.EXPENSE -> "支出"
    TransactionType.INCOME -> "收入"
    TransactionType.TRANSFER -> "转账"
}

private fun ingestionStateColor(state: IngestionState): Color = when (state) {
    IngestionState.OCR_PENDING -> Color(0xFFF59E0B)
    IngestionState.OCR_DONE -> Color(0xFF16834A)
    IngestionState.OCR_FAILED -> Color(0xFFE85D5D)
    IngestionState.RAW -> CashFlowTextMuted
}

private fun formatMoney(cents: Long): String {
    val currency = NumberFormat.getCurrencyInstance(Locale.CHINA)
    return currency.format(cents / 100.0)
}

private fun appVersionName(): String {
    return runCatching {
        val context = ServiceLocator.context
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "未知"
    }.getOrDefault("未知")
}

private fun formatPlainMoney(cents: Long): String {
    return NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(cents / 100.0)
}

private fun formatSignedMoney(cents: Long): String {
    return if (cents >= 0L) "+${formatMoney(cents)}" else "-${formatMoney(kotlin.math.abs(cents))}"
}

private fun centsToInput(cents: Long): String {
    if (cents <= 0L) return ""
    return (cents.toBigDecimal().movePointLeft(2).setScale(2, RoundingMode.HALF_UP)).toPlainString()
}

private fun formatDayTime(timeMillis: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timeMillis))
}

private fun formatDateGroup(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timeMillis))
}

private fun syncUserMessage(throwable: Throwable): String {
    val raw = throwable.localizedMessage ?: throwable.message ?: return "未知错误"
    return syncUserMessageText(raw)
}

private fun syncUserMessageText(raw: String): String {
    return when {
        raw.contains("login", ignoreCase = true) || raw.contains("sign in", ignoreCase = true) -> "登录已过期，请重新登录"
        raw.contains("upload", ignoreCase = true) -> "上传失败，请稍后重试"
        raw.contains("download", ignoreCase = true) -> "下载失败，请稍后重试"
        else -> raw
    }
}

private fun currentMonthLabel(): String {
    return SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date())
}

private fun monthLabel(monthStartMillis: Long): String {
    return SimpleDateFormat("yyyy/M", Locale.CHINA).format(Date(monthStartMillis))
}

private fun recentMonthlySummaries(items: List<PeriodSummary>, count: Int): List<PeriodSummary> {
    return recentMonthlySummariesUntil(items, monthStartMillis(), count)
}

private fun recentMonthlySummariesUntil(
    items: List<PeriodSummary>,
    selectedMonth: Long,
    count: Int
): List<PeriodSummary> {
    val byLabel = items.associateBy { it.label }
    val formatter = SimpleDateFormat("yyyy-MM", Locale.CHINA)
    val calendar = Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = selectedMonth
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, -(count - 1))
    }
    return List(count) {
        val label = formatter.format(calendar.time)
        val summary = byLabel[label] ?: PeriodSummary(
            label = label,
            expenseCents = 0L,
            incomeCents = 0L,
            transferCents = 0L,
            count = 0
        )
        calendar.add(Calendar.MONTH, 1)
        summary
    }
}

private fun previousMonthTotal(items: List<PeriodSummary>, type: TransactionType): Long {
    if (items.isEmpty()) return 0L
    val current = items.lastOrNull()
    val previous = items.dropLast(1).lastOrNull()
    return when (type) {
        TransactionType.INCOME -> previous?.incomeCents ?: 0L
        else -> previous?.expenseCents ?: 0L
    }.takeIf { current != null } ?: 0L
}

private fun monthTotalFromSummariesOrEntries(
    summaries: List<PeriodSummary>,
    entries: List<LedgerEntryEntity>,
    selectedMonth: Long,
    type: TransactionType
): Long {
    val label = SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date(selectedMonth))
    val summary = summaries.firstOrNull { it.label == label }
    if (summary != null) {
        return when (type) {
            TransactionType.INCOME -> summary.incomeCents
            TransactionType.TRANSFER -> summary.transferCents
            else -> summary.expenseCents
        }
    }
    return entries
        .filter { isSameMonth(it.occurredAt, selectedMonth) && it.transactionType == type }
        .sumOf { it.amountCents }
}

private fun chartMonthDeltaText(current: Long, previous: Long): String {
    if (previous > 0L) {
        val delta = (current - previous).toFloat() / previous.toFloat() * 100f
        return "${if (delta >= 0f) "+" else ""}${delta.toBigDecimal().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}%"
    }
    return if (current > 0L) "+100%" else "0%"
}

private fun chartDeltaColor(current: Long, previous: Long, isIncome: Boolean): Color {
    val delta = current - previous
    return when {
        delta > 0L && isIncome -> Color(0xFF16834A)
        delta > 0L -> Color(0xFFE85D5D)
        delta < 0L && isIncome -> Color(0xFFE85D5D)
        delta < 0L -> Color(0xFF16834A)
        else -> CashFlowTextMuted
    }
}

private fun monthStartMillis(timeMillis: Long = System.currentTimeMillis()): Long {
    return Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = timeMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun shiftMonth(monthStartMillis: Long, delta: Int): Long {
    return Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = monthStartMillis
        add(Calendar.MONTH, delta)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun isSameMonth(timeMillis: Long, monthStartMillis: Long): Boolean {
    val dateFormat = SimpleDateFormat("yyyy-MM", Locale.CHINA)
    return dateFormat.format(Date(timeMillis)) == dateFormat.format(Date(monthStartMillis))
}

private fun buildCategoryTotalsForMonth(
    monthEntries: List<LedgerEntryEntity>,
    categories: List<CategoryEntity>,
    selectedType: TransactionType
): List<CategoryTotal> {
    val categoryMap = categories.associateBy { it.code }
    return monthEntries
        .filter { it.transactionType == selectedType }
        .groupBy { it.categoryCode ?: if (selectedType == TransactionType.INCOME) "other_income" else "other" }
        .map { (categoryCode, entries) ->
            val category = categoryMap[categoryCode]
            CategoryTotal(
                categoryCode = categoryCode,
                categoryName = category?.name ?: entries.firstOrNull()?.categoryNameSnapshot.orEmpty().ifBlank {
                    if (selectedType == TransactionType.INCOME) "其他收入" else "其他"
                },
                amountCents = entries.sumOf { it.amountCents },
                count = entries.size
            )
        }
        .sortedByDescending { it.amountCents }
}

private data class AccountMonthlyStat(
    val account: AccountEntity,
    val expenseCents: Long,
    val incomeCents: Long,
    val transferCents: Long,
    val entryCount: Int
) {
    val netCents: Long get() = incomeCents - expenseCents
    val activityCents: Long get() = expenseCents + incomeCents + transferCents
}

private fun buildCurrentMonthAccountStats(
    confirmedEntries: List<LedgerEntryEntity>,
    accounts: List<AccountEntity>
): List<AccountMonthlyStat> {
    return buildAccountStatsForMonth(confirmedEntries, accounts, monthStartMillis())
}

private fun buildAccountStatsForMonth(
    confirmedEntries: List<LedgerEntryEntity>,
    accounts: List<AccountEntity>,
    selectedMonth: Long
): List<AccountMonthlyStat> {
    val monthEntries = confirmedEntries.filter { isSameMonth(it.occurredAt, selectedMonth) }
    val grouped = monthEntries.groupBy { it.accountCode.ifBlank { "cash" } }
    return accounts.map { account ->
        val entries = grouped[account.code].orEmpty()
        AccountMonthlyStat(
            account = account,
            expenseCents = entries.filter { it.transactionType == TransactionType.EXPENSE }.sumOf { it.amountCents },
            incomeCents = entries.filter { it.transactionType == TransactionType.INCOME }.sumOf { it.amountCents },
            transferCents = entries.filter { it.transactionType == TransactionType.TRANSFER }.sumOf { it.amountCents },
            entryCount = entries.size
        )
    }
}

private data class CalendarCell(
    val dayOfMonth: Int,
    val dateKey: String
)

private fun monthCalendarCells(monthStartMillis: Long): List<CalendarCell?> {
    val calendar = Calendar.getInstance(Locale.CHINA).apply { timeInMillis = monthStartMillis }
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leadingBlanks = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val cells = mutableListOf<CalendarCell?>()
    repeat(leadingBlanks) { cells += null }
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    for (day in 1..daysInMonth) {
        calendar.set(Calendar.DAY_OF_MONTH, day)
        cells += CalendarCell(day, formatter.format(calendar.time))
    }
    while (cells.size % 7 != 0) {
        cells += null
    }
    return cells
}

private fun formatCompactMoney(cents: Long): String {
    return "¥${formatCompactAmount(cents)}"
}

private fun formatCompactAmount(cents: Long): String {
    val amount = cents / 100.0
    return when {
        cents <= 0L -> "0"
        cents >= 1_000_000L -> "${(amount / 10_000).toBigDecimal().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}万"
        cents >= 100_000L -> "${(amount / 1_000).toBigDecimal().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}K"
        cents % 100L == 0L -> "${cents / 100L}"
        else -> cents.toBigDecimal().movePointLeft(2).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}

private fun formatPercent(part: Long, total: Long): String {
    if (total <= 0L || part <= 0L) return "0%"
    val percent = part.toFloat() / total.toFloat() * 100f
    val value = percent.toBigDecimal().setScale(if (percent < 10f) 1 else 0, RoundingMode.HALF_UP).stripTrailingZeros()
    return "${value.toPlainString()}%"
}

private fun suggestCategoryCodeForManualEntry(
    note: String,
    amountText: String,
    selectedType: TransactionType,
    categories: List<CategoryEntity>
): String? {
    val text = listOf(note, amountText)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    if (text.isBlank()) return categories.firstOrNull()?.code
    val suggestion = com.codex.suishouledger.domain.CategorySuggestionEngine.suggestCode(text, categories)
    val preferred = categories.firstOrNull { it.code == suggestion.categoryCode }?.code
    if (preferred != null) return preferred
    return when {
        selectedType == TransactionType.INCOME -> categories.firstOrNull { it.code == "other_income" }?.code ?: categories.firstOrNull()?.code
        else -> categories.firstOrNull { it.code == "other" }?.code ?: categories.firstOrNull()?.code
    }
}

private fun shortCategoryLabel(name: String): String {
    return if (name.length > 4) name.take(4) else name
}

private fun String.toCentsOrNull(): Long? {
    return toBigDecimalOrNull()
        ?.setScale(2, RoundingMode.HALF_UP)
        ?.movePointRight(2)
        ?.toLong()
        ?.takeIf { it > 0 }
}

private fun String.toPositiveCentsOrNull(): Long? {
    return trim().takeIf { it.isNotBlank() }?.toCentsOrNull()
}

private fun LedgerEntryEntity.matchesBillsKeyword(keyword: String): Boolean {
    if (keyword.isBlank()) return true
    return listOf(
        merchant,
        note,
        categoryNameSnapshot.orEmpty(),
        accountNameSnapshot,
        rawText,
        sourceAppName
    ).any { value ->
        value.contains(keyword, ignoreCase = true)
    }
}

private fun categoryEmoji(category: CategoryEntity?): String = categoryIconGlyphOrNull(category?.iconKey) ?: when (category?.code) {
    "food", "delivery", "coffee" -> "🍔"
    "transport", "taxi" -> "🚇"
    "shopping", "daily", "clothing", "electronics" -> "🛍"
    "housing", "utilities" -> "🏠"
    "entertainment" -> "🎮"
    "education" -> "📚"
    "medical" -> "💊"
    "travel" -> "✈️"
    "communication" -> "📶"
    "salary" -> "💼"
    "stock_income" -> "📈"
    "bonus_income" -> "🎁"
    "side_income" -> "🧰"
    "refund_income" -> "↩"
    "other_income", "income" -> "💰"
    "transfer" -> "🔁"
    else -> "📌"
}

private fun categoryEmoji(code: String): String = categoryEmoji(CategoryEntity(code, "", "", "#64748B"))

private fun categoryColor(code: String, categories: List<CategoryEntity> = emptyList()): Color {
    return categories.firstOrNull { it.code == code }?.colorHex?.toColor() ?: defaultCategoryColor(code)
}

private fun defaultCategoryColor(code: String): Color = when (code) {
    "food", "delivery", "coffee" -> Color(0xFFFF6B6B)
    "housing", "utilities" -> Color(0xFF4ECDC4)
    "shopping", "daily", "clothing", "electronics" -> Color(0xFF45B7D1)
    "transport", "taxi" -> Color(0xFF96CEB4)
    "entertainment" -> Color(0xFFFFD166)
    "education" -> Color(0xFFDDA0DD)
    "medical" -> Color(0xFFF8A5C2)
    "travel" -> Color(0xFF6C5CE7)
    "salary" -> Color(0xFF16A34A)
    "stock_income" -> Color(0xFF0EA5E9)
    "bonus_income" -> Color(0xFF22C55E)
    "side_income" -> Color(0xFF14B8A6)
    "refund_income" -> Color(0xFF84CC16)
    "other_income", "income" -> Color(0xFF64748B)
    "transfer" -> Color(0xFFE17055)
    else -> Color(0xFFB2BEC3)
}

private fun accountEmoji(account: AccountEntity): String = accountIconGlyphOrNull(account.iconKey) ?: when (account.code) {
    "wechat" -> "微"
    "alipay" -> "支"
    "bank" -> "卡"
    "cash" -> "现"
    else -> "账"
}

@Composable
private fun AccountIconImage(
    account: AccountEntity,
    size: Dp,
    fallbackStyle: TextStyle = MaterialTheme.typography.labelMedium,
    fallbackWeight: FontWeight = FontWeight.Bold
) {
    val resId = accountIconDrawableRes(account)
    if (resId != null) {
        Image(
            painter = painterResource(resId),
            contentDescription = "${account.name}图标",
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.24f)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(account.colorHex.toColor().copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(accountEmoji(account), style = fallbackStyle, fontWeight = fallbackWeight)
        }
    }
}

private fun accountIconDrawableRes(account: AccountEntity): Int? = when (account.code) {
    "wechat" -> R.drawable.ic_account_wechat
    "alipay" -> R.drawable.ic_account_alipay
    "bank" -> R.drawable.ic_account_bank
    "cash" -> R.drawable.ic_account_cash
    else -> null
}

private fun categoryIconGlyph(key: String): String = categoryIconGlyphOrNull(key) ?: categoryIconGlyphOrNull(DefaultCategoryIconKey).orEmpty()

private fun categoryIconGlyphOrNull(key: String?): String? = CategoryIconOptions.firstOrNull { it.key == key }?.glyph

private fun accountIconGlyph(key: String): String = accountIconGlyphOrNull(key) ?: accountIconGlyphOrNull(DefaultAccountIconKey).orEmpty()

private fun accountIconGlyphOrNull(key: String?): String? = AccountIconOptions.firstOrNull { it.key == key }?.glyph

private fun accountBalancePreview(account: AccountEntity): String {
    val balance = account.balanceCents ?: 0L
    return if (balance > 0L) "余额 ${formatMoney(balance)}" else "未设余额"
}

private fun accountMonthSummaryText(stat: AccountMonthlyStat): String {
    if (stat.entryCount == 0) return "本月暂无已确认流水"
    return "收 ${formatMoney(stat.incomeCents)} · 支 ${formatMoney(stat.expenseCents)} · ${stat.entryCount} 笔"
}

private fun String.toColor(): Color {
    return runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(Color(0xFFB2BEC3))
}

