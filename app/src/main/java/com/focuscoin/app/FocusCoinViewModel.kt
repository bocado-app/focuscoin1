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

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Bitmap?,
    val isSystemApp: Boolean
)

data class MainUiState(
    val preferences: PreferenceState = PreferenceState(),
    val appLocks: List<AppLockEntity> = emptyList(),
    val sessions: List<StudySessionEntity> = emptyList(),
    val transactions: List<CoinTransactionEntity> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val appListError: Boolean = false,
    val isReady: Boolean = false
)

class FocusCoinViewModel(
    application: Application,
    private val repository: FocusCoinRepository
) : AndroidViewModel(application) {
    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val appListError = MutableStateFlow(false)

    private val baseState = combine(
        repository.preferenceFlow,
        repository.appLocks,
        repository.sessions,
        repository.transactions,
        installedApps
    ) { preferences, locks, sessions, transactions, apps ->
        MainUiState(preferences, locks, sessions, transactions, apps, isReady = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    val uiState: StateFlow<MainUiState> = combine(baseState, appListError) { state, error ->
        state.copy(appListError = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch(Dispatchers.IO) { loadInstalledApps() }
        viewModelScope.launch { repository.reconcileBalance() }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1_000)
                repository.refreshTimer()?.let { AppNotifications.show(getApplication(), it) }
            }
        }
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
    fun startTimer(minutes: Int) = viewModelScope.launch { repository.startTimer(minutes) }
    fun pauseTimer() = viewModelScope.launch { repository.pauseTimer() }
    fun resumeTimer() = viewModelScope.launch { repository.resumeTimer() }
    fun finishTimer() = viewModelScope.launch {
        repository.finishTimer()?.let { AppNotifications.show(getApplication(), it) }
    }
    fun setCoinPerMinute(value: Int) = viewModelScope.launch { repository.setCoinPerMinute(value) }
    fun setCompletionBonus(value: Int) = viewModelScope.launch { repository.setCompletionBonus(value) }
    fun setEmergencyEnabled(value: Boolean) = viewModelScope.launch { repository.setEmergencyEnabled(value) }
    fun setEmergencyMinutes(value: Int) = viewModelScope.launch { repository.setEmergencyMinutes(value) }
    fun resetAll() = viewModelScope.launch { repository.resetAll() }

    fun setAppSelected(app: InstalledApp, selected: Boolean) = viewModelScope.launch {
        repository.upsertAppLock(app.packageName, app.appName, selected)
    }

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
