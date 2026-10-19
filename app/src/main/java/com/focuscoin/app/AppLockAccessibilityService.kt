package com.focuscoin.app

import android.accessibilityservice.AccessibilityService
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Checks only event.packageName. It never requests or reads an accessibility node tree. */
class AppLockAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentlyHandled = mutableMapOf<String, Long>()
    @Volatile private var selectedPackages: Set<String> = emptySet()
    private var overlay: View? = null
    private var overlayPackage: String? = null
    private lateinit var windowManager: WindowManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val repository = (application as FocusCoinApplication).repository
        serviceScope.launch {
            repository.appLocks.collect { configs ->
                val packages = configs.filter { it.isLocked }.map { it.packageName }.toSet()
                selectedPackages = packages
                withContext(Dispatchers.Main) {
                    runCatching {
                        serviceInfo = serviceInfo.apply {
                            packageNames = packages.toTypedArray()
                            eventTypes = if (packages.isEmpty()) 0 else AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        }
                    }
                    if (overlayPackage != null && overlayPackage !in packages) dismissOverlay()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in selectedPackages) return

        val now = System.currentTimeMillis()
        synchronized(recentlyHandled) {
            recentlyHandled.entries.removeAll { now - it.value > 15_000L }
            if (overlayPackage == packageName || now - (recentlyHandled[packageName] ?: 0L) < 3_000L) return
            recentlyHandled[packageName] = now
        }

        serviceScope.launch {
            val repository = (application as FocusCoinApplication).repository
            val config = repository.getAppLock(packageName) ?: return@launch
            if (!config.isLocked || config.unlockedUntil > now) return@launch
            val preferences = repository.currentPreferences()
            withContext(Dispatchers.Main) { showOverlay(config, preferences) }
        }
    }

    private fun showOverlay(config: AppLockEntity, preferences: PreferenceState, confirming: Boolean = false, message: String = "") {
        dismissOverlay()
        overlayPackage = config.packageName
        val density = resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).toInt()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(248, 18, 18, 28))
            isClickable = true
            isFocusable = true
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(24), px(26), px(24), px(22))
            background = GradientDrawable().apply {
                setColor(Color.rgb(38, 37, 53))
                cornerRadius = px(28).toFloat()
            }
        }
        val frameParams = FrameLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = px(24); rightMargin = px(24)
        }
        root.addView(card, frameParams)

        val focusMode = preferences.timer.status == "RUNNING" || preferences.timer.status == "PAUSED"
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val emergencyAvailable = preferences.emergencyUnlockEnabled && preferences.lastEmergencyUnlockDate != day

        card.addView(label("FocusCoin", 13, 0xFFBDB5FF.toInt(), true, Gravity.CENTER), matchWidth())
        addSpace(card, px(10))
        card.addView(label(
            when {
                confirming -> "구매를 확인해 주세요"
                focusMode -> "지금은 집중 시간입니다."
                else -> "이 앱은 잠겨 있어요."
            }, 23, Color.WHITE, true, Gravity.CENTER), matchWidth())
        addSpace(card, px(12))

        val description = when {
            confirming -> "${config.appName}을(를) ${config.unlockDurationMinutes}분 이용합니다.\n코인 ${config.unlockCostCoins}개를 사용합니다."
            focusMode -> "${config.appName}은(는) 공부 모드가 끝난 뒤 이용할 수 있어요.\n공부를 완료하면 코인을 얻을 수 있어요."
            else -> "${config.appName} 이용권을 코인으로 열 수 있어요."
        }
        card.addView(label(description, 15, 0xFFE1E0EA.toInt(), false, Gravity.CENTER), matchWidth())
        addSpace(card, px(14))

        if (!focusMode && !confirming) {
            card.addView(label("필요 ${config.unlockCostCoins} 코인 · ${config.unlockDurationMinutes}분 이용", 16, 0xFFBDB5FF.toInt(), true, Gravity.CENTER), matchWidth())
            card.addView(label("보유 ${preferences.balance} 코인", 14, 0xFFC7C5D2.toInt(), false, Gravity.CENTER), matchWidth())
            addSpace(card, px(12))
            if (preferences.balance >= config.unlockCostCoins) {
                card.addView(actionButton("코인으로 해제하기", 0xFF7163D8.toInt()) { showOverlay(config, preferences, confirming = true) }, matchWidth())
            } else {
                card.addView(label("코인이 ${config.unlockCostCoins - preferences.balance}개 부족해요.", 14, 0xFFFFB5B5.toInt(), true, Gravity.CENTER), matchWidth())
                addSpace(card, px(6))
                card.addView(actionButton("공부 시작하기", 0xFF168C83.toInt()) { openMainActivity() }, matchWidth())
            }
        }

        if (confirming) {
            if (message.isNotBlank()) card.addView(label(message, 14, 0xFFFFB5B5.toInt(), true, Gravity.CENTER), matchWidth())
            card.addView(actionButton("구매 확정", 0xFF7163D8.toInt()) { purchase(config.packageName, emergency = false) }, matchWidth())
            card.addView(actionButton("취소", 0xFF454454.toInt()) { showOverlay(config, preferences) }, matchWidth())
        }

        if (emergencyAvailable && !confirming) {
            addSpace(card, px(6))
            card.addView(actionButton("긴급 해제 · ${preferences.emergencyUnlockMinutes}분", 0xFF168C83.toInt()) { purchase(config.packageName, emergency = true) }, matchWidth())
        } else if (preferences.emergencyUnlockEnabled && !confirming) {
            card.addView(label("오늘의 긴급 해제를 이미 사용했어요.", 12, 0xFFC7C5D2.toInt(), false, Gravity.CENTER), matchWidth())
        }

        if (message.isNotBlank() && !confirming) {
            addSpace(card, px(8))
            card.addView(label(message, 13, 0xFFFFB5B5.toInt(), true, Gravity.CENTER), matchWidth())
        }
        addSpace(card, px(10))
        card.addView(actionButton("앱 닫기", 0xFF454454.toInt()) {
            dismissOverlay()
            performGlobalAction(GLOBAL_ACTION_HOME)
        }, matchWidth())

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        runCatching {
            windowManager.addView(root, params)
            overlay = root
        }.onFailure { overlayPackage = null }
    }

    private fun purchase(packageName: String, emergency: Boolean) {
        serviceScope.launch {
            val repository = (application as FocusCoinApplication).repository
            when (val result = repository.unlock(packageName, emergency)) {
                is UnlockResult.Success -> withContext(Dispatchers.Main) { dismissOverlay() }
                is UnlockResult.InsufficientCoins -> {
                    val config = repository.getAppLock(packageName) ?: return@launch
                    val preferences = repository.currentPreferences()
                    withContext(Dispatchers.Main) { showOverlay(config, preferences, message = "코인이 ${result.missing}개 부족합니다.") }
                }
                UnlockResult.EmergencyUnavailable -> {
                    val config = repository.getAppLock(packageName) ?: return@launch
                    val preferences = repository.currentPreferences()
                    withContext(Dispatchers.Main) { showOverlay(config, preferences, message = "오늘 사용할 긴급 해제를 이미 사용했어요.") }
                }
                UnlockResult.AppNotSelected -> withContext(Dispatchers.Main) { dismissOverlay() }
            }
        }
    }

    private fun openMainActivity() {
        dismissOverlay()
        runCatching {
            val intent = packageManager.getLaunchIntentForPackage(applicationContext.packageName) ?: return
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.putExtra("focuscoin.start_study", true)
            startActivity(intent)
        }.onFailure { performGlobalAction(GLOBAL_ACTION_HOME) }
    }

    private fun dismissOverlay() {
        overlay?.let { view -> runCatching { windowManager.removeView(view) } }
        overlay = null
        overlayPackage = null
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean, gravity: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        this.gravity = gravity
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(0f, 1.15f)
    }

    private fun actionButton(text: String, color: Int, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(color)
        setOnClickListener { onClick() }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun addSpace(parent: LinearLayout, height: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, height)) }

    override fun onInterrupt() { dismissOverlay() }

    override fun onDestroy() {
        dismissOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }
}
