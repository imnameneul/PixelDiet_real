package com.example.pixeldiet.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.pixeldiet.model.NotificationSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SharedPreferences를 관리하는 헬퍼 클래스
 * - 알림 설정 (켜기/끄기, 반복 시간)
 * - 마지막 알림 보낸 시간/날짜 기록
 */
class NotificationPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("PixelDietPrefs", Context.MODE_PRIVATE)

    // --- 오늘 날짜 (YYYY-MM-DD) ---
    private val todayString: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())

    // --- 알림 설정 저장/로드 ---

    fun saveNotificationSettings(settings: NotificationSettings) {
        prefs.edit().apply {
            putBoolean("ind_50", settings.individualApp50)
            putBoolean("ind_70", settings.individualApp70)
            putBoolean("ind_100", settings.individualApp100)
            putBoolean("total_50", settings.total50)
            putBoolean("total_70", settings.total70)
            putBoolean("total_100", settings.total100)
            putInt("repeat_interval", settings.repeatIntervalMinutes)
            apply()
        }
    }

    fun loadNotificationSettings(): NotificationSettings {
        return NotificationSettings(
            individualApp50 = prefs.getBoolean("ind_50", true),
            individualApp70 = prefs.getBoolean("ind_70", true),
            individualApp100 = prefs.getBoolean("ind_100", true),
            total50 = prefs.getBoolean("total_50", true),
            total70 = prefs.getBoolean("total_70", true),
            total100 = prefs.getBoolean("total_100", true),
            repeatIntervalMinutes = prefs.getInt("repeat_interval", 5) // 기본값 5분
        )
    }

    // --- "하루 한 번" 알림 날짜 기록 ---

    /**
     * @param type "ind_50", "ind_70", "total_50", "total_70"
     * @return 오늘 이 타입의 알림을 보낸 적이 있으면 true
     */
    fun hasSentToday(type: String): Boolean {
        // "ind_50"라는 키에 "2025-11-17" (오늘 날짜)이 저장되어 있는지 확인
        return prefs.getString(type, null) == todayString
    }

    /**
     * @param type "ind_50", "ind_70", "total_50", "total_70"
     */
    fun recordSentToday(type: String) {
        // "ind_50" 키에 오늘 날짜("2025-11-17")를 저장
        prefs.edit().putString(type, todayString).apply()
    }

    // --- "반복 알림" 시간 기록 ---

    /**
     * @param type "ind_100", "total_100"
     * @return 마지막 100% 알림 보낸 시간 (타임스탬프)
     */
    fun getLastRepeatSentTime(type: String): Long {
        return prefs.getLong(type, 0L) // 0L = 보낸 적 없음
    }

    /**
     * @param type "ind_100", "total_100"
     */
    fun recordRepeatSentTime(type: String) {
        prefs.edit().putLong(type, System.currentTimeMillis()).apply()
    }
}