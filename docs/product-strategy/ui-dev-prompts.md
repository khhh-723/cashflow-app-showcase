# CashFlow (随记账) UI 开发提示词 — Material 3 主题化版本

> 用途：指导 Codex / AI 编程助手用 Jetpack Compose + Material 3 一比一复刻 APP 页面
> 基于设计图：`docs/assets/app-pages-reference.png`（原 `APP页面.png`，15个核心页面）
> 核心原则：**基于 Material 3 设计令牌（Design Tokens）构建，拒绝硬编码，确保主题一致性**
> 当前定位：视觉参考资料。若本文与 `docs/PRODUCT_UI_SPEC.md` 或 `docs/PRODUCT_REQUIREMENTS.md` 冲突，以当前产品规格为准。

---

## 一、设计哲学与主题架构

### 设计方向
采用 **Material 3 + 品牌绿色** 的融合方案。以 Material 3 的规范为骨架，用品牌绿（`#22C55E`）替换默认主色，保持动态色（Dynamic Color）兼容能力。所有视觉决策必须能通过主题系统推导，禁止在组件中写死颜色或尺寸。

### 主题结构（Compose MaterialTheme）

```kotlin
@Composable
fun CashFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Android 12+ 支持动态取色
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
                .copy(primary = BrandGreenDark)
            else dynamicLightColorScheme(context)
                .copy(primary = BrandGreen)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CashFlowTypography,
        shapes = CashFlowShapes,
        content = content
    )
}
```

### 色彩方案（ColorScheme 定制）

```kotlin
// Light
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF22C55E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF334155),
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color.White,
    background = Color(0xFFF8FAF9),
    onBackground = Color(0xFF1F2937),
    surface = Color.White,
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE8EAED),
    error = Color(0xFFEA4335),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E1),
    onErrorContainer = Color(0xFFB3261E),
)

// Dark
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF064E3B),
    primaryContainer = Color(0xFF166534),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = Color(0xFF3C4043),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF370B0B),
)
```

### 语义化色彩映射（业务层）

业务状态色不直接写 hex，而是通过 MaterialTheme.colorScheme 派生或扩展：

```kotlin
// 在 Theme 文件或 UI 工具类中统一提供
object CashFlowColors {
    @Composable
    fun expense() = MaterialTheme.colorScheme.error

    @Composable
    fun income() = if (isSystemInDarkTheme())
        Color(0xFF4ADE80) else Color(0xFF34A853)

    @Composable
    fun budgetWarning() = Color(0xFFFBBC04) // 琥珀色，保持跨主题一致

    @Composable
    fun brandGradient() = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.primary.copy(red = 0.08f, green = 0.64f)
    )
}
```

### 字体排版（Typography）

```kotlin
val CashFlowTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp
    ),
    displayMedium = TextStyle(
        fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp
    ),
    displaySmall = TextStyle(
        fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp
    ),
    headlineLarge = TextStyle(
        fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp
    ),
    headlineMedium = TextStyle(
        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp
    ),
    headlineSmall = TextStyle(
        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp
    ),
    titleLarge = TextStyle(
        fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 12.sp
    ),
)
```

### 形状体系（Shapes）

```kotlin
val CashFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // 小标签、徽章
    small = RoundedCornerShape(8.dp),        // 按钮、输入框
    medium = RoundedCornerShape(12.dp),      // 小卡片、列表项
    large = RoundedCornerShape(16.dp),       // 普通卡片
    extraLarge = RoundedCornerShape(24.dp),  // 大卡片、BottomSheet
)
```

### 间距体系（Spacing Tokens）

```kotlin
object CashFlowSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    val pageHorizontal = lg        // 页面左右统一边距
    val pageVertical = md          // 页面顶部/底部边距
    val cardPadding = lg           // 卡片内部统一内边距
    val sectionGap = xl            // 模块之间间距
    val itemGap = md               // 列表项之间间距
    val elementGap = sm            // 同组元素之间间距
}
```

---

## 二、全局组件规范

### 底部导航栏（CashFlowBottomNav）

```kotlin
@Composable
fun CashFlowBottomNav(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onAddClick: () -> Unit
) {
    // 使用自定义 BottomAppBar + 中央悬浮 FAB
    BottomAppBar(
        tonalElevation = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(64.dp)
    ) {
        // 左侧两个 Tab
        // 中央 FAB（凹陷效果）
        // 右侧两个 Tab
    }
}
```

**规范：**
- Tab 数量：4个（首页、账单、图表、我的）
- 中央 FAB：56dp 圆形，`MaterialTheme.colorScheme.primary` 背景，白色 `+` 图标
- FAB 使用 `FloatingActionButtonDefaults.elevation(6.dp)` 制造悬浮感
- Tab 选中：`MaterialTheme.colorScheme.primary`，未选中：`onSurfaceVariant`
- Tab 图标使用 `MaterialTheme.typography.labelMedium` 对应的尺寸（24dp 图标 + 11sp 文字）
- 顶部 1.dp 分割线：`MaterialTheme.colorScheme.outline`，透明度 50%

