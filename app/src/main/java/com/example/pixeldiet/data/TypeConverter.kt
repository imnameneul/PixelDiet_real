package com.example.pixeldiet.data

import androidx.room.TypeConverter
import com.google.common.reflect.TypeToken
import com.google.gson.Gson

class Converters {
    @TypeConverter
    fun fromFriendList(friends: List<String>): String = Gson().toJson(friends)

    @TypeConverter
    fun toFriendList(friendsJson: String): List<String> =
        Gson().fromJson(friendsJson, object : TypeToken<List<String>>() {}.type)
}
