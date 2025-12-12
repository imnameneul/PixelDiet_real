package com.example.pixeldiet.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pixeldiet.model.AppUsage
import com.example.pixeldiet.model.NotificationSettings
import com.example.pixeldiet.repository.NotificationPrefs
import com.example.pixeldiet.repository.UsageRepository
import com.example.pixeldiet.ui.notification.NotificationHelper
import com.google.firebase.auth.FirebaseAuth

class UsageCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    // 알림 관련 SharedPreferences 래퍼
    private val prefs = NotificationPrefs(context)

    override suspend fun doWork(): Result {
        return try {
            val repository = UsageRepository
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.failure()
            // 1. 최신 사용량 데이터 로딩
            UsageRepository.init(context)
            repository.loadRealData(context,uid)

            // 2. 최신 데이터 가져오기
            val appList = repository.appUsageListFlow.value
            val settings = prefs.loadNotificationSettings()

            if (appList.isNullOrEmpty()) {
                return Result.failure()
            }

            // 3. 추적 중인 앱 목록(패키지명) 로드
            val trackedPackages = getTrackedPackages()

            // 4. 실제로 알림 대상으로 사용할 앱 리스트
            val targetApps: List<AppUsage> =
                if (trackedPackages.isEmpty()) {
                    // 아직 사용자가 추적앱을 고르지 않았다면: 설치된 모든 앱 사용
                    appList
                } else {
                    appList.filter { it.packageName in trackedPackages }
                }

            if (targetApps.isEmpty()) {
                return Result.failure()
            }

            // 5. 알림 조건 확인
            checkIndividualAppAlerts(targetApps, settings)
            checkTotalAppAlerts(targetApps, settings)

            Result.success()

        } catch (e: Exception) {
            Result.failure()
        }
    }

    // 📌 추적 중인 앱 패키지 목록 가져오기 (SharedViewModel과 동일 prefs 사용)
    private fun getTrackedPackages(): Set<String> {
        val trackedPrefs =
            context.getSharedPreferences("tracked_apps_prefs", Context.MODE_PRIVATE)
        return trackedPrefs.getStringSet("tracked_packages", emptySet()) ?: emptySet()
    }

    // 🔹 SharedViewModel과 동일 prefs에서 전체 목표시간(분) 읽기
    private fun getOverallGoalMinutes(): Int? {
        val goalPrefs =
            context.getSharedPreferences("goal_prefs", Context.MODE_PRIVATE)
        val saved = goalPrefs.getInt("overall_goal_minutes", -1)
        return if (saved >= 0) saved else null
    }

    // ---------------- 개별 앱 알림 ----------------

    private fun checkIndividualAppAlerts(
        appList: List<AppUsage>,
        settings: NotificationSettings
    ) {
        val now = System.currentTimeMillis()

        for (app in appList) {
            if (app.goalTime == 0) continue

            val usage = app.currentUsage
            val goal = app.goalTime
            val percentage = (usage.toFloat() / goal) * 100
            val intervalMillis = settings.repeatIntervalMinutes * 60 * 1000L

            // 100% 초과 알림 (반복 가능)
            val type100 = "ind_100_${app.packageName}"   // 예: ind_100_com.google.android.youtube
            if (settings.individualApp100 && percentage >= 100) {
                val lastSent = prefs.getLastRepeatSentTime(type100)
                if (now - lastSent > intervalMillis) {
                    NotificationHelper.showNotification(
                        context,
                        "${app.appLabel} 멈춰!",
                        "목표 시간 ${formatTime(goal)} / 사용 ${formatTime(usage)}"
                    )
                    prefs.recordRepeatSentTime(type100)
                }
            }

            // 70% 도달 알림 (하루 1회)
            val type70 = "ind_70_${app.packageName}"
            if (settings.individualApp70 && percentage >= 70 && !prefs.hasSentToday(type70)) {
                NotificationHelper.showNotification(
                    context,
                    "${app.appLabel} 70% 사용",
                    "목표 사용시간을 70% 사용했어요!"
                )
                prefs.recordSentToday(type70)
            }

            // 50% 도달 알림 (하루 1회)
            val type50 = "ind_50_${app.packageName}"
            if (settings.individualApp50 && percentage >= 50 && !prefs.hasSentToday(type50)) {
                NotificationHelper.showNotification(
                    context,
                    "${app.appLabel} 50% 사용",
                    "목표 사용시간을 50% 사용했어요!"
                )
                prefs.recordSentToday(type50)
            }
        }
    }

    // ---------------- 전체 앱 합산 알림 ----------------

    private fun checkTotalAppAlerts(
        appList: List<AppUsage>,
        settings: NotificationSettings
    ) {
        // 🔹 추적 대상 앱들의 총 사용시간(분)
        val totalUsage = appList.sumOf { it.currentUsage }

        // 🔹 전체 목표시간(분): 설정된 값이 있으면 그걸 사용, 없으면 앱별 목표 합
        val overallGoal = getOverallGoalMinutes()
        val autoGoal = appList.sumOf { it.goalTime }
        val totalGoal = overallGoal ?: autoGoal

        if (totalGoal == 0) return

        val percentage = (totalUsage.toFloat() / totalGoal) * 100
        val now = System.currentTimeMillis()
        val intervalMillis = settings.repeatIntervalMinutes * 60 * 1000L

        // 100% 초과
        val type100 = "total_100"
        if (settings.total100 && percentage >= 100) {
            val lastSent = prefs.getLastRepeatSentTime(type100)
            if (now - lastSent > intervalMillis) {
                NotificationHelper.showNotification(
                    context,
                    "전체 시간 초과!",
                    "전체 목표 ${formatTime(totalGoal)} / 사용 ${formatTime(totalUsage)}"
                )
                prefs.recordRepeatSentTime(type100)
            }
        }

        // 70%
        val type70 = "total_70"
        if (settings.total70 && percentage >= 70 && !prefs.hasSentToday(type70)) {
            NotificationHelper.showNotification(
                context,
                "전체 시간 70% 사용",
                "전체 목표사용시간을 70% 사용했어요!"
            )
            prefs.recordSentToday(type70)
        }

        // 50%
        val type50 = "total_50"
        if (settings.total50 && percentage >= 50 && !prefs.hasSentToday(type50)) {
            NotificationHelper.showNotification(
                context,
                "전체 시간 50% 사용",
                "전체 목표사용시간을 50% 사용했어요!"
            )
            prefs.recordSentToday(type50)
        }
    }


    private fun formatTime(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return String.format("%d시간 %02d분", hours, mins)
    }
}
