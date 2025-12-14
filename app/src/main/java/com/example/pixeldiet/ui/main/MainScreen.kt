package com.example.pixeldiet.ui.main

import android.util.Log
import android.widget.Toast
import com.example.pixeldiet.model.AppUsage
import com.example.pixeldiet.ui.common.progressUi

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
    USAGE_DESC("정렬: 사용시간순"),
    NAME_ASC("정렬: 이름순"),
    OVER_RATIO_DESC("정렬: 목표초과순")
}

private fun SortMode.next(): SortMode = when (this) {
    SortMode.USAGE_DESC -> SortMode.NAME_ASC
    SortMode.NAME_ASC -> SortMode.OVER_RATIO_DESC
    SortMode.OVER_RATIO_DESC -> SortMode.USAGE_DESC
}

@Composable
fun MainScreen(
    viewModel: SharedViewModel,              // ✅ 기본값 제거
    onAppSelectionClick: () -> Unit          // ✅ 기본값 제거 (항상 넘겨주기)
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

    var showGoalDialog by remember { mutableStateOf(false) }
    val displayAppList = trackedPackagesWithGoals.map { tracked ->
        val usage = appList.find { it.packageName == tracked.packageName }
        if (usage != null) {
            usage.copy(goalTime = tracked.goalTime)
        } else {
            val appInfo = try { pm.getApplicationInfo(tracked.packageName, 0) } catch (e: Exception) { null }
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: tracked.packageName
            val icon = appInfo?.let { try { pm.getApplicationIcon(it) } catch(e: Exception){ null } }
            AppUsage(
                packageName = tracked.packageName,
                appLabel = label,
                icon = icon,
                currentUsage = 0,
                goalTime = tracked.goalTime,
                streak = 0
            )
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
                Text("추적할 앱 선택하기")
            }
        }

        // 목표 시간 설정 버튼
        item {
            Button(
                onClick = { showGoalDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("목표 시간 설정")
            }
        }
        // 시각화 거품 뷰
        item {
            VisualNotification(
                sortedDisplayAppList
            )
        }

        // 전체 사용 시간/목표 프로그레스
        item {
            TotalProgress(totalUsage.first, totalUsage.second)
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

    if (showGoalDialog) {
        GoalSettingDialog(
            appList = displayAppList,     // 추적앱 기준
            overallGoal = overallGoal,    // 🔹 전체 목표시간 전달
            onDismiss = { showGoalDialog = false },
            onSave = { newGoals: Map<String, Int>, totalGoalMinutes: Int? ->
                // 1️⃣ 앱별 목표시간 DB + ViewModel 상태 한 번에 갱신
                viewModel.saveTrackedAppsWithGoals(newGoals)
                viewModel.uploadDailyGoalToFirebase(newGoals)

                // 2️⃣ 전체 목표시간 업데이트
                viewModel.setOverallGoal(totalGoalMinutes)

                showGoalDialog = false
            }
        )
    }

}

@Composable
fun VisualNotification(appList: List<AppUsage>) {
    val appsWithUsage = appList.filter { it.currentUsage > 0 }
    val maxUsage = appsWithUsage.maxOfOrNull { it.currentUsage }?.toFloat() ?: 1f

    if (appsWithUsage.isEmpty()) return

    Card(elevation = CardDefaults.cardElevation(2.dp)) {
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
                    // 앱 아이콘을 거품 크기만큼 표시
                    AsyncImage(
                        model = app.icon,
                        contentDescription = app.appLabel,
                        modifier = Modifier.size(size)
                    )
                } else {
                    // 아이콘 없으면 단색 박스 폴백
                    Box(
                        modifier = Modifier
                            .size(size)
                            .background(Color.Gray)
                    )
                }
            }
        }
    }
}

@Composable
fun TotalProgress(totalUsage: Int, totalGoal: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("총 사용시간", fontSize = 14.sp, color = Color.Gray)
                Row {
                    Text(
                        formatTime(totalUsage),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Text(
                        "목표 ${formatTime(totalGoal)}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val ui = progressUi(totalUsage, totalGoal)

            LinearProgressIndicator(
                progress = { ui.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = ui.color,
                trackColor = Color.LightGray
            )
        }
    }
}

@Composable
fun GoalSettingDialog(
    appList: List<AppUsage>,
    overallGoal: Int?,                          // 🔹 전체 목표시간 (null이면 없음)
    onDismiss: () -> Unit,
    onSave: (Map<String, Int>, Int?) -> Unit   // 🔹 (앱별 목표, 전체 목표)
) {
    // app.packageName -> (시간, 분) 문자열 상태
    val goalStates = remember(appList) {
        mutableStateMapOf<String, Pair<String, String>>().apply {
            appList.forEach { app ->
                val currentMinutes = app.goalTime
                val hours = (currentMinutes / 60).toString()
                val minutes = (currentMinutes % 60).toString()
                put(app.packageName, hours to minutes)
            }
        }
    }

    // 🔹 전체 목표시간 초기값 (분 단위)
    val initialTotalMinutes: Int? = overallGoal
        ?: appList.sumOf { it.goalTime }.takeIf { it > 0 }

    // 🔹 초기값을 시/분으로 분해
    val initialHours = initialTotalMinutes?.div(60) ?: 0
    val initialMinutes = initialTotalMinutes?.rem(60) ?: 0

    var totalGoalHoursText by remember(appList, overallGoal) {
        mutableStateOf(
            if (initialTotalMinutes != null) initialHours.toString() else ""
        )
    }

    var totalGoalMinutesText by remember(appList, overallGoal) {
        mutableStateOf(
            if (initialTotalMinutes != null) initialMinutes.toString() else ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("목표 시간 설정") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(appList, key = { it.packageName }) { app ->
                    val pkg = app.packageName
                    val (hours, minutes) = goalStates[pkg] ?: ("0" to "0")

                    Text(
                        app.appLabel,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hours,
                            onValueChange = { new ->
                                goalStates[pkg] = new.filter { it.isDigit() } to minutes
                            },
                            label = { Text("시간") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { new ->
                                goalStates[pkg] = hours to new.filter { it.isDigit() }
                            },
                            label = { Text("분") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 🔹 전체 목표시간 입력 블록 추가
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "전체 목표시간 (선택사항)",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = totalGoalHoursText,
                            onValueChange = { new ->
                                totalGoalHoursText = new.filter { it.isDigit() }
                            },
                            label = { Text("시간") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = totalGoalMinutesText,
                            onValueChange = { new ->
                                totalGoalMinutesText = new.filter { it.isDigit() }
                            },
                            label = { Text("분") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "둘 다 비워두면 앱별 목표시간 합계를 전체 목표로 사용합니다.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val newGoals: Map<String, Int> = goalStates.mapValues { (_, hm) ->
                    val h = hm.first.toIntOrNull() ?: 0
                    val m = hm.second.toIntOrNull() ?: 0
                    h * 60 + m
                }

                // 🔹 전체 목표시간 계산
                val h = totalGoalHoursText.toIntOrNull()
                val m = totalGoalMinutesText.toIntOrNull()

                val totalGoalMinutes: Int? = if (h == null && m == null) {
                    // 둘 다 비어 있으면 → null (SharedViewModel에서 자동 합산)
                    null
                } else {
                    (h ?: 0) * 60 + (m ?: 0)
                }

                onSave(newGoals, totalGoalMinutes)
            }) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

private fun formatTime(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return String.format("%d시간 %02d분", hours, mins)
}
