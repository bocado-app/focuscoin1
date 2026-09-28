package com.focuscoin.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.min

data class TimerSnapshot(
    val sessionId: String = "",
    val status: String = "IDLE",
    val startedAt: Long = 0L,
    val plannedMinutes: Int = 0,
    val elapsedBeforeResume: Long = 0L,
    val resumedAt: Long = 0L
) {
    fun elapsedAt(now: Long): Long = elapsedBeforeResume +
        if (status == "RUNNING" && resumedAt > 0L) (now - resumedAt).coerceAtLeast(0L) else 0L
}

data class PreferenceState(
    val balance: Int = 0,
    val coinPerMinute: Int = 1,
    val completionBonus: Int = 10,
    val emergencyUnlockEnabled: Boolean = true,
    val emergencyUnlockMinutes: Int = 10,
    val lastEmergencyUnlockDate: String = "",
    val onboardingComplete: Boolean = false,
    val timer: TimerSnapshot = TimerSnapshot()
)

sealed interface UnlockResult {
    data class Success(val until: Long, val durationMinutes: Int) : UnlockResult
    data class InsufficientCoins(val missing: Int) : UnlockResult
    data object EmergencyUnavailable : UnlockResult
    data object AppNotSelected : UnlockResult
}

class FocusCoinRepository(
    private val database: FocusCoinDatabase,
    private val store: DataStore<Preferences>
) {
    private val lockDao = database.appLockDao()
    private val sessionDao = database.studySessionDao()
    private val transactionDao = database.coinTransactionDao()
    private val mutex = Mutex()

    private object Keys {
        val balance = intPreferencesKey("coin_balance")
        val rate = intPreferencesKey("coin_per_minute")
        val bonus = intPreferencesKey("completion_bonus")
        val emergencyEnabled = booleanPreferencesKey("emergency_enabled")
        val emergencyMinutes = intPreferencesKey("emergency_minutes")
        val lastEmergencyDate = stringPreferencesKey("last_emergency_date")
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val timerId = stringPreferencesKey("timer_session_id")
        val timerStatus = stringPreferencesKey("timer_status")
        val timerStartedAt = longPreferencesKey("timer_started_at")
        val timerPlanned = intPreferencesKey("timer_planned_minutes")
        val timerElapsed = longPreferencesKey("timer_elapsed_before_resume")
        val timerResumedAt = longPreferencesKey("timer_resumed_at")
    }

    val preferenceFlow: Flow<PreferenceState> = store.data.map { p ->
        PreferenceState(
            balance = (p[Keys.balance] ?: 0).coerceAtLeast(0),
            coinPerMinute = (p[Keys.rate] ?: 1).coerceIn(1, 100),
            completionBonus = (p[Keys.bonus] ?: 10).coerceIn(0, 1000),
            emergencyUnlockEnabled = p[Keys.emergencyEnabled] ?: true,
            emergencyUnlockMinutes = (p[Keys.emergencyMinutes] ?: 10).coerceIn(1, 60),
            lastEmergencyUnlockDate = p[Keys.lastEmergencyDate] ?: "",
            onboardingComplete = p[Keys.onboarding] ?: false,
            timer = TimerSnapshot(
                sessionId = p[Keys.timerId] ?: "",
                status = p[Keys.timerStatus] ?: "IDLE",
                startedAt = p[Keys.timerStartedAt] ?: 0L,
                plannedMinutes = p[Keys.timerPlanned] ?: 0,
                elapsedBeforeResume = p[Keys.timerElapsed] ?: 0L,
                resumedAt = p[Keys.timerResumedAt] ?: 0L
            )
        )
    }

    val appLocks: Flow<List<AppLockEntity>> = lockDao.observeAll()
    val sessions: Flow<List<StudySessionEntity>> = sessionDao.observeAll()
    val transactions: Flow<List<CoinTransactionEntity>> = transactionDao.observeAll()

    suspend fun reconcileBalance() = mutex.withLock {
        val ledgerBalance = transactionDao.netCoinBalance().coerceAtLeast(0)
        store.edit { it[Keys.balance] = ledgerBalance }
    }

    suspend fun finishOnboarding() { store.edit { it[Keys.onboarding] = true } }
    suspend fun setCoinPerMinute(value: Int) { store.edit { it[Keys.rate] = value.coerceIn(1, 100) } }
    suspend fun setCompletionBonus(value: Int) { store.edit { it[Keys.bonus] = value.coerceIn(0, 1000) } }
    suspend fun setEmergencyEnabled(value: Boolean) { store.edit { it[Keys.emergencyEnabled] = value } }
    suspend fun setEmergencyMinutes(value: Int) { store.edit { it[Keys.emergencyMinutes] = value.coerceIn(1, 60) } }

    suspend fun upsertAppLock(
        packageName: String,
        appName: String,
        selected: Boolean,
        duration: Int? = null,
        cost: Int? = null
    ) {
        if (!selected) {
            lockDao.delete(packageName)
            return
        }
        val old = lockDao.get(packageName)
        val defaults = unlockDefaults(appName, packageName)
        lockDao.upsert(
            AppLockEntity(
                packageName = packageName,
                appName = appName,
                isLocked = selected,
                unlockDurationMinutes = (duration ?: old?.unlockDurationMinutes ?: defaults.first).coerceIn(1, 240),
                unlockCostCoins = (cost ?: old?.unlockCostCoins ?: defaults.second).coerceIn(1, 10000),
                unlockedUntil = old?.unlockedUntil ?: 0L
            )
        )
    }

    suspend fun updateUnlockPricing(packageName: String, duration: Int, cost: Int) {
        val old = lockDao.get(packageName) ?: return
        lockDao.upsert(old.copy(unlockDurationMinutes = duration.coerceIn(1, 240), unlockCostCoins = cost.coerceIn(1, 10000)))
    }

    suspend fun startTimer(minutes: Int) = mutex.withLock {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        store.edit { p ->
            p[Keys.timerId] = id
            p[Keys.timerStatus] = "RUNNING"
            p[Keys.timerStartedAt] = now
            p[Keys.timerPlanned] = minutes.coerceIn(1, 180)
            p[Keys.timerElapsed] = 0L
            p[Keys.timerResumedAt] = now
        }
    }

    suspend fun pauseTimer() = mutex.withLock {
        val current = preferenceFlow.firstValue()
        if (current.timer.status != "RUNNING") return@withLock
        val elapsed = current.timer.elapsedAt(System.currentTimeMillis())
        store.edit { p ->
            p[Keys.timerElapsed] = elapsed
            p[Keys.timerResumedAt] = 0L
            p[Keys.timerStatus] = "PAUSED"
        }
    }

    suspend fun resumeTimer() = mutex.withLock {
        val current = preferenceFlow.firstValue()
        if (current.timer.status != "PAUSED") return@withLock
        store.edit { p ->
            p[Keys.timerStatus] = "RUNNING"
            p[Keys.timerResumedAt] = System.currentTimeMillis()
        }
    }

    suspend fun finishTimer(): Int? = mutex.withLock {
        val current = preferenceFlow.firstValue()
        val timer = current.timer
        if (timer.sessionId.isBlank() || timer.status !in setOf("RUNNING", "PAUSED")) return@withLock null
        val now = System.currentTimeMillis()
        val elapsed = timer.elapsedAt(now)
        val plannedMillis = timer.plannedMinutes * 60_000L
        val completed = min(elapsed, plannedMillis)
        val full = elapsed >= plannedMillis
        val minutes = (completed / 60_000L).toInt()
        val earned = minutes * current.coinPerMinute + if (full) current.completionBonus else 0

        sessionDao.insertIgnore(
            StudySessionEntity(
                id = timer.sessionId,
                startedAt = timer.startedAt,
                endedAt = now,
                plannedMinutes = timer.plannedMinutes,
                completedMinutes = minutes,
                status = if (full) "COMPLETED" else "ABANDONED",
                earnedCoins = earned
            )
        )
        if (minutes > 0) addTransactionOnce(
            CoinTransactionEntity("${timer.sessionId}:earn", now, "EARN", minutes * current.coinPerMinute, memo = "공부 ${minutes}분")
        )
        if (full && current.completionBonus > 0) addTransactionOnce(
            CoinTransactionEntity("${timer.sessionId}:bonus", now, "BONUS", current.completionBonus, memo = "집중 세션 완주 보너스")
        )
        clearTimer()
        earned
    }

    suspend fun refreshTimer(): Int? {
        val current = preferenceFlow.firstValue()
        if (current.timer.status in setOf("RUNNING", "PAUSED") && current.timer.plannedMinutes > 0 &&
            current.timer.elapsedAt(System.currentTimeMillis()) >= current.timer.plannedMinutes * 60_000L
        ) return finishTimer()
        return null
    }

    suspend fun unlock(packageName: String, emergency: Boolean): UnlockResult = mutex.withLock {
        val config = lockDao.get(packageName) ?: return@withLock UnlockResult.AppNotSelected
        if (!config.isLocked) return@withLock UnlockResult.AppNotSelected
        val now = System.currentTimeMillis()
        if (config.unlockedUntil > now) return@withLock UnlockResult.Success(config.unlockedUntil, 0)
        val prefs = preferenceFlow.firstValue()
        val duration = if (emergency) prefs.emergencyUnlockMinutes else config.unlockDurationMinutes
        if (emergency) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
            if (!prefs.emergencyUnlockEnabled || prefs.lastEmergencyUnlockDate == today) {
                return@withLock UnlockResult.EmergencyUnavailable
            }
            val until = maxOf(config.unlockedUntil, now) + duration * 60_000L
            val inserted = database.withTransaction {
                val row = transactionDao.insertIgnore(
                    CoinTransactionEntity("emergency:$today", now, "EMERGENCY_UNLOCK", 0, packageName, "긴급 해제 ${duration}분", duration)
                )
                if (row == -1L) false else {
                    lockDao.upsert(config.copy(unlockedUntil = until))
                    true
                }
            }
            if (!inserted) return@withLock UnlockResult.EmergencyUnavailable
            store.edit { it[Keys.lastEmergencyDate] = today }
            return@withLock UnlockResult.Success(until, duration)
        } else {
            if (prefs.balance < config.unlockCostCoins) {
                return@withLock UnlockResult.InsufficientCoins(config.unlockCostCoins - prefs.balance)
            }
            val until = maxOf(config.unlockedUntil, now) + duration * 60_000L
            database.withTransaction {
                transactionDao.insertIgnore(
                    CoinTransactionEntity(UUID.randomUUID().toString(), now, "SPEND", config.unlockCostCoins, packageName, "${config.appName} 이용권", duration)
                )
                lockDao.upsert(config.copy(unlockedUntil = until))
            }
            store.edit { p -> p[Keys.balance] = (prefs.balance - config.unlockCostCoins).coerceAtLeast(0) }
            return@withLock UnlockResult.Success(until, duration)
        }
    }

    suspend fun resetAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.clearAllTables()
            store.edit { it.clear() }
        }
    }

    suspend fun getAppLock(packageName: String): AppLockEntity? = lockDao.get(packageName)

    suspend fun currentPreferences(): PreferenceState = preferenceFlow.firstValue()

    private suspend fun addTransactionOnce(transaction: CoinTransactionEntity) {
        val inserted = transactionDao.insertIgnore(transaction)
        if (inserted != -1L) {
            store.edit { p -> p[Keys.balance] = ((p[Keys.balance] ?: 0) + transaction.amount).coerceAtLeast(0) }
        }
    }

    private suspend fun clearTimer() {
        store.edit { p ->
            p[Keys.timerId] = ""
            p[Keys.timerStatus] = "IDLE"
            p[Keys.timerStartedAt] = 0L
            p[Keys.timerPlanned] = 0
            p[Keys.timerElapsed] = 0L
            p[Keys.timerResumedAt] = 0L
        }
    }

    private suspend fun Flow<PreferenceState>.firstValue(): PreferenceState = first()

    private fun unlockDefaults(name: String, packageName: String): Pair<Int, Int> {
        val text = (name + packageName).lowercase(Locale.ROOT)
        return when {
            listOf("youtube", "유튜브", "netflix", "넷플", "tving", "티빙", "disney", "웨이브").any(text::contains) -> 60 to 60
            listOf("instagram", "인스타", "facebook", "페이스북", "tiktok", "틱톡", "twitter", "threads", "카카오톡").any(text::contains) -> 30 to 35
            listOf("game", "게임", "pubg", "roblox", "롤", "minecraft", "마인크래프트").any(text::contains) -> 30 to 45
            else -> 30 to 30
        }
    }
}
