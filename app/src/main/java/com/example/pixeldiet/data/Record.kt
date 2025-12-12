package com.example.pixeldiet.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.pixeldiet.model.DailyUsage
import com.example.pixeldiet.model.NotificationSettings
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import java.util.UUID


@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val imageUrl: String,
    val friendCode: String
)
@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
    @PrimaryKey val packageName: String,
    val goalTime: Int // 사용자가 설정한 목표 시간(분)
)
// 앱별 목표
@Entity(tableName = "app_usage")
data class AppUsageEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val currentUsage: Int = 0,   // 분 단위 사용 시간
    val goalTime: Int = 0,       // 분 단위 목표 시간
    val streak: Int = 0          // 양수(성공 연속), 음수(실패 연속)
)
// 하루 단위 사용 기록
// -------------------- DailyUsageEntity --------------------
@Entity(
    tableName = "daily_usage",
    primaryKeys = ["uid", "date"]  // uid + date 조합을 PK로 설정
)
data class DailyUsageEntity(
    val uid: String,               // 사용자 ID
    val date: String,              // "YYYY-MM-DD"
    val appUsagesJson: String      // Map<String, Int>를 JSON으로 저장
) {
    fun toDailyUsage(): DailyUsage {
        val type = object : TypeToken<Map<String, Int>>() {}.type
        val map: Map<String, Int> = Gson().fromJson(appUsagesJson, type)
        return DailyUsage(date, map)
    }

    companion object {
        fun fromDailyUsage(uid: String, dailyUsage: DailyUsage): DailyUsageEntity {
            return DailyUsageEntity(
                uid = uid,
                date = dailyUsage.date,
                appUsagesJson = Gson().toJson(dailyUsage.appUsages)
            )
        }
    }
}

@Entity(tableName = "notification_settings")
data class NotificationSettingsEntity(
    @PrimaryKey val id: Int = 0,  // 항상 하나만 존재
    val individualApp50: Boolean = true,
    val individualApp70: Boolean = true,
    val individualApp100: Boolean = true,
    val total50: Boolean = true,
    val total70: Boolean = true,
    val total100: Boolean = true,
    val repeatIntervalMinutes: Int = 5
) {
    // ✅ 엔티티 -> DTO
    fun toDto(): NotificationSettings {
        return NotificationSettings(
            individualApp50 = individualApp50,
            individualApp70 = individualApp70,
            individualApp100 = individualApp100,
            total50 = total50,
            total70 = total70,
            total100 = total100,
            repeatIntervalMinutes = repeatIntervalMinutes
        )
    }

    // ✅ DTO -> 엔티티 (Companion object)
    companion object {
        fun fromDto(dto: NotificationSettings): NotificationSettingsEntity {
            return NotificationSettingsEntity(
                individualApp50 = dto.individualApp50,
                individualApp70 = dto.individualApp70,
                individualApp100 = dto.individualApp100,
                total50 = dto.total50,
                total70 = dto.total70,
                total100 = dto.total100,
                repeatIntervalMinutes = dto.repeatIntervalMinutes
            )
        }
    }
}