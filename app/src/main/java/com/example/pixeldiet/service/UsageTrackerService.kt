package com.example.pixeldiet.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.pixeldiet.data.DatabaseProvider
import com.example.pixeldiet.model.AppUsage
import com.example.pixeldiet.model.NotificationSettings
import com.example.pixeldiet.repository.NotificationPrefs
import com.example.pixeldiet.repository.UsageRepository
import com.example.pixeldiet.ui.notification.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlin.math.roundToInt
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

private const val UI_POLL_MS = 3_000L       // 알림 전환용 (3초)
private const val HEAVY_POLL_MS = 60_000L   // 사용시간/경고/업로드용 (60초)

private var lastShownPkg: String? = null
private var lastShownTitle: String? = null
private var lastShownText: String? = null

class UsageTrackerService : Service() {

    companion object {
        const val ACTION_START = "com.example.pixeldiet.service.action.START"
        const val ACTION_STOP = "com.example.pixeldiet.service.action.STOP"

        // ✅ 옵션: “지금 당장 1회 업로드(조건 무시)” 같은 수동 액션이 필요하면 사용
        const val ACTION_FORCE_UPLOAD = "com.example.pixeldiet.service.action.FORCE_UPLOAD"

        // 폴링 주기(원하면 30_000L로 줄여도 됨)
        private const val POLL_INTERVAL_MS = 60_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    // ✅ 매 tick마다 DB 생성하지 않게 캐싱
    private val db by lazy { DatabaseProvider.getDatabase(this) }
    private val trackedAppDao by lazy { db.trackedAppDao() }

    override fun onCreate() {
        super.onCreate()

        // 채널 생성(상시/경고 채널)
        NotificationHelper.createNotificationChannel(this)

        // Repository 초기화(TrackedApps, NotificationSettings 로드)
        UsageRepository.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_FORCE_UPLOAD -> {
                // ✅ 루프는 유지하되, “이번 1회는 강제 업로드”
                if (uid.isNullOrBlank()) {
                    Log.w("UsageTrackerService", "No uid -> ignore FORCE_UPLOAD")
                } else {
                    serviceScope.launch {
                        try {
                            tick(uid, forceUpload = true)
                        } catch (e: Exception) {
                            Log.e("UsageTrackerService", "force upload tick failed", e)
                        }
                    }
                }
                return START_STICKY
            }

            ACTION_START, null -> {
                val prefs = NotificationPrefs(this)
                if (!prefs.isNotificationServiceEnabled()) {
                    Log.d("UsageTrackerService", "Service disabled in settings -> stopSelf")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }

                // 서비스 시작(이미 돌고 있으면 중복 시작 방지)
                if (loopJob == null) {
                    if (uid.isNullOrBlank()) {
                        Log.w("UsageTrackerService", "No uid -> stopSelf()")
                        stopSelf()
                        return START_NOT_STICKY
                    }
                    startTracking(uid)
                }
                return START_STICKY
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTracking(uid: String) {
        // ✅ Foreground 시작: 즉시 상시 알림 띄우기(진행바는 이후 루프에서 업데이트)
        val initial = NotificationHelper.buildOngoingProgressNotification(
            context = this,
            title = "리마타 사용시간",
            text = "사용시간 확인 중...",
            progressPercent = 0
        )
        startForeground(NotificationHelper.ONGOING_NOTIFICATION_ID, initial)

        loopJob = serviceScope.launch {
            var lastHeavyAt = 0L

            while (isActive) {
                if (!NotificationPrefs(this@UsageTrackerService).isNotificationServiceEnabled()) {
                    Log.d("UsageTrackerService", "Disabled -> stop")
                    stopTracking()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                val now = System.currentTimeMillis()

                // ✅ 1) 가벼운 UI 업데이트: 포그라운드 앱 감지 + 알림 전환만
                try {
                    updateOngoingUiQuick(uid)   // 아래 함수 추가
                } catch (e: Exception) {
                    Log.e("UsageTrackerService", "updateOngoingUiQuick failed", e)
                }

                // ✅ 2) 무거운 처리: 사용시간 계산 + 경고 체크 + (throttle된) 업로드
                if (now - lastHeavyAt >= HEAVY_POLL_MS) {
                    lastHeavyAt = now
                    try {
                        tick(uid, forceUpload = false) // 기존 tick 유지(= loadRealData + alerts)
                    } catch (e: Exception) {
                        Log.e("UsageTrackerService", "tick failed", e)
                    }
                }

                delay(UI_POLL_MS)
            }
        }

    }

    private fun stopTracking() {
        loopJob?.cancel()
        loopJob = null
    }

    private suspend fun tick(uid: String, forceUpload: Boolean) = withContext(Dispatchers.IO) {
        // 1) 최신 사용량 계산 + Room/Flow 갱신
        // ✅ 서비스가 1분마다 돌아도 업로드는 Repository에서 throttle 됨
        UsageRepository.loadRealData(this@UsageTrackerService, uid, forceUpload)

        // 2) 추적 앱 목록 로드
        val trackedApps = trackedAppDao.getAllTrackedAppsOnce()
        val trackedSet = trackedApps.map { it.packageName }.toSet()

        if (trackedSet.isEmpty()) {
            // ✅ ongoing UI는 updateOngoingUiQuick()가 담당
            // 여기서는 경고 체크할 대상이 없으니 그냥 종료
            return@withContext
        }


        // 3) 현재 사용량 리스트에서 추적 앱만 필터
        val all = UsageRepository.appUsageListFlow.value
        val targetApps = all.filter { it.packageName in trackedSet }

        // 4) 상시 알림(진행바) 업데이트:
    // - 추적앱 사용 중이면: 해당 앱 사용시간 + 해당 앱 진행바
    // - 미사용이면: 격려 멘트 + 총 사용시간(작게), 진행바는 숨김(null)
        /*
        val foregroundPkg = getTopForegroundPackage()
        Log.d("UsageTrackerService", "foregroundPkg=$foregroundPkg, isTracked=${foregroundPkg in trackedSet}")
        val totalUsage = targetApps.sumOf { it.currentUsage } // ✅ 멘트 모드에서만 표시할 총 사용시간

        val (title, text, progressPercent) =
            if (foregroundPkg != null && foregroundPkg in trackedSet) {
                val app = targetApps.firstOrNull { it.packageName == foregroundPkg }

                if (app != null) {
                    val used = app.currentUsage
                    val goal = app.goalTime

                    val pct = if (goal > 0) {
                        ((used.toFloat() / goal) * 100f).coerceIn(0f, 100f).roundToInt()
                    } else 0

                    val msg = if (goal > 0) {
                        "오늘 ${formatTime(used)} / 목표 ${formatTime(goal)}"
                    } else {
                        "오늘 ${formatTime(used)} (목표 미설정)"
                    }

                    Triple(
                        "${app.appLabel} 사용 중",
                        msg,     // ✅ 개별 앱 모드: 원래대로 “개별 사용/목표”만
                        pct      // ✅ 개별 앱 모드: 진행바 유지
                    )
                } else {
                    Triple(
                        "리프레쉬 마이 타임",
                        "오늘의 낭비시간을 줄여봐요!  ·  총 ${formatTime(totalUsage)}",
                        null // ✅ 멘트 모드: 진행바 제거
                    )
                }
            } else {
                Triple(
                    "리마타",
                    "오늘의 낭비시간을 줄여봐요!  ·  총 ${formatTime(totalUsage)}",
                    null // ✅ 멘트 모드: 진행바 제거
                )
            }

        NotificationHelper.notifyOngoing(
            this@UsageTrackerService,
            NotificationHelper.buildOngoingProgressNotification(
                context = this@UsageTrackerService,
                title = title,
                text = text,
                progressPercent = progressPercent
            )
        )
        */

        // 5) 개별 앱 경고 알림 체크(50/70/100)
        val settings = UsageRepository.notificationSettingsFlow.value ?: NotificationSettings()
        checkIndividualAppAlerts(targetApps, settings)
    }

    private fun checkIndividualAppAlerts(
        appList: List<AppUsage>,
        settings: NotificationSettings
    ) {
        val now = System.currentTimeMillis()
        val prefs = NotificationPrefs(this)

        for (app in appList) {
            val goal = app.goalTime
            if (goal <= 0) continue

            val usage = app.currentUsage
            val percentage = (usage.toFloat() / goal) * 100f
            val intervalMillis = settings.repeatIntervalMinutes * 60 * 1000L
            val pkg = app.packageName

            // ✅ 100% (반복)
            val prefKey100 = "ind_100_$pkg"
            if (settings.individualApp100 && percentage >= 100f) {
                val lastSent = prefs.getLastRepeatSentTime(prefKey100)
                if (now - lastSent > intervalMillis) {
                    val notifIdKey = "${pkg}_100" // ✅ 패키지+임계치 기반 ID
                    NotificationHelper.showNotification(
                        context = this,
                        idKey = notifIdKey,
                        title = "${app.appLabel} 멈춰!",
                        message = "목표 ${formatTime(goal)} / 사용 ${formatTime(usage)}"
                    )
                    prefs.recordRepeatSentTime(prefKey100)
                }
            }

            // ✅ 70% (하루 1회)
            val prefKey70 = "ind_70_$pkg"
            if (settings.individualApp70 && percentage >= 70f && !prefs.hasSentToday(prefKey70)) {
                val notifIdKey = "${pkg}_70"
                NotificationHelper.showNotification(
                    context = this,
                    idKey = notifIdKey,
                    title = "${app.appLabel} 70% 사용",
                    message = "목표 사용시간의 70%에 도달했어"
                )
                prefs.recordSentToday(prefKey70)
            }

            // ✅ 50% (하루 1회)
            val prefKey50 = "ind_50_$pkg"
            if (settings.individualApp50 && percentage >= 50f && !prefs.hasSentToday(prefKey50)) {
                val notifIdKey = "${pkg}_50"
                NotificationHelper.showNotification(
                    context = this,
                    idKey = notifIdKey,
                    title = "${app.appLabel} 50% 사용",
                    message = "목표 사용시간의 50%에 도달했어"
                )
                prefs.recordSentToday(prefKey50)
            }
        }
    }

    private fun formatTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format("%d시간 %02d분", h, m)
    }

    private fun getForegroundPackageName(windowMs: Long = 30_000L): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - windowMs

        val events = usm.queryEvents(begin, end)
        val event = UsageEvents.Event()

        var lastForeground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            val isForeground =
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)

            if (isForeground) lastForeground = event.packageName
        }
        return lastForeground
    }

