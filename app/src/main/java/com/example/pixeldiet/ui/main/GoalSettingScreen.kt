package com.example.pixeldiet.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pixeldiet.model.AppUsage
import com.example.pixeldiet.viewmodel.SharedViewModel
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalSettingScreen(
    viewModel: SharedViewModel,
    onDone: () -> Unit
) {
    val allApps by viewModel.appUsageListFlow.collectAsState()
    val trackedPackages by viewModel.trackedPackagesFlow.collectAsState()

    // ✅ 추적 중인 앱만 뽑아서 목표 설정 리스트로 사용
    val goalApps = remember(allApps, trackedPackages) {
        allApps
            .filter { trackedPackages.isEmpty() || it.packageName in trackedPackages }
            .sortedBy { it.appLabel.lowercase() }
    }

    // ✅ 입력 상태: packageName -> (hoursText, minutesText)
    val inputs = remember(goalApps) {
        mutableStateMapOf<String, Pair<String, String>>().apply {
            goalApps.forEach { app ->
                val (h, m) = minutesToHm(app.goalTime)
                this[app.packageName] = h.toString() to m.toString()
            }
        }
    }

    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("목표 시간 설정") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.padding(16.dp)) {
                    saveError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                    }

                    Button(
                        onClick = {
                            // 저장 로직: 입력값 -> Map<pkg, minutes> 만들기
                            saving = true
                            saveError = null

                            val newGoals: Map<String, Int> = goalApps.associate { app ->
                                val pair = inputs[app.packageName] ?: ("0" to "0")
                                val minutes = sanitizeMinutes(pair.first, pair.second)
                                app.packageName to minutes
                            }

                            // ✅ VM에서 이미 목표 저장 함수가 준비돼 있음
                            // - tracked_apps 갱신 + goalTime 갱신 + overallGoalMinutes 갱신 + refreshDataInternal까지 수행
                            viewModel.saveTrackedAppsWithGoals(newGoals)
                            viewModel.uploadDailyGoalToFirebase(newGoals)

                            saving = false
                            onDone()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving && goalApps.isNotEmpty()
                    ) {
                        Text(if (saving) "저장 중..." else "저장")
                    }
                }
            }
        }
    ) { innerPadding ->

        if (goalApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("목표 시간을 설정할 앱이 없어")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = goalApps, key = { it.packageName }) { app ->
                    GoalSettingRow(
                        app = app,
                        hoursText = inputs[app.packageName]?.first ?: "0",
                        minutesText = inputs[app.packageName]?.second ?: "0",
                        onHoursChange = { newH ->
                            inputs[app.packageName] = (newH.filterDigitsMaxLen(2)) to (inputs[app.packageName]?.second ?: "0")
                        },
                        onMinutesChange = { newM ->
                            inputs[app.packageName] = (inputs[app.packageName]?.first ?: "0") to (newM.filterDigitsMaxLen(2))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalSettingRow(
    app: AppUsage,
    hoursText: String,
    minutesText: String,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 앱 아이콘
            if (app.icon != null) {
                AsyncImage(
                    model = app.icon,
                    contentDescription = app.appLabel,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Gray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", color = Color.White, fontSize = 20.sp)
                }
            }

            Spacer(Modifier.width(12.dp))

            // 앱 이름
            Text(
                text = app.appLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            // 시간/분 입력
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = onHoursChange,
                    modifier = Modifier.width(76.dp),
                    singleLine = true,
                    label = { Text("시간") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = onMinutesChange,
                    modifier = Modifier.width(76.dp),
                    singleLine = true,
                    label = { Text("분") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
    }
}

/** minutes -> (hours, minutes) */
private fun minutesToHm(totalMinutes: Int): Pair<Int, Int> {
    val m = max(0, totalMinutes)
    return (m / 60) to (m % 60)
}

/** 입력 문자열을 안전하게 minutes로 변환 */
private fun sanitizeMinutes(hoursText: String, minutesText: String): Int {
    val h = hoursText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val m = minutesText.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return h * 60 + m
}

private fun String.filterDigitsMaxLen(maxLen: Int): String =
    this.filter { it.isDigit() }.take(maxLen)
