package com.example.pixeldiet.repository

import android.content.Context
import android.util.Log
import com.example.pixeldiet.data.DatabaseProvider
import com.example.pixeldiet.data.UserProfileEntity
import com.example.pixeldiet.backup.BackupManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Firestore ↔ Room 동기화/백업/업로드 전담 레이어.
 *
 * - ViewModel은 UI 상태만 다루고, Firestore I/O는 여기로 몰아넣기 위한 1차 리팩터링용 클래스.
 * - (추후) syncFromFirestore() 전체 복원 로직도 이 클래스로 흡수/통합하는 것을 권장.
 */
class SyncRepository(private val appContext: Context) {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val db by lazy { DatabaseProvider.getDatabase(appContext) }
    private val userDao by lazy { db.userProfileDao() }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())

    // ------------------- Firestore READ -------------------

    /** Firestore users/{uid}/dailyRecords/{today} 의 appUsages(Map<pkg, minutes>)를 가져온다. */
    suspend fun loadBackupToday(uid: String): Map<String, Int> {
        val today = todayString()
        return try {
            val doc = firestore.collection("users")
                .document(uid)
                .collection("dailyRecords")
                .document(today)
                .get()
                .await()

            (doc.get("appUsages") as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = (v as? Number)?.toInt() ?: 0
                key to value
            }?.toMap() ?: emptyMap()
        } catch (e: Exception) {
            Log.e("SyncRepository", "Failed to load Firestore today usage: $e")
            emptyMap()
        }
    }

    /**
     * Firestore에서 특정 날짜(dateStr)의 목표(goalHistory)와 사용량(dailyRecords)을 (goals, usages)로 가져온다.
     * - dateStr: yyyy-MM-dd
     */
    suspend fun fetchGoalAndUsageForDate(uid: String, dateStr: String): Pair<Map<String, Int>, Map<String, Int>> {
        val goals = try {
            val snap = firestore.collection("users").document(uid)
                .collection("goalHistory").document(dateStr)
                .get().await()

            (snap.get("appUsages") as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = (v as? Number)?.toInt() ?: 0
                key to value
            }?.toMap() ?: emptyMap()
        } catch (e: Exception) {
            Log.e("SyncRepository", "Failed to fetch goals($dateStr): $e")
            emptyMap()
        }

        val usages = try {
            val snap = firestore.collection("users").document(uid)
                .collection("dailyRecords").document(dateStr)
                .get().await()

            (snap.get("appUsages") as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = (v as? Number)?.toInt() ?: 0
                key to value
            }?.toMap() ?: emptyMap()
        } catch (e: Exception) {
            Log.e("SyncRepository", "Failed to fetch usages($dateStr): $e")
            emptyMap()
        }

        return goals to usages
    }

    // ------------------- Firestore WRITE -------------------

    /** Firestore users/{uid}/dailyRecords/{today} 에 오늘 사용량을 업로드한다. */
    suspend fun uploadDailyUsage(uid: String, appUsagesMap: Map<String, Int>, dateStr: String = todayString()) {
        val data = mapOf(
            "date" to dateStr,
            "appUsages" to appUsagesMap
        )
        try {
            firestore.collection("users")
                .document(uid)
                .collection("dailyRecords")
                .document(dateStr)
                .set(data)
                .await()

            Log.d("SyncRepository", "✅ Daily usage uploaded ($uid / $dateStr)")
        } catch (e: Exception) {
            Log.e("SyncRepository", "❌ Failed to upload daily usage ($uid / $dateStr): $e")
        }
    }

    /** Firestore users/{uid}/goalHistory/{today} 에 오늘 목표를 업로드한다. */
    suspend fun uploadDailyGoal(uid: String, newGoals: Map<String, Int>, dateStr: String = todayString()) {
        val data = mapOf(
            "date" to dateStr,
            "appUsages" to newGoals
        )
        try {
            firestore.collection("users")
                .document(uid)
                .collection("goalHistory")
                .document(dateStr)
                .set(data)
                .await()

            Log.d("SyncRepository", "✅ Daily goals uploaded ($uid / $dateStr)")
        } catch (e: Exception) {
            Log.e("SyncRepository", "❌ Failed to upload daily goals ($uid / $dateStr): $e")
        }
    }

    // ------------------- Profile init (Room + Firestore) -------------------

    private fun generateFriendCode(length: Int = 8): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    /**
     * Room에 프로필이 없으면 생성해서 저장하고, Firestore에도 users/{uid} + profile/main 저장까지 수행.
     * 반환값은 Room에 저장된 최종 UserProfileEntity.
     */
    suspend fun initUserProfileIfNeeded(uid: String, displayName: String?): UserProfileEntity {
        val existing = userDao.getUserProfileOnce(uid)
        if (existing != null) return existing

        val profileEntity = UserProfileEntity(
            uid = uid,
            name = displayName ?: "사용자",
            imageUrl = "default_image_url",
            friendCode = generateFriendCode()
        )

        // Room 저장
        userDao.insert(profileEntity)

        // Firestore 저장(상위 문서 + profile 서브컬렉션)
        try {
            val userDocRef = firestore.collection("users").document(uid)
            userDocRef.set(mapOf("createdAt" to FieldValue.serverTimestamp())).await()
            userDocRef.collection("profile").document("main").set(profileEntity).await()
        } catch (e: Exception) {
            Log.e("SyncRepository", "Failed to write profile to Firestore: $e")
            // Room에 이미 저장했으니, Firestore 실패는 일단 로그만 남김(추후 재시도 정책 가능)
        }

        return profileEntity
    }

    private val backupManager by lazy {
        BackupManager(
            userDao = db.userProfileDao(),
            trackedAppDao = db.trackedAppDao(),
            dailyUsageDao = db.dailyUsageDao(),
            groupDao = db.groupDao(),
            friendDao = db.friendDao()
        )
    }

    suspend fun syncFromFirestore(uid: String) {
        backupManager.syncFromFirestore(uid)  // viewModel 없이 호출 가능해야 함
    }

    // ------------------- Debug/Test data -------------------

    /** (디버그용) goalHistory/dailyRecords에 테스트 데이터 삽입 */
    suspend fun insertTestDataForUid(uid: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN)

        val calendar = Calendar.getInstance().apply {
            set(2025, Calendar.OCTOBER, 10)
        }
        val today = Calendar.getInstance()

        val testApps = listOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.android.chrome"
        )

        while (calendar <= today) {
            val dateStr = sdf.format(calendar.time)

            val goalData = mapOf(
                "date" to dateStr,
                "appUsages" to testApps.associateWith { (30..120).random() }
            )
            val usageData = mapOf(
                "date" to dateStr,
                "appUsages" to testApps.associateWith { (0..150).random() }
            )

            try {
                firestore.collection("users").document(uid)
                    .collection("goalHistory").document(dateStr)
                    .set(goalData).await()

                firestore.collection("users").document(uid)
                    .collection("dailyRecords").document(dateStr)
                    .set(usageData).await()
            } catch (e: Exception) {
                Log.e("SyncRepository", "Failed to insert test data ($dateStr): $e")
            }

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        Log.d("SyncRepository", "✅ 테스트 데이터 삽입 완료 for $uid")
    }
}