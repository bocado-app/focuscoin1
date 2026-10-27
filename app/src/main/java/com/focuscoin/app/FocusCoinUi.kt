package com.focuscoin.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.material3.Checkbox
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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
    TabItem("planner", "플래너", Icons.Filled.DateRange),
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
            composable("home") {
                HomeScreen(
                    state,
                    viewModel,
                    onStart = { nav.navigate("timer") },
                    onStartSubject = { subject -> nav.navigate("timer?subjectId=${subject.id}") },
                    onOpenPlanner = { nav.navigate("planner") }
                )
            }
            composable("apps") { AppsScreen(state, viewModel, permissionRefresh) }
            composable("planner") {
                PlannerScreen(
                    state,
                    viewModel,
                    permissionRefresh,
                    onStartTimer = { subject -> nav.navigate("timer?subjectId=${subject.id}") }
                )
            }
            composable("report") { ReportScreen(state, permissionRefresh) }
            composable("settings") { SettingsScreen(state, viewModel, permissionRefresh, requestNotifications) }
            composable("timer?subjectId={subjectId}", arguments = listOf(navArgument("subjectId") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                TimerScreen(state, viewModel, onBack = { nav.popBackStack() }, initialSubjectId = entry.arguments?.getString("subjectId"))
            }
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
        Text("공부한 시간에 따라 코인을 받고, 직접 고른 앱의 이용권을 코인으로 열 수 있어요. 공부 앱을 선택하면 공부 모드 중 사용 시간에 따라 보너스 코인도 받을 수 있습니다. 데이터는 이 기기에만 저장됩니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)

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
            description = "허용하면 사용자가 선택한 앱의 사용 시간을 기기에서 조회합니다. 리포트 표시와 공부 모드 중 보너스 코인 적립에만 사용하며 서버로 보내지 않습니다.",
            granted = usageGranted,
            actionLabel = if (usageGranted) "허용됨" else "설정 열기",
            icon = Icons.Filled.BarChart,
            onClick = { PermissionStatus.openUsageSettings(context) }
        )
        PermissionCard(
            title = "접근성 서비스",
            description = "선택한 잠금 앱과 플래너 일정의 허용 앱을 적용하기 위해 열린 앱의 패키지명만 확인합니다. 화면 글자, 키 입력, 비밀번호, 메시지와 화면 캡처는 읽거나 저장하지 않습니다.",
            granted = accessibilityGranted,
            actionLabel = if (accessibilityGranted) "켜짐" else "접근성 설정 열기",
            icon = Icons.Filled.Shield,
            onClick = { PermissionStatus.openAccessibilitySettings(context) }
        )
        Text("권한은 선택 사항입니다. 잠금 앱 차단, 플래너의 앱 제한, 공부 앱 보너스와 일부 리포트가 제한될 수 있지만 공부 타이머와 기본 코인 기능은 계속 사용할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun HomeScreen(
    state: MainUiState,
    viewModel: FocusCoinViewModel,
    onStart: () -> Unit,
    onStartSubject: (SubjectPlanEntity) -> Unit,
    onOpenPlanner: () -> Unit
) {
    val today = startOfDay(System.currentTimeMillis())
    val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(today))
    val savedMemo = state.dailyLearningMemos.firstOrNull { it.date == todayKey }
    var memoDraft by remember(todayKey) { mutableStateOf(savedMemo?.content.orEmpty()) }
    var memoSaved by remember(todayKey) { mutableStateOf(false) }
    LaunchedEffect(todayKey, savedMemo?.content) { memoDraft = savedMemo?.content.orEmpty() }
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
        item { TodayTasksCard(state, onStartSubject, onOpenPlanner) }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("오늘의 학습 한 줄", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${memoDraft.length}/200", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = memoDraft,
                        onValueChange = { memoDraft = it.filterNot { ch -> ch == '\n' || ch == '\r' }.take(200); memoSaved = false },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("오늘 배운 내용이나 기억할 점을 적어보세요.") },
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (memoSaved) "기기에 저장했어요." else "이 메모는 오늘 날짜로 저장됩니다.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { viewModel.saveDailyLearningMemo(todayKey, memoDraft); memoSaved = true }) { Text("저장") }
                    }
                }
            }
        }
        item { Text("선택한 잠금 앱 ${state.appLocks.count { it.isLocked }}개", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)) }
    }
}

