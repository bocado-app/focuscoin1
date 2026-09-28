package com.focuscoin.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

private val CoinGold = Color(0xFFE7AA32)
private val Mint = Color(0xFF168C83)

private data class TabItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val tabs = listOf(
    TabItem("home", "홈", Icons.Filled.Home),
    TabItem("apps", "잠금 앱", Icons.Filled.Apps),
    TabItem("report", "리포트", Icons.Filled.BarChart),
    TabItem("settings", "설정", Icons.Filled.Settings)
)

@Composable
fun FocusCoinRoot(
    state: MainUiState,
    viewModel: FocusCoinViewModel,
    permissionRefresh: Int,
    startWithTimer: Boolean,
    requestNotifications: () -> Unit
) {
    if (!state.isReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (!state.preferences.onboardingComplete) {
        OnboardingScreen(
            context = androidx.compose.ui.platform.LocalContext.current,
            permissionRefresh = permissionRefresh,
            requestNotifications = requestNotifications,
            onContinue = viewModel::finishOnboarding
        )
        return
    }

    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val showBottomBar = route in tabs.map { it.route }
    Scaffold(
        bottomBar = {
            if (showBottomBar) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = if (startWithTimer) "timer" else "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(state, onStart = { nav.navigate("timer") }) }
            composable("apps") { AppsScreen(state, viewModel, permissionRefresh) }
            composable("report") { ReportScreen(state, permissionRefresh) }
            composable("settings") { SettingsScreen(state, viewModel, permissionRefresh, requestNotifications) }
            composable("timer") { TimerScreen(state, viewModel, onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun OnboardingScreen(
    context: Context,
    permissionRefresh: Int,
    requestNotifications: () -> Unit,
    onContinue: () -> Unit
) {
    val usageGranted = remember(permissionRefresh) { PermissionStatus.usageAccessGranted(context) }
    val accessibilityGranted = remember(permissionRefresh) { PermissionStatus.accessibilityEnabled(context) }
    val notificationGranted = remember(permissionRefresh) { PermissionStatus.notificationsGranted(context) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text("₵", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Text("FocusCoin", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("공부로 코인을 모아, 휴대폰 사용 시간을 직접 관리하세요.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("공부한 시간에 따라 코인을 받고, 직접 고른 앱의 이용권을 코인으로 열 수 있어요. 데이터는 이 기기에만 저장됩니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        PermissionCard(
            title = "알림 권한",
            description = "집중 타이머가 끝났을 때 정산 결과를 알림으로 보여줍니다. 허용하지 않아도 타이머와 코인 기능은 동작합니다.",
            granted = notificationGranted,
            actionLabel = if (notificationGranted) "허용됨" else "알림 허용 선택",
            icon = Icons.Filled.Notifications,
            onClick = requestNotifications
        )
        PermissionCard(
            title = "사용 기록 접근",
            description = "허용하면 사용자가 선택한 앱의 화면 사용 시간을 기기에서 조회해 리포트에 표시합니다. 기록을 서버로 보내거나 별도 저장하지 않습니다.",
            granted = usageGranted,
            actionLabel = if (usageGranted) "허용됨" else "설정 열기",
            icon = Icons.Filled.BarChart,
            onClick = { PermissionStatus.openUsageSettings(context) }
        )
        PermissionCard(
            title = "접근성 서비스",
            description = "선택한 잠금 앱이 열렸는지만 확인해 집중 안내를 띄웁니다. 화면 글자, 키 입력, 비밀번호, 메시지와 화면 캡처는 읽거나 저장하지 않습니다.",
            granted = accessibilityGranted,
            actionLabel = if (accessibilityGranted) "켜짐" else "접근성 설정 열기",
            icon = Icons.Filled.Shield,
            onClick = { PermissionStatus.openAccessibilitySettings(context) }
        )
        Text("권한은 선택 사항입니다. 잠금 앱 차단과 일부 리포트만 제한되며, 공부 타이머와 코인 기능은 계속 사용할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
            Text("FocusCoin 시작하기", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (granted) Icon(Icons.Filled.CheckCircle, "허용됨", tint = Mint)
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(actionLabel) }
        }
    }
}

@Composable
private fun HomeScreen(state: MainUiState, onStart: () -> Unit) {
    val today = startOfDay(System.currentTimeMillis())
    val todaysSessions = state.sessions.filter { it.startedAt >= today }
    val todaysTransactions = state.transactions.filter { it.createdAt >= today }
    val studyMinutes = todaysSessions.sumOf { it.completedMinutes }
    val earned = todaysTransactions.filter { it.type == "EARN" || it.type == "BONUS" }.sumOf { it.amount }
    val spent = todaysTransactions.filter { it.type == "SPEND" }.sumOf { it.amount }
    val timer = state.preferences.timer
    val modeLabel = when (timer.status) { "RUNNING" -> "진행 중"; "PAUSED" -> "일시정지"; else -> "비활성" }
    val encouragement = listOf("오늘의 작은 집중이 큰 변화를 만들어요.", "한 번에 한 가지, 지금 할 일에 집중해요.", "꾸준함은 이미 실력입니다.")[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % 3]
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("안녕하세요", "오늘도 나에게 집중해볼까요?") }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("보유 코인", color = Color.White.copy(alpha = .82f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.preferences.balance}", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp)); Text("코인", color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.titleMedium)
                    }
                    Text("공부한 만큼 차곡차곡 모아보세요.", color = Color.White.copy(alpha = .82f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("오늘 공부", "${studyMinutes / 60}시간 ${studyMinutes % 60}분", Modifier.weight(1f))
                MetricCard("오늘 획득", "+$earned", Modifier.weight(1f), CoinGold)
                MetricCard("오늘 사용", "−$spent", Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("공부 모드", fontWeight = FontWeight.Bold)
                        Text(modeLabel, color = if (timer.status == "RUNNING") Mint else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (timer.status == "IDLE") OutlinedButton(onClick = onStart) { Text("시작") }
                    else Button(onClick = onStart) { Text("타이머 열기") }
                }
            }
        }
        item {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(19.dp)) {
                Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("공부 시작", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✦", color = MaterialTheme.colorScheme.secondary, fontSize = 24.sp)
                Text(encouragement, Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        } }
        item { Text("선택한 잠금 앱 ${state.appLocks.count { it.isLocked }}개", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
        }
    }
}

@Composable
private fun AppsScreen(state: MainUiState, viewModel: FocusCoinViewModel, permissionRefresh: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accessibility = remember(permissionRefresh) { PermissionStatus.accessibilityEnabled(context) }
    var showSystem by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<Pair<InstalledApp, AppLockEntity>?>(null) }
    val configByPackage = state.appLocks.associateBy { it.packageName }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            PageHeading("잠금 앱", "집중할 때 잠글 앱을 직접 골라보세요")
            if (!accessibility) {
                Card(Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("앱 잠금 기능을 사용하려면 접근성 서비스를 켜주세요.", fontWeight = FontWeight.SemiBold)
                        Text("선택 앱 실행 여부만 확인하며, 앱 콘텐츠는 읽지 않습니다.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { PermissionStatus.openAccessibilitySettings(context) }) { Text("접근성 설정 열기") }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("시스템 앱 표시", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(checked = showSystem, onCheckedChange = { showSystem = it })
            }
            if (state.appListError) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("앱 목록을 가져오지 못했어요.")
                        TextButton(onClick = viewModel::retryLoadApps) { Text("다시 시도") }
                    }
                }
            }
        }
        val visibleApps = state.installedApps.filter { showSystem || !it.isSystemApp }
        if (visibleApps.isEmpty() && !state.appListError) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("표시할 앱이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleApps, key = { it.packageName }) { app ->
                    val config = configByPackage[app.packageName]
                    AppLockRow(
                        app = app,
                        config = config,
                        onToggle = { viewModel.setAppSelected(app, it) },
                        onEdit = { editingApp = app to (config ?: AppLockEntity(app.packageName, app.appName, false)) }
                    )
                }
            }
        }
    }
    editingApp?.let { (app, config) -> PricingDialog(config, onDismiss = { editingApp = null }, onSave = { minutes, cost ->
        viewModel.updatePricing(app.packageName, minutes, cost); editingApp = null
    }) }
}

@Composable
private fun AppLockRow(app: InstalledApp, config: AppLockEntity?, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val locked = config?.isLocked == true
    val remaining = ((config?.unlockedUntil ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (app.icon != null) Image(app.icon.asImageBitmap(), app.appName, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
            else Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Text(app.appName.take(1)) }
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.appName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (app.isSystemApp) Text("  잠금 비추천", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    when {
                        remaining > 0 -> "해제 ${remaining}분 남음 · 다음 ${config?.unlockCostCoins ?: 0}코인"
                        locked -> "잠금 중 · ${config?.unlockDurationMinutes ?: 30}분 / ${config?.unlockCostCoins ?: 30}코인"
                        else -> "잠금 꺼짐"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (locked) IconButton(onClick = onEdit) { Text("⋯", fontSize = 22.sp) }
            Switch(checked = locked, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun PricingDialog(config: AppLockEntity, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var minutes by remember(config) { mutableStateOf(config.unlockDurationMinutes.toString()) }
    var coins by remember(config) { mutableStateOf(config.unlockCostCoins.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${config.appName} 이용권") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("앱을 사용할 시간과 필요한 코인을 정하세요.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("해제 시간 (분)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(coins, { coins = it.filter(Char::isDigit).take(5) }, label = { Text("필요 코인") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(minutes.toIntOrNull()?.coerceIn(1, 240) ?: 30, coins.toIntOrNull()?.coerceIn(1, 10_000) ?: 30) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun ReportScreen(state: MainUiState, permissionRefresh: Int) {
    var weekly by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val todayStart = startOfDay(now)
    val weekStart = startOfWeek(now)
    val start = if (weekly) weekStart else todayStart
    val sessions = state.sessions.filter { it.startedAt >= start }
    val transactions = state.transactions.filter { it.createdAt >= start }
    val minutes = sessions.sumOf { it.completedMinutes }
    val completed = sessions.count { it.status == "COMPLETED" }
    val earned = transactions.filter { it.type == "EARN" || it.type == "BONUS" }.sumOf { it.amount }
    val spent = transactions.filter { it.type == "SPEND" }.sumOf { it.amount }
    val emergencyCount = transactions.count { it.type == "EMERGENCY_UNLOCK" }
    val appSpend = transactions.filter { it.type == "SPEND" && it.relatedPackageName != null }.groupBy { it.relatedPackageName }
    val appMap = state.appLocks.associateBy { it.packageName }
    val context = androidx.compose.ui.platform.LocalContext.current
    val usageAllowed = remember(permissionRefresh) { PermissionStatus.usageAccessGranted(context) }
    var appUseSummary by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(state.appLocks, usageAllowed, weekly, permissionRefresh) {
        appUseSummary = if (usageAllowed) withContext(Dispatchers.IO) {
            UsageStatsAccess.selectedAppUsage(context, state.appLocks.filter { it.isLocked }, weekly)
        } else emptyMap()
    }
    val priorWeekStart = weekStart - 7L * 24 * 60 * 60 * 1000
    val priorWeekMinutes = state.sessions.filter { it.startedAt in priorWeekStart until weekStart }.sumOf { it.completedMinutes }
    val abandon50 = sessions.count { it.plannedMinutes == 50 && it.status == "ABANDONED" }
    val topApp = appSpend.maxByOrNull { (_, items) -> items.size }
    val lastCompleted = state.sessions.filter { it.status == "COMPLETED" }.maxOfOrNull { it.endedAt } ?: 0L
    val feedback = when {
        weekly && topApp != null -> {
            val packageName = topApp.key
            val appName = packageName?.let { appMap[it]?.appName ?: state.installedApps.firstOrNull { app -> app.packageName == it }?.appName } ?: "선택 앱"
            "이번 주에는 $appName 사용 시간이 많았어요."
        }
        weekly && minutes > priorWeekMinutes -> "지난주보다 더 오래 집중했어요. 좋은 흐름을 이어가요!"
        abandon50 >= 2 -> "50분 세션을 마치기 어려웠다면 25분 집중 모드를 시도해볼까요?"
        now - lastCompleted >= 3L * 24 * 60 * 60 * 1000 -> "오늘은 25분부터 가볍게 시작해보세요."
        else -> "집중한 시간은 모두 의미가 있어요. 지금의 리듬을 이어가 보세요."
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("리포트", "내 집중 습관을 살펴보세요") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("오늘") })
                FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("이번 주") })
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${minutes / 60}시간 ${minutes % 60}분", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("총 공부 시간", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Divider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ReportValue("완주 세션", "$completed")
                        ReportValue("획득 코인", "+$earned")
                        ReportValue("사용 코인", "−$spent")
                    }
                }
            }
        }
        if (weekly) item { WeeklyChart(state.sessions, weekStart, now) }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("앱 잠금 이용", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (appSpend.isEmpty()) Text("아직 코인으로 구매한 앱 이용권이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    appSpend.forEach { (packageName, purchases) ->
                        val duration = purchases.sumOf { it.durationMinutes }
                        val appName = packageName?.let { appMap[it]?.appName ?: state.installedApps.firstOrNull { app -> app.packageName == it }?.appName } ?: "선택 앱"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(appName, modifier = Modifier.weight(1f))
                            Text("${purchases.size}회 · ${duration}분", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Divider()
                    Text("긴급 해제 ${emergencyCount}회", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (usageAllowed) item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("선택 앱 화면 사용 시간", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("기기 사용 기록에서 선택 앱만 조회하며, FocusCoin에 저장하지 않습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (appUseSummary.isEmpty()) Text("표시할 기록이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    appUseSummary.toList().sortedByDescending { it.second }.forEach { (packageName, duration) ->
                        val name = state.installedApps.firstOrNull { it.packageName == packageName }?.appName ?: packageName
                        Text("$name · ${duration / 60_000}분")
                    }
                }
            }
        } else item {
            TextButton(onClick = { PermissionStatus.openUsageSettings(context) }) { Text("앱 사용 시간 리포트를 위해 사용 기록 접근 설정") }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("나를 위한 한마디", fontWeight = FontWeight.Bold)
                    Text(feedback, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun ReportValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeeklyChart(sessions: List<StudySessionEntity>, weekStart: Long, now: Long) {
    val days = (0..6).map { day ->
        val start = weekStart + day * 24L * 60 * 60 * 1000
        val value = sessions.filter { it.startedAt in start until start + 24L * 60 * 60 * 1000 }.sumOf { it.completedMinutes }
        start to value
    }
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("이번 주 공부 시간", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { index, (date, value) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(if (value == 0) "" else "${value}분", style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.width(24.dp).height((70f * value / max).coerceAtLeast(4f).dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(if (index == Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 2) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = .72f)))
                        Text(dayLabel(date), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: MainUiState, viewModel: FocusCoinViewModel, permissionRefresh: Int, requestNotifications: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notify = remember(permissionRefresh) { PermissionStatus.notificationsGranted(context) }
    val usage = remember(permissionRefresh) { PermissionStatus.usageAccessGranted(context) }
    val access = remember(permissionRefresh) { PermissionStatus.accessibilityEnabled(context) }
    var resetConfirm by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("설정", "나에게 맞게 집중 방식을 조정하세요") }
        item {
            SettingsCard("코인 적립") {
                StepperRow("분당 코인", "${state.preferences.coinPerMinute}코인", { viewModel.setCoinPerMinute(state.preferences.coinPerMinute - 1) }, { viewModel.setCoinPerMinute(state.preferences.coinPerMinute + 1) })
                StepperRow("완주 보너스", "+${state.preferences.completionBonus}코인", { viewModel.setCompletionBonus(state.preferences.completionBonus - 1) }, { viewModel.setCompletionBonus(state.preferences.completionBonus + 1) })
            }
        }
        item {
            SettingsCard("긴급 해제") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("하루 1회 긴급 해제 허용", fontWeight = FontWeight.SemiBold); Text("집중 중 선택 앱을 잠시 열어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(state.preferences.emergencyUnlockEnabled, viewModel::setEmergencyEnabled)
                }
                StepperRow("해제 시간", "${state.preferences.emergencyUnlockMinutes}분", { viewModel.setEmergencyMinutes(state.preferences.emergencyUnlockMinutes - 1) }, { viewModel.setEmergencyMinutes(state.preferences.emergencyUnlockMinutes + 1) })
            }
        }
        item {
            SettingsCard("권한 상태") {
                PermissionSetting("알림", notify, if (notify) "허용됨" else "설정", if (notify) ({}) else requestNotifications)
                PermissionSetting("사용 기록 접근", usage, if (usage) "허용됨" else "설정", { PermissionStatus.openUsageSettings(context) })
                PermissionSetting("접근성 서비스", access, if (access) "켜짐" else "설정", { PermissionStatus.openAccessibilitySettings(context) })
            }
        }
        item {
            SettingsCard("정보 및 데이터") {
                TextButton(onClick = { showPrivacy = true }) { Icon(Icons.Filled.Info, null); Text("  개인정보 안내") }
                TextButton(onClick = { resetConfirm = true }) { Text("저장 데이터 초기화", color = MaterialTheme.colorScheme.error) }
                Text("FocusCoin · 버전 ${appVersion(context)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (resetConfirm) AlertDialog(
        onDismissRequest = { resetConfirm = false },
        title = { Text("모든 데이터를 초기화할까요?") },
        text = { Text("코인 잔액, 세션, 거래 내역, 앱 잠금 설정이 이 기기에서 삭제됩니다.") },
        confirmButton = { TextButton(onClick = { viewModel.resetAll(); resetConfirm = false }) { Text("초기화", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text("취소") } }
    )
    if (showPrivacy) AlertDialog(
        onDismissRequest = { showPrivacy = false },
        title = { Text("개인정보 안내") },
        text = { Text("FocusCoin은 서버 계정이나 광고를 사용하지 않습니다. 앱 잠금 설정, 코인 잔액, 공부 세션과 거래 기록은 기기 내부에 저장됩니다. 접근성 서비스는 선택한 앱의 패키지명만 확인합니다. 화면 글자, 키 입력, 비밀번호, 메시지, 연락처와 화면 캡처는 수집하거나 저장하지 않습니다. 사용 기록 권한을 허용하면 선택한 앱의 화면 사용 시간을 리포트에 표시하기 위해 기기에서 조회하며, 해당 조회값은 저장하지 않습니다.") },
        confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("확인") } }
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(21.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, "감소") }
        Text(value, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, "증가") }
    }
}

@Composable
private fun PermissionSetting(title: String, granted: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(if (granted) "권한 사용 가능" else "선택 권한 · 없어도 타이머 사용 가능", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun TimerScreen(state: MainUiState, viewModel: FocusCoinViewModel, onBack: () -> Unit) {
    val timer = state.preferences.timer
    var customMinutes by remember { mutableStateOf("") }
    var showFinishConfirm by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer.sessionId, timer.status) {
        while (timer.status == "RUNNING") {
            clock = System.currentTimeMillis()
            delay(500)
        }
    }
    val running = timer.status == "RUNNING" || timer.status == "PAUSED"
    val elapsed = timer.elapsedAt(clock)
    val planMillis = timer.plannedMinutes * 60_000L
    val remaining = (planMillis - elapsed).coerceAtLeast(0L)
    val progress = if (planMillis > 0) (elapsed.toFloat() / planMillis).coerceIn(0f, 1f) else 0f
    val expected = timer.plannedMinutes * state.preferences.coinPerMinute + state.preferences.completionBonus
    val latestSession = state.sessions.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "뒤로") }
            Text("집중 타이머", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (!running && latestSession?.status == "COMPLETED" && System.currentTimeMillis() - latestSession.endedAt < 120_000L) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Mint, modifier = Modifier.size(42.dp))
                    Text("집중 세션 완료!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("코인을 모았어요. ${latestSession.earnedCoins} 코인", color = CoinGold, fontWeight = FontWeight.Bold)
                    Button(onClick = onBack) { Text("완료") }
                }
            }
        } else if (!running) {
            Text("집중 시간을 선택하세요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf(25, 50, 90).forEach { minutes ->
                    OutlinedButton(onClick = { viewModel.startTimer(minutes) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 6.dp)) {
                            Text("${minutes}분", fontWeight = FontWeight.Bold)
                            Text("${minutes * state.preferences.coinPerMinute + state.preferences.completionBonus} 코인", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("직접 시간 입력", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(customMinutes, { customMinutes = it.filter(Char::isDigit).take(3) }, modifier = Modifier.weight(1f), label = { Text("분 (1~180)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        Spacer(Modifier.width(10.dp))
                        Button(onClick = { viewModel.startTimer(customMinutes.toIntOrNull()?.coerceIn(1, 180) ?: 25) }) { Text("시작") }
                    }
                    Text("1분 미만은 적립되지 않으며, 완주하면 보너스 ${state.preferences.completionBonus}코인을 받아요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Card(shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(228.dp), strokeWidth = 12.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatTime(remaining), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                            Text(if (timer.status == "PAUSED") "일시정지" else "집중 중", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("예상 획득 ${expected} 코인", color = CoinGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("잠긴 앱 ${state.appLocks.count { it.isLocked }}개", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (timer.status == "RUNNING") Button(onClick = viewModel::pauseTimer) { Icon(Icons.Filled.Pause, null); Text("  일시정지") }
                        else Button(onClick = viewModel::resumeTimer) { Icon(Icons.Filled.PlayArrow, null); Text("  재개") }
                        OutlinedButton(onClick = { showFinishConfirm = true }) { Text("공부 종료") }
                    }
                }
            }
        }
        if (running) Text("타이머는 앱을 닫거나 화면을 회전해도 이어집니다.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    if (showFinishConfirm) AlertDialog(
        onDismissRequest = { showFinishConfirm = false },
        title = { Text("공부를 종료할까요?") },
        text = { Text("완주 전 종료하면 실제로 집중한 온전한 분만 적립되고 완주 보너스는 지급되지 않습니다.") },
        confirmButton = { TextButton(onClick = { viewModel.finishTimer(); showFinishConfirm = false }) { Text("종료하고 정산") } },
        dismissButton = { TextButton(onClick = { showFinishConfirm = false }) { Text("계속 공부") } }
    )
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfWeek(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(time)
    firstDayOfWeek = Calendar.MONDAY
    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
}.timeInMillis

private fun dayLabel(time: Long): String = SimpleDateFormat("E", Locale.KOREAN).format(Date(time))
private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun appVersion(context: Context): String = runCatching {
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }
}.getOrNull() ?: "1.0.0"
