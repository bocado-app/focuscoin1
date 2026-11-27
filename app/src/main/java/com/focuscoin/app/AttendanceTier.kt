package com.focuscoin.app

import androidx.annotation.DrawableRes

enum class AttendanceTier(
    val displayName: String,
    val minimumAttendanceCount: Int,
    @DrawableRes val badgeIcon: Int
) {
    PEBBLE("자갈", 0, R.drawable.tier_pebble),
    ROCK("바위", 5, R.drawable.tier_rock),
    COPPER("구리", 15, R.drawable.tier_copper),
    COIN("코인", 30, R.drawable.tier_coin),
    FOCUS_COIN("포커스 코인", 60, R.drawable.tier_focus_coin);

    fun nextTier(): AttendanceTier? = values().getOrNull(ordinal + 1)

    companion object {
        fun forAttendanceCount(count: Int): AttendanceTier =
            values().last { count >= it.minimumAttendanceCount }
    }
}