@Composable
private fun TodayTasksCard(
    state: MainUiState,
    onStartSubject: (SubjectPlanEntity) -> Unit,
    onOpenPlanner: () -> Unit
) {
    val todayStart = startOfDay(System.currentTimeMillis())
    val todayEpochDay = java.time.LocalDate.now().toEpochDay()
    val upcomingPlans = state.subjectPlans
        .filter { it.examDateEpochDay >= todayEpochDay }
        .sortedWith(compareBy<SubjectPlanEntity> { it.examDateEpochDay }.thenBy { it.subjectName })

    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("오늘 할 일", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("시험이 가까운 과목부터 오늘 목표를 채워보세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when {
                state.subjectPlans.isEmpty() -> {
                    Text("과목 계획을 등록하면 시험 D-day에 맞춘 오늘 할 일이 생겨요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onOpenPlanner, modifier = Modifier.fillMaxWidth()) { Text("과목 계획 만들기") }
                }
                upcomingPlans.isEmpty() -> {
                    Text("다가오는 시험 일정이 없어요. 새 시험 계획을 등록해보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onOpenPlanner, modifier = Modifier.fillMaxWidth()) { Text("시험 일정 확인하기") }
                }
                else -> {
                    val tasks = upcomingPlans.map { plan ->
                        val studied = state.sessions
                            .filter { it.subjectPlanId == plan.id && it.startedAt >= todayStart }
                            .sumOf { it.completedMinutes }
                        val goal = plan.dailyGoalMinutes.coerceAtLeast(0)
                        Triple(plan, studied, (goal - studied).coerceAtLeast(0))
                    }
                    if (tasks.all { it.third == 0 }) {
                        Text("오늘 목표를 모두 채웠어요. 좋은 흐름을 이어가고 있어요!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    tasks.forEach { (plan, studied, remaining) ->
                        val daysLeft = (plan.examDateEpochDay - todayEpochDay).toInt()
                        val progress = if (plan.dailyGoalMinutes <= 0) 1f else (studied.toFloat() / plan.dailyGoalMinutes).coerceIn(0f, 1f)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(plan.subjectName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Text(if (daysLeft == 0) "오늘 시험" else "D-$daysLeft", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${studied}분 / 목표 ${plan.dailyGoalMinutes}분", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (remaining == 0) "오늘 목표 완료" else "${remaining}분 남음", style = MaterialTheme.typography.labelMedium, color = if (remaining == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            }
                            if (remaining > 0) {
                                Button(onClick = { onStartSubject(plan) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("${plan.subjectName} 공부하기 · ${remaining}분")
                                }
                            }
                        }
                    }
                }
            }
        }
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
    val usageAccess = remember(permissionRefresh) { PermissionStatus.usageAccessGranted(context) }
    var showSystem by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<Pair<InstalledApp, AppLockEntity>?>(null) }
    val configByPackage = state.appLocks.associateBy { it.packageName }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            PageHeading("잠금 앱", "잠글 앱과 공부 보너스 앱을 선택하세요")
            Text("앱 잠금과 별도로 공부 앱 보너스를 설정할 수 있어요. 공부 모드 중 선택 앱을 사용하면 1분마다 보너스 1코인을 적립합니다.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!usageAccess) {
                Card(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("보너스 적립에는 사용 기록 접근 권한이 필요해요.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { PermissionStatus.openUsageSettings(context) }) { Text("설정") }
                    }
                }
            }
            if (!accessibility) {
                Card(Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("앱 잠금 기능을 사용하려면 접근성 서비스를 켜주세요.", fontWeight = FontWeight.SemiBold)
                        Text("잠금 앱과 플래너 시간표를 적용할 때 실행 앱의 패키지명만 확인하며, 앱 콘텐츠는 읽지 않습니다.", style = MaterialTheme.typography.bodySmall)
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
        val bonusPackages = state.studyBonusApps.filter { it.isEnabled }.map { it.packageName }.toSet()
        if (visibleApps.isEmpty() && !state.appListError) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("표시할 앱이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleApps, key = { it.packageName }) { app ->
                    val config = configByPackage[app.packageName]
                    AppLockRow(
                        app = app,
                        config = config,
                        isStudyBonusEnabled = app.packageName in bonusPackages,
                        onToggle = { viewModel.setAppSelected(app, it) },
                        onStudyBonusToggle = { viewModel.setStudyBonusSelected(app, it) },
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
private fun AppLockRow(
    app: InstalledApp,
    config: AppLockEntity?,
    isStudyBonusEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onStudyBonusToggle: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    val locked = config?.isLocked == true
    val remaining = ((config?.unlockedUntil ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
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
            Row(Modifier.fillMaxWidth().padding(start = 64.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("공부 앱 보너스 · +1코인/분", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = isStudyBonusEnabled, onCheckedChange = onStudyBonusToggle)
            }
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

private val planWeekdays = listOf(
    "월" to Calendar.MONDAY,
    "화" to Calendar.TUESDAY,
    "수" to Calendar.WEDNESDAY,
    "목" to Calendar.THURSDAY,
    "금" to Calendar.FRIDAY,
    "토" to Calendar.SATURDAY,
    "일" to Calendar.SUNDAY
)

private fun dayBit(day: Int): Int = 1 shl (day - 1)
private fun formatPlanTime(minute: Int): String = if (minute == 1440) "24:00" else "%02d:%02d".format(minute / 60, minute % 60)
private fun parsePlanTime(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour == 24 && minute == 0) return 1440
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun localDateBit(date: LocalDate): Int = dayBit((date.dayOfWeek.value % 7) + 1)

private fun StudyPlanEntity.occursOn(date: LocalDate): Boolean =
    dateEpochDay?.let { it == date.toEpochDay() } ?: (weekdaysMask and localDateBit(date) != 0)

private fun planConflicts(candidate: StudyPlanEntity, plans: List<StudyPlanEntity>): Boolean =
    candidate.isEnabled && plans.any { existing ->
        if (existing.id == candidate.id || !existing.isEnabled) return@any false
        val daysOverlap = when {
            candidate.dateEpochDay != null && existing.dateEpochDay != null -> candidate.dateEpochDay == existing.dateEpochDay
            candidate.dateEpochDay != null -> existing.weekdaysMask and localDateBit(LocalDate.ofEpochDay(candidate.dateEpochDay)) != 0
            existing.dateEpochDay != null -> candidate.weekdaysMask and localDateBit(LocalDate.ofEpochDay(existing.dateEpochDay)) != 0
            else -> candidate.weekdaysMask and existing.weekdaysMask != 0
        }
        daysOverlap && candidate.startMinute < existing.endMinute && existing.startMinute < candidate.endMinute
    }
@Composable
private fun PlannerScreen(
    state: MainUiState,
    viewModel: FocusCoinViewModel,
    permissionRefresh: Int,
    onStartTimer: (SubjectPlanEntity) -> Unit,
) {
    var plannerPane by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = plannerPane == 0, onClick = { plannerPane = 0 }, label = { Text("시험·과목") })
            FilterChip(selected = plannerPane == 1, onClick = { plannerPane = 1 }, label = { Text("시간표 앱 제한") })
        }
        when (plannerPane) {
            0 -> SubjectPlannerScreen(state, viewModel, onStartTimer)
            1 -> CalendarScheduleScreen(state, viewModel, permissionRefresh)
        }
    }
}

@Composable
private fun SubjectPlannerScreen(state: MainUiState, viewModel: FocusCoinViewModel, onStartTimer: (SubjectPlanEntity) -> Unit) {
    var editing by remember { mutableStateOf<SubjectPlanEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SubjectPlanEntity?>(null) }
    var breakDialog by remember { mutableStateOf(false) }
    var breakMinutesText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val timerActive = state.preferences.timer.status in setOf("RUNNING", "PAUSED")
    val breakActive = state.preferences.breakUntil > now
    val remainingBreak = (state.preferences.breakUntil - now).coerceAtLeast(0L)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PageHeading("시험·과목 계획", "시험일까지 과목별 목표를 세우고 공부 시간을 쌓아보세요")
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (breakActive) "계획된 휴식 중" else "휴식 시간 지갑", fontWeight = FontWeight.Bold)
                    if (breakActive) {
                        Text(formatTime(remainingBreak), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("휴식이 끝나면 앱 제한이 다시 적용됩니다.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = viewModel::finishPlannedBreak) { Text("휴식 일찍 끝내기") }
                    } else {
                        Text("공부 25분마다 휴식 5분을 받아요. 세션이 짧아도 공부 시간은 누적됩니다.", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("사용 가능 ${state.preferences.breakBalanceMinutes}분", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Button(onClick = { message = ""; breakMinutesText = minOf(5, state.preferences.breakBalanceMinutes).toString(); breakDialog = true }, enabled = state.preferences.breakBalanceMinutes > 0 && !timerActive) { Text("휴식 사용") }
                        }
                        if (timerActive) Text("집중 타이머를 마친 뒤 휴식을 시작할 수 있어요.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("과목 계획 추가") }
        }
        if (state.subjectPlans.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("아직 과목 계획이 없어요.\n시험 날짜와 하루 목표를 정해보세요.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.subjectPlans, key = { it.id }) { subject ->
                    val todayStart = startOfDay(now)
                    val studiedToday = state.sessions.filter { it.subjectPlanId == subject.id && it.startedAt >= todayStart }.sumOf { it.completedMinutes }
                    val examDate = java.time.LocalDate.ofEpochDay(subject.examDateEpochDay)
                    val daysLeft = (subject.examDateEpochDay - java.time.LocalDate.now().toEpochDay()).toInt()
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(subject.subjectName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("시험 ${examDate.format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))} · ${if (daysLeft < 0) "시험일 지남" else "D-${daysLeft}"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { editing = subject }) { Text("수정") }
                                TextButton(onClick = { deleting = subject }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                            }
                            LinearProgressIndicator(progress = { (studiedToday.toFloat() / subject.dailyGoalMinutes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("오늘 ${studiedToday}분 / 목표 ${subject.dailyGoalMinutes}분", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { onStartTimer(subject) }) { Text("공부 시작") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding || editing != null) SubjectPlanDialog(
        plan = editing,
        onDismiss = { adding = false; editing = null },
        onSave = { viewModel.saveSubjectPlan(it); message = ""; adding = false; editing = null }
    )
    deleting?.let { subject ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("과목 계획을 삭제할까요?") },
            text = { Text("${subject.subjectName} 계획을 삭제합니다. 기존 공부 기록은 리포트에 남습니다.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteSubjectPlan(subject.id); deleting = null }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } })
    }
    if (breakDialog) AlertDialog(
        onDismissRequest = { breakDialog = false },
        title = { Text("계획된 휴식 사용") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("FocusCoin 앱 제한을 잠시 해제하고, 남은 시간은 지갑으로 돌려받을 수 있어요.")
            OutlinedTextField(breakMinutesText, { breakMinutesText = it.filter(Char::isDigit).take(2) }, label = { Text("휴식 시간 (분)") }, supportingText = { Text("최대 ${state.preferences.breakBalanceMinutes}분") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = {
            val requested = breakMinutesText.toIntOrNull() ?: 0
            if (requested !in 1..state.preferences.breakBalanceMinutes.coerceAtMost(60)) message = "사용할 수 있는 휴식 시간 안에서 입력해 주세요."
            else viewModel.startPlannedBreak(requested) { success ->
                if (!success) message = "타이머 상태나 휴식 잔액을 확인해 주세요."
                breakDialog = false
            }
        }) { Text("휴식 시작") } },
        dismissButton = { TextButton(onClick = { breakDialog = false }) { Text("취소") } }
    )
}

@Composable
private fun SubjectPlanDialog(plan: SubjectPlanEntity?, onDismiss: () -> Unit, onSave: (SubjectPlanEntity) -> Unit) {
    var name by remember(plan?.id) { mutableStateOf(plan?.subjectName ?: "") }
    var dateText by remember(plan?.id) { mutableStateOf(plan?.let { java.time.LocalDate.ofEpochDay(it.examDateEpochDay).toString() } ?: java.time.LocalDate.now().plusDays(30).toString()) }
    var goalText by remember(plan?.id) { mutableStateOf((plan?.dailyGoalMinutes ?: 60).toString()) }
    var error by remember(plan?.id) { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (plan == null) "과목 계획 추가" else "과목 계획 수정") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("과목 이름") }, singleLine = true)
            OutlinedTextField(dateText, { dateText = it.take(10) }, label = { Text("시험 날짜 (YYYY-MM-DD)") }, placeholder = { Text("2026-11-15") }, singleLine = true)
            OutlinedTextField(goalText, { goalText = it.filter(Char::isDigit).take(4) }, label = { Text("하루 공부 목표 (분)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            Text("공부 시작 시 이 과목을 선택하면 시험일까지의 실적에 반영됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = {
            val date = runCatching { java.time.LocalDate.parse(dateText.trim()) }.getOrNull()
            val goal = goalText.toIntOrNull()
            when {
                name.isBlank() -> error = "과목 이름을 입력해 주세요."
                date == null -> error = "시험 날짜를 YYYY-MM-DD 형식으로 입력해 주세요."
                goal == null || goal !in 5..1440 -> error = "하루 목표는 5~1440분으로 입력해 주세요."
                else -> onSave(SubjectPlanEntity(plan?.id ?: java.util.UUID.randomUUID().toString(), name.trim(), date.toEpochDay(), goal))
            }
        }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

private fun calendarDays(month: YearMonth): List<LocalDate?> {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cellCount = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    return List(cellCount) { index ->
        val day = index - offset + 1
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }
}

@Composable
private fun CalendarScheduleScreen(state: MainUiState, viewModel: FocusCoinViewModel, permissionRefresh: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accessibility = remember(permissionRefresh) { PermissionStatus.accessibilityEnabled(context) }
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var editorPlan by remember { mutableStateOf<StudyPlanEntity?>(null) }
    var creatingPlan by remember { mutableStateOf(false) }
    var editorStartMinute by remember { mutableIntStateOf(9 * 60) }
    var deletePlan by remember { mutableStateOf<StudyPlanEntity?>(null) }
    var message by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.studyPlans) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val activePlan = state.studyPlans.firstOrNull { it.isActiveAt(now) }
    val selectedPlans = state.studyPlans.filter { it.occursOn(selectedDate) }.sortedBy { it.startMinute }
    val today = LocalDate.now()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PageHeading("스터디 플래너", "날짜를 고르고 시간 칸을 눌러 계획을 세워보세요")
                Text("일정 중에는 허용한 앱과 시스템 앱을 사용할 수 있어요. 다른 일반 앱은 잠깁니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (activePlan != null) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("지금 집중 중 · ${activePlan.title}", fontWeight = FontWeight.Bold)
                        Text("${formatPlanTime(activePlan.startMinute)}–${formatPlanTime(activePlan.endMinute)} · 허용 앱 ${activePlan.allowedPackageSet().size}개", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (!accessibility) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("시간표에 따른 앱 제한을 쓰려면 접근성 서비스를 켜주세요.", fontWeight = FontWeight.SemiBold)
                        Text("실행된 앱의 패키지명만 확인하고 화면 내용은 읽지 않습니다.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { PermissionStatus.openAccessibilitySettings(context) }) { Text("접근성 설정 열기") }
                    }
                }
            }
        }
        if (message.isNotBlank()) item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            val next = displayedMonth.minusMonths(1)
                            displayedMonth = next
                            selectedDate = next.atDay(selectedDate.dayOfMonth.coerceAtMost(next.lengthOfMonth()))
                        }) { Text("‹", fontSize = 24.sp) }
                        Text("${displayedMonth.year}년 ${displayedMonth.monthValue}월", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = {
                            val next = displayedMonth.plusMonths(1)
                            displayedMonth = next
                            selectedDate = next.atDay(selectedDate.dayOfMonth.coerceAtMost(next.lengthOfMonth()))
                        }) { Text("›", fontSize = 24.sp) }
                        TextButton(onClick = { displayedMonth = YearMonth.from(today); selectedDate = today }) { Text("오늘") }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        listOf("월", "화", "수", "목", "금", "토", "일").forEach { day ->
                            Text(day, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    calendarDays(displayedMonth).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                if (date == null) {
                                    Box(Modifier.weight(1f).height(42.dp))
                                } else {
                                    val selected = date == selectedDate
                                    val hasPlan = state.studyPlans.any { it.isEnabled && it.occursOn(date) }
                                    Column(
                                        Modifier.weight(1f).height(42.dp).clip(CircleShape)
                                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable {
                                                selectedDate = date
                                                displayedMonth = YearMonth.from(date)
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            date.dayOfMonth.toString(),
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (selected || date == today) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Box(Modifier.size(4.dp).background(if (hasPlan) if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary else Color.Transparent, CircleShape))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("시간 칸을 누르면 일정이 추가됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { editorStartMinute = 9 * 60; creatingPlan = true }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("일정 추가")
                }
            }
        }
        if (selectedPlans.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text("이 날짜에 등록된 일정이 없어요. 원하는 시간 칸을 눌러 추가해 보세요.", Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items((0..23).toList(), key = { "hour-$it" }) { hour ->
            val startOfHour = hour * 60
            val endOfHour = startOfHour + 60
            val hourPlans = selectedPlans.filter { it.startMinute < endOfHour && it.endMinute > startOfHour }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text("%02d:00".format(hour), Modifier.width(50.dp).padding(top = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hourPlans.isEmpty()) {
                    Card(
                        Modifier.weight(1f).height(54.dp).clickable { editorStartMinute = startOfHour; creatingPlan = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                            Text("＋ 일정 추가", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        hourPlans.forEach { plan ->
                            Card(
                                Modifier.fillMaxWidth().clickable { editorPlan = plan },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = when {
                                    !plan.isEnabled -> MaterialTheme.colorScheme.surfaceVariant
                                    plan.isActiveAt(now) -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                })
                            ) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(if (plan.startMinute / 60 == hour) plan.title else "${plan.title} · 계속", fontWeight = FontWeight.SemiBold, maxLines = 1)
                                            Text("${formatPlanTime(plan.startMinute)}–${formatPlanTime(plan.endMinute)} · 허용 앱 ${plan.allowedPackageSet().size}개", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = plan.isEnabled, onCheckedChange = { enabled ->
                                            val changed = plan.copy(isEnabled = enabled)
                                            if (planConflicts(changed, state.studyPlans)) message = "같은 날짜와 시간에 겹치는 일정이 있어 켤 수 없습니다."
                                            else { message = ""; viewModel.saveStudyPlan(changed) }
                                        })
                                        TextButton(onClick = { deletePlan = plan }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (creatingPlan || editorPlan != null) {
        StudyPlanEditorDialog(
            plan = editorPlan,
            selectedDate = selectedDate,
            initialStartMinute = editorStartMinute,
            apps = state.installedApps,
            existingPlans = state.studyPlans,
            onDismiss = { creatingPlan = false; editorPlan = null },
            onSave = { plan -> viewModel.saveStudyPlan(plan); message = "일정을 저장했어요."; creatingPlan = false; editorPlan = null }
        )
    }
    deletePlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { deletePlan = null },
            title = { Text("일정을 삭제할까요?") },
            text = { Text("${plan.title} 일정과 허용 앱 설정을 삭제합니다.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteStudyPlan(plan.id); deletePlan = null }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletePlan = null }) { Text("취소") } }
        )
    }
}

@Composable
private fun StudyPlanEditorDialog(
    plan: StudyPlanEntity?,
    selectedDate: LocalDate,
    initialStartMinute: Int,
    apps: List<InstalledApp>,
    existingPlans: List<StudyPlanEntity>,
    onDismiss: () -> Unit,
    onSave: (StudyPlanEntity) -> Unit
) {
    var title by remember(plan?.id, selectedDate.toEpochDay()) { mutableStateOf(plan?.title ?: "공부 시간") }
    var planDate by remember(plan?.id, selectedDate.toEpochDay()) {
        mutableStateOf(plan?.dateEpochDay?.let { LocalDate.ofEpochDay(it) } ?: selectedDate)
    }
    var repeatsWeekly by remember(plan?.id, selectedDate.toEpochDay()) { mutableStateOf(plan != null && plan.dateEpochDay == null) }
    var weekdays by remember(plan?.id, selectedDate.toEpochDay()) {
        mutableIntStateOf(plan?.weekdaysMask?.takeIf { it != 0 } ?: localDateBit(selectedDate))
    }
    var startText by remember(plan?.id, initialStartMinute) { mutableStateOf(formatPlanTime(plan?.startMinute ?: initialStartMinute)) }
    var endText by remember(plan?.id, initialStartMinute) {
        mutableStateOf(formatPlanTime(plan?.endMinute ?: (initialStartMinute + 60).coerceAtMost(1440)))
    }
    var allowedPackages by remember(plan?.id, selectedDate.toEpochDay()) { mutableStateOf(plan?.allowedPackageSet() ?: emptySet()) }
    var error by remember(plan?.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plan == null) "스터디 플랜 추가" else "스터디 플랜 수정") },
        text = {
            Column(Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("일정 이름") }, singleLine = true)
                Text("날짜 · ${planDate.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN))}", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = repeatsWeekly, onCheckedChange = { repeatsWeekly = it })
                    Text("매주 반복")
                }
                if (repeatsWeekly) {
                    Text("반복 요일", fontWeight = FontWeight.SemiBold)
                    planWeekdays.chunked(4).forEach { rowDays ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowDays.forEach { (label, day) ->
                                FilterChip(
                                    selected = weekdays and dayBit(day) != 0,
                                    onClick = { weekdays = weekdays xor dayBit(day) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                } else {
                    Text("선택한 날짜에만 적용됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startText, { startText = it.take(5) }, modifier = Modifier.weight(1f), label = { Text("시작 (시:분)") }, placeholder = { Text("19:00") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), singleLine = true)
                    OutlinedTextField(endText, { endText = it.take(5) }, modifier = Modifier.weight(1f), label = { Text("종료 (시:분)") }, placeholder = { Text("20:00") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), singleLine = true)
                }
                Text("일정 중 허용할 앱", fontWeight = FontWeight.SemiBold)
                Text("선택하지 않은 일반 앱은 일정 시간 동안 잠깁니다. FocusCoin과 시스템 앱은 사용할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (apps.isEmpty()) Text("설치된 앱 목록을 불러오지 못했어요. 앱 목록이 준비된 뒤 다시 시도해 주세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Column(Modifier.fillMaxWidth().heightIn(max = 230.dp).verticalScroll(rememberScrollState())) {
                    apps.forEach { app ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (app.icon != null) Image(app.icon.asImageBitmap(), app.appName, Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)))
                            Text(app.appName, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1)
                            Checkbox(
                                checked = app.packageName in allowedPackages,
                                onCheckedChange = { checked ->
                                    allowedPackages = if (checked) allowedPackages + app.packageName else allowedPackages - app.packageName
                                }
                            )
                        }
                    }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = parsePlanTime(startText)
                val end = parsePlanTime(endText)
                when {
                    title.isBlank() -> error = "일정 이름을 입력해 주세요."
                    repeatsWeekly && weekdays == 0 -> error = "반복할 요일을 하나 이상 선택해 주세요."
                    start == null || end == null || start >= end -> error = "시작과 종료 시간을 올바르게 입력해 주세요. (자정 넘김은 지원하지 않습니다.)"
                    allowedPackages.isEmpty() -> error = "일정 중 사용할 앱을 하나 이상 선택해 주세요."
                    else -> {
                        val candidate = StudyPlanEntity(
                            id = plan?.id ?: java.util.UUID.randomUUID().toString(),
                            title = title.trim(),
                            weekdaysMask = if (repeatsWeekly) weekdays else 0,
                            startMinute = start,
                            endMinute = end,
                            allowedPackages = allowedPackages.sorted().joinToString(","),
                            isEnabled = plan?.isEnabled ?: true,
                            dateEpochDay = if (repeatsWeekly) null else planDate.toEpochDay()
                        )
                        if (planConflicts(candidate, existingPlans)) error = "같은 날짜와 시간에 겹치는 다른 일정이 있습니다."
                        else onSave(candidate)
                    }
                }
            }) { Text("저장") }
        },
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
    val memos = state.dailyLearningMemos.filter { memo ->
        val timestamp = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(memo.date)?.time ?: 0L }.getOrDefault(0L)
        timestamp >= start && timestamp < startOfDay(now) + 24L * 60 * 60 * 1000
    }
    val sessions = state.sessions.filter { it.startedAt >= start }
    val transactions = state.transactions.filter { it.createdAt >= start }
    val minutes = sessions.sumOf { it.completedMinutes }
    val completed = sessions.count { it.status == "COMPLETED" }
    val earned = transactions.filter { it.type == "EARN" || it.type == "BONUS" }.sumOf { it.amount }
    val spent = transactions.filter { it.type == "SPEND" }.sumOf { it.amount }
    val emergencyCount = transactions.count { it.type == "EMERGENCY_UNLOCK" }
    val appSpend = transactions.filter { it.type == "SPEND" && it.relatedPackageName != null }.groupBy { it.relatedPackageName }
    val studyBonusTransactions = transactions.filter { it.type == "BONUS" && it.memo.startsWith("공부 앱 보너스") }
        .groupBy { it.relatedPackageName }
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
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (weekly) "이번 주 학습 메모" else "오늘의 학습 메모", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (memos.isEmpty()) Text("저장된 메모가 없습니다. 홈에서 오늘 배운 내용을 한 줄로 남겨보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    memos.forEach { memo ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(runCatching { SimpleDateFormat("M월 d일", Locale.KOREAN).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(memo.date)!!) }.getOrDefault(memo.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(memo.content, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
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
                    val bonusCoins = studyBonusTransactions.values.flatten().sumOf { it.amount }
                    if (bonusCoins > 0) {
                        Divider()
                        Text("공부 앱 보너스 +${bonusCoins}코인", fontWeight = FontWeight.SemiBold, color = CoinGold)
                        studyBonusTransactions.forEach { (packageName, entries) ->
                            val appName = packageName?.let { state.installedApps.firstOrNull { app -> app.packageName == it }?.appName } ?: "선택 앱"
                            Text("$appName · ${entries.sumOf { it.durationMinutes }}분 사용 · +${entries.sumOf { it.amount }}코인", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            TextButton(onClick = { PermissionStatus.openUsageSettings(context) }) { Text("공부 앱 보너스와 사용 시간 리포트를 위해 사용 기록 접근 설정") }
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
                StepperRow(
                    "하루 앱 보너스 상한",
                    if (state.preferences.studyAppDailyBonusCapCoins == 0) "끔" else "${state.preferences.studyAppDailyBonusCapCoins}코인",
                    { viewModel.setStudyAppDailyBonusCap(state.preferences.studyAppDailyBonusCapCoins - 10) },
                    { viewModel.setStudyAppDailyBonusCap(state.preferences.studyAppDailyBonusCapCoins + 10) }
                )
                Text("선택한 공부 앱 전체의 보너스를 합산해 적용합니다. 0으로 설정하면 앱 보너스 적립이 꺼집니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        text = { Text("코인 잔액, 세션, 거래 내역, 앱 잠금 설정, 플래너 데이터가 이 기기에서 삭제됩니다.") },
        confirmButton = { TextButton(onClick = { viewModel.resetAll(); resetConfirm = false }) { Text("초기화", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text("취소") } }
    )
    if (showPrivacy) AlertDialog(
        onDismissRequest = { showPrivacy = false },
        title = { Text("개인정보 안내") },
        text = { Text("FocusCoin은 서버 계정이나 광고를 사용하지 않습니다. 앱 잠금 설정, 요일별 플래너, 과목·시험 날짜와 목표, 학습 메모, 코인 및 휴식 시간 잔액, 공부 세션과 거래 기록은 기기 내부에 저장됩니다. 접근성 서비스는 잠금 설정과 플래너 일정 적용을 위해 실행 앱의 패키지명만 확인합니다. 계획된 휴식 중에는 앱 제한이 끝날 시각을 확인합니다. 화면 글자, 키 입력, 비밀번호, 메시지, 연락처와 화면 캡처는 수집하거나 저장하지 않습니다. 사용 기록 권한을 허용하면 사용자가 선택한 앱의 화면 사용 시간을 기기에서 조회합니다. 이 정보는 리포트 표시와 공부 모드 중 선택 앱의 보너스 코인 정산에만 쓰고 기기 외부로 보내지 않습니다.") },
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
private fun TimerScreen(state: MainUiState, viewModel: FocusCoinViewModel, onBack: () -> Unit, initialSubjectId: String? = null) {
    val timer = state.preferences.timer
    var selectedSubjectId by remember(timer.sessionId, initialSubjectId) { mutableStateOf(initialSubjectId ?: timer.subjectPlanId ?: state.subjectPlans.firstOrNull()?.id) }
    val selectedSubject = state.subjectPlans.firstOrNull { it.id == selectedSubjectId }
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
    val expectedBreak = (state.preferences.breakStudyRemainderMinutes + timer.plannedMinutes) / 25 * 5
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
                    if (latestSession.breakEarnedMinutes > 0) Text("계획된 휴식 ${latestSession.breakEarnedMinutes}분을 받았어요.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    if (latestSession.subjectName.isNotBlank()) Text("${latestSession.subjectName} 공부 기록이 저장됐어요.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onBack) { Text("완료") }
                }
            }
        } else if (!running) {
            Text("공부 과목 선택", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedSubject == null, onClick = { selectedSubjectId = null }, label = { Text("과목 없이") })
                state.subjectPlans.forEach { subject ->
                    FilterChip(selected = selectedSubject?.id == subject.id, onClick = { selectedSubjectId = subject.id }, label = { Text(subject.subjectName) })
                }
            }
            Text("집중 시간을 선택하세요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf(25, 50, 90).forEach { minutes ->
                    OutlinedButton(onClick = { viewModel.startTimer(minutes, selectedSubject) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
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
                        Button(onClick = { viewModel.startTimer(customMinutes.toIntOrNull()?.coerceIn(1, 180) ?: 25, selectedSubject) }) { Text("시작") }
                    }
                    Text("완료한 공부 25분마다 휴식 5분을 받아요. 현재 누적 ${state.preferences.breakStudyRemainderMinutes}분이라 이번 세션에서 최대 ${expectedBreak}분이 적립될 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    if (timer.subjectName.isNotBlank()) Text("과목 · ${timer.subjectName}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text("예상 휴식 적립 ${expectedBreak}분 · 지갑 ${state.preferences.breakBalanceMinutes}분", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
