package com.focuscoin.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object StudyPlanReminderScheduler {
    private const val ACTION_REMIND = "com.focuscoin.app.STUDY_PLAN_REMINDER"
    private const val EXTRA_PLAN_ID = "study_plan_id"
    private const val EXTRA_START_AT = "study_plan_start_at"

    fun schedule(context: Context, plan: StudyPlanEntity) {
        cancel(context, plan.id)
        if (!plan.isEnabled || !plan.reminderEnabled) return
        val now = System.currentTimeMillis()
        val next = nextOccurrence(plan, now) ?: return
        val pending = pendingIntent(context, plan.id, next.startAt, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.remindAt, pending) }
    }

    fun cancel(context: Context, planId: String) {
        val pending = pendingIntent(context, planId, 0L, PendingIntent.FLAG_NO_CREATE) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(pending)
        pending.cancel()
    }

    internal fun receive(context: Context, planId: String, expectedStartAt: Long, pendingResult: BroadcastReceiver.PendingResult) {
        val database = Room.databaseBuilder(context, FocusCoinDatabase::class.java, "focuscoin.db").build()
        val planDao = database.studyPlanDao()
        goAsyncScope.launch {
            try {
                val plan = planDao.get(planId) ?: return@launch
                if (!plan.isEnabled || !plan.reminderEnabled) return@launch
                val expected = nextOccurrence(plan, expectedStartAt - plan.reminderMinutesBefore * 60_000L - 1L)
                val now = System.currentTimeMillis()
                if (expected?.startAt == expectedStartAt && now < expectedStartAt + plan.durationMillis()) {
                    showReminder(context, plan)
                    schedule(context, plan)
                }
            } finally {
                database.close()
                pendingResult.finish()
            }
        }
    }

    internal fun restoreAfterBoot(context: Context, pendingResult: BroadcastReceiver.PendingResult) {
        val database = Room.databaseBuilder(context, FocusCoinDatabase::class.java, "focuscoin.db").build()
        goAsyncScope.launch {
            try {
                database.studyPlanDao().observeAll().first().forEach { schedule(context, it) }
            } finally {
                database.close()
                pendingResult.finish()
            }
        }
    }

    private val goAsyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class NextOccurrence(val startAt: Long, val remindAt: Long)

    private fun nextOccurrence(plan: StudyPlanEntity, afterMillis: Long): NextOccurrence? {
        if (!plan.isEnabled || !plan.reminderEnabled) return null
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(afterMillis).atZone(zone).toLocalDate()
        val daysToCheck = if (plan.dateEpochDay == null) 0..7 else 0..1
        for (offset in daysToCheck) {
            val date = today.plusDays(offset.toLong())
            if (!plan.occursOn(date)) continue
            val startAt = date.atStartOfDay(zone).plusMinutes(plan.startMinute.toLong()).toInstant().toEpochMilli()
            val remindAt = startAt - plan.reminderMinutesBefore.coerceIn(0, 15) * 60_000L
            if (remindAt > afterMillis) return NextOccurrence(startAt, remindAt)
        }
        return null
    }

    private fun pendingIntent(context: Context, planId: String, startAt: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, StudyPlanReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .setData(Uri.parse("focuscoin://study-plan/${Uri.encode(planId)}"))
            .putExtra(EXTRA_PLAN_ID, planId)
            .putExtra(EXTRA_START_AT, startAt)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            flag or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showReminder(context: Context, plan: StudyPlanEntity) {
        if (!PermissionStatus.notificationsGranted(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "스터디 플래너", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "선택한 공부 일정 시작을 조용히 알려줍니다."
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val minutes = plan.reminderMinutesBefore
        val detail = if (minutes == 0) "공부를 시작할 시간이에요." else "${minutes}분 뒤에 시작해요. 준비해 볼까요?"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_focuscoin)
            .setContentTitle("${plan.title} 공부 시간")
            .setContentText(detail)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(plan.id.hashCode(), notification) }
    }

    private fun StudyPlanEntity.durationMillis(): Long =
        (endMinute - startMinute).coerceAtLeast(1) * 60_000L

    private fun StudyPlanEntity.occursOn(date: LocalDate): Boolean {
        val dayBit = 1 shl ((date.dayOfWeek.value % 7))
        return dateEpochDay?.let { it == date.toEpochDay() } ?: (weekdaysMask and dayBit != 0)
    }

    private const val CHANNEL_ID = "focuscoin_study_plan_reminders"
}

class StudyPlanReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> StudyPlanReminderScheduler.restoreAfterBoot(appContext, goAsync())
            "com.focuscoin.app.STUDY_PLAN_REMINDER" -> {
                val planId = intent.getStringExtra("study_plan_id") ?: return
                val startAt = intent.getLongExtra("study_plan_start_at", 0L)
                if (startAt > 0L) StudyPlanReminderScheduler.receive(appContext, planId, startAt, goAsync())
            }
        }
    }
}
