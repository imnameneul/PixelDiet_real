package com.example.pixeldiet.friend.group

import android.util.Log
import com.example.pixeldiet.data.GroupDao
import com.example.pixeldiet.data.UserProfileDao
import com.example.pixeldiet.data.UserProfileEntity
import com.example.pixeldiet.friend.FriendRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import java.util.UUID

class GroupRepository(
    val dao: GroupDao,
    val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val friendRepository: FriendRepository,
    val userProfileDao: UserProfileDao
) {

    // (Room) 멤버 추가: 현재는 로컬 DB만 업데이트.
    // ⚠️ Firestore의 groups/{groupId}.memberIds는 다른 로직에서 관리되고 있을 가능성이 큼.
    suspend fun addMembersToGroup(groupId: String, memberIds: List<String>) {
        val group = dao.getGroup(groupId) ?: return
        val updatedMembers = (group.memberIds + memberIds).distinct()
        dao.updateMembers(groupId, updatedMembers)
    }

    fun getGroupMembers(ids: List<String>): Flow<List<UserProfileEntity>> {
        return userProfileDao.getUsersByIds(ids)
    }

    suspend fun getGroup(groupId: String): GroupRecord? = dao.getGroup(groupId)

    suspend fun updateGroupApp(groupId: String, appId: String) {
        dao.updateApp(groupId, appId)
    }

    val currentUserId: String?
        get() = auth.currentUser?.uid

    // 1️⃣ 전체 그룹 가져오기 (Room Flow)
    fun getGroups(): Flow<List<GroupRecord>> = flow {
        emit(dao.getAllGroups())
    }

    // 2️⃣ 특정 그룹 가져오기
    suspend fun getGroupById(groupId: String): GroupRecord? = dao.getGroup(groupId)

    // ----------------------------
    // Firestore members 서브컬렉션 안전 업서트(중요)
    // ----------------------------

    /**
     * ✅ members/{memberId} 문서를 "update()" 대신 set(merge)로 업데이트한다.
     * - 문서가 없어도 생성되므로 "No document to update" 크래시를 피할 수 있음.
     * - 호출하는 쪽에서 update(...)를 직접 쓰지 말고 이 함수로 통일하는게 안전함.
     */
    suspend fun upsertMemberFields(
        groupId: String,
        memberId: String,
        fields: Map<String, Any?>
    ) {
        val cleaned = fields.filterValues { it != null }.toMutableMap()
        cleaned["updatedAt"] = System.currentTimeMillis()

        try {
            firestore.collection("groups")
                .document(groupId)
                .collection("members")
                .document(memberId)
                .set(cleaned, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("GroupRepository", "upsertMemberFields failed ($groupId / $memberId): $e")
        }
    }

    /**
     * ✅ 기존 데이터/구조가 섞여 있을 때(특히 과거에 members 문서가 안 만들어진 그룹),
     *   멤버 문서가 없으면 최소 필드로 생성해두는 용도.
     */
    suspend fun ensureMemberDocExists(groupId: String, memberId: String, name: String? = null) {
        upsertMemberFields(
            groupId = groupId,
            memberId = memberId,
            fields = mapOf(
                "name" to name,
                "usage" to 0,
                "isRunning" to false
            )
        )
    }

    // 3️⃣ 그룹 생성
    suspend fun createGroup(name: String, appId: String) {
        val uid = currentUserId ?: return

        // Firestore에서 사용자 이름 가져오기 (users/{uid}/profile/main 의 name)
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

        // ✅ 여기서 제일 중요한 수정:
        // 기존 코드는 addOnSuccessListener만 달아두고 await()를 안 해서,
        // 함수가 "끝났는데도" Firestore 쓰기가 아직 완료되지 않은 레이스가 발생할 수 있음.
        // (그 사이 다른 코드가 members/{uid}를 update()하면 NOT_FOUND 크래시가 날 수 있음)
        try {
            val groupRef = firestore.collection("groups").document(newGroup.groupId)

            // 1) groups/{groupId}
            groupRef.set(newGroup).await()

            // 2) users/{uid}/groups/{groupId}
            firestore.collection("users")
                .document(uid)
                .collection("groups")
                .document(newGroup.groupId)
                .set(mapOf("groupId" to newGroup.groupId))
                .await()

            // 3) groups/{groupId}/members/{uid}
            groupRef.collection("members")
                .document(uid)
                .set(
                    mapOf(
                        "name" to userName,
                        "usage" to 0,
                        "isRunning" to false,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                .await()

            // 4) Room 저장 (UI 갱신용)
            dao.createGroup(newGroup)

        } catch (e: Exception) {
            Log.e("Firestore", "그룹 생성 중 오류", e)
        }
    }

    fun getMyGroups(): Flow<List<GroupRecord>> = dao.loadAllGroups()

    // 4️⃣ 그룹 나가기
    suspend fun leaveGroup(group: GroupRecord) {
        val uid = currentUserId ?: return

        val groupRef = firestore.collection("groups").document(group.groupId)
        val userGroupRef = firestore.collection("users")
            .document(uid)
            .collection("groups")
            .document(group.groupId)

        // 1) members/{uid} 정리(있으면 삭제, 없어도 무시)
        try {
            groupRef.collection("members").document(uid).delete().await()
        } catch (e: Exception) {
            Log.w("GroupRepository", "leaveGroup: member doc delete failed (ignored): $e")
        }

        // 2) groups/{groupId}.memberIds 갱신 (문서가 없어도 set(merge)로 안전하게)
        val updatedMembers = group.memberIds.filter { it != uid }
        try {
            if (updatedMembers.isEmpty()) {
                groupRef.delete().await()
            } else {
                groupRef.set(mapOf("memberIds" to updatedMembers), SetOptions.merge()).await()
            }
        } catch (e: Exception) {
            Log.e("GroupRepository", "leaveGroup: update memberIds failed: $e")
        }

        // 3) users/{uid}/groups/{groupId} 삭제
        try {
            userGroupRef.delete().await()
        } catch (e: Exception) {
            Log.e("GroupRepository", "leaveGroup: delete user group ref failed: $e")
        }

        // 4) Room 정리
        dao.deleteGroup(group.groupId)
    }

    // 5️⃣ 그룹 삭제 (방장만 가능)
    suspend fun deleteGroup(group: GroupRecord) {
        val uid = currentUserId ?: return
        if (group.ownerId != uid) return

        dao.deleteGroup(group.groupId)

        try {
            firestore.collection("groups")
                .document(group.groupId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("GroupRepository", "deleteGroup Firestore failed: $e")
        }
    }

    suspend fun getGoalMinutes(groupId: String): Int {
        return dao.getGroupGoalMinutes(groupId).firstOrNull() ?: 0
    }

    suspend fun updateGoalMinutes(groupId: String, minutes: Int) {
        // 1) Room 업데이트
        dao.updateGoalMinutes(groupId, minutes)

        // 2) Firestore 업데이트 (update()는 문서가 없으면 NOT_FOUND라서 set(merge)로 안전하게)
        try {
            firestore.collection("groups")
                .document(groupId)
                .set(mapOf("goalMinutes" to minutes), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("GroupRepository", "updateGoalMinutes Firestore failed ($groupId): $e")
        }
    }

    suspend fun getUserAppUsage(uid: String, appId: String): Int {
        return dao.getUsageForUserApp(uid, appId) ?: 0
    }

    suspend fun getMemberIds(groupId: String): List<String> {
        return dao.getGroupMemberIds(groupId) ?: emptyList()
    }
}
