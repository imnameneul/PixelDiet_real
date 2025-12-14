package com.example.pixeldiet.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import com.example.pixeldiet.data.*
import com.example.pixeldiet.model.AppUsage
import com.example.pixeldiet.model.DailyUsage
import com.example.pixeldiet.model.NotificationSettings
import com.google.common.reflect.TypeToken
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

object UsageRepository {

    private lateinit var db: AppDatabase

    private val _trackedApps = MutableStateFlow<List<TrackedAppEntity>>(emptyList())
    val trackedAppsFlow: StateFlow<List<TrackedAppEntity>> get() = _trackedApps

    private val _appUsageList = MutableStateFlow<List<AppUsage>>(emptyList())
    val appUsageListFlow: StateFlow<List<AppUsage>> get() = _appUsageList

    private val _dailyUsageList = MutableStateFlow<List<DailyUsage>>(emptyList())
    val dailyUsageListFlow: StateFlow<List<DailyUsage>> get() = _dailyUsageList

    private val _notificationSettings = MutableStateFlow<NotificationSettings?>(null)
    val notificationSettingsFlow: StateFlow<NotificationSettings?> get() = _notificationSettings

    private val currentGoals = mutableMapOf<String, Int>()

    // ---------------- Firestore 업로드 throttle ----------------
    private const val UPLOAD_PREFS = "usage_upload_prefs"
    private const val KEY_LAST_UPLOAD_AT_PREFIX = "last_upload_at_"      // + uid
    private const val KEY_LAST_UPLOAD_TOTAL_PREFIX = "last_upload_total_" // + uid

    // 업로드 최소 간격(추천: 5분~15분 사이)
    private const val UPLOAD_MIN_INTERVAL_MS = 5 * 60 * 1000L

    // 사용량 변화가 거의 없으면 업로드 생략(분 단위)
    private const val UPLOAD_MIN_DELTA_MINUTES = 2

    private fun getUploadPrefs(context: Context) =
        context.getSharedPreferences(UPLOAD_PREFS, Context.MODE_PRIVATE)

    private fun shouldUploadThrottled(
        context: Context,
        uid: String,
        totalMinutesNow: Int,
        force: Boolean
    ): Boolean {
        if (force) return true

        val prefs = getUploadPrefs(context)
        val lastAt = prefs.getLong(KEY_LAST_UPLOAD_AT_PREFIX + uid, 0L)
        val lastTotal = prefs.getInt(KEY_LAST_UPLOAD_TOTAL_PREFIX + uid, -1)

        // 첫 업로드는 1회 허용
        if (lastAt == 0L || lastTotal < 0) return true

        val now = System.currentTimeMillis()
        val elapsed = now - lastAt
        val delta = kotlin.math.abs(totalMinutesNow - lastTotal)

        // 최소 간격 만족 + 변화량 충분할 때만 업로드
        return elapsed >= UPLOAD_MIN_INTERVAL_MS && delta >= UPLOAD_MIN_DELTA_MINUTES
    }

    private fun markUploaded(context: Context, uid: String, totalMinutesNow: Int) {
        val prefs = getUploadPrefs(context)
        prefs.edit()
            .putLong(KEY_LAST_UPLOAD_AT_PREFIX + uid, System.currentTimeMillis())
            .putInt(KEY_LAST_UPLOAD_TOTAL_PREFIX + uid, totalMinutesNow)
            .apply()
    }

    // ---------------- 초기화 ----------------
    fun init(context: Context) {
        if (!::db.isInitialized) {
            db = DatabaseProvider.getDatabase(context)
        }

        // Coroutine 안에서 suspend 함수 호출
        CoroutineScope(Dispatchers.IO).launch {
            _trackedApps.value = db.trackedAppDao().getAllTrackedAppsOnce()
            loadNotificationSettings()
        }
    }
    // ---------------- 추적 앱 관리 ----------------

