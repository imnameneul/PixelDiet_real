package com.example.pixeldiet.friend

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friend_record")
data class FriendRecord(
    @PrimaryKey val uid: String,       // 친구 UID
    val name: String,                   // 친구 닉네임
    val photoUrl: String? = null // 아직 없으면 null
)
