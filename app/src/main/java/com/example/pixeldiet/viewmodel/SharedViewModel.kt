package com.example.pixeldiet.viewmodel

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixeldiet.data.DatabaseProvider
import com.example.pixeldiet.data.TrackedAppEntity
import com.example.pixeldiet.data.UserProfileEntity
import com.example.pixeldiet.model.*
import com.example.pixeldiet.repository.UsageRepository
import com.example.pixeldiet.repository.SyncRepository
import com.github.mikephil.charting.data.Entry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.prolificinteractive.materialcalendarview.CalendarDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class UserProfile(
    val uid: String,
    val name: String,
    val imageUrl: String,
    val friendCode: String
)
class SharedViewModel(application: Application) : AndroidViewModel(application) {
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile

    private val trackedPrefs by lazy { application.getSharedPreferences("tracked_apps_prefs", Context.MODE_PRIVATE) }
    private val goalPrefs by lazy { application.getSharedPreferences("goal_prefs", Context.MODE_PRIVATE) }

    private val _trackedPackages = MutableStateFlow<Set<String>>(emptySet())
    val trackedPackagesFlow: StateFlow<Set<String>> = _trackedPackages

    private val _trackedApps = MutableStateFlow<List<TrackedAppEntity>>(emptyList())
    val trackedAppsFlow: StateFlow<List<TrackedAppEntity>> = _trackedApps
    private val _isDataReady = MutableStateFlow(true)
    val isDataReady: StateFlow<Boolean> = _isDataReady

    private val _dailyDetailFlow = MutableStateFlow<List<Pair<AppUsage, Int>>>(emptyList())
    val dailyDetailFlow: StateFlow<List<Pair<AppUsage, Int>>> get() = _dailyDetailFlow

    private val _overallGoalMinutes = MutableStateFlow<Int?>(null)
    val overallGoalFlow: StateFlow<Int?> = _overallGoalMinutes


    // ------------------- Firebase Auth -------------------
    private val auth = FirebaseAuth.getInstance()
    private val _userName = MutableStateFlow(getUserName())
    val userName: StateFlow<String> = _userName
    val isGoogleUser = MutableStateFlow(isGoogleLogin())

    private val authListener = FirebaseAuth.AuthStateListener {
        _userName.value = getUserName()
        isGoogleUser.value = isGoogleLogin()
    }

    // ------------------- 앱 사용 데이터 -------------------
    private val repository = UsageRepository
    private val syncRepository = SyncRepository(application.applicationContext)
    private val _appUsageList = MutableStateFlow<List<AppUsage>>(emptyList())
    val appUsageListFlow: StateFlow<List<AppUsage>> get() = _appUsageList
    val context = getApplication<Application>().applicationContext

    val dailyUsageListFlow: StateFlow<List<DailyUsage>> = repository.dailyUsageListFlow
    val notificationSettingsFlow: StateFlow<NotificationSettings?> = repository.notificationSettingsFlow
    private val _dailyUsageList = MutableStateFlow<List<DailyUsage>>(emptyList())



    val totalUsageFlow: StateFlow<Pair<Int, Int>> =
        dailyUsageListFlow.combine(trackedPackagesFlow) { dailies, tracked ->
            val totalUsed = dailies.flatMap { it.appUsages.entries }
                .filter { tracked.isEmpty() || it.key in tracked }
                .sumOf { it.value }

            totalUsed
        }.combine(_overallGoalMinutes) { used, goal ->
            used to (goal ?: 0)
        }.stateIn(viewModelScope, SharingStarted.Lazily, 0 to (_overallGoalMinutes.value ?: 0))

    // ------------------- 선택된 필터 -------------------
    private val _selectedFilter = MutableStateFlow<String?>(null)
    val selectedFilterTextFlow: StateFlow<String> = combine(_selectedFilter, appUsageListFlow) { pkg, apps ->
        if (pkg == null) "전체" else apps.find { it.packageName == pkg }?.appLabel ?: "전체"
    }.stateIn(viewModelScope, SharingStarted.Lazily, "전체")

    // ------------------- 선택된 달 -------------------
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    val selectedMonthFlow: StateFlow<Int> = _selectedMonth