    fun updateDailyUsageList(list: List<DailyUsage>) {
        _dailyUsageList.value = list
    }
    suspend fun deleteTrackedApp(packageName: String) {
        db.trackedAppDao().deleteByPackage(packageName)
        _trackedApps.value = _trackedApps.value.filter { it.packageName != packageName }
    }
    suspend fun getDailyUsageList(uid: String, from: String, to: String): List<DailyUsage> {
        return db.dailyUsageDao().getUsageInRange(uid, from, to) // Flow<List<DailyUsageEntity>>
            .first() // Flow에서 첫 번째 emit 값 가져오기
            .map { it.toDailyUsage() } // Entity → 도메인 모델 변환
    }
    suspend fun updateTrackedApps(apps: List<TrackedAppEntity>) {
        db.trackedAppDao().insertOrUpdate(apps)
        // DB에서 다시 읽어서 emit
        val updated = db.trackedAppDao().getAllTrackedAppsOnce()
        _trackedApps.value = updated
    }
    fun updateTrackedAppsFromBackup(apps: List<TrackedAppEntity>) {
        CoroutineScope(Dispatchers.Main).launch {  // UI 스레드에서 emit
            _trackedApps.value = apps
        }
    }

    suspend fun getAllTrackedOnce(): List<TrackedAppEntity> {
        val list = db.trackedAppDao().getAllTrackedAppsOnce()
        _trackedApps.value = list
        return list
    }

    // ---------------- 목표 시간 업데이트 ----------------
    fun updateGoalTimes(goals: Map<String, Int>) {
        currentGoals.clear()
        currentGoals.putAll(goals)

        // trackedAppsFlow 갱신
        val updatedTracked = _trackedApps.value.map { tracked ->
            tracked.copy(goalTime = currentGoals[tracked.packageName] ?: tracked.goalTime)
        }
        _trackedApps.value = updatedTracked

        // _appUsageList에도 반영
        val newList = _appUsageList.value.map { usage ->
            usage.copy(goalTime = currentGoals[usage.packageName] ?: usage.goalTime)
        }
        _appUsageList.value = newList
    }


    // ---------------- 실제 사용 데이터 로딩 ----------------

    private suspend fun refreshDailyUsageCache(uid: String, days: Int = 120) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        val to = sdf.format(Date())
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(days - 1)) }
        val from = sdf.format(cal.time)

        val list = db.dailyUsageDao().getUsageInRange(uid, from, to)
            .first()
            .map { it.toDailyUsage() }

        _dailyUsageList.value = list
    }


    suspend fun loadRealData(context: Context, uid: String, forceUpload: Boolean = false) {
        if (!::db.isInitialized) {
            db = DatabaseProvider.getDatabase(context)
        }
        val packageManager = context.packageManager
        val todayUsageMap = calculatePreciseUsage(context)

        val trackedPackages = _trackedApps.value.map { it.packageName }.toSet()
        val packageNames = mutableSetOf<String>()
        packageNames.addAll(trackedPackages)
        packageNames.addAll(todayUsageMap.keys)
        packageNames.addAll(currentGoals.keys)

        val newAppUsageList = packageNames.map { pkg ->
            val todayUsage = todayUsageMap[pkg] ?: 0
            val goal = currentGoals[pkg] ?: _trackedApps.value.find { it.packageName == pkg }?.goalTime ?: 0

            val label = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) { pkg }

            val icon = try {
                packageManager.getApplicationIcon(pkg)
            } catch (e: Exception) { null }

            AppUsage(pkg, label, icon, todayUsage, goal, 0)
        }.sortedBy { it.appLabel.lowercase() }

        _appUsageList.value = newAppUsageList

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        val todayKey = sdf.format(Date())
        val dailyEntity = DailyUsageEntity.fromDailyUsage(uid, DailyUsage(todayKey, todayUsageMap))

        CoroutineScope(Dispatchers.IO).launch {
            db.dailyUsageDao().insertOrUpdate(dailyEntity)
            refreshDailyUsageCache(uid)   // ✅ 추가 (최근 120일 캐시 갱신)
        }

        // ✅ 업로드는 throttle 적용 (서비스가 1분마다 돌더라도 Firestore는 과도하게 안 찍힘)
        val totalMinutesNow = todayUsageMap.values.sum()

        if (shouldUploadThrottled(context, uid, totalMinutesNow, force = forceUpload)) {
            uploadDailyUsageToFirebase(uid, todayUsageMap)
            markUploaded(context, uid, totalMinutesNow)
        } else {
            Log.d("FirebaseBackup", "⏭️ upload skipped(throttled): total=$totalMinutesNow")
        }
    }

    // ---------------- 알림 설정 ----------------
    private fun loadNotificationSettings() = CoroutineScope(Dispatchers.IO).launch {
        val entity = db.notificationSettingsDao().getSettings() ?: NotificationSettingsEntity()
        _notificationSettings.value = entity.toDto()
    }
    suspend fun uploadDailyUsageToFirebase(uid: String, appUsageMap: Map<String, Int>) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())
        val data = mapOf("date" to today, "appUsages" to appUsageMap)

        try {
            Firebase.firestore
                .collection("users")
                .document(uid)
                .collection("dailyRecords")
                .document(today)
                .set(data)
                .await()

            Log.d("FirebaseBackup", "✅ Daily usage uploaded: $uid / $today")
        } catch (e: Exception) {
            Log.e("FirebaseBackup", "❌ upload failed: $uid / $today", e)
        }
    }

    suspend fun updateNotificationSettings(settings: NotificationSettings) = CoroutineScope(Dispatchers.IO).launch {
        val entity = NotificationSettingsEntity.fromDto(settings)
        db.notificationSettingsDao().insertOrUpdate(entity)
        _notificationSettings.value = settings
    }

    suspend fun getDailyAppUsage(uid: String, date: String): Map<String, Int> {
        val entity = db.dailyUsageDao().getByDate(uid, date)

        return entity?.appUsagesJson?.let {
            Gson().fromJson(it, object : TypeToken<Map<String, Int>>() {}.type)
        } ?: emptyMap()
    }
}


