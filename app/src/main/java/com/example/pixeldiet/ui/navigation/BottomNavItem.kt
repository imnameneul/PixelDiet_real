package com.example.pixeldiet.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

// 하단 네비게이션 아이템 정의
sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Main : BottomNavItem("main", "메인", Icons.Default.Home)
    object Calendar : BottomNavItem("calendar", "캘린더", Icons.Default.CalendarMonth)
    object Friends : BottomNavItem("friends", "친구", Icons.Default.Group)
    object Settings : BottomNavItem("settings", "설정", Icons.Default.Settings)
}