    // ------------------- 초기화 -------------------
    init {
        auth.addAuthStateListener(authListener)
        viewModelScope.launch(Dispatchers.IO) { // 코루틴 시작
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch

            // Firestore/Room 초기값 불러오기
            val backupToday = syncRepository.loadBackupToday(uid)

            // 실시간 UsageStats
            val realtimeUsage = calculateRealtimeUsage()

            // 모든 패키지 통합
            val trackedList = UsageRepository.getAllTrackedOnce()
            _trackedApps.value = trackedList
            _trackedPackages.value = trackedList.map { it.packageName }.toSet()
            val allPackages = (trackedList.map { it.packageName } + backupToday.keys + realtimeUsage.keys).distinct()

            // AppUsage 리스트 생성
            val mergedList = allPackages.map { pkg ->
                val label = try {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                } catch (e: Exception) { pkg }

                val icon = try {
                    context.packageManager.getApplicationIcon(pkg)
                } catch (e: Exception) { null }

                val initialUsage = backupToday[pkg] ?: 0
                val realtime = realtimeUsage[pkg] ?: 0
                val goal = trackedList.find { it.packageName == pkg }?.goalTime ?: 0

                AppUsage(pkg, label, icon, initialUsage + realtime, goal, streak = 0)
            }.sortedBy { it.appLabel.lowercase() }

            // Flow에 emit
            _appUsageList.value = mergedList

            // 총 목표시간 계산
            _overallGoalMinutes.value = trackedList.sumOf { it.goalTime }
        }
    }

    // ------------------- SharedPreferences 로드 -------------------

    fun markDataReady() {
        _isDataReady.value = true
    }
    private fun loadOverallGoal() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1️⃣ DB에서 앱별 목표시간 불러오기
                val appsFromDb = UsageRepository.getAllTrackedOnce()
                _trackedApps.value = appsFromDb
                _trackedPackages.value = appsFromDb.map { it.packageName }.toSet()

                // 2️⃣ 총 목표시간 계산
                val totalGoal = appsFromDb.sumOf { it.goalTime }
                _overallGoalMinutes.value = totalGoal

                // 3️⃣ 필요하다면 SharedPreferences에도 저장 (선택)
                goalPrefs.edit().putInt("overall_goal_minutes", totalGoal).apply()
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Failed to load overall goal: $e")
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
    private fun getCurrentUserUid(): String? = auth.currentUser?.uid

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = DatabaseProvider.getDatabase(getApplication())
            // 1️⃣ Room DB 초기화
            db.userProfileDao().clearAll()
            db.trackedAppDao().clearAll()
            db.dailyUsageDao().clearAll()
            db.groupDao().clearAll()
            db.friendDao().clearAll()

            // 2️⃣ SharedPreferences 초기화 (선택)
            trackedPrefs.edit().clear().apply()
            goalPrefs.edit().clear().apply()

            // 3️⃣ Firebase 로그아웃
            auth.signOut()

