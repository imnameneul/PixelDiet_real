package com.example.pixeldiet.friend.group

import android.annotation.SuppressLint
import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.example.pixeldiet.data.TrackedAppDao
import com.example.pixeldiet.data.TrackedAppEntity
import com.example.pixeldiet.data.UserProfileDao
import com.example.pixeldiet.data.UserProfileEntity
import com.example.pixeldiet.friend.FriendRecord
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.auth.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class MemberUsage(
    val uid: String,
    val name: String,
    var usageSeconds: Int,
    var isRunning: Boolean = false,
    var lastStartTime: Long? = null
)

class GroupViewModel(private val repository: GroupRepository, private val usageDao: TrackedAppDao, application: Application,) : ViewModel() {

    private val context = application.applicationContext
    private val _appList = MutableStateFlow<List<TrackedAppEntity>>(emptyList())
    val appList: StateFlow<List<TrackedAppEntity>> = _appList

    private val _friendList = MutableStateFlow<List<FriendRecord>>(emptyList())
    val friendList: StateFlow<List<FriendRecord>> = _friendList
    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog

    private val _goalMinutes = MutableStateFlow(0)
    val goalMinutes: StateFlow<Int> = _goalMinutes.asStateFlow()

    // GoalSettingDialog 표시 여부
    private val _showGoalDialog = MutableStateFlow(false)
    val showGoalDialog: StateFlow<Boolean> = _showGoalDialog.asStateFlow()

    //=============================================================
    private val _friends = MutableStateFlow<List<FriendRecord>>(emptyList())
    val friends: StateFlow<List<FriendRecord>> = _friends
    private val _selectedGroup = MutableStateFlow<GroupRecord?>(null)
    val selectedGroup: StateFlow<GroupRecord?> = _selectedGroup
    private val _groupList = MutableStateFlow<List<GroupRecord>>(emptyList())
    val groupList: StateFlow<List<GroupRecord>> = _groupList

    private val _groupMembers = MutableStateFlow<List<MemberUsage>>(emptyList())
    val groupMembers: StateFlow<List<MemberUsage>> = _groupMembers
    private val firestore = FirebaseFirestore.getInstance()
    private val _memberIds = MutableLiveData<List<String>>()
    val memberIds: LiveData<List<String>> = _memberIds
    private var membersListener: ListenerRegistration? = null

    private var monitoringStarted = false

    private var appUsageMonitor: AppUsageMonitor? = null

    private var observeStarted = false
    //========================================

    private val _selectedApp = MutableStateFlow<String?>(null)
    val selectedApp: StateFlow<String?> = _selectedApp
    private val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid
    private val _selecteddGroup = MutableStateFlow<GroupRecord?>(null)
    val selecteddGroup: StateFlow<GroupRecord?> = _selecteddGroup
    fun loadMyGroups() {
        //val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        repository.getMyGroups()
            .onEach { groups -> _groupList.value = groups }
            .launchIn(viewModelScope)
    }

    fun loadSelectedApp(groupId: String) {
        viewModelScope.launch {
            val group = repository.getGroup(groupId)
            _selectedApp.value = group?.appId
        }
    }



    // 멤버 추가 다이얼로그 상태
    private val _showAddMemberDialog = MutableStateFlow(false)
    val showAddMemberDialog: StateFlow<Boolean> = _showAddMemberDialog


    init {
        usageDao.getAllTrackedApps()
            .onEach { apps ->
                Log.d("GroupDebug", "Tracked apps: $apps")
                _appList.value = apps
            }
            .launchIn(viewModelScope)

        repository.getGroups()
            .onEach { groups ->
                _groupList.value = groups
            }
            .launchIn(viewModelScope)

        observeMyGroups() // 이건 그냥 호출하면 됨
    }
    fun openCreateGroupDialog() {
        _showCreateDialog.value = true
    }

    fun closeCreateGroupDialog() {
        _showCreateDialog.value = false
    }

    // =============================================================
    fun createGroup(name: String, appId: String) = viewModelScope.launch {
        viewModelScope.launch {
            repository.createGroup(name, appId) // suspend fun
            // 방장 멤버 정보가 Firestore에 저장된 후 호출
            _selectedGroup.value?.let { group ->
                _selectedApp.value = appId

            }
        }
    }

