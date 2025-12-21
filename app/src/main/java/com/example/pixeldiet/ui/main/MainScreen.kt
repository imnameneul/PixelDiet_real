package com.example.pixeldiet.ui.main

import android.util.Log
import android.widget.Toast
import com.example.pixeldiet.model.AppUsage

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.pixeldiet.viewmodel.SharedViewModel
import java.text.SimpleDateFormat
import java.util.*


private enum class SortMode(val buttonLabel: String) {
    USAGE_DESC("사용시간순"),
    NAME_ASC("이름순"),
    OVER_RATIO_DESC("목표초과순")
}

private fun SortMode.next(): SortMode = when (this) {
    SortMode.USAGE_DESC -> SortMode.NAME_ASC
    SortMode.NAME_ASC -> SortMode.OVER_RATIO_DESC
    SortMode.OVER_RATIO_DESC -> SortMode.USAGE_DESC
}

@Composable
fun MainScreen(
    viewModel: SharedViewModel,              // ✅ 기본값 제거
    onAppSelectionClick: () -> Unit,          // ✅ 기본값 제거 (항상 넘겨주기)
    onAppDeleteClick: () -> Unit,   // 추가
    onGoalSettingClick: () -> Unit   // ✅ 추가
) {
    val isDataReady by viewModel.isDataReady.collectAsState()
    Log.d("MainScreen", "isDataReady: $isDataReady")
    if (!isDataReady) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }


    val trackedPackagesWithGoals by viewModel.trackedAppsFlow.collectAsState(initial = emptyList())
    val appList by viewModel.appUsageListFlow.collectAsState()           // List<AppUsage>
    val totalUsage by viewModel.totalUsageFlow.collectAsState(Pair(0, 0))  // Pair<Int, Int>
    val overallGoal by viewModel.overallGoalFlow.collectAsState(null)   // Int?
    val context = LocalContext.current
    val pm = context.packageManager

// goalTime만 trackedAppsFlow에서 주입하고, 나머지(usage, streak)는 VM 결과(appList) 그대로 씀
    val goalMap = remember(trackedPackagesWithGoals) {
        trackedPackagesWithGoals.associate { it.packageName to it.goalTime }
    }

    val displayAppList = remember(appList, goalMap) {
        appList.map { app ->
            app.copy(goalTime = goalMap[app.packageName] ?: app.goalTime)
        }
    }

    // ------------------- 정렬 토글(버튼 1개로 순환) -------------------
    var sortMode by rememberSaveable { mutableStateOf(SortMode.USAGE_DESC) }

    var sortMenuExpanded by remember { mutableStateOf(false) }

    val sortedDisplayAppList = remember(displayAppList, sortMode) {
        when (sortMode) {
            SortMode.USAGE_DESC ->
                displayAppList.sortedByDescending { it.currentUsage }

            SortMode.NAME_ASC ->
                displayAppList.sortedBy { it.appLabel.lowercase() }

            SortMode.OVER_RATIO_DESC ->
                displayAppList.sortedWith(
                    compareByDescending<AppUsage> {
                        if (it.goalTime > 0) it.currentUsage.toFloat() / it.goalTime.toFloat() else 0f
                    }.thenByDescending { it.currentUsage }
                )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 오늘 날짜
        item {
            val dateFormat = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREAN)
            Text(text = dateFormat.format(Date()), fontSize = 16.sp, color = Color.Gray)
        }

        // ⭐ 앱 선택 화면으로 이동하는 버튼 (목표 시간 설정 버튼 위)
        item {
            Button(
                onClick = { onAppSelectionClick() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("앱 선택하기")
            }
            Button(
                onClick = onAppDeleteClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) { Text("앱 삭제하기") }
        }

        // 목표 시간 설정 버튼
        item {
            Button(
                onClick = onGoalSettingClick,
                modifier = Modifier.fillMaxWidth()
            ) { Text("목표 시간 설정") }
        }
        // 시각화 거품 뷰 + 전체 사용시간
        item {
            VisualSummaryCard(
                appList = sortedDisplayAppList,
                totalUsage = totalUsage.first
            )
        }

        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(sortMode.buttonLabel) // 현재 선택된 정렬 표시
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    SortMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.buttonLabel) },
                            onClick = {
                                sortMode = mode
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 개별 앱 카드 리스트 → displayAppList 사용
        items(
            sortedDisplayAppList,
            key = { it.packageName }
        ) { app ->
            AppUsageCard(app)
        }
    }

}

@Composable
fun VisualNotification(appList: List<AppUsage>) {
    val appsWithUsage = appList.filter { it.currentUsage > 0 }
    if (appsWithUsage.isEmpty()) return

    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        VisualNotificationContent(appList)
    }
}

@Composable
private fun VisualNotificationContent(appList: List<AppUsage>) {
    val appsWithUsage = appList.filter { it.currentUsage > 0 }
    val maxUsage = appsWithUsage.maxOfOrNull { it.currentUsage }?.toFloat() ?: 1f

    if (appsWithUsage.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .horizontalScroll(rememberScrollState())
            .padding(24.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        appsWithUsage.forEach { app ->
            val size = (40 + (app.currentUsage / maxUsage) * 100).dp

            if (app.icon != null) {
                AsyncImage(
                    model = app.icon,
                    contentDescription = app.appLabel,
                    modifier = Modifier.size(size)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(size)
                        .background(Color.Gray)
                )
            }
        }
    }
}

@Composable
private fun VisualSummaryCard(appList: List<AppUsage>, totalUsage: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            VisualNotificationContent(appList)
            Divider()
            TotalProgressContent(totalUsage)
        }
    }
}

@Composable
fun TotalProgress(totalUsage: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        TotalProgressContent(totalUsage)
    }
}

@Composable
private fun TotalProgressContent(totalUsage: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("총 사용시간", fontSize = 14.sp, color = Color.Gray)
        Text(
            text = formatTime(totalUsage),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatTime(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return String.format("%d시간 %02d분", hours, mins)
}
