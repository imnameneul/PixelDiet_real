package com.example.pixeldiet.data

// DatabaseProvider.kt


import android.content.Context
import androidx.room.Room
import kotlin.jvm.java

object DatabaseProvider {

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app_database"
            ).fallbackToDestructiveMigration() // 필요하면 마이그레이션 처리
                .build()
            INSTANCE = instance
            instance
        }
    }
}