            // 4️⃣ ViewModel StateFlow 초기화
            _userProfile.value = null
            _trackedApps.value = emptyList()
            _trackedPackages.value = emptySet()
            _overallGoalMinutes.value = null
        }
    }
    fun loadDailyAppUsage(uid: String, date: String) {
        viewModelScope.launch {
            // UID와 날짜 확인
            Log.d("AppUsageList", "UID: $uid, Date: $date")

            val usageMap = repository.getDailyAppUsage(uid, date)

            // DB에서 가져온 Map 확인
            Log.d("AppUsageList", "UsageMap from DB: $usageMap")

            val usageList = usageMap.map { (pkg, mins) ->
                AppUsage(pkg, appLabel = pkg, icon = null, currentUsage = mins, goalTime = 0, streak = 0)
            }
            _appUsageList.value = usageList

            Log.d("AppUsageList", "Loaded: $usageList")
        }
    }


    fun onGoogleLoginSuccess(idToken: String) {
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()

                // 로그인 후 이전 StateFlow 초기화
                _userProfile.value = null
                _trackedApps.value = emptyList()
                _trackedPackages.value = emptySet()
                _overallGoalMinutes.value = null

                // 새 계정 정보 불러오기
                initUserProfile()
            } catch (e: Exception) {
                Log.e("GoogleLogin", "Firebase sign in failed: $e")
            }
        }
    }

    // ------------------- 캘린더 관련 -------------------
    fun setCalendarFilter(packageName: String?) { _selectedFilter.value = packageName }
    fun setSelectedMonth(year: Int, month: Int) { _selectedMonth.value = month }

    val calendarGoalTimeFlow: StateFlow<Int> = combine(
        _overallGoalMinutes, appUsageListFlow, trackedPackagesFlow
    ) { overallGoal, apps, tracked ->
        if (overallGoal != null) overallGoal
        else apps.filter { tracked.isEmpty() || it.packageName in tracked }.sumOf { it.goalTime }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val calendarDecoratorDataFlow: StateFlow<List<CalendarDecoratorData>> = combine(
        dailyUsageListFlow, appUsageListFlow, _selectedFilter, trackedPackagesFlow, _overallGoalMinutes
    ) { dailies, apps, filterPkg, tracked, overallGoal ->
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)
        val decorators = mutableListOf<CalendarDecoratorData>()
        for (daily in dailies) {
            val date = sdf.parse(daily.date) ?: continue
            val cal = Calendar.getInstance().apply { time = date }
            val calDay = CalendarDay.from(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

            val (usage, goal) = if (filterPkg == null) {
                val dayUsage = daily.appUsages.filterKeys { pkg -> tracked.isEmpty() || pkg in tracked }.values.sum()
                val autoGoal = apps.filter { tracked.isEmpty() || it.packageName in tracked }.sumOf { it.goalTime }
                dayUsage to (overallGoal ?: autoGoal)
            } else {
                val dayUsage = daily.appUsages[filterPkg] ?: 0
                val appGoal = apps.find { it.packageName == filterPkg }?.goalTime ?: 0
                dayUsage to appGoal
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

    private fun calculateOverallStreak(dailies: List<DailyUsage>, apps: List<AppUsage>, tracked: Set<String>, goal: Int): Int {
        if (goal <= 0) return 0
        val sortedDays = dailies.sortedByDescending { it.date }
        var wasSuccess: Boolean? = null
        var streakCount = 0
        for (day in sortedDays) {
            val dayUsage = day.appUsages.filterKeys { pkg -> tracked.isEmpty() || pkg in tracked }.values.sum()
            val success = dayUsage <= goal
            if (wasSuccess == null) wasSuccess = success
            if (success == wasSuccess) streakCount++ else break
        }
        return if (wasSuccess == true) streakCount else -streakCount
    }

    val streakTextFlow: StateFlow<String> = combine(
        appUsageListFlow, dailyUsageListFlow, _selectedFilter, trackedPackagesFlow, _overallGoalMinutes
    ) { apps, dailies, filterPkg, tracked, overallGoal ->
        val streak = if (filterPkg == null) calculateOverallStreak(dailies, apps, tracked, overallGoal ?: 0)
        else apps.find { it.packageName == filterPkg }?.streak ?: 0
        val appName = if (filterPkg == null) "전체" else apps.find { it.packageName == filterPkg }?.appLabel ?: "알 수 없음"
        val days = kotlin.math.abs(streak)
        val emoji = if (streak >= 0) "🔥" else "💀"
        "$appName: $emoji$days"
    }.stateIn(viewModelScope, SharingStarted.Lazily, "")

    val chartDataFlow: StateFlow<List<Entry>> = combine(
        dailyUsageListFlow, _selectedFilter, trackedPackagesFlow, selectedMonthFlow
    ) { dailies, filterPkg, tracked, month ->
        dailies.filter { it.date.substring(5, 7).toInt() == month }.map { daily ->
            val dayOfMonth = daily.date.substring(8, 10).toFloat()
            val usage = if (filterPkg == null) daily.appUsages.filterKeys { pkg -> tracked.isEmpty() || pkg in tracked }.values.sum()
            else daily.appUsages[filterPkg] ?: 0
            Entry(dayOfMonth, usage.toFloat())
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    private fun calculateRealtimeUsage(): Map<String, Int> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 24 * 60 * 60 * 1000  // 오늘 24시간
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?: emptyList()
        return stats.associate { it.packageName to (it.totalTimeInForeground / 1000 / 60).toInt() }
    }

    //==========================날짜 선택시 앱 사용기록 불러오는 메소드=============================
//==========================날짜 선택시 앱 사용기록 불러오는 메소드=============================
    fun loadDailyDetail(selectedDate: CalendarDay, context: Context) = viewModelScope.launch {
        val uid = getCurrentUserUid() ?: return@launch
        val dateStr = "%04d-%02d-%02d".format(selectedDate.year, selectedDate.month, selectedDate.day)

        try {
            val (goals, usages) = syncRepository.fetchGoalAndUsageForDate(uid, dateStr)

            val pm = context.packageManager
            val list = goals.map { (pkg, goalTime) ->
                val usage = usages[pkg] ?: 0
                val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { null }
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
        } catch (e: Exception) {
            Log.e("CalendarDetail", "Failed to load daily detail: $e")
            _dailyDetailFlow.value = emptyList()
        }
    }

    // ------------------- 데이터 로딩 -------------------
    fun refreshData() = viewModelScope.launch(Dispatchers.IO) {
        val uid = getCurrentUserUid() ?: return@launch
        val backupToday = syncRepository.loadBackupToday(uid)
        val realtimeUsage = calculateRealtimeUsage()
        val trackedList = UsageRepository.getAllTrackedOnce()

        _trackedApps.value = trackedList
        _trackedPackages.value = trackedList.map { it.packageName }.toSet()

        val allPackages = (trackedList.map { it.packageName } + realtimeUsage.keys).distinct()

        val mergedList = allPackages.map { pkg ->
            val label = try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
            val icon = try { context.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }
            val initialUsage = backupToday[pkg] ?: 0
            val usage = realtimeUsage[pkg] ?: initialUsage   // ✅ 수정
            val goal = trackedList.find { it.packageName == pkg }?.goalTime ?: 0
            AppUsage(pkg, label, icon, usage, goal, streak = 0)
        }.sortedBy { it.appLabel.lowercase() }

        _appUsageList.value = mergedList
        _overallGoalMinutes.value = trackedList.sumOf { it.goalTime }
    }



    fun saveNotificationSettings(settings: NotificationSettings) = viewModelScope.launch {
        repository.updateNotificationSettings(settings)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }

    // ------------------- 트래킹 앱 업데이트 -------------------UsageRepository.getAllTrackedOnce()

    fun saveTrackedAppsWithGoals(selectedAppsWithGoals: Map<String, Int>) = viewModelScope.launch(Dispatchers.IO) {
        try {
            // 1️⃣ 기존 DB에 저장된 모든 앱 가져오기
            val currentTracked = UsageRepository.getAllTrackedOnce()

            // 2️⃣ 삭제할 앱 = 기존 DB에 있는데 선택 목록에 없는 앱
            val toDelete = currentTracked.filter { it.packageName !in selectedAppsWithGoals.keys }
            toDelete.forEach { UsageRepository.deleteTrackedApp(it.packageName) }

            // 3️⃣ 추가/업데이트할 앱 = 선택된 앱 + 목표시간
            val toSave = selectedAppsWithGoals.map { (pkg, goal) ->
                TrackedAppEntity(packageName = pkg, goalTime = goal)
            }

            // 4️⃣ 저장 (업데이트/삽입)
            UsageRepository.updateTrackedApps(toSave)
            _trackedPackages.value = selectedAppsWithGoals.keys

            // 5️⃣ Repository의 currentGoals 갱신
            repository.updateGoalTimes(selectedAppsWithGoals)

            // 6️⃣ 최신 데이터 불러오기 & Flow 직접 emit
            val latestTracked = UsageRepository.getAllTrackedOnce()
            _trackedApps.value = latestTracked.map { tracked ->
                val goal = selectedAppsWithGoals[tracked.packageName] ?: tracked.goalTime
                tracked.copy(goalTime = goal)
            }

            // 🔹 전체 목표시간 항상 앱별 합계로 갱신
            val totalGoal = _trackedApps.value.sumOf { it.goalTime }
            _overallGoalMinutes.value = totalGoal

            // 7️⃣ 로그
            _trackedApps.value.forEach {
                Log.d("DBCheck", "Saved Package: ${it.packageName}, GoalTime: ${it.goalTime}")
            }

        } catch (e: Exception) {
            Log.e("SharedViewModel", "Failed to save tracked apps with goals: $e")
        }
    }

    //===================백그라운드 이동시 일일 사용기록 백업=================
//===================백그라운드 이동시 일일 사용기록 백업=================
    fun uploadDailyUsageToFirebase() = viewModelScope.launch(Dispatchers.IO) {
        val uid = getCurrentUserUid() ?: return@launch
        val appUsagesMap = appUsageListFlow.value.associate { it.packageName to it.currentUsage }
        syncRepository.uploadDailyUsage(uid, appUsagesMap)
    }


    // ------------------- 개별 앱 목표 시간 업데이트 -------------------
// ------------------- 개별 앱 목표 시간 업데이트 -------------------
    fun uploadDailyGoalToFirebase(newGoals: Map<String, Int>) = viewModelScope.launch(Dispatchers.IO) {
        val uid = getCurrentUserUid() ?: return@launch
        syncRepository.uploadDailyGoal(uid, newGoals)
    }
    // ------------------- 친구코드 생성-------------------


    //=====================프로필 초기 등록====================================
//=====================프로필 초기 등록====================================
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
    //==================임시 데이터 삽입 코드==================================
//==================임시 데이터 삽입 코드==================================
    fun insertTestDataForUid(uid: String) = CoroutineScope(Dispatchers.IO).launch {
        syncRepository.insertTestDataForUid(uid)
    }


}