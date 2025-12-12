package com.example.pixeldiet.friend.group

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "group_table")
data class GroupRecord(
    @PrimaryKey val groupId: String = UUID.randomUUID().toString(),
    val name: String,
    val ownerId: String,               // 그룹 생성자
    val memberIds: List<String> = emptyList(), // 멤버 UID 리스트
    val appId: String? = null,
    val goalMinutes: Int = 0
)