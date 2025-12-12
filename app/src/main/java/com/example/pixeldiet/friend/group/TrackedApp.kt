package com.example.pixeldiet.friend.group

import android.graphics.drawable.Drawable
import androidx.room.Entity
import androidx.room.Ignore

@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
    val packageName: String,
    val goalTime: Int,
    @Ignore val icon: Drawable? = null   // Room에는 저장하지 않고, 런타임에만 사용
)