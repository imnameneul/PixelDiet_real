package com.example.pixeldiet.ui.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.pixeldiet.R
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.TaskStackBuilder
import com.example.pixeldiet.MainActivity


object NotificationHelper {

    private const val CHANNEL_ID_ALERT = "PixelDietAlert"
    private const val CHANNEL_ID_ONGOING = "PixelDietOngoing"

    private const val CHANNEL_NAME_ALERT = "앱 사용 경고 알림"
    private const val CHANNEL_NAME_ONGOING = "사용시간 진행바"

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        return TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(intent)
            .getPendingIntent(0, flags)!!
    }


    // 1. 알림 채널 생성 (안드로이드 8.0 이상 필수)
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alert = NotificationChannel(
                CHANNEL_ID_ALERT,
                CHANNEL_NAME_ALERT,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "앱 목표시간 도달/초과 시 알림"
            }

            val ongoing = NotificationChannel(
                CHANNEL_ID_ONGOING,
                CHANNEL_NAME_ONGOING,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "현재 사용시간 진행상태 표시(상시)"
                setShowBadge(false)
            }

            nm.createNotificationChannel(alert)
            nm.createNotificationChannel(ongoing)
        }
    }

    // "상시 진행바 알림" 함수 추가
    const val ONGOING_NOTIFICATION_ID = 1001

    fun buildOngoingProgressNotification(
        context: Context,
        title: String,
        text: String,
        progressPercent: Int? // ✅ null이면 진행바 숨김
    ): android.app.Notification {

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ONGOING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent(context))

        // ✅ 진행바는 값이 있을 때만
        if (progressPercent != null) {
            val p = progressPercent.coerceIn(0, 100)
            builder.setProgress(100, p, false)
        }

        return builder.build()
    }

    fun notifyOngoing(
        context: Context,
        notification: android.app.Notification
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ONGOING_NOTIFICATION_ID, notification)
    }



    // 2. 알림 팝업 표시
    fun showNotification(context: Context, idKey: String, title: String, message: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppPendingIntent(context)) // ✅ 추가
            .setAutoCancel(true)                              // ✅ 추가(탭하면 알림 닫힘)
            .build()


        notificationManager.notify(idKey.hashCode(), notification)

    }
}