    // =============================================================
    // 그룹 나가기
    fun leaveGroup(group: GroupRecord) = viewModelScope.launch {
        repository.leaveGroup(group)
    }
    // =============================================================
    // 그룹 삭제 (방장만 가능)
    fun deleteGroup(group: GroupRecord) = viewModelScope.launch {
        repository.deleteGroup(group)
    }


    fun openGroup(group: GroupRecord) = viewModelScope.launch {
        _selectedGroup.value = group          // 그룹 정보 저장
        _showAddMemberDialog.value = false    // 다이얼로그 초기화
        loadGroupMembers(group.groupId)               // 멤버 + 사용 시간 불러오기
    }
    fun openGoalDialog() {
        _showGoalDialog.value = true
    }

    fun closeGoalDialog() {
        _showGoalDialog.value = false
    }
    fun closeAddMemberDialog() {
        _showAddMemberDialog.value = false
    }

    fun openAddMemberDialog() {
        _showAddMemberDialog.value = true
    }
    fun getGoalMinutes(groupId: String) {
        viewModelScope.launch {
            val minutes = repository.getGoalMinutes(groupId) // suspend fun
            _goalMinutes.value = minutes
        }
    }

    fun updateGoalMinutes(groupId: String, minutes: Int) {
        viewModelScope.launch {
            repository.updateGoalMinutes(groupId, minutes) // Room + Firestore 업데이트
            _goalMinutes.value = minutes // UI 즉시 반영
        }
    }


