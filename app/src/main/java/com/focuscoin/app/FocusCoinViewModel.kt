package com.focuscoin.app

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Bitmap?,
    val isSystemApp: Boolean
)

data class MainUiState(
    val preferences: PreferenceState = PreferenceState(),
    val appLocks: List<AppLockEntity> = emptyList(),
    val studyBonusApps: List<StudyBonusAppEntity> = emptyList(),
    val studyPlans: List<StudyPlanEntity> = emptyList(),
    val subjectPlans: List<SubjectPlanEntity> = emptyList(),
    val dailyLearningMemos: List<DailyLearningMemoEntity> = emptyList(),
    val sessions: List<StudySessionEntity> = emptyList(),
    val transactions: List<CoinTransactionEntity> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val appListError: Boolean = false,
    val isReady: Boolean = false
)

private data class CombinedScreenData(val plans: Triple<Pair<List<AppLockEntity>, List<StudyBonusAppEntity>>, List<StudyPlanEntity>, List<SubjectPlanEntity>>, val memos: List<DailyLearningMemoEntity>)

class FocusCoinViewModel(
    application: Application,
    private val repository: FocusCoinRepository
) : AndroidViewModel(application) {
    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val appListError = MutableStateFlow(false)
    private val studyUsageMutex = Mutex()
    private var studyUsageAccessAvailable = false

    private val appConfigurations = combine(repository.appLocks, repository.studyBonusApps) { locks, bonusApps ->
        locks to bonusApps
    }
    private val plans = combine(appConfigurations, repository.studyPlans, repository.subjectPlans) { apps, plans, subjects ->
        Triple(apps, plans, subjects)
    }
    private val screenData = combine(plans, repository.dailyLearningMemos) { planData, memos ->
        CombinedScreenData(planData, memos)
    }

    private val baseState = combine(
        repository.preferenceFlow,
        screenData,
        repository.sessions,
        repository.transactions,
        installedApps
    ) { preferences, appConfigs, sessions, transactions, apps ->
        MainUiState(
            preferences = preferences,
            appLocks = appConfigs.plans.first.first,
            studyBonusApps = appConfigs.plans.first.second,
            studyPlans = appConfigs.plans.second,
            subjectPlans = appConfigs.plans.third,
            dailyLearningMemos = appConfigs.memos,
            sessions = sessions,
            transactions = transactions,
            installedApps = apps,
            isReady = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    val uiState: StateFlow<MainUiState> = combine(baseState, appListError) { state, error ->
        state.copy(appListError = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch(Dispatchers.IO) { loadInstalledApps() }
        viewModelScope.launch { repository.reconcileBalance() }
        viewModelScope.launch(Dispatchers.IO) {
            baselineStudyUsage()
            lastStudyUsageSampleAt = System.currentTimeMillis()
            while (true) {
                kotlinx.coroutines.delay(1_000)
                val timer = repository.currentPreferences().timer
                val dueToFinish = timer.status == "RUNNING" && timer.plannedMinutes > 0 &&
                    timer.elapsedAt(System.currentTimeMillis()) >= timer.plannedMinutes * 60_000L
                if (System.currentTimeMillis() - lastStudyUsageSampleAt >= 10_000L || dueToFinish) {
                    val timerRunning = timer.status == "RUNNING"
                    collectStudyUsage(timerRunning)
                    lastStudyUsageSampleAt = System.currentTimeMillis()
                }
                repository.refreshTimer()?.let { AppNotifications.show(getApplication(), it) }
            }
        }
    }

    @Volatile private var lastStudyUsageSampleAt: Long = 0L

    private suspend fun baselineStudyUsage() = studyUsageMutex.withLock {
        val selections = repository.studyBonusApps.first().filter { it.isEnabled }
        if (selections.isEmpty()) return@withLock
        val current = withContext(Dispatchers.IO) {
            UsageStatsAccess.studyAppForegroundMillis(getApplication(), selections.map { it.packageName }.toSet())
        }
        if (current == null) {
            studyUsageAccessAvailable = false
            return@withLock
        }
        repository.baselineStudyBonusUsage(current)
        studyUsageAccessAvailable = true
    }

    private suspend fun collectStudyUsage(timerRunning: Boolean) = studyUsageMutex.withLock {
        val selections = repository.studyBonusApps.first().filter { it.isEnabled }
        if (selections.isEmpty()) return@withLock
        val current = withContext(Dispatchers.IO) {
            UsageStatsAccess.studyAppForegroundMillis(getApplication(), selections.map { it.packageName }.toSet())
        }
        if (current == null) {
            studyUsageAccessAvailable = false
            return@withLock
        }
        if (!studyUsageAccessAvailable) {
            repository.baselineStudyBonusUsage(current)
            studyUsageAccessAvailable = true
            return@withLock
        }
        repository.collectStudyBonusUsage(current, timerRunning)
    }

    private fun loadInstalledApps() {
        val pm = getApplication<Application>().packageManager
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            val ownPackage = getApplication<Application>().packageName
            val apps = resolved.asSequence()
                .mapNotNull { info ->
                    val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (packageName == ownPackage) return@mapNotNull null
                    val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(packageName)
                    val icon = runCatching { info.loadIcon(pm).toBitmap(96, 96) }.getOrNull()
                    val flags = info.activityInfo?.applicationInfo?.flags ?: 0
                    InstalledApp(packageName, label, icon, flags and ApplicationInfo.FLAG_SYSTEM != 0)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }
                .toList()
            installedApps.value = apps
            appListError.value = false
        } catch (_: Exception) {
            installedApps.value = emptyList()
            appListError.value = true
        }
    }

    fun retryLoadApps() = viewModelScope.launch(Dispatchers.IO) { loadInstalledApps() }
    fun finishOnboarding() = viewModelScope.launch { repository.finishOnboarding() }
    fun startTimer(minutes: Int, subject: SubjectPlanEntity? = null) = viewModelScope.launch {
        baselineStudyUsage()
        repository.startTimer(minutes, subject?.id, subject?.subjectName.orEmpty())
    }
    fun pauseTimer() = viewModelScope.launch {
        collectStudyUsage(timerRunning = true)
        repository.pauseTimer()
    }
    fun resumeTimer() = viewModelScope.launch {
        baselineStudyUsage()
        repository.resumeTimer()
    }
    fun finishTimer() = viewModelScope.launch {
        collectStudyUsage(repository.currentPreferences().timer.status == "RUNNING")
        repository.finishTimer()?.let { AppNotifications.show(getApplication(), it) }
    }
    fun setCoinPerMinute(value: Int) = viewModelScope.launch { repository.setCoinPerMinute(value) }
    fun setCompletionBonus(value: Int) = viewModelScope.launch { repository.setCompletionBonus(value) }
    fun setStudyAppDailyBonusCap(value: Int) = viewModelScope.launch { repository.setStudyAppDailyBonusCap(value) }
    fun setEmergencyEnabled(value: Boolean) = viewModelScope.launch { repository.setEmergencyEnabled(value) }
    fun setEmergencyMinutes(value: Int) = viewModelScope.launch { repository.setEmergencyMinutes(value) }
    fun resetAll() = viewModelScope.launch { repository.resetAll() }

    fun setAppSelected(app: InstalledApp, selected: Boolean) = viewModelScope.launch {
        repository.upsertAppLock(app.packageName, app.appName, selected)
    }

    fun setStudyBonusSelected(app: InstalledApp, selected: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val usage = if (selected) UsageStatsAccess.studyAppForegroundMillis(getApplication(), setOf(app.packageName))?.get(app.packageName) else null
        repository.setStudyBonusApp(app.packageName, app.appName, selected, usage)
    }

    fun saveStudyPlan(plan: StudyPlanEntity) = viewModelScope.launch { repository.saveStudyPlan(plan) }
    fun deleteStudyPlan(id: String) = viewModelScope.launch { repository.deleteStudyPlan(id) }
    fun saveSubjectPlan(plan: SubjectPlanEntity) = viewModelScope.launch { repository.saveSubjectPlan(plan) }
    fun deleteSubjectPlan(id: String) = viewModelScope.launch { repository.deleteSubjectPlan(id) }
    fun saveDailyLearningMemo(date: String, content: String) = viewModelScope.launch { repository.saveDailyLearningMemo(date, content) }
    fun startPlannedBreak(minutes: Int, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(repository.startPlannedBreak(minutes))
    }
    fun finishPlannedBreak() = viewModelScope.launch { repository.finishPlannedBreak() }

    fun updatePricing(packageName: String, duration: Int, cost: Int) = viewModelScope.launch {
        repository.updateUnlockPricing(packageName, duration, cost)
    }

    suspend fun unlock(packageName: String, emergency: Boolean): UnlockResult = repository.unlock(packageName, emergency)
}

class FocusCoinViewModelFactory(
    private val application: Application,
    private val repository: FocusCoinRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusCoinViewModel::class.java)) {
            return FocusCoinViewModel(application, repository) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