    private fun getTopForegroundPackage(windowMs: Long = 5 * 60 * 1000L): String? {
        val usm = getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - windowMs

        val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, begin, end)
            ?: return null

        val top = stats.maxByOrNull { it.lastTimeUsed }
        return top?.packageName
    }

    private suspend fun updateOngoingUiQuick(uid: String) = withContext(Dispatchers.IO) {
        // 추적앱 목록(룸) 자주 읽는 게 부담되면: 1분 tick에서 캐시해도 됨.
        val trackedSet = trackedAppDao.getAllTrackedAppsOnce().map { it.packageName }.toSet()

        val foregroundPkg = getTopForegroundPackage() // 너가 잘 된다고 한 방식(최근 사용 앱)
        val all = UsageRepository.appUsageListFlow.value
        val targetApps = all.filter { it.packageName in trackedSet }

        val totalUsage = targetApps.sumOf { it.currentUsage }

        val (title, text, progressPercent) =
            if (foregroundPkg != null && foregroundPkg in trackedSet) {
                val app = targetApps.firstOrNull { it.packageName == foregroundPkg }
                if (app != null) {
                    val used = app.currentUsage
                    val goal = app.goalTime
                    val pct = if (goal > 0) ((used.toFloat() / goal) * 100f).coerceIn(0f, 100f).toInt() else 0
                    val msg = if (goal > 0) "오늘 ${formatTime(used)} / 목표 ${formatTime(goal)}"
                    else "오늘 ${formatTime(used)} (목표 미설정)"
                    Triple("${app.appLabel} 사용 중", msg, pct)   // ✅ 앱모드: 진행바 유지
                } else {
                    Triple("리프레쉬 마이 타임", "오늘의 낭비시간을 줄여봐요! · 총 ${formatTime(totalUsage)}", null) // ✅ 멘트모드: 진행바 숨김
                }
            } else {
                Triple("리프레쉬 마이 타임", "오늘의 낭비시간을 줄여봐요! · 총 ${formatTime(totalUsage)}", null)
            }

        // ✅ 같은 내용이면 notify 안 해서 깜빡임/부하 줄이기
        if (foregroundPkg == lastShownPkg && title == lastShownTitle && text == lastShownText) return@withContext

        lastShownPkg = foregroundPkg
        lastShownTitle = title
        lastShownText = text

        NotificationHelper.notifyOngoing(
            this@UsageTrackerService,
            NotificationHelper.buildOngoingProgressNotification(
                context = this@UsageTrackerService,
                title = title,
                text = text,
                progressPercent = progressPercent
            )
        )
    }

}