    fun loadGroupMembers(groupId: String) {
        Log.d("GroupVM", "loadGroupMembers START groupId=$groupId")

        membersListener?.remove()

        membersListener = firestore.collection("groups")
            .document(groupId)
            .collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("GroupVM", "members snapshot error", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    Log.e("GroupVM", "members snapshot NULL")
                    return@addSnapshotListener
                }

                Log.d("GroupVM", "members snapshot size=${snapshot.size()}")

                val members = snapshot.documents.mapNotNull { doc ->
                    Log.d(
                        "GroupVM",
                        "member doc ${doc.id} data=${doc.data}"
                    )

                    MemberUsage(
                        uid = doc.id,
                        name = doc.getString("name") ?: "",
                        usageSeconds = (doc.getLong("usageSeconds") ?: 0L).toInt(),
                        isRunning = doc.getBoolean("isRunning") ?: false,
                        lastStartTime = doc.getLong("lastStartTime")
                    )
                }

                _groupMembers.value = members
            }
    }
    private var observingGroupId: String? = null

    private fun observeMyGroups() {
        if (observeStarted) return
        observeStarted = true
        val uid = currentUserId ?: return

        firestore.collection("users")
            .document(uid)
            .collection("groups")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("GroupVM", "observeMyGroups error", error)
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    Log.d("GroupVM", "observeMyGroups empty")
                    return@addSnapshotListener
                }

                val groupId = snapshot.documents
                    .mapNotNull { it.getString("groupId") }
                    .firstOrNull()

                Log.d("GroupVM", "observeMyGroups found groupId=$groupId")

                if (groupId == null || groupId == observingGroupId) return@addSnapshotListener

                observingGroupId = groupId

                viewModelScope.launch {
                    val group = repository.getGroup(groupId)
                    if (group == null) {
                        Log.e("GroupVM", "❌ repository.getGroup returned null")
                        return@launch
                    }

                    _selectedGroup.value = group
                    _selectedApp.value = group.appId

                    loadGroupMembers(groupId)

                    if (group.appId != null) {
                        startAppMonitoring()
                    }
                }
            }
    }




    private var monitoringAppId: String? = null

    fun startAppMonitoring() {
        val group = _selectedGroup.value ?: return
        val appId = _selectedApp.value ?: return

        if (monitoringAppId == appId) return
        monitoringAppId = appId

        appUsageMonitor?.stop()

        appUsageMonitor = AppUsageMonitor(
            context = context,
            groupId = group.groupId,
            appId = appId,
            scope = viewModelScope   // ⭐⭐⭐ 핵심
        )
        appUsageMonitor?.start()
    }

    fun addSelectedMembers(groupId: String, memberIds: List<String>) {
        viewModelScope.launch {
            try {
                // 1️⃣ Room DB 업데이트 (옵션)
                repository.addMembersToGroup(groupId, memberIds)
                Log.d("GroupMembers", "Room: Added memberIds $memberIds to group $groupId")

                // 2️⃣ selectedGroup 최신화
                val current = selectedGroup.value ?: return@launch
                val updated = current.copy(
                    memberIds = (current.memberIds + memberIds).distinct()
                )
                _selectedGroup.value = updated
                Log.d("GroupMembers", "selectedGroup updated: ${updated.memberIds}")

                // 3️⃣ Firestore 최상위 그룹 멤버Ids 업데이트
                firestore.collection("groups")
                    .document(groupId)
                    .update("memberIds", FieldValue.arrayUnion(*memberIds.toTypedArray()))
                    .await()
                Log.d("GroupMembers", "Global group memberIds updated")

                // 4️⃣ Firestore members 서브컬렉션 & users/{uid}/groups 초기화
                memberIds.forEach { memberId ->
                    val memberRef = firestore.collection("groups")
                        .document(groupId)
                        .collection("members")
                        .document(memberId)

                    // Firestore users 컬렉션에서 프로필 이름 직접 가져오기
                    val name = try {
                        firestore.collection("users")
                            .document(memberId)
                            .collection("profile")
                            .document("main")
                            .get()
                            .await()
                            .getString("name") ?: ""  // profile/main 문서의 name 필드 가져오기
                    }catch (e: Exception) {
                        Log.e("GroupMembers", "Error fetching profile name for $memberId", e)
                        ""
                    }

                    val memberData = mapOf(
                        "name" to name,                  // 이름
                        "usageSeconds" to 0,                     // 초기 사용시간
                        "isRunning" to false,
                        "lastStartTime" to null
                    )

                    // members 서브컬렉션에 저장
                    memberRef.set(memberData).await()
                    Log.d("GroupMembers", "Member $memberId initialized in members subcollection")

                    // users/{uid}/groups/{groupId} 참조 추가
                    firestore.collection("users")
                        .document(memberId)
                        .collection("groups")
                        .document(groupId)
                        .set(mapOf("groupId" to groupId))
                        .await()
                    Log.d("GroupMembers", "User $memberId group reference added")
                }

                // 5️⃣ UI 업데이트는 snapshotListener로 실시간 반영 가능

            } catch (e: Exception) {
                Log.e("GroupMembers", "Error in addSelectedMembers", e)
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        // 아무 것도 cancel 하지 말아도 됨
        // viewModelScope가 자동 종료됨
    }
}

class AppUsageMonitor(
    private val context: Context,
    private val groupId: String,
    private val appId: String,
    private val scope: CoroutineScope
) {    private val monitorScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val firestore = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    private var lastRunning: Boolean? = null
    private var job: Job? = null
    fun start() {
        job = monitorScope.launch {
            while (isActive) {
                delay(1_000L)
                val running = isAppInForeground(appId)
                if (running != lastRunning) {
                    onRunningChanged(running)
                    lastRunning = running
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        monitorScope.cancel()
    }

    private suspend fun onRunningChanged(isRunning: Boolean) {
        val userId = uid ?: return
        val ref = firestore.collection("groups")
            .document(groupId)
            .collection("members")
            .document(userId)

        try {
            if (isRunning) {
                ref.update(
                    mapOf(
                        "isRunning" to true,
                        "lastStartTime" to System.currentTimeMillis()
                    )
                ).await()
            } else {
                firestore.runTransaction { tx ->
                    val snap = tx.get(ref)
                    val lastStart = snap.getLong("lastStartTime") ?: return@runTransaction
                    val prev = snap.getLong("usageSeconds") ?: 0L
                    val added = (System.currentTimeMillis() - lastStart) / 1000

                    tx.update(
                        ref,
                        mapOf(
                            "isRunning" to false,
                            "lastStartTime" to null,
                            "usageSeconds" to prev + added
                        )
                    )
                }.await()
            }
        } catch (e: Exception) {
            Log.e("AppUsageMonitor", "Firestore update failed", e)
            // ❗ 여기서 앱은 살아있음
        }
    }


    private fun isAppInForeground(packageName: String): Boolean {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 10_000,
                now
            )
            stats.maxByOrNull { it.lastTimeUsed }?.packageName == packageName
        } catch (e: Exception) {
            Log.e("UsageStats", "error", e)
            false
        }
    }
}

