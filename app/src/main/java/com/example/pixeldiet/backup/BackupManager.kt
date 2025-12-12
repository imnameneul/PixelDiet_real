package com.example.pixeldiet.backup

import android.util.Log
import com.example.pixeldiet.data.DailyUsageDao
import com.example.pixeldiet.data.DailyUsageEntity
import com.example.pixeldiet.data.FriendDao
import com.example.pixeldiet.data.GroupDao
import com.example.pixeldiet.data.TrackedAppDao
import com.example.pixeldiet.data.TrackedAppEntity
import com.example.pixeldiet.data.UserProfileDao
import com.example.pixeldiet.data.UserProfileEntity
import com.example.pixeldiet.friend.FriendRecord
import com.example.pixeldiet.friend.group.GroupRecord
import com.example.pixeldiet.repository.UsageRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Firestore -> Room 복원(동기화) 담당.
 *
 * ✅ 리팩터링 포인트:
 * - ViewModel을 인자로 받지 않는다.
 * - UI 갱신(markDataReady/refreshData 등)은 SharedViewModel(호출자)에서 처리한다.
 */
class BackupManager(
    private val userDao: UserProfileDao,
    private val trackedAppDao: TrackedAppDao,
    private val dailyUsageDao: DailyUsageDao,
    private val groupDao: GroupDao,
    private val friendDao: FriendDao
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun currentUserId(): String = auth.currentUser?.uid ?: "anonymous"

    /** 익명 로그인 (앱 최초 실행 시) */
    suspend fun initUser() {
        try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "initUser failed: $e")
        }
    }

    suspend fun signInWithGoogle(idToken: String) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
        } catch (e: Exception) {
            Log.e("BackupManager", "signInWithGoogle failed: $e")
        }
    }

    /**
     * ✅ uid 버전 동기화 함수 (ViewModel 의존성 제거)
     *
     * - caller(SharedViewModel/SyncRepository)에서 uid를 전달해 호출한다.
     * - 여기서는 Firestore에서 내려받아 Room에 저장까지만 수행한다.
     */
    suspend fun syncFromFirestore(uid: String) {
        if (uid.isBlank()) return

        Log.d("BackupManager", "===== Sync started for UID: $uid =====")

        // 1️⃣ Profile 동기화
        try {
            val profileSnap = firestore.collection("users")
                .document(uid)
                .collection("profile")
                .get()
                .await()

            Log.d("BackupManager", "[Profile] ${profileSnap.size()} documents found")

            profileSnap.documents.forEach { doc ->
                val entity = UserProfileEntity(
                    uid = doc.getString("uid") ?: uid,
                    name = doc.getString("name") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: "",
                    friendCode = doc.getString("friendCode") ?: ""
                )
                userDao.insert(entity)
                Log.d("BackupManager", "[Profile] Inserted: $entity")
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "[Profile] Failed to sync", e)
        }

        // 2️⃣ Tracked Apps / Goal 동기화
        try {
            val goalSnap = firestore.collection("users")
                .document(uid)
                .collection("goalHistory")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            Log.d("BackupManager", "[TrackedApps] ${goalSnap.size()} documents found")

            val trackedAppsFromFirestore = mutableListOf<TrackedAppEntity>()
            goalSnap.documents.forEach { doc ->
                val appUsages = doc.get("appUsages") as? Map<String, Long> ?: emptyMap()
                appUsages.forEach { (pkg, goalTime) ->
                    val entity = TrackedAppEntity(pkg, goalTime.toInt())
                    trackedAppDao.insertOrUpdate(entity)
                    trackedAppsFromFirestore.add(entity)
                    Log.d("BackupManager", "[TrackedApps] Inserted: $entity")
                }
            }

            // ⚠️ 현재 구조 유지(중간단계): UsageRepository 내부 상태 갱신
            UsageRepository.updateTrackedAppsFromBackup(trackedAppsFromFirestore)
        } catch (e: Exception) {
            Log.e("BackupManager", "[TrackedApps] Failed to sync", e)
        }

        // 3️⃣ DailyUsage 동기화 (오늘만)
        try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val dailySnap = firestore.collection("users")
                .document(uid)
                .collection("dailyRecords")
                .whereEqualTo("date", today)
                .get()
                .await()

            Log.d("BackupManager", "[DailyUsage] ${dailySnap.size()} documents found for $today")

            dailySnap.documents.forEach { doc ->
                val appUsages = doc.get("appUsages") as? Map<String, Long> ?: emptyMap()
                val json = Gson().toJson(appUsages.mapValues { it.value.toInt() })
                val dailyEntity = DailyUsageEntity(uid, today, json)

                dailyUsageDao.insertOrUpdate(dailyEntity)
                Log.d(
                    "BackupManager",
                    "[DailyUsage] Inserted: ${dailyEntity.uid} / Date: ${dailyEntity.date} / Apps: $appUsages"
                )
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "[DailyUsage] Failed to sync", e)
        }

        // 4️⃣ Group 동기화
        try {
            val groupSnap = firestore.collection("users")
                .document(uid)
                .collection("groups")
                .get()
                .await()

            Log.d("BackupManager", "[Group] ${groupSnap.size()} group documents found")

            groupSnap.documents.forEach { doc ->
                val groupId = doc.id
                try {
                    val groupDoc = firestore.collection("groups").document(groupId).get().await()
                    if (groupDoc.exists()) {
                        val entity = GroupRecord(
                            groupId = groupDoc.getString("groupId") ?: groupId,
                            name = groupDoc.getString("name") ?: "",
                            ownerId = groupDoc.getString("ownerId") ?: "",
                            memberIds = groupDoc.get("memberIds") as? List<String> ?: emptyList(),
                            appId = groupDoc.getString("appId"),
                            goalMinutes = (groupDoc.getLong("goalMinutes") ?: 0).toInt()
                        )
                        groupDao.insertOrUpdate(listOf(entity))
                        Log.d("BackupManager", "[Group] Inserted: $entity")
                    }
                } catch (e: Exception) {
                    Log.e("BackupManager", "[Group] Failed to fetch group $groupId", e)
                }
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "[Group] Failed to sync", e)
        }

        // 5️⃣ Friend 동기화
        try {
            val friendSnap = firestore.collection("users")
                .document(uid)
                .collection("friends")
                .get()
                .await()

            Log.d("BackupManager", "[Friend] ${friendSnap.size()} friend documents found")

            friendSnap.documents.forEach { doc ->
                val entity = FriendRecord(
                    uid = doc.getString("uid") ?: "",
                    name = doc.getString("name") ?: "",
                    photoUrl = doc.getString("photoUrl")
                )
                friendDao.insertOrUpdate(listOf(entity))
                Log.d("BackupManager", "[Friend] Inserted: $entity")
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "[Friend] Failed to sync", e)
        }

        Log.d("BackupManager", "===== Sync finished for UID: $uid =====")
        // ✅ 여기서 markDataReady() 같은 UI 호출은 하지 않음!
    }
}
