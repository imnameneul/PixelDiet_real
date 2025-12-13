package com.example.pixeldiet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters


import com.example.pixeldiet.friend.group.GroupRecord
import com.example.pixeldiet.friend.FriendRequest
import com.example.pixeldiet.friend.FriendRecord

@Database(
    entities = [UserProfileEntity::class, GroupRecord::class, FriendRecord::class, FriendRequest::class,  DailyUsageEntity::class, AppUsageEntity::class,TrackedAppEntity::class, NotificationSettingsEntity::class],
    version = 4
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