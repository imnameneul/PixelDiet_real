package com.example.pixeldiet.friend

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "friend_request")
data class FriendRequest(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fromUid: String,
    val toUid: String,
    val fromName: String,
    val toName: String,
    val fromPhotoUrl: String? = null,
    val toPhotoUrl: String? = null,
    val status: String = "pending" // pending, accepted
)