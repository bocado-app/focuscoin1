package com.focuscoin.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object AppNotifications {
    private const val CHANNEL_ID = "focuscoin_sessions"
    private const val NOTIFICATION_ID = 1201

    fun show(context: Context, earnedCoins: Int) {
        if (!PermissionStatus.notificationsGranted(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "집중 세션", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "집중 타이머 종료와 코인 정산 결과"
                }
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val message = if (earnedCoins > 0) "공부 세션을 정산했어요. 코인 ${earnedCoins}개를 적립했습니다." else "공부 세션을 정산했어요. 1분 미만은 코인으로 적립되지 않습니다."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_focuscoin)
            .setContentTitle("FocusCoin 집중 세션 정산")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}
