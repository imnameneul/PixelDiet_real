package com.example.pixeldiet.model

import android.graphics.drawable.Drawable
import com.prolificinteractive.materialcalendarview.CalendarDay

// ✅ (중요) 더 이상 고정된 AppName enum을 쓰지 않고,
//    packageName + appLabel + icon을 직접 들고 다니는 구조로 변경.

data class UserProfile(
    val uid: String,
    val name: String,
    val imageUrl: String,
    val friendCode: String
)

// 메인화면에 표시될 개별 앱 데이터
data class AppUsage(
    val packageName: String,      // 예: "com.google.android.youtube"
    val appLabel: String,         // 예: "YouTube"
    val icon: Drawable?,          // 앱 아이콘 (없으면 null)
    var currentUsage: Int,    // 분 단위 사용 시간
    var goalTime: Int = 0,        // 분 단위 목표 시간
    var streak: Int = 0           // 양수(달성 연속일), 음수(실패 연속일)
)

// 일별 전체 사용 데이터 (캘린더/차트용)
data class DailyUsage(
    val date: String,                // "YYYY-MM-DD"
    val appUsages: Map<String, Int>  // key: packageName, value: 사용 시간(분)
)

// 알림 설정 (기존 그대로 사용)
data class NotificationSettings(
    var individualApp50: Boolean = true,
    var individualApp70: Boolean = true,
    var individualApp100: Boolean = true,
    var total50: Boolean = true,
    var total70: Boolean = true,
    var total100: Boolean = true,
    // 100% 초과 시 반복 알림 간격 (기본값 5분)
    var repeatIntervalMinutes: Int = 5
)

// 캘린더 데코레이터용 데이터 (기존 그대로)
data class CalendarDecoratorData(
    val date: CalendarDay,
    val status: DayStatus
)

// 날짜별 달성/경고/실패 상태
enum class DayStatus {
    SUCCESS, // 초록
    WARNING, // 노랑 (70% 초과)
    FAIL     // 빨강
}
