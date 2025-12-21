package com.example.pixeldiet.viewmodel

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixeldiet.data.DatabaseProvider
import com.example.pixeldiet.data.TrackedAppEntity
import com.example.pixeldiet.model.*
import com.example.pixeldiet.repository.SyncRepository
import com.example.pixeldiet.repository.UsageRepository
import com.example.pixeldiet.data.GoalHistoryEntity
import com.github.mikephil.charting.data.Entry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.prolificinteractive.materialcalendarview.CalendarDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class UserProfile(
    val uid: String,
    val name: String,
    val imageUrl: String,
    val friendCode: String
)

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    // ------------------- User/Profile -------------------
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile

    private val trackedPrefs by lazy {
        application.getSharedPreferences("tracked_apps_prefs", Context.MODE_PRIVATE)
    }
    private val goalPrefs by lazy {
        application.getSharedPreferences("goal_prefs", Context.MODE_PRIVATE)
    }

    private val _trackedPackages = MutableStateFlow<Set<String>>(emptySet())
    val trackedPackagesFlow: StateFlow<Set<String>> = _trackedPackages

    private val _trackedApps = MutableStateFlow<List<TrackedAppEntity>>(emptyList())
    val trackedAppsFlow: StateFlow<List<TrackedAppEntity>> = _trackedApps

    // ✅ 로딩 게이트: 기본은 false (동기화/초기 로딩 끝나면 true)
    private val _isDataReady = MutableStateFlow(false)
    val isDataReady: StateFlow<Boolean> = _isDataReady

    private val _dailyDetailFlow = MutableStateFlow<List<Pair<AppUsage, Int>>>(emptyList())
    val dailyDetailFlow: StateFlow<List<Pair<AppUsage, Int>>> get() = _dailyDetailFlow

    private val _overallGoalMinutes = MutableStateFlow<Int?>(null)
    val overallGoalFlow: StateFlow<Int?> = _overallGoalMinutes

    // ----------- 1) “마지막 복원 기록” 저장용 Prefs 추가 -----------
    private val restorePrefs by lazy {
        getApplication<Application>().getSharedPreferences("restore_prefs", Context.MODE_PRIVATE)
    }

    private companion object {
        private const val KEY_LAST_RESTORE_UID = "last_restore_uid"
        private const val KEY_LAST_RESTORE_AT = "last_restore_at"
        private const val RESTORE_TTL_MS = 24L * 60 * 60 * 1000 // 24시간(원하면 조절)
    }

    private suspend fun shouldRestoreFromFirestore(uid: String): Boolean {
        val lastUid = restorePrefs.getString(KEY_LAST_RESTORE_UID, null)
        if (lastUid != uid) return true            // 계정이 바뀌었으면 복원 필요

        val lastAt = restorePrefs.getLong(KEY_LAST_RESTORE_AT, 0L)
        if (lastAt == 0L) return true              // 복원 기록이 없으면 1회 복원
        if (System.currentTimeMillis() - lastAt > RESTORE_TTL_MS) return true // 너무 오래됐으면 복원

        // ✅ “필요할 때만 예외적 복원” 핵심: 로컬(Room)이 비어있으면 복원
        val db = DatabaseProvider.getDatabase(getApplication())
        val hasProfile = db.userProfileDao().getUserProfileOnce(uid) != null
        val hasAnyUsage = db.dailyUsageDao().hasAnyDailyUsage(uid)

        return !hasProfile || !hasAnyUsage
    }

    // ------------------- Firebase Auth -------------------
    private val auth = FirebaseAuth.getInstance()
    private val _userName = MutableStateFlow(getUserName())
    val userName: StateFlow<String> = _userName
    val isGoogleUser = MutableStateFlow(isGoogleLogin())

    private val authListener = FirebaseAuth.AuthStateListener {
        _userName.value = getUserName()
        isGoogleUser.value = isGoogleLogin()
    }

    private fun getCurrentUserUid(): String? = auth.currentUser?.uid

    // ------------------- Repositories -------------------
    private val repository = UsageRepository
    private val syncRepository = SyncRepository(application.applicationContext)

    // ------------------- App usage (UI DTO) -------------------
    private val _appUsageList = MutableStateFlow<List<AppUsage>>(emptyList())
    val appUsageListFlow: StateFlow<List<AppUsage>> get() = _appUsageList

    private val context = getApplication<Application>().applicationContext

    val dailyUsageListFlow: StateFlow<List<DailyUsage>> = repository.dailyUsageListFlow

    private val goalHistoryListFlow: StateFlow<List<GoalHistoryEntity>> =
        callbackFlow {
            val auth = FirebaseAuth.getInstance()
            val listener = FirebaseAuth.AuthStateListener {
                trySend(it.currentUser?.uid)
            }
            auth.addAuthStateListener(listener)
            trySend(auth.currentUser?.uid)
            awaitClose { auth.removeAuthStateListener(listener) }
        }.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -120) }
                val from = sdf.format(cal.time)
                val to = sdf.format(Date())

                val db = DatabaseProvider.getDatabase(getApplication())
                db.goalHistoryDao().getGoalsInRange(uid, from, to)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notificationSettingsFlow: StateFlow<NotificationSettings?> = repository.notificationSettingsFlow

    // ------------------- Total usage (calendar/stat) -------------------
    val totalUsageFlow: StateFlow<Pair<Int, Int>> =
        combine(appUsageListFlow, trackedPackagesFlow, overallGoalFlow) { apps, tracked, overallGoal ->
            val totalUsed = apps
                .filter { tracked.isEmpty() || it.packageName in tracked }
                .sumOf { it.currentUsage }   // ✅ 오늘 사용량 합

            val totalGoal = overallGoal
                ?: apps.filter { tracked.isEmpty() || it.packageName in tracked }
                    .sumOf { it.goalTime }

            totalUsed to totalGoal
        }.stateIn(viewModelScope, SharingStarted.Lazily, 0 to (_overallGoalMinutes.value ?: 0))

    // ------------------- Filters -------------------
    private val _selectedFilter = MutableStateFlow<String?>(null)
    val selectedFilterTextFlow: StateFlow<String> =
        combine(_selectedFilter, appUsageListFlow) { pkg, apps ->
            if (pkg == null) "전체" else apps.find { it.packageName == pkg }?.appLabel ?: "전체"
        }.stateIn(viewModelScope, SharingStarted.Lazily, "전체")

    // ------------------- Selected month -------------------
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    val selectedMonthFlow: StateFlow<Int> = _selectedMonth

    // ------------------- Initial load -------------------
    private var hasSyncedOnce = false

    init {
        auth.addAuthStateListener(authListener)

        // ✅ init에서는 직접 syncRepository를 부르지 말고, 아래 "내부 함수"를 통해 1회 동기화 + 데이터 구성까지 끝낸다.
        viewModelScope.launch(Dispatchers.IO) {
            val uid = getCurrentUserUid()
            if (uid == null) {
                // 로그인 전 상태라면 UI가 영원히 로딩에 갇히지 않게 풀어줌
                _isDataReady.value = true
                return@launch
            }

            // ✅ 추가: 로그인된 사용자라면 프로필 정보를 불러와 _userProfile을 채웁니다.
            initUserProfile()

            // ✅ B안: 기본은 "로컬만 빠르게" / 단, 예외적으로 "필요할 때만 1회 복원"
            if (shouldRestoreFromFirestore(uid)) {
                syncFromFirestoreInternal(uid, force = true)
            } else {
                refreshDataInternal(uid)
                hasSyncedOnce = true        // ✅ 세션 내에서 “이미 준비됨” 표시
                _isDataReady.value = true
            }
        }
    }

    // ------------------- SharedPreferences / Room 업데이트 -------------------
    fun updateTrackedPackages(newSet: Set<String>) {
        _trackedPackages.value = newSet
        trackedPrefs.edit().putStringSet("tracked_packages", newSet).apply()
    }

    fun setOverallGoal(minutes: Int?) {
        _overallGoalMinutes.value = minutes
        goalPrefs.edit().apply {
            if (minutes == null) remove("overall_goal_minutes") else putInt("overall_goal_minutes", minutes)
        }.apply()
    }

    // ------------------- Firebase 인증 관련 -------------------
    private fun getUserName(): String {
        val user = auth.currentUser
        return if (user != null && !user.isAnonymous) {
            "${user.displayName ?: "사용자"}님 환영합니다"
        } else "게스트 로그인 중입니다"
    }

    private fun isGoogleLogin(): Boolean {
        val user = auth.currentUser
        return user != null && !user.isAnonymous
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = DatabaseProvider.getDatabase(getApplication())
            db.userProfileDao().clearAll()
            db.trackedAppDao().clearAll()
            db.dailyUsageDao().clearAll()
            db.groupDao().clearAll()
            db.friendDao().clearAll()

            trackedPrefs.edit().clear().apply()
            goalPrefs.edit().clear().apply()
            restorePrefs.edit().clear().apply()

            auth.signOut()

            _userProfile.value = null
            _trackedApps.value = emptyList()
            _trackedPackages.value = emptySet()
            _overallGoalMinutes.value = null

            // 로그아웃 직후에는 다시 로딩상태로 돌려도 되지만,
            // 현재 화면 네비게이션에서 Login으로 이동할 것이므로 true로 풀어둠
            _isDataReady.value = true
            hasSyncedOnce = false
        }
    }

    fun onGoogleLoginSuccess(idToken: String) {
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()

                // 상태 초기화
                _userProfile.value = null
                _trackedApps.value = emptyList()
                _trackedPackages.value = emptySet()
                _overallGoalMinutes.value = null

                val uid = getCurrentUserUid() ?: return@launch
                val name = auth.currentUser?.displayName

                // ✅ (중요) Room에 프로필 없으면 생성 + friendCode 생성까지
                val entity = syncRepository.initUserProfileIfNeeded(uid, name)

                // ✅ (중요) SettingsScreen이 보는 userProfile을 즉시 채움
                _userProfile.value = UserProfile(
                    uid = entity.uid,
                    name = entity.name,
                    imageUrl = entity.imageUrl,
                    friendCode = entity.friendCode
                )

                // ✅ 계정 바뀌었으면 동기화 1회 다시 수행
                viewModelScope.launch(Dispatchers.IO) {
                    hasSyncedOnce = false
                    syncFromFirestoreInternal(uid, force = true)
                }

            } catch (e: Exception) {
                Log.e("GoogleLogin", "Firebase sign in failed: $e")
            }
        }
    }


    // ------------------- 캘린더 관련 -------------------
    fun setCalendarFilter(packageName: String?) { _selectedFilter.value = packageName }
    fun setSelectedMonth(year: Int, month: Int) { _selectedMonth.value = month }

    private fun mergeTodayRealtime(
        dailies: List<DailyUsage>,
        todayUsageMap: Map<String, Int>
    ): List<DailyUsage> {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())

        val others = dailies.filter { it.date != todayStr }
        val today = DailyUsage(todayStr, todayUsageMap)

        return others + today
    }

    val calendarGoalTimeFlow: StateFlow<Int> = combine(
        _overallGoalMinutes, appUsageListFlow, trackedPackagesFlow
    ) { overallGoal, apps, tracked ->
        if (overallGoal != null) overallGoal
        else apps.filter { tracked.isEmpty() || it.packageName in tracked }.sumOf { it.goalTime }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val calendarDecoratorDataFlow: StateFlow<List<CalendarDecoratorData>> = combine(
        dailyUsageListFlow, goalHistoryListFlow, _selectedFilter
    ) { dailies, goalsHistory, filterPkg ->
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)

        // dateStr -> goalMap
        val goalByDate: Map<String, Map<String, Int>> =
            goalsHistory.associate { it.date to it.toGoalMap() }

        val decorators = mutableListOf<CalendarDecoratorData>()

        val todayUsageMap =
            appUsageListFlow.value.associate { it.packageName to it.currentUsage }

        val mergedDailies = mergeTodayRealtime(dailies, todayUsageMap)

        for (daily in mergedDailies) {
            val date = sdf.parse(daily.date) ?: continue
            val cal = Calendar.getInstance().apply { time = date }
            val calDay = CalendarDay.from(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )

            val dayGoals = goalByDate[daily.date].orEmpty()
            if (dayGoals.isEmpty()) continue // 그 날 목표 기록 없으면 색 표시 안 함

            val (usage, goal) = if (filterPkg == null) {
                // ✅ “전체”: 그 날짜의 목표에 포함된 앱들만 합산 (과거 추적앱 기준)
                val goalSum = dayGoals.values.sum()
                val usageSum = daily.appUsages
                    .filterKeys { it in dayGoals.keys }
                    .values.sum()
                usageSum to goalSum
            } else {
                // ✅ “특정 앱”: 그 날짜 목표를 기준으로
                val g = dayGoals[filterPkg] ?: 0
                val u = daily.appUsages[filterPkg] ?: 0
                u to g
            }

            if (goal <= 0) continue

            val status = when {
                usage > goal -> DayStatus.FAIL
                usage > goal * 0.7 -> DayStatus.WARNING
                else -> DayStatus.SUCCESS
            }
            decorators.add(CalendarDecoratorData(calDay, status))
        }

        decorators
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


    val calendarStatsTextFlow: StateFlow<String> = combine(
        calendarDecoratorDataFlow, selectedMonthFlow, _selectedFilter
    ) { decorators, month, _ ->
        val successDays = decorators.count { it.date.month == month && (it.status == DayStatus.SUCCESS || it.status == DayStatus.WARNING) }
        "${month}월 목표 성공일: 총 ${successDays}일!"
    }.stateIn(viewModelScope, SharingStarted.Lazily, "")

    private fun calculateOverallStreak(
        dailies: List<DailyUsage>,
        goalsHistory: List<GoalHistoryEntity>,
        filterPkg: String?
    ): Int {
        val goalByDate = goalsHistory.associate { it.date to it.toGoalMap() }
        val sortedDays = dailies.sortedByDescending { it.date }

        var wasSuccess: Boolean? = null
        var streakCount = 0

        for (day in sortedDays) {
            val goals = goalByDate[day.date].orEmpty()
            if (goals.isEmpty()) continue // 목표 없는 날은 스킵(또는 break로 바꿔도 됨)

            val (usage, goal) = if (filterPkg == null) {
                val g = goals.values.sum()
                val u = day.appUsages.filterKeys { it in goals.keys }.values.sum()
                u to g
            } else {
                val g = goals[filterPkg] ?: 0
                val u = day.appUsages[filterPkg] ?: 0
                u to g
            }

            if (goal <= 0) continue
            val success = usage <= goal

            if (wasSuccess == null) wasSuccess = success
            if (success == wasSuccess) streakCount++ else break
        }

        return if (wasSuccess == true) streakCount else -streakCount
    }
    val streakTextFlow: StateFlow<String> = combine(
        appUsageListFlow, dailyUsageListFlow, goalHistoryListFlow, _selectedFilter
    ) { apps, dailies, goalsHistory, filterPkg ->
        val todayUsageMap =
            appUsageListFlow.value.associate { it.packageName to it.currentUsage }

        val mergedDailies = mergeTodayRealtime(dailies, todayUsageMap)

        val streak = calculateOverallStreak(
            mergedDailies,
            goalsHistory,
            filterPkg
        )
        val appName = if (filterPkg == null) "전체" else apps.find { it.packageName == filterPkg }?.appLabel ?: "알 수 없음"
        val days = kotlin.math.abs(streak)
        val emoji = if (streak >= 0) "🔥" else "💀"
        "$appName: $emoji$days"
    }.stateIn(viewModelScope, SharingStarted.Lazily, "")

    private fun calculatePkgStreak(
        dailies: List<DailyUsage>,
        goalsHistory: List<GoalHistoryEntity>,
        pkg: String
    ): Int {
        val goalByDate = goalsHistory.associate { it.date to it.toGoalMap() }
        val sortedDays = dailies.sortedByDescending { it.date }

        var wasSuccess: Boolean? = null
        var streakCount = 0

        for (day in sortedDays) {
            val goals = goalByDate[day.date].orEmpty()
            val goal = goals[pkg] ?: continue          // 그날 목표에 이 앱이 없으면 스킵(그날은 추적앱 아님)
            if (goal <= 0) continue

            val usage = day.appUsages[pkg] ?: 0
            val success = usage <= goal

            if (wasSuccess == null) wasSuccess = success
            if (success == wasSuccess) streakCount++ else break
        }

        return if (wasSuccess == true) streakCount else -streakCount
    }

    val chartGoalDataFlow: StateFlow<List<Entry>> = combine(
        goalHistoryListFlow, _selectedFilter, selectedMonthFlow
    ) { goalsHistory, filterPkg, month ->

        goalsHistory
            .filter { it.date.substring(5, 7).toInt() == month }
            .mapNotNull { goalEntity ->
                val dayGoals = goalEntity.toGoalMap()
                if (dayGoals.isEmpty()) return@mapNotNull null

                val dayOfMonth = goalEntity.date.substring(8, 10).toFloat()

                val goal = if (filterPkg == null) {
                    // ✅ 전체: 그 날짜에 설정된 목표 합
                    dayGoals.values.sum()
                } else {
                    // ✅ 특정 앱: 그 날짜에 설정된 해당 앱 목표
                    dayGoals[filterPkg] ?: return@mapNotNull null
                }

                Entry(dayOfMonth, goal.toFloat())
            }
    }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val chartDataFlow: StateFlow<List<Entry>> = combine(
        dailyUsageListFlow, goalHistoryListFlow, _selectedFilter, selectedMonthFlow
    ) { dailies, goalsHistory, filterPkg, month ->
        val goalByDate: Map<String, Map<String, Int>> =
            goalsHistory.associate { it.date to it.toGoalMap() }

        dailies
            .filter { it.date.substring(5, 7).toInt() == month }
            .mapNotNull { daily ->
                val dayGoals = goalByDate[daily.date].orEmpty()
                if (dayGoals.isEmpty()) return@mapNotNull null  // 목표 없는 날은 그래프에서 제외(원하면 0으로 넣어도 됨)

                val dayOfMonth = daily.date.substring(8, 10).toFloat()

                val usage = if (filterPkg == null) {
                    // ✅ “전체”: 그 날짜 목표에 포함된 앱만 합산(과거 추적앱 기준)
                    daily.appUsages
                        .filterKeys { it in dayGoals.keys }
                        .values.sum()
                } else {
                    // ✅ “특정 앱”: 그 날짜 목표에 포함된 앱이면 사용량, 아니면 0
                    if (filterPkg in dayGoals.keys) (daily.appUsages[filterPkg] ?: 0) else 0
                }

                Entry(dayOfMonth, usage.toFloat())
            }

    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ========================== 날짜 선택시 앱 사용기록 불러오기 =============================
    fun loadDailyDetail(selectedDate: CalendarDay, context: Context) = viewModelScope.launch {
        val uid = getCurrentUserUid() ?: return@launch
        val dateStr = "%04d-%02d-%02d".format(selectedDate.year, selectedDate.month, selectedDate.day)

        val pm = context.packageManager

        try {
            val db = DatabaseProvider.getDatabase(getApplication())
            val goalDao = db.goalHistoryDao()

            // 1) ✅ Room(SSOT)에서 먼저 조회
            val localUsages: Map<String, Int> = repository.getDailyAppUsage(uid, dateStr)

            if (localUsages.isNotEmpty()) {
                // ✅ 1) Room goals(과거 목표) 먼저
                val localGoalEntity = goalDao.getGoalsOnce(uid, dateStr)
                val localGoals: Map<String, Int> = localGoalEntity?.toGoalMap() ?: emptyMap()

            // ✅ 2) goals가 없으면 Firestore에서 가져와서 Room에 1일치 저장
                val (goals, usagesFromFs) = if (localGoals.isNotEmpty()) {
                    localGoals to emptyMap()
                } else {
                    val (fsGoals, fsUsages) = syncRepository.fetchGoalAndUsageForDate(uid, dateStr)
                    // goals 1일치 Room 백필
                    runCatching {
                        goalDao.insertOrUpdate(GoalHistoryEntity.fromGoalMap(uid, dateStr, fsGoals))
                    }
                    fsGoals to fsUsages
                }

            // ✅ 3) 표시할 앱 목록은 "그 날짜 goals"의 키
                val pkgs = goals.keys

                val list = pkgs.map { pkg ->
                    val goalTime = goals[pkg] ?: 0
                    // ✅ 사용량은 Room 우선, 없으면 Firestore usage fallback
                    val usage = localUsages[pkg] ?: (usagesFromFs[pkg] ?: 0)

                    val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { null }
                    val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
                    val icon = appInfo?.let { pm.getApplicationIcon(it) }

                    AppUsage(
                        packageName = pkg,
                        appLabel = label,
                        icon = icon,
                        currentUsage = usage,
                        goalTime = goalTime,
                        streak = 0
                    ) to goalTime
                }

                _dailyDetailFlow.value = list
                return@launch
            }

            // 2) ✅ Room에 없으면 Firestore fallback
            val localGoalEntity = goalDao.getGoalsOnce(uid, dateStr)
            val localGoals: Map<String, Int> = localGoalEntity?.toGoalMap() ?: emptyMap()

            val (goals, usages) = if (localGoals.isNotEmpty()) {
                localGoals to emptyMap()
            } else {
                val (fsGoals, fsUsages) = syncRepository.fetchGoalAndUsageForDate(uid, dateStr)
                runCatching {
                    goalDao.insertOrUpdate(GoalHistoryEntity.fromGoalMap(uid, dateStr, fsGoals))
                }
                fsGoals to fsUsages
            }

            val list = goals.map { (pkg, goalTime) ->
                val usage = usages[pkg] ?: 0
                val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { null }
                val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
                val icon = appInfo?.let { pm.getApplicationIcon(it) }

                AppUsage(
                    packageName = pkg,
                    appLabel = label,
                    icon = icon,
                    currentUsage = usage,
                    goalTime = goalTime,
                    streak = 0
                ) to goalTime
            }

            _dailyDetailFlow.value = list

            // 3) (선택) ✅ Firestore로 가져온 "사용량"은 Room에 1일치 백필해두기(다음 클릭부터 빨라짐)
            // - goals는 별도 테이블이 없어서 여기서는 usage만 백필
            runCatching {
                val db = DatabaseProvider.getDatabase(getApplication())
                val json = com.google.gson.Gson().toJson(usages)
                db.dailyUsageDao().insertOrUpdate(com.example.pixeldiet.data.DailyUsageEntity(uid, dateStr, json))
            }
        } catch (e: Exception) {
            Log.e("CalendarDetail", "Failed to load daily detail: $e")
            _dailyDetailFlow.value = emptyList()
        }
    }


    // ------------------- 데이터 로딩(내부) -------------------
    private suspend fun refreshDataInternal(uid: String) {
        // 1) tracked_apps(Room) 로드
        val trackedList = UsageRepository.getAllTrackedOnce()
        val trackedSet = trackedList.map { it.packageName }.toSet()
        val goalMap = trackedList.associate { it.packageName to it.goalTime }

        _trackedApps.value = trackedList
        _trackedPackages.value = trackedSet
        _overallGoalMinutes.value = trackedList.sumOf { it.goalTime }

        // 2) ✅ SSOT: 오늘 사용량 계산/Room저장/Firestore업로드는 Repository가 전담
        repository.loadRealData(context, uid)

        // 3) Repository가 만든 "전체 앱 usage 리스트" 중에서 tracked 앱만 필터링
        val baseAppsAll = repository.appUsageListFlow.value
        val trackedAppsNow = baseAppsAll
            .filter { it.packageName in trackedSet }            // ✅ 추적앱만
            .map { app ->                                      // ✅ goalTime도 tracked 기준으로 보정
                app.copy(goalTime = goalMap[app.packageName] ?: app.goalTime)
            }

        // 4) ✅ 오늘 포함(실시간) merged dailies 만들기: "tracked 앱들만"으로 todayUsageMap 구성
        val todayUsageMap = trackedAppsNow.associate { it.packageName to it.currentUsage }
        val mergedDailies = mergeTodayRealtime(repository.dailyUsageListFlow.value, todayUsageMap)

        // 5) ✅ goalHistory(Room) 기반 앱별 streak 계산 (달력과 동일 기준)
        val goalsHistory = goalHistoryListFlow.value

        val withStreak = trackedAppsNow.map { app ->
            val streak = calculatePkgStreak(
                dailies = mergedDailies,
                goalsHistory = goalsHistory,
                pkg = app.packageName
            )
            app.copy(streak = streak)
        }

        // 6) 메인 UI는 이 리스트만 보면 됨
        _appUsageList.value = withStreak
    }


    val appStreakMapFlow: StateFlow<Map<String, Int>> =
        combine(
            dailyUsageListFlow,
            goalHistoryListFlow,
            repository.appUsageListFlow
        ) { dailies, goalsHistory, apps ->

            val todayUsageMap =
                apps.associate { it.packageName to it.currentUsage }

            val mergedDailies = mergeTodayRealtime(dailies, todayUsageMap)

            apps.associate { app ->
                app.packageName to calculatePkgStreak(
                    dailies = mergedDailies,
                    goalsHistory = goalsHistory,
                    pkg = app.packageName
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // ------------------- 데이터 로딩(외부 호출) -------------------
    fun refreshData() = viewModelScope.launch(Dispatchers.IO) {
        val uid = getCurrentUserUid() ?: return@launch
        refreshDataInternal(uid)
    }

    fun saveNotificationSettings(settings: NotificationSettings) = viewModelScope.launch {
        repository.updateNotificationSettings(settings)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }

    // ------------------- Firestore -> Room 동기화 -------------------
    private suspend fun syncFromFirestoreInternal(uid: String, force: Boolean) {
        if (!force && hasSyncedOnce) {
            // 이미 1회 동기화/초기 로딩 끝났으면, 이후에는 데이터만 새로 고치기
            refreshDataInternal(uid)
            _isDataReady.value = true
            return
        }

        _isDataReady.value = false
        try {
            syncRepository.syncFromFirestore(uid)
            hasSyncedOnce = true

            restorePrefs.edit()
                .putString(KEY_LAST_RESTORE_UID, uid)
                .putLong(KEY_LAST_RESTORE_AT, System.currentTimeMillis())
                .apply()

            // 동기화 끝났으니 화면 데이터 재계산
            refreshDataInternal(uid)
        } catch (e: Exception) {
            Log.e("SharedViewModel", "syncFromFirestore failed: $e")
        } finally {
            _isDataReady.value = true
        }
    }

    /** 수동 동기화(설정화면 버튼 등에서 호출) */
    fun syncFromFirestore() = viewModelScope.launch(Dispatchers.IO) {
        val uid = getCurrentUserUid() ?: return@launch
        syncFromFirestoreInternal(uid, force = true)
    }

    // ------------------- 트래킹 앱 업데이트 -------------------
    fun saveTrackedAppsWithGoals(selectedAppsWithGoals: Map<String, Int>) =
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentTracked = UsageRepository.getAllTrackedOnce()
                val toDelete = currentTracked.filter { it.packageName !in selectedAppsWithGoals.keys }
                toDelete.forEach { UsageRepository.deleteTrackedApp(it.packageName) }

                val toSave = selectedAppsWithGoals.map { (pkg, goal) ->
                    TrackedAppEntity(packageName = pkg, goalTime = goal)
                }

                UsageRepository.updateTrackedApps(toSave)
                _trackedPackages.value = selectedAppsWithGoals.keys

                repository.updateGoalTimes(selectedAppsWithGoals)

                val latestTracked = UsageRepository.getAllTrackedOnce()
                _trackedApps.value = latestTracked.map { tracked ->
                    val goal = selectedAppsWithGoals[tracked.packageName] ?: tracked.goalTime
                    tracked.copy(goalTime = goal)
                }

                val totalGoal = _trackedApps.value.sumOf { it.goalTime }
                _overallGoalMinutes.value = totalGoal

                _trackedApps.value.forEach {
                    Log.d("DBCheck", "Saved Package: ${it.packageName}, GoalTime: ${it.goalTime}")
                }


                // 저장 후 UI도 갱신
                val uid = getCurrentUserUid()
                if (uid != null) refreshDataInternal(uid)
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Failed to save tracked apps with goals: $e")
            }
        }

    // ------------------- 트래킹 앱 삭제 -------------------
    fun deleteTrackedApp(packageName: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            // 1) Room(tracked_apps)에서 삭제
            val db = DatabaseProvider.getDatabase(getApplication())
            db.trackedAppDao().deleteByPackage(packageName)

            // 2) (선택) 목표/설정도 같이 정리하고 싶으면 여기서 처리 가능
            // - 예: overallGoalMinutes 재계산은 refreshDataInternal이 해줌

            // 3) UI 갱신
            val uid = getCurrentUserUid() ?: return@launch
            refreshDataInternal(uid)
        } catch (e: Exception) {
            Log.e("SharedViewModel", "Failed to delete tracked app($packageName): $e")
        }
    }

    fun deleteTrackedApps(packages: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        val db = DatabaseProvider.getDatabase(getApplication())
        packages.forEach { db.trackedAppDao().deleteByPackage(it) }
        val uid = getCurrentUserUid() ?: return@launch
        refreshDataInternal(uid)
    }

    // =================== 백그라운드 이동시 일일 사용기록 백업 ==================
    fun uploadDailyUsageToFirebase() = viewModelScope.launch(Dispatchers.IO) {
        val uid = getCurrentUserUid() ?: return@launch
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())

        // ✅ Room(SSOT)에서 오늘 사용량을 읽음
        val appUsagesMap = repository.getDailyAppUsage(uid, today)

        // ✅ 그 값을 Firestore에 업로드
        syncRepository.uploadDailyUsage(uid, appUsagesMap, today)
    }

    fun uploadDailyGoalToFirebase(newGoals: Map<String, Int>) = viewModelScope.launch(Dispatchers.IO) {
        val uid = getCurrentUserUid() ?: return@launch
        syncRepository.uploadDailyGoal(uid, newGoals)
    }

    fun initUserProfile() = viewModelScope.launch(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@launch
        try {
            val entity = syncRepository.initUserProfileIfNeeded(
                uid = uid,
                displayName = auth.currentUser?.displayName
            )

            _userProfile.value = UserProfile(
                uid = entity.uid,
                name = entity.name,
                imageUrl = entity.imageUrl,
                friendCode = entity.friendCode
            )
        } catch (e: Exception) {
            Log.e("UserProfile", "Failed to init user profile: $e")
        }
    }

    fun insertTestDataForUid(uid: String) = CoroutineScope(Dispatchers.IO).launch {
        syncRepository.insertTestDataForUid(uid)
    }
}