// ---------------- 앱 사용 시간 계산 ----------------
private fun calculatePreciseUsage(context: Context): Map<String, Int> {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startTime = calendar.timeInMillis
    val endTime = System.currentTimeMillis()

    val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
    val event = UsageEvents.Event()

    val appUsageMap = mutableMapOf<String, Long>()
    val startMap = mutableMapOf<String, Long>()

    while (usageEvents.hasNextEvent()) {
        usageEvents.getNextEvent(event)
        val pkg = event.packageName
        when (event.eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND,
            UsageEvents.Event.ACTIVITY_RESUMED -> startMap[pkg] = event.timeStamp
            UsageEvents.Event.MOVE_TO_BACKGROUND,
            UsageEvents.Event.ACTIVITY_PAUSED -> {
                startMap[pkg]?.let { sTime ->
                    val duration = event.timeStamp - sTime
                    if (duration > 0) appUsageMap[pkg] = (appUsageMap[pkg] ?: 0L) + duration
                    startMap.remove(pkg)
                }
            }
            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                startMap.forEach { (p, sTime) ->
                    val duration = event.timeStamp - sTime
                    if (duration > 0) appUsageMap[p] = (appUsageMap[p] ?: 0L) + duration
                }
                startMap.clear()
            }
        }
    }

    startMap.forEach { (pkg, sTime) ->
        val duration = endTime - sTime
        if (duration > 0) appUsageMap[pkg] = (appUsageMap[pkg] ?: 0L) + duration
    }

    return appUsageMap.mapValues { (_, millis) -> (millis / (1000 * 60)).toInt() }

}

// ---------------- NotificationSettings 변환 ----------------
fun NotificationSettingsEntity.toDto(): NotificationSettings = NotificationSettings(
    individualApp50 = individualApp50,
    individualApp70 = individualApp70,
    individualApp100 = individualApp100,
    repeatIntervalMinutes = repeatIntervalMinutes
)

fun NotificationSettingsEntity.Companion.fromDto(dto: NotificationSettings): NotificationSettingsEntity =
    NotificationSettingsEntity(
        individualApp50 = dto.individualApp50,
        individualApp70 = dto.individualApp70,
        individualApp100 = dto.individualApp100,
        repeatIntervalMinutes = dto.repeatIntervalMinutes
    )

