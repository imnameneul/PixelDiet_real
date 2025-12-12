package com.example.pixeldiet.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.pixeldiet.friend.FriendRecord
import com.example.pixeldiet.friend.FriendRequest
import com.example.pixeldiet.friend.group.GroupRecord

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull


@Dao
interface AppUsageDao {

    @Query("SELECT * FROM app_usage")
    fun getAllAppUsages(): Flow<List<AppUsageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<AppUsageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(usage: AppUsageEntity)
}

// -------------------- DailyUsageDao --------------------
@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage WHERE uid = :uid AND date = :date")
    suspend fun getDailyUsageEntity(uid: String, date: String): DailyUsageEntity?

    @Transaction
    suspend fun getDailyUsageMap(uid: String, date: String): Map<String, Int> {
        val entity = getDailyUsageEntity(uid, date) ?: return emptyMap()
        return try {
            // Gson으로 JSON을 Map<String, Int>로 변환
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Int>>() {}.type
            com.google.gson.Gson().fromJson<Map<String, Int>>(entity.appUsagesJson, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }
    @Query("DELETE FROM daily_usage")
    suspend fun clearAll()

    // 1️⃣ UI용: 특정 범위 데이터를 Flow로 가져오기
    @Query("SELECT * FROM daily_usage WHERE uid = :uid AND date BETWEEN :from AND :to ORDER BY date ASC")
    fun getUsageInRange(uid: String, from: String, to: String): Flow<List<DailyUsageEntity>>

    // 2️⃣ 백업/동기화용: 전체 데이터 가져오기
    @Query("SELECT * FROM daily_usage ORDER BY date ASC")
    suspend fun getAll(): List<DailyUsageEntity>

    // 3️⃣ 데이터 삽입
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<DailyUsageEntity>)

    // 4️⃣ 단일 기록 삽입/업데이트
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(usage: DailyUsageEntity)

    // 5️⃣ 특정 날짜 기록 삭제
    @Query("DELETE FROM daily_usage WHERE date = :date")
    suspend fun deleteByDate(date: String)
    @Query("SELECT * FROM daily_usage WHERE uid = :uid AND date = :date LIMIT 1")
    suspend fun getByDate(uid: String, date: String): DailyUsageEntity?

    // 6️⃣ 전체 삭제
    @Query("DELETE FROM daily_usage")
    suspend fun deleteAll()
}

// -------------------- UserProfileDao --------------------
@Dao
interface UserProfileDao {
    @Query("DELETE FROM user_profile")
    suspend fun clearAll()
    @Query("SELECT * FROM user_profile WHERE uid = :uid")
    fun getUserProfile(uid: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE uid IN (:uids)")
    fun getUsersByIds(uids: List<String>): Flow<List<UserProfileEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfileEntity)

    @Update
    suspend fun update(profile: UserProfileEntity)
    @Query("SELECT * FROM user_profile WHERE uid = :uid LIMIT 1")
    suspend fun getUserProfileOnce(uid: String): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE friendCode = :code LIMIT 1")
    suspend fun getUserByFriendCode(code: String): UserProfileEntity?

    // friendCode로 사용자 존재 여부 체크
    @Query("SELECT EXISTS(SELECT 1 FROM user_profile WHERE friendCode = :code)")
    suspend fun existsByFriendCode(code: String): Boolean

}

// -------------------- NotificationSettingsDao --------------------
@Dao
interface NotificationSettingsDao {
    @Query("SELECT * FROM notification_settings LIMIT 1")
    suspend fun getSettings(): NotificationSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: NotificationSettingsEntity)

    @Update
    suspend fun update(settings: NotificationSettingsEntity)
}

@Dao
interface FriendDao {
    @Query("DELETE FROM friend_record")
    suspend fun clearAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(friends: List<FriendRecord>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(friend: FriendRecord)
    // ---------------------------
    // 친구
    // ---------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriend(friend: FriendRecord)

    @Query("SELECT * FROM friend_record ORDER BY name ASC")
    fun getAllFriendsFlow(): Flow<List<FriendRecord>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriends(friends: List<FriendRecord>)

    @Query("SELECT * FROM friend_record")
    suspend fun getAllFriends(): List<FriendRecord>

    @Query("DELETE FROM friend_record WHERE uid = :friendUid")
    suspend fun removeFriend(friendUid: String)

    // ---------------------------
    // 받은 친구 요청
    // ---------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriendRequestReceived(request: FriendRequest)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriendRequestsReceived(requests: List<FriendRequest>)

    @Query("SELECT * FROM friend_request WHERE toUid = :currentUserUid AND status = 'pending'")
    suspend fun getFriendRequestsReceived(currentUserUid: String): List<FriendRequest>

    @Query("DELETE FROM friend_request WHERE id = :requestUid AND toUid = :currentUserUid")
    suspend fun removeFriendRequestReceived(requestUid: String, currentUserUid: String)

    // ---------------------------
    // 보낸 친구 요청
    // ---------------------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriendRequestSent(request: FriendRequest)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFriendRequestsSent(requests: List<FriendRequest>)

    @Query("SELECT * FROM friend_request WHERE fromUid = :currentUserUid AND status = 'pending'")
    suspend fun getFriendRequestsSent(currentUserUid: String): List<FriendRequest>

    @Query("DELETE FROM friend_request WHERE id = :requestUid AND fromUid = :currentUserUid")
    suspend fun removeFriendRequestSent(requestUid: String, currentUserUid: String)
    @Query("DELETE FROM friend_request WHERE fromUid = :uid")
    suspend fun clearFriendRequestsSent(uid: String)
    @Query("SELECT * FROM friend_request")
    fun getSentRequests(): List<FriendRequest>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentRequests(list: List<FriendRequest>)

    @Query("DELETE FROM friend_request")
    suspend fun clearSentRequests()
    @Query("DELETE FROM friend_record")
    suspend fun clearFriends()
    @Query("DELETE FROM friend_request WHERE toUid = :uid")
    suspend fun clearReceivedRequests(uid: String)

}

@Dao
interface TrackedAppDao {
    @Query("DELETE FROM tracked_apps")
    suspend fun clearAll()

