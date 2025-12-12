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
    var usage: Int,
    var isRunning: Boolean = false,
    var updatedAt: Long = 0L
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

    private var usageTimerJob: Job? = null
    private val timerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var appUsageMonitor: AppUsageMonitor? = null


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
                startAppMonitoring() // 바로 감시 시작
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
        viewModelScope.launch {
            firestore.collection("groups")
                .document(groupId)
                .collection("members")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener

                    if (snapshot != null) {
                        val members = snapshot.documents.mapNotNull { doc ->
                            val uid = doc.id
                            val name = doc.getString("name") ?: ""
                            val usage = doc.getLong("usage")?.toInt() ?: 0
                            val isRunning = doc.getBoolean("isRunning") ?: false
                            val updatedAt = doc.getLong("updatedAt") ?: 0L

                            MemberUsage(uid, name, usage, isRunning, updatedAt)
                        }

                        _groupMembers.value = members
                    }
                }
        }
    }

    private fun observeMyGroups() {
        val uid = currentUserId ?: return
        Log.d("GroupViewModel", "Starting observeMyGroups() for user $uid")

        firestore.collection("users")
            .document(uid)
            .collection("groups")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) {
                    Log.d("GroupViewModel", "Snapshot is null")
                    return@addSnapshotListener
                }

                Log.d("GroupViewModel", "Snapshot listener triggered with ${snapshot.documents.size} documents")

                snapshot.documents.forEach { doc ->
                    val groupId = doc.getString("groupId") ?: return@forEach
                    Log.d("GroupViewModel", "Found groupId in snapshot: $groupId")

                    viewModelScope.launch {
                        val group = repository.getGroup(groupId)
                        if (group != null) {
                            _selectedGroup.value = group
                            _selectedApp.value = group.appId

                            Log.d(
                                "GroupViewModel",
                                "Setting selected group: ${group.groupId}, selected app: ${group.appId}"
                            )

                            if (_selectedApp.value != null) {
                                Log.d("GroupViewModel", "Calling startAppMonitoring() for app ${_selectedApp.value}")
                                startAppMonitoring()
                            } else {
                                Log.d("GroupViewModel", "selectedApp is null, skipping startAppMonitoring()")
                            }

                            loadGroupMembers(groupId)
                        } else {
                            Log.d("GroupViewModel", "Repository returned null for groupId $groupId")
                        }
                    }
                }
            }
    }


    private fun startUsageTimer(groupId: String) {
        if (usageTimerJob?.isActive == true) {
            Log.d("GroupViewModel", "Usage timer already running")
            return
        }

        Log.d("GroupViewModel", "Starting usage timer for group $groupId")

        usageTimerJob = timerScope.launch {
            while (isActive) {
                delay(60_000L) // 1분 단위로 측정
                val now = System.currentTimeMillis()
                val updatedList = _groupMembers.value.map { member ->
                    if (member.isRunning) {
                        member.usage += 1
                        Log.d("GroupViewModel", "Incrementing usage for ${member.name}: ${member.usage}분")
                        // Firestore 업데이트
                        firestore.collection("groups")
                            .document(groupId)
                            .collection("members")
                            .document(member.uid)
                            .update(
                                mapOf(
                                    "usage" to member.usage,
                                    "updatedAt" to now // 1분 단위로만 갱신
                                )
                            )
                        member
                    } else member
                }
                _groupMembers.value = updatedList
            }
        }
    }




    fun startAppMonitoring() {
        val group = _selectedGroup.value ?: return
        val appId = _selectedApp.value ?: return

        Log.d("GroupViewModel", "Calling startAppMonitoring() for app $appId")

        // 이미 실행 중이면 중복 제거
        stopAppMonitoring()
        Log.d("GroupViewModel", "Stopping AppUsageMonitor and usage timer")

        // 1️⃣ AppUsageMonitor 시작 (실시간 foreground 체크)
        appUsageMonitor = AppUsageMonitor(context, group.groupId, appId)
        appUsageMonitor?.startMonitoring()
        Log.d("GroupViewModel", "Starting AppUsageMonitor for $appId")

        // 2️⃣ Firestore snapshot listener (멤버 상태 갱신만)
        membersListener = firestore.collection("groups")
            .document(group.groupId)
            .collection("members")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val members = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.id
                    val name = doc.getString("name") ?: ""
                    val usage = doc.getLong("usage")?.toInt() ?: 0
                    val isRunning = doc.getBoolean("isRunning") ?: false
                    val updatedAt = doc.getLong("updatedAt") ?: 0L
                    MemberUsage(uid, name, usage, isRunning, updatedAt)
                }
                _groupMembers.value = members

                // 타이머는 이미 실행 중인지 확인 후 시작
                if (usageTimerJob?.isActive != true) {
                    startUsageTimer(group.groupId)
                }
            }
    }



    fun stopAppMonitoring() {
        Log.d("GroupViewModel", "Stopping AppUsageMonitor and usage timer")
        appUsageMonitor?.stopMonitoring()
        appUsageMonitor = null
        membersListener?.remove()
        membersListener = null
        usageTimerJob?.cancel()
        usageTimerJob = null
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
                        "usage" to 0,                     // 초기 사용시간
                        "isRunning" to false,             // 실행 상태
                        "updatedAt" to System.currentTimeMillis() // 타임스탬프
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



}

class AppUsageMonitor(
    private val context: Context,
    private val groupId: String,
    private val appId: String
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId get() = FirebaseAuth.getInstance().currentUser?.uid
    private val monitorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startMonitoring() {
        monitorScope.launch {
            while (isActive) {
                delay(1_000L) // 1초 단위로 앱 foreground 체크
                val isRunning = isAppInForeground(appId)
                Log.d("AppUsageMonitor", "[$appId] isRunning=$isRunning")
                updateRunningStatus(isRunning) // isRunning만 갱신
            }
        }
    }

    fun stopMonitoring() {
        monitorScope.cancel()
    }

    @SuppressLint("ServiceCast")
    private fun isAppInForeground(packageName: String): Boolean {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 10_000,
            now
        )
        val lastUsedApp = stats.maxByOrNull { it.lastTimeUsed }?.packageName
        return lastUsedApp == packageName
    }

    private suspend fun updateRunningStatus(isRunning: Boolean) {
        val uid = currentUserId ?: return
        firestore.collection("groups")
            .document(groupId)
            .collection("members")
            .document(uid)
            .update("isRunning", isRunning)
            .await()
    }
}