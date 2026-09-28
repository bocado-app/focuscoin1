package com.focuscoin.app

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object UsageStatsAccess {
    /** Queries only packages the user selected. Results stay in memory and are not persisted. */
    fun selectedAppUsage(context: Context, selected: List<AppLockEntity>, weekly: Boolean): Map<String, Long> {
        if (selected.isEmpty() || !PermissionStatus.usageAccessGranted(context)) return emptyMap()
        val now = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (weekly) {
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
        }.timeInMillis
        return try {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val selectedPackages = selected.map { it.packageName }.toSet()
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
                .orEmpty()
                .asSequence()
                .filter { it.packageName in selectedPackages }
                .groupBy { it.packageName }
                .mapValues { (_, entries) -> entries.sumOf { it.totalTimeInForeground } }
                .filterValues { it > 0L }
        } catch (_: SecurityException) {
            emptyMap()
        } catch (_: RuntimeException) {
            emptyMap()
        }
    }
}
