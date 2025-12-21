package com.example.pixeldiet.ui.main

import coil.compose.AsyncImage
import com.example.pixeldiet.model.AppUsage
import com.example.pixeldiet.ui.common.progressUi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun AppUsageCard(appUsage: AppUsage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ─── 상단: 아이콘 + 앱 이름 + 스트릭 ───────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // 아이콘
                if (appUsage.icon != null) {
                    AsyncImage(
                        model = appUsage.icon,
                        contentDescription = appUsage.appLabel,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    // 아이콘 없으면 회색 박스로 폴백
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Gray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("?", color = Color.White, fontSize = 24.sp)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 앱 이름
                Text(
                    text = appUsage.appLabel,   // ✅ AppName.displayName 대신 appLabel
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )

            }

            Spacer(modifier = Modifier.height(8.dp))

            // ─── 하단: 현재 사용시간 / 목표시간 / 프로그레스바 ────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = formatTime(appUsage.currentUsage), fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))

                val ui = progressUi(appUsage.currentUsage, appUsage.goalTime)

                LinearProgressIndicator(
                    progress = { ui.progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = ui.color,
                    trackColor = Color.LightGray
                )

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTime(appUsage.goalTime),
                    fontSize = 14.sp,
                    color = ui.color   // ✅ 프로그레스바 색과 동일
                )
            }
        }
    }
}

private fun formatTime(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return String.format("%d시간 %02d분", hours, mins)
}