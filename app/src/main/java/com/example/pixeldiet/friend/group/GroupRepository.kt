package com.example.pixeldiet.friend.group

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.pixeldiet.data.DailyUsageDao
import com.example.pixeldiet.data.GroupDao
import com.example.pixeldiet.data.UserProfileDao
import com.example.pixeldiet.data.UserProfileEntity
import com.example.pixeldiet.friend.FriendRecord
import com.example.pixeldiet.friend.FriendRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GroupRepository(
    val dao: GroupDao,
     val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val friendRepository: FriendRepository,
    val userProfileDao : UserProfileDao
) {

    suspend fun addMembersToGroup(groupId: String, memberIds: List<String>) {
        val group = dao.getGroup(groupId) ?: return
        val updatedMembers = (group.memberIds + memberIds).distinct()
        dao.updateMembers(groupId, updatedMembers)
    }


    fun getGroupMembers(ids: List<String>): Flow<List<UserProfileEntity>> {
        return userProfileDao.getUsersByIds(ids)
    }
    suspend fun getGroup(groupId: String): GroupRecord? {
        return dao.getGroup(groupId)
    }
    suspend fun updateGroupApp(groupId: String, appId: String) {
        dao.updateApp(groupId, appId)
    }
    val currentUserId: String?
        get() = auth.currentUser?.uid

    // 1️⃣ 전체 그룹 가져오기 (Flow)
    fun getGroups(): Flow<List<GroupRecord>> = kotlinx.coroutines.flow.flow {
        val list = dao.getAllGroups() // suspend 함수 호출 가능
        emit(list)
    }

    // 2️⃣ 특정 그룹 가져오기
    suspend fun getGroupById(groupId: String): GroupRecord? {
        return dao.getGroup(groupId)
    }

    // 3️⃣ 그룹 생성
    suspend fun createGroup(name: String, appId: String) {
        val uid = currentUserId ?: return

        // Firestore에서 사용자 이름 가져오기 (profile/main 문서 안의 name 필드)
        val userName = try {
            firestore.collection("users")
                .document(uid)
                .collection("profile")
                .document("main")
                .get()
                .await()
                .getString("name") ?: ""
        } catch (e: Exception) {
            Log.e("Firestore", "Error fetching user name for $uid", e)
            ""
        }

        val newGroup = GroupRecord(
            groupId = UUID.randomUUID().toString(),
            name = name,
            ownerId = uid,
            memberIds = listOf(uid),
            appId = appId,
            goalMinutes = 0
        )

        try {
            // 1️⃣ 최상위 groups 컬렉션에 그룹 생성
            firestore.collection("groups")
                .document(newGroup.groupId)
                .set(newGroup)
                .addOnSuccessListener { Log.d("Firestore", "그룹 저장 성공") }
                .addOnFailureListener { e -> Log.e("Firestore", "그룹 저장 실패", e) }

            // 2️⃣ 사용자의 groups 서브컬렉션에 groupId만 저장
            firestore.collection("users")
                .document(uid)
                .collection("groups")
                .document(newGroup.groupId)
                .set(mapOf("groupId" to newGroup.groupId))
                .addOnSuccessListener { Log.d("Firestore", "사용자 그룹 참조 저장 성공") }
                .addOnFailureListener { e -> Log.e("Firestore", "사용자 그룹 참조 저장 실패", e) }

            // 3️⃣ members 서브컬렉션에 방장 정보 추가 (이름 포함)
            firestore.collection("groups")
                .document(newGroup.groupId)
                .collection("members")
                .document(uid)
                .set(
                    mapOf(
                        "name" to userName,             // profile/main에서 가져온 이름
                        "usage" to 0,
                        "isRunning" to false,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .addOnSuccessListener { Log.d("Firestore", "방장 멤버 정보 추가 성공") }
                .addOnFailureListener { e -> Log.e("Firestore", "방장 멤버 정보 추가 실패", e) }

            // 4️⃣ Room DB에 저장 (UI 자동 갱신용)
            dao.createGroup(newGroup)

        } catch (e: Exception) {
            Log.e("Firestore", "그룹 생성 중 오류", e)
        }
    }



    fun getMyGroups(): Flow<List<GroupRecord>> {
        return dao.loadAllGroups()
    }



    // 4️⃣ 그룹 나가기
    suspend fun leaveGroup(group: GroupRecord) {
        val uid = currentUserId ?: return

        // 1️⃣ 그룹 멤버 목록에서 본인 제거
        val updatedMembers = group.memberIds.filter { it != uid }

        if (updatedMembers.isEmpty()) {
            // 2️⃣ 멤버가 0명이면 그룹 자체 삭제
            firestore.collection("groups")
                .document(group.groupId)
                .delete()
                .addOnFailureListener { e ->
                    Log.e("GroupDebug", "그룹 삭제 실패: $e")
                }

            // Room DB에서도 삭제
            dao.deleteGroup(group.groupId)

        } else {
            // 3️⃣ Firestore groups 컬렉션의 멤버 목록 업데이트
            firestore.collection("groups")
                .document(group.groupId)
                .update("memberIds", updatedMembers)
                .addOnFailureListener { e ->
                    Log.e("GroupDebug", "그룹 멤버 업데이트 실패: $e")
                }
        }

        // 3️⃣ Firestore users 컬렉션 내 본인의 그룹 참조 삭제
        firestore.collection("users")
            .document(uid)
            .collection("groups")
            .document(group.groupId)
            .delete()
            .addOnFailureListener { e ->
                Log.e("GroupDebug", "사용자 그룹 참조 삭제 실패: $e")
            }

        // 4️⃣ 필요하다면 Room DB에 사용자별 그룹 참조도 삭제
        dao.deleteGroup(group.groupId)
    }

    // 5️⃣ 그룹 삭제 (방장만 가능)
    suspend fun deleteGroup(group: GroupRecord) {
        val uid = currentUserId ?: return
        if (group.ownerId != uid) return

        dao.deleteGroup(group.groupId)
        firestore.collection("groups")
            .document(group.groupId)
            .delete()
    }

    suspend fun getGoalMinutes(groupId: String): Int {
        // Flow<Int?>를 첫 번째 값으로 변환, null이면 0
        return dao.getGroupGoalMinutes(groupId).firstOrNull() ?: 0
    }
    suspend fun updateGoalMinutes(groupId: String, minutes: Int) {
        // 1️⃣ Room 업데이트
        dao.updateGoalMinutes(groupId, minutes)

        // 2️⃣ Firestore 업데이트
        firestore.collection("groups")
            .document(groupId)
            .update("goalMinutes", minutes)
            .await()
    }

    suspend fun getUserAppUsage(uid: String, appId: String): Int {
        return dao.getUsageForUserApp(uid, appId) ?: 0
    }
    suspend fun getMemberIds(groupId: String): List<String> {
        return dao.getGroupMemberIds(groupId) ?: emptyList()
    }


}

