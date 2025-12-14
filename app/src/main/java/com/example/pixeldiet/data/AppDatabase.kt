package com.example.pixeldiet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

import com.example.pixeldiet.friend.group.GroupRecord
import com.example.pixeldiet.friend.FriendRequest
import com.example.pixeldiet.friend.FriendRecord
import com.example.pixeldiet.data.GoalHistoryEntity
import com.example.pixeldiet.data.GoalHistoryDao

@Database(
    entities = [
        UserProfileEntity::class,
        GroupRecord::class,
        FriendRecord::class,
        FriendRequest::class,
        DailyUsageEntity::class,
        GoalHistoryEntity::class, // ✅ 추가
        AppUsageEntity::class,
        TrackedAppEntity::class,
        NotificationSettingsEntity::class
    ],
    version = 6
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun trackedAppDao(): TrackedAppDao
    abstract fun notificationSettingsDao(): NotificationSettingsDao
    abstract fun groupDao(): GroupDao
    abstract fun friendDao(): FriendDao

    abstract fun goalHistoryDao(): GoalHistoryDao  // ✅ 추가

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}