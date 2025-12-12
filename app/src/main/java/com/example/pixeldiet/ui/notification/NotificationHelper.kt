package com.example.pixeldiet.ui.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.pixeldiet.R

object NotificationHelper {

    private const val CHANNEL_ID = "PixelDietChannel"
    private const val CHANNEL_NAME = "앱 사용 시간 알림"

    // 1. 알림 채널 생성 (안드로이드 8.0 이상 필수)
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "설정한 앱 사용 시간을 초과하면 알림을 보냅니다."
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 2. 알림 팝업 표시
    fun showNotification(context: Context, title: String, message: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // ⭐️ 임시 아이콘 (나중에 앱 아이콘으로 변경)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // 고유한 ID로 알림을 표시 (ID가 같으면 덮어쓰기됨)
        notificationManager.notify(title.hashCode(), notification)
    }
}