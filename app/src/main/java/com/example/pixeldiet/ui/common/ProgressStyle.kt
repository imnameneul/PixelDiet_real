package com.example.pixeldiet.ui.common

import androidx.compose.ui.graphics.Color

data class ProgressUi(
    val progress: Float,
    val color: Color
)

fun progressUi(usedMinutes: Int, goalMinutes: Int): ProgressUi {
    if (goalMinutes <= 0) {
        return ProgressUi(progress = 0f, color = Color.LightGray)
    }

    val ratio = usedMinutes.toFloat() / goalMinutes.toFloat()
    val clamped = ratio.coerceIn(0f, 1f)

    val color = when {
        ratio < 0.7f -> Color(0xFF2E7D32)   // 초록
        ratio <= 1.0f -> Color(0xFFF9A825)  // 노랑
        else -> Color(0xFFC62828)           // 빨강
    }

    return ProgressUi(progress = clamped, color = color)
}