    @Query("SELECT * FROM tracked_apps")
    fun getAllTrackedApps(): Flow<List<TrackedAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(app: TrackedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(apps: List<TrackedAppEntity>)

    @Delete
    suspend fun delete(app: TrackedAppEntity)

    @Query("DELETE FROM tracked_apps")
    suspend fun deleteAll()

    @Query("SELECT * FROM tracked_apps")
    suspend fun getAllTrackedAppsOnce(): List<TrackedAppEntity>

    @Query("DELETE FROM tracked_apps WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)
}

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(groups: List<GroupRecord>)
    @Query("SELECT * FROM group_table WHERE ownerId = :uid OR :uid IN (memberIds)")
    fun getGroupsForCurrentUser(uid: String): Flow<List<GroupRecord>>
    @Query("SELECT * FROM friend_record ORDER BY name ASC")
    fun getAllFriendsFlow(): Flow<List<FriendRecord>>
    @Query("SELECT * FROM group_table")
    fun getAllGroupsOnce(): Flow<List<GroupRecord>>
    // 1️⃣ 그룹 생성
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createGroup(group: GroupRecord)
    @Query("SELECT * FROM group_table")
    fun loadAllGroups(): Flow<List<GroupRecord>>
    // 2️⃣ 전체 그룹 조회
    @Query("SELECT * FROM group_table")
    suspend fun getAllGroups(): List<GroupRecord>

    // 3️⃣ 특정 그룹 조회
    @Query("SELECT * FROM group_table WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroup(groupId: String): GroupRecord?

    // 4️⃣ 그룹 삭제
    @Query("DELETE FROM group_table WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)

    // 5️⃣ 멤버 추가 (방장은 ownerId)
    @Query("UPDATE group_table SET memberIds = :memberIds WHERE groupId = :groupId")
    suspend fun updateMembers(groupId: String, memberIds: List<String>)

    // 6️⃣ 선택 앱 변경 (사용 시간 정렬 기준)
    @Query("UPDATE group_table SET appId = :appId WHERE groupId = :groupId")
    suspend fun updateApp(groupId: String, appId: String)

    // 특정 유저의 하루치 사용 기록 전체 가져오기
    @Query("SELECT * FROM daily_usage WHERE uid = :uid ORDER BY date DESC")
    fun getDailyUsage(uid: String): Flow<List<DailyUsageEntity>>

    // 특정 유저 + 특정 날짜의 기록 1개 가져오기
    @Query("SELECT * FROM daily_usage WHERE uid = :uid AND date = :date LIMIT 1")
    suspend fun getDailyUsageByDate(uid: String, date: String): DailyUsageEntity?

    @Query("UPDATE group_table SET goalMinutes = :minutes WHERE groupId = :groupId")
    suspend fun updateGoalMinutes(groupId: String, minutes: Int)

    @Query("SELECT goalMinutes FROM group_table WHERE groupId = :groupId LIMIT 1")
    fun getGroupGoalMinutes(groupId: String): Flow<Int?>


    @Query("SELECT memberIds FROM group_table WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroupMemberIds(groupId: String): List<String>?

    @Query("SELECT * FROM friend_record ORDER BY name ASC")
    fun getFriendsFlow(): Flow<List<FriendRecord>>
    suspend fun getUsageForUserApp(uid: String, appId: String): Int {
        // 최신 날짜 기록 가져오기
        val dailyUsage = getDailyUsage(uid = uid).firstOrNull()?.firstOrNull()
        // JSON에서 앱 사용 시간 추출
        return dailyUsage?.toDailyUsage()?.appUsages?.get(appId) ?: 0
    }
    @Query("DELETE FROM group_table")
    suspend fun clearAll()
}