### 顶部应用栏（CashFlowTopAppBar）

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowTopAppBar(
    title: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { /* 品牌 Logo + 标题 或纯文字 */ },
        navigationIcon = navigationIcon ?: {},
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.height(56.dp)
    )
}
```

**规范：**
- 高度 56dp，白色/深色表面背景，无阴影（elevation = 0）
- 首页左侧：品牌 Logo（36dp 圆形，主色背景）+ "随记账"（`headlineSmall`）
- 其他页面左侧：返回箭头（`navigationIcon`）+ 页面标题（`headlineSmall`）
- 右侧图标统一 24dp，`onSurfaceVariant` 色

### 卡片组件（CashFlowCard）

```kotlin
@Composable
fun CashFlowCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,  // 默认 16dp
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { Column(modifier = Modifier.padding(CashFlowSpacing.cardPadding)) { content() } }
    )
}
```

**规范：**
- 默认无阴影（elevation = 0），纯靠背景色区分层次
- 大卡片使用 `shapes.extraLarge`（24dp）
- 普通卡片使用 `shapes.large`（16dp）
- 小卡片/列表项使用 `shapes.medium`（12dp）

### 分类图标容器（CategoryIcon）

```kotlin
@Composable
fun CategoryIcon(
    icon: ImageVector,
    color: Color,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(iconSize))
    }
}
```

**规范：**
- 外圈：分类色 12% 透明度背景，圆形
- 图标：分类色，24dp 默认
- 小尺寸场景：32dp 外圈 + 20dp 图标

### 金额文本（AmountText）

```kotlin
@Composable
fun AmountText(
    amount: Long,  // 单位：分
    isExpense: Boolean = true,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    showSign: Boolean = false
) {
    val formatted = remember(amount) {
        val yuan = amount / 100.0
        val sign = when {
            !showSign -> ""
            isExpense -> "-"
            else -> "+"
        }
        String.format("%s%,.2f", sign, kotlin.math.abs(yuan))
    }

    Text(
        text = formatted,
        style = style,
        color = if (isExpense) CashFlowColors.expense() else CashFlowColors.income()
    )
}
```

**规范：**
- 金额统一使用 `Long`（分）存储，显示时转换为元（保留两位小数）
- 支出使用 `error` 色，收入使用自定义 `income()` 绿色
- 大金额：`displayLarge`（36sp），中金额：`displaySmall`（24sp），小金额：`headlineSmall`（16sp）

---

## 三、页面级实现规范

### 页面 01：首页概览（HomeScreen）

```kotlin
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = CashFlowSpacing.pageHorizontal,
            vertical = CashFlowSpacing.pageVertical
        ),
        verticalArrangement = Arrangement.spacedBy(CashFlowSpacing.sectionGap)
    ) {
        item { HomeTopBar(draftCount = uiState.draftCount) }
        item { MonthSelector(selectedMonth = uiState.currentMonth) }
        item { MonthlyOverviewCard(state = uiState.monthlyOverview) }
        item { TopCategoriesRow(categories = uiState.topCategories) }
        if (uiState.pendingDrafts.isNotEmpty()) {
            item { PendingDraftsBanner(count = uiState.pendingDrafts.size) }
        }
        item { RecentTransactionsList(transactions = uiState.recentTransactions) }
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 顶部栏 | 品牌 Logo（36dp 圆形，主色渐变）+ "随记账" + 右侧图标组（搜索/通知/设置） |
| 月份选择器 | `titleMedium` 样式，右侧 `Icons.Default.KeyboardArrowDown`，点击展开 `MonthPickerBottomSheet` |
| 月度概览卡 | 渐变背景（`Brush.linearGradient(CashFlowColors.brandGradient())`），`shapes.extraLarge` 圆角，`Modifier.height(180.dp)` |
| 卡内结构 | 标签 `labelLarge`（白色 70% 透明度）→ 大金额 `displayLarge`（纯白）→ 收支双列 `bodySmall`（白色）→ 进度条 |
| 进度条 | 背景：`Color.White.copy(alpha = 0.3)`，前景纯白，`Modifier.height(6.dp)`，`StrokeCap.Round` |
| TOP分类行 | `Row` 三等分（`Modifier.weight(1f)`），间距 `CashFlowSpacing.md`，每个 `CashFlowCard(shape = shapes.large)` |
| 分类卡内 | `CategoryIcon` + 分类名 `titleSmall` + 金额 `titleMedium` + 占比 `labelMedium` + 微型进度条（3dp 高） |
| 草稿横幅 | `CashFlowCard` + 边框 `BorderStroke(1.dp, primary.copy(alpha = 0.2f))`，铃铛图标 + 文字 |
| 近期账单 | 标题 `headlineSmall` + "查看全部" `labelLarge` 主色，每项 64dp 高，`CategoryIcon` + 双行文字 + 金额时间 |

---

### 页面 02：账单列表（BillsScreen）

```kotlin
@Composable
fun BillsScreen(viewModel: BillsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        BillsTopBar(
            currentMonth = uiState.currentMonth,
            onPrevMonth = viewModel::prevMonth,
            onNextMonth = viewModel::nextMonth
        )
        MonthlySummaryRow(state = uiState.summary)
        CalendarHeatMap(
            days = uiState.calendarDays,
            selectedDay = uiState.selectedDay,
            onDaySelect = viewModel::selectDay
        )
        CategoryFilterChips(
            categories = uiState.availableCategories,
            selected = uiState.selectedCategory,
            onSelect = viewModel::selectCategory
        )
        BillsList(
            groupedBills = uiState.groupedBills,
            modifier = Modifier.weight(1f)
        )
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 月份切换栏 | 56dp 高，返回箭头 + 左右箭头 + 月份文字 `headlineSmall` + 日历/筛选图标 |
| 月度收支概览 | 三等分 Row，标签 `labelLarge`（`onSurfaceVariant`），金额 `headlineMedium`（支出=error，收入=income()，结余=onSurface） |
| 月历热力图 | 7列 Grid，`LazyVerticalGrid(columns = GridCells.Fixed(7))`，cell 约 48dp |
| 日期格子 | 日期数字 `bodyLarge` 在上，支出金额 `labelSmall`（error 色）在下 |
| 热力背景 | 无支出=纯白，有支出从 `errorContainer.copy(alpha = 0.3)` 渐变到 `errorContainer` |
| 选中日期 | `MaterialTheme.colorScheme.primary` 圆形背景，日期文字白色 |
| 分类筛选 | `LazyRow`，间距 `CashFlowSpacing.sm`，Chip 使用 `FilterChip` 或自定义 |
| Chip 样式 | 选中：主色背景 + 白色文字；未选中：`surfaceVariant` 背景 + `onSurfaceVariant` 文字，高度 32dp，`shapes.small`（圆角 16dp，胶囊形） |
| 账单分组 | 日期头部：背景 `surfaceVariant`，文字 `bodySmall`（`onSurfaceVariant`），padding 12dp |
| 账单项 | 同首页近期账单项，草稿额外显示 "忽略"/"确认" 操作按钮 |

---

### 页面 03：账单详情（BillDetailScreen）

```kotlin
@Composable
fun BillDetailScreen(
    transactionId: String,
    viewModel: BillDetailViewModel = hiltViewModel()
) {
    val transaction by viewModel.transaction.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeader(transaction = transaction)
        DetailContentCard(transaction = transaction, modifier = Modifier.weight(1f))
        DetailActionBar(onEdit = {}, onDelete = {}, onDuplicate = {})
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 顶部渐变区 | `Modifier.height(200.dp)`，`Brush.linearGradient` 品牌渐变，左上角返回箭头（白色），右上角更多菜单（白色） |
| 顶部中央 | `CategoryIcon`（48dp 外圈，白色背景，主色图标）+ 分类名 `labelLarge`（白色 90%）+ 金额 `displayLarge`（纯白 Bold） |
| 详情卡片 | `CashFlowCard(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))`，向上偏移 `-20.dp` 覆盖在渐变区下方 |
| 字段列表 | 每项 Row，fillMaxWidth，padding vertical 14dp，左标签 `bodyLarge`（`onSurfaceVariant`），右值 `titleMedium`（`onSurface`） |
| 分割线 | `Divider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)`，左侧缩进避免与文字对齐 |
| 凭证缩略图 | 80dp 正方形，`shapes.medium` 圆角，点击展开全屏查看 |
| 底部操作栏 | Row padding 16dp，三等分：删除（`OutlinedButton`，边框 error）/ 编辑（`OutlinedButton`，边框 primary）/ 复制（`Button`，主色填充） |

---

### 页面 04：图表分析（ChartsScreen）

```kotlin
@Composable
fun ChartsScreen(viewModel: ChartsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CashFlowSpacing.pageHorizontal)
    ) {
        item { ChartTypeTabs(selected = uiState.chartType, onSelect = viewModel::setType) }
        item { TotalAmountCard(amount = uiState.totalAmount, type = uiState.chartType) }
        item { DonutChart(data = uiState.categoryDistribution, modifier = Modifier.size(250.dp)) }
        item { CategoryWeightList(categories = uiState.categoryDistribution) }
        item { TrendLineChart(data = uiState.trendData, period = uiState.trendPeriod) }
        item { BudgetProgressList(budgets = uiState.categoryBudgets) }
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 顶部 Tab | Row 三等分，48dp 高，选中：底部 2dp 主色指示线 + 文字主色 Bold，未选中：`onSurfaceVariant` |
| Tab 切换 | `AnimatedContent` 或 `Crossfade`，duration = 300ms |
| 大金额 | `displayLarge`，支出=error，收入=income() |
| 环形图 | 自定义 Canvas，外径 250dp，strokeWidth = 40dp，内圆白色/深色表面，中心显示总额 |
| 分段颜色 | 使用分类预定义色（见分类色表），按金额占比分配角度 |
| 引线标注 | 前6大分类，引线颜色=分类色，末端文字 `labelSmall` |
| 分类权重列表 | 每项：4dp宽色条 + 分类名 `bodyLarge` + 金额 `titleMedium` + 占比 `labelMedium` + `LinearProgressIndicator`（高度 6dp，颜色=分类色） |
| 趋势折线图 | Canvas 200dp 高，X轴标签 `labelSmall`，折线 3dp 主色，数据点 6dp 主色填充+白色描边，区域渐变填充（主色 10%→0%） |
| 预算进度 | 进度颜色规则：<80%=`income()`，80-99%=budgetWarning，≥100%=`error` |

---

### 页面 05：预算管理（BudgetScreen）

```kotlin
@Composable
fun BudgetScreen(viewModel: BudgetViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CashFlowSpacing.pageHorizontal)
    ) {
        item { TotalBudgetCard(budget = uiState.totalBudget) }
        items(uiState.categoryBudgets) { budget ->
            CategoryBudgetItem(budget = budget)
        }
        item { AddBudgetButton() }
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 总预算卡 | `CashFlowCard`，左侧已用/总额 `displaySmall`，右侧百分比 `headlineMedium`（根据进度变色） |
| 进度条 | 同首页样式，`LinearProgressIndicator`，背景 `surfaceVariant`，进度色根据百分比变色 |
| 分类预算项 | `CategoryIcon`（32dp）+ 分类名 `bodyLarge` + 已用金额 `titleMedium` + 预算金额 `bodySmall`（`onSurfaceVariant`）+ 百分比标签 `labelSmall` |
| 添加按钮 | TextButton，`+ 添加预算`，主色文字，居中，底部 padding 24dp |

---

### 页面 06：草稿审核（DraftReviewScreen）

```kotlin
@Composable
fun DraftReviewScreen(viewModel: DraftReviewViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DraftStatusTabs(selected = uiState.filter, onSelect = viewModel::setFilter)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(CashFlowSpacing.pageHorizontal)
        ) {
            items(uiState.filteredDrafts) { draft ->
                DraftItemCard(draft = draft, onAction = viewModel::handleAction)
            }
        }
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 状态 Tab | "待确认(n)" / "已忽略" / "全部"，`TabRow` 或自定义，选中指示线 2dp 主色 |
| 草稿卡片 | `CashFlowCard`，内部垂直排列 |
| 卡片头 | 来源图标（24dp）+ 来源名 `labelMedium`（来源色）+ 商户名 `titleMedium` + 时间 `labelSmall`（`onSurfaceVariant`）+ 金额 `headlineSmall`（Bold） |
| 标签行 | 分类 Chip（分类色 20% 背景 + 分类色文字）+ 状态标签（"待识别"=琥珀色背景） |
| 操作按钮 | Row：忽略（`TextButton`，`onSurfaceVariant`）/ 编辑（`OutlinedButton`，主色边框）/ 确认入账（`Button`，主色填充，高度 32dp） |
| 空状态 | 居中：大图标 80dp（`onSurfaceVariant`）+ "所有草稿已处理完毕" `bodyLarge` + 庆祝 emoji |

---

### 页面 07：草稿详情（DraftDetailScreen）

```kotlin
@Composable
fun DraftDetailScreen(draftId: String) {
    // 结构同 BillDetailScreen，增加识别信息区域
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 顶部 | 白色背景（无渐变），大金额居中 `displayLarge`，右上角状态 Chip（成功=绿色背景白色文字，失败=error 背景） |
| 识别来源 | `CashFlowCard`，来源图标 + "识别来源：微信支付通知" `bodyLarge` |
| 原始文本 | `TextField`（readOnly=true）或自定义 Card，背景 `surfaceVariant`，等宽字体 12sp，最大高度 120dp，可滚动 |
| 可编辑字段 | 同账单详情，但分类和账户旁显示编辑铅笔图标 |
| 底部按钮 | 忽略（`OutlinedButton`，灰色边框）+ 确认入账（`Button`，主色，fillMaxWidth，48dp 高） |

---

### 页面 08：AI 对话助手（AiChatScreen）

```kotlin
@Composable
fun AiChatScreen(viewModel: AiChatViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AiChatTopBar() },
        bottomBar = { AiChatInputBar(input = inputText, onSend = viewModel::send) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            reverseLayout = true,
            contentPadding = PaddingValues(CashFlowSpacing.lg)
        ) {
            items(messages.reversed()) { message ->
                ChatMessageItem(message = message)
            }
        }
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 顶部栏 | "AI助手" `headlineSmall` 居中，左侧返回箭头 |
| 用户气泡 | 靠右，`Card` 主色背景（`primary`），白色文字，`shapes.large`（左下直角，其他圆角），maxWidth 80%，padding 12dp，`bodyLarge` |
| AI 气泡 | 靠左，`Card` 表面背景（`surface`），`onSurface` 文字，1dp `outline` 边框，`shapes.large`（右下直角），开头带机器人图标（16dp，主色）+ "AI助手" `labelMedium` |
| 快捷卡片 | AI 发送的操作卡片，垂直排列 `OutlinedButton`，边框 `primary.copy(alpha = 0.3)`，`labelLarge` 主色文字 |
| 记账确认卡 | 显示解析结果（金额/分类/账户/时间），每项一行，底部 "取消"（`TextButton`）+ "确认"（`Button` 主色） |
| 底部输入区 | `TextField`（`shapes.medium` 圆角，背景 `surfaceVariant`，占位符"有什么可以帮你？"）+ 圆形发送按钮（48dp，主色，白色图标，空输入时灰色禁用） |

---

### 页面 09：账户管理（AccountScreen）

```kotlin
@Composable
fun AccountScreen(viewModel: AccountViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CashFlowSpacing.pageHorizontal)
    ) {
        item { NetWorthCard(netWorth = uiState.netWorth) }
        item { SectionTitle("我的账户") }
        items(uiState.accounts) { account ->
            AccountListItem(account = account)
        }
        item { AddAccountButton() }
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 净资产卡 | `CashFlowCard`，高度 140dp，品牌渐变背景，`shapes.extraLarge` |
| 卡内 | "净资产" `labelLarge`（白色 70%）+ 金额 `displayLarge`（白色）+ 总资产/负债 `bodySmall`（白色） |
| 账户项 | 账户图标（36dp 圆形，账户色背景，白色图标）+ 账户名 `titleMedium` + 余额 `titleMedium`（Bold，未设置显示 "未设置" `bodySmall` `onSurfaceVariant`）+ 箭头 |
| 系统账户 | 不可删除，无滑动操作 |
| 自定义账户 | 左滑显示 "编辑"（主色）和 "删除"（error） |

---

### 页面 10：分类管理（CategoryScreen）

```kotlin
@Composable
fun CategoryScreen(viewModel: CategoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        CategoryTypeTabs(selected = uiState.type, onSelect = viewModel::setType)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.categories) { category ->
                CategoryListItem(category = category)
            }
        }
        AddCategoryButton()
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 类型 Tab | "支出分类" / "收入分类"，选中主色 + 指示线 |
| 分类项 | `CategoryIcon`（32dp）+ 分类名 `titleMedium` + 系统标签（"系统" `labelSmall`，`surfaceVariant` 背景）+ 预算（如有）+ 箭头 |
| 系统分类 | 不可删除、不可重命名 |
| 自定义分类 | 左滑编辑/删除 |
| 添加按钮 | 底部 "+ 添加分类" `TextButton` 主色 |

---

### 页面 11：搜索筛选（SearchScreen）

```kotlin
@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SearchInputField(query = uiState.query, onQueryChange = viewModel::setQuery)
        TimeFilterChips(selected = uiState.timeFilter, onSelect = viewModel::setTimeFilter)
        AdvancedFilters(
            category = uiState.categoryFilter,
            account = uiState.accountFilter,
            minAmount = uiState.minAmount,
            maxAmount = uiState.maxAmount
        )
        SearchActionBar(onSearch = viewModel::search, onReset = viewModel::reset)
        if (uiState.query.isBlank()) {
            SearchHistoryList(history = uiState.searchHistory)
        } else {
            SearchResultsList(results = uiState.results)
        }
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 搜索框 | `TextField`，fillMaxWidth，48dp 高，`shapes.small` 圆角，背景 `surfaceVariant`，左侧搜索图标，占位符 "搜索账单备注、商户..."，右侧清除图标 |
| 时间 Chip | "今天"/"本周"/"本月"/"自定义"，高度 32dp，`shapes.small`（胶囊形），选中主色背景白字，未选中 `surfaceVariant` 背景 `onSurfaceVariant` |
| 高级筛选 | 分类/账户下拉（`ExposedDropdownMenuBox`），金额范围两个小 `TextField`（64dp 宽）用 "-" 连接 |
| 搜索按钮 | `Button`，主色，fillMaxWidth，44dp 高 |
| 重置按钮 | `TextButton`，`onSurfaceVariant`，居中 |
| 搜索历史 | `FlowRow` 或 `Wrap`，历史标签 Chip，可点击，右侧带 × 删除 |
| 结果列表 | 同账单列表项样式，底部显示 "共找到 X 条结果" `labelMedium`（`onSurfaceVariant`） |

---

### 页面 12：截图识别预览（OcrPreviewScreen）

```kotlin
@Composable
fun OcrPreviewScreen(viewModel: OcrPreviewViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        OcrPreviewTopBar()
        ScreenshotPreviewCard(imageUri = uiState.imageUri, count = uiState.resultCount)
        RecognizedItemsList(
            items = uiState.recognizedItems,
            onToggle = viewModel::toggleItem,
            modifier = Modifier.weight(1f)
        )
        ConfirmGenerateButton(selectedCount = uiState.selectedCount, totalAmount = uiState.selectedTotal)
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 顶部栏 | 返回箭头 + "识别预览" `headlineSmall` |
| 截图预览 | `CashFlowCard`，截图 `Image`，fillMaxWidth，200dp 高，`shapes.large` 顶部圆角，底部叠加黑色半透明遮罩 + "共识别到 X 笔" 白色 `titleMedium` |
| 结果列表 | 每项带 `Checkbox`（左侧），商户名 + 金额 + 时间 |
| 底部统计 | "已选择 X 笔，合计 ¥XX.XX" `bodyLarge`，居中 |
| 确认按钮 | `Button`，主色，"确认生成(X)"，fillMaxWidth，48dp 高 |

---

### 页面 13：数据同步（SyncScreen）

```kotlin
@Composable
fun SyncScreen(viewModel: SyncViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CashFlowSpacing.pageHorizontal)
    ) {
        item { SyncStatusCard(status = uiState.syncStatus) }
        item { SyncStatsRow(stats = uiState.syncStats) }
        item { SectionTitle("最近同步记录") }
        items(uiState.recentLogs) { log ->
            SyncLogItem(log = log)
        }
        item { SyncNowButton(onClick = viewModel::syncNow) }
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 状态卡 | `CashFlowCard`，边框 `income()`（成功时），Row：对勾图标（24dp，绿色）+ "同步完成" `headlineSmall`（绿色）+ 时间 `bodySmall` |
| 统计行 | 3列等分，标签 `labelLarge` + 数字 `headlineMedium` |
| 同步记录 | 时间 `bodySmall` + 状态 `labelMedium`（成功=绿色，失败=error）+ 条数 `bodySmall` |
| 同步按钮 | `Button`，主色，"立即同步"，fillMaxWidth |

---

### 页面 14：设置中心（SettingsScreen）

```kotlin
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(uiState.settingsGroups) { group ->
            SettingsGroup(title = group.title, items = group.items)
        }
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 分组标题 | 12sp，`onSurfaceVariant`，大写，padding(top=20dp, bottom=8dp, start=16dp) |
| 设置项 | Row，56dp 高，16dp 水平 padding，白色/表面背景 |
| 左侧图标 | 20dp，主色，在 36dp 圆形 `primaryContainer` 背景中 |
| 标题 | `titleMedium` |
| 右侧 | Switch（选中=主色）或 值文字 `bodySmall`（`onSurfaceVariant`）+ 箭头 |
| 退出登录 | 文字 `error` 色，无图标或红色图标 |
| 分割线 | 0.5dp `outline`，左侧缩进 56dp（图标右边缘） |

---

### 页面 15：个人中心（ProfileScreen）

```kotlin
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { UserProfileCard(user = uiState.user) }
        items(uiState.menuItems) { item ->
            ProfileMenuItem(item = item)
        }
    }
}
```

**视觉规范：**

| 区域 | 实现细节 |
|------|---------|
| 用户卡片 | `CashFlowCard`，140dp 高，品牌渐变，`shapes.extraLarge` |
| 卡内 | 头像（56dp 圆形，白色背景，绿色用户图标）+ 用户名 `headlineSmall`（白色）+ "已登录" 标签（10sp，白色背景 20% 透明度，圆角 4dp）+ 白色箭头 |
| 菜单项 | 同设置项，但图标 24dp 直接显示（无圆形背景），`onSurfaceVariant` 色 |
| 菜单列表 | 个人信息、会员中心、数据备份、数据导出、使用统计、帮助与反馈、关于随记账 |

---

## 四、公共弹窗与 BottomSheet

### 快速记账 BottomSheet（QuickEntryBottomSheet）

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryBottomSheet(
    onDismiss: () -> Unit,
    onSave: (TransactionDraft) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CashFlowSpacing.xxl)
        ) {
            // 顶部横条
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(CashFlowSpacing.lg))

            // 类型切换
            TransactionTypeToggle()

            // 金额输入
            AmountInputField()

            // 分类网格
            CategoryGrid()

            // 账户选择
            AccountSelector()

            // 备注
            NoteInputField()

            // 导入截图
            OutlinedButton(onClick = { /* */ }) {
                Icon(Icons.Default.Image, null)
                Text("导入截图")
            }

            // 保存按钮
            Button(
                onClick = { /* */ },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                Text("保存")
            }
        }
    }
}
```

**规范：**
- 顶部圆角 `shapes.extraLarge`（24dp），初始高度约 500dp
- 顶部小横条 40dp×4dp，`outline` 色
- 类型切换：胶囊形 Toggle，支出/收入，`shapes.small` 圆角，选中主色
- 金额输入："¥" + 大输入框，`displayLarge` 样式，居中
- 分类网格：4 列 `LazyVerticalGrid`，每项 `CategoryIcon` + 分类名 `labelMedium`
- 保存按钮：`Button`，主色，fillMaxWidth，48dp 高，`shapes.small`（8dp 圆角）

### 确认 Dialog

```kotlin
@Composable
fun CashFlowAlertDialog(
    title: String,
    text: String,
    confirmText: String = "确认",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Button(onClick = onConfirm, shape = MaterialTheme.shapes.small) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}
```

**规范：**
- 圆角 `shapes.large`（16dp）
- 标题 `headlineSmall`，内容 `bodyLarge`
- 确认按钮：`Button` 主色，`shapes.small`
- 取消按钮：`TextButton`

---

## 五、动画与交互规范

### 页面转场

```kotlin
// 使用 Compose Navigation 的动画
val enterTransition = fadeIn(animationSpec = tween(300)) +
    slideInHorizontally(animationSpec = tween(300)) { it / 4 }

val exitTransition = fadeOut(animationSpec = tween(300)) +
    slideOutHorizontally(animationSpec = tween(300)) { it / 4 }
```

### 列表项入场

```kotlin
@Composable
fun <T> AnimatedListItem(
    index: Int,
    content: @Composable () -> Unit
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(200, delayMillis = index * 30)) +
            slideInVertically(tween(200, delayMillis = index * 30)) { it / 2 }
    ) {
        content()
    }
}
```

### 数字变化动画

```kotlin
@Composable
fun AnimatedAmount(amount: Long, isExpense: Boolean = true) {
    val animatedValue by animateFloatAsState(
        targetValue = amount / 100f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "amount"
    )

    Text(
        text = String.format("%,.2f", animatedValue),
        style = MaterialTheme.typography.displayLarge,
        color = if (isExpense) CashFlowColors.expense() else CashFlowColors.income()
    )
}
```

### 进度条动画

```kotlin
@Composable
fun AnimatedProgressBar(progress: Float, color: Color) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "progress"
    )

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round
    )
}
```

---

## 六、分类与账户色彩映射表

分类和账户的颜色应在数据库或配置中持久化，而非硬编码。但系统默认提供以下配色：

### 支出分类色

| 分类 | 颜色 Token |
|------|-----------|
| 餐饮美食 | `Color(0xFFEA4335)` |
| 日常购物 | `Color(0xFFFB923C)` |
| 交通出行 | `Color(0xFF3B82F6)` |
| 休闲娱乐 | `Color(0xFFA855F7)` |
| 医疗健康 | `Color(0xFFEF4444)` |
| 教育学习 | `Color(0xFFF59E0B)` |
| 居住缴费 | `Color(0xFF14B8A6)` |
| 其他支出 | `Color(0xFF64748B)` |

### 收入分类色

| 分类 | 颜色 Token |
|------|-----------|
| 工资薪金 | `Color(0xFF22C55E)` |
| 投资理财 | `Color(0xFF3B82F6)` |
| 兼职收入 | `Color(0xFFA855F7)` |
| 红包礼金 | `Color(0xFFFB923C)` |
| 其他收入 | `Color(0xFF64748B)` |

### 账户色

| 账户 | 颜色 Token |
|------|-----------|
| 微信支付 | `Color(0xFF22C55E)` |
| 支付宝 | `Color(0xFF1677FF)` |
| 银行卡 | `Color(0xFF6366F1)` |
| 现金 | `Color(0xFF64748B)` |

> 自定义分类/账户的颜色应由用户从预设调色板中选择，持久化到数据库。系统默认分类/账户使用上表中的固定色。

---

## 七、图标映射表（Material Icons）

使用 Material Icons Extended 库，避免自定义 SVG。

| 功能 | Icon 名称 | 说明 |
|------|----------|------|
| 首页 | `Icons.Outlined.Home` / `Icons.Filled.Home` | 底部导航 |
| 账单 | `Icons.Outlined.Receipt` / `Icons.AutoMirrored.Filled.Receipt` | 底部导航 |
| 图表 | `Icons.Outlined.PieChart` / `Icons.Filled.PieChart` | 底部导航 |
| 我的 | `Icons.Outlined.Person` / `Icons.Filled.Person` | 底部导航 |
| 添加 | `Icons.Default.Add` | FAB |
| 搜索 | `Icons.Outlined.Search` / `Icons.Default.Search` | 顶部栏 |
| 通知 | `Icons.Outlined.Notifications` | 顶部栏，有草稿时显示红点 |
| 设置 | `Icons.Outlined.Settings` | 顶部栏/设置页 |
| 返回 | `Icons.AutoMirrored.Filled.ArrowBack` | 导航返回 |
| 更多 | `Icons.Default.MoreVert` | 菜单 |
| 日历 | `Icons.Outlined.CalendarToday` | 账单页 |
| 筛选 | `Icons.Outlined.FilterList` | 账单页 |
| 编辑 | `Icons.Outlined.Edit` | 详情页 |
| 删除 | `Icons.Outlined.Delete` | 详情页 |
| 复制 | `Icons.Outlined.ContentCopy` | 详情页 |
| 微信 | `Icons.Outlined.AccountBalanceWallet` | 账户/来源 |
| 支付宝 | `Icons.Outlined.AccountBalance` | 账户/来源 |
| 银行卡 | `Icons.Outlined.CreditCard` | 账户 |
| 现金 | `Icons.Outlined.Payments` | 账户 |
| 餐饮 | `Icons.Outlined.Restaurant` | 分类 |
| 购物 | `Icons.Outlined.ShoppingBag` | 分类 |
| 交通 | `Icons.Outlined.DirectionsBus` | 分类 |
| 娱乐 | `Icons.Outlined.SportsEsports` | 分类 |
| 医疗 | `Icons.Outlined.LocalHospital` | 分类 |
| 教育 | `Icons.Outlined.School` | 分类 |
| 居住 | `Icons.Outlined.Home` | 分类 |
| 工资 | `Icons.Outlined.Work` | 分类 |
| 投资 | `Icons.Outlined.TrendingUp` | 分类 |
| 红包 | `Icons.Outlined.Redeem` | 分类 |
| 发送 | `Icons.AutoMirrored.Filled.Send` | 聊天输入 |
| 图片 | `Icons.Outlined.Image` | 导入截图 |
| 对勾 | `Icons.Default.CheckCircle` | 同步成功 |
| 警告 | `Icons.Outlined.Warning` | 错误状态 |
| 帮助 | `Icons.Outlined.HelpOutline` | 帮助反馈 |
| 信息 | `Icons.Outlined.Info` | 关于 |
| 退出 | `Icons.AutoMirrored.Filled.Logout` | 退出登录 |

---

## 八、实现原则 checklist

开发每个页面时，请按以下顺序检查：

- [ ] **主题一致性**：所有颜色通过 `MaterialTheme.colorScheme` 或 `CashFlowColors` 获取，不直接写 `Color(0xFF...)`
- [ ] **字体层级**：使用 `MaterialTheme.typography` 定义好的层级（`display`/`headline`/`title`/`body`/`label`），不直接写 `fontSize = 16.sp`
- [ ] **形状体系**：圆角通过 `MaterialTheme.shapes` 获取，不用魔法数字
- [ ] **间距体系**：使用 `CashFlowSpacing` 定义的令牌，保持 4dp 基倍数
- [ ] **无硬编码尺寸**：组件尺寸通过 Modifier 约束或基线网格推导，不随意写 123.dp
- [ ] **无障碍支持**：所有图标添加 `contentDescription`，按钮最小触摸目标 48dp
- [ ] **深色模式**：使用 `isSystemInDarkTheme()` 判断，颜色通过 `MaterialTheme` 自动切换
- [ ] **动画规范**：列表入场、页面切换、数字变化使用统一的动画时长和缓动函数
- [ ] **状态处理**：每个页面实现 Loading / Empty / Error / Content 四种状态
- [ ] **预览支持**：每个 Composable 提供 `@Preview`（light + dark 双主题）

---

> **给 Codex 的使用说明**：
> 1. 首先实现 `CashFlowTheme`、`CashFlowTypography`、`CashFlowShapes`、`CashFlowSpacing`、`CashFlowColors`
> 2. 然后实现全局组件（BottomNav、TopAppBar、Card、CategoryIcon、AmountText）
> 3. 按页面顺序逐个实现，每个页面严格对照设计图的布局结构和上述规范
> 4. 所有颜色、字体、形状、间距必须通过主题系统引用，禁止在组件内部硬编码
> 5. 完成每个页面后，运行预览验证 light/dark 双主题效果
