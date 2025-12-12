package com.example.pixeldiet.ui.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pixeldiet.model.AppUsage
import com.example.pixeldiet.ui.common.WrappedBarChart
import com.example.pixeldiet.ui.common.WrappedMaterialCalendar
import com.example.pixeldiet.viewmodel.SharedViewModel
import com.prolificinteractive.materialcalendarview.CalendarDay



import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pixeldiet.friend.group.toBitmap

import kotlinx.coroutines.launch

// ----------------------
// 캘린더 데코레이터
// ----------------------
@Composable
fun CalendarScreen(viewModel: SharedViewModel = viewModel()) {
    val decoratorData by viewModel.calendarDecoratorDataFlow.collectAsState(initial = emptyList())
    val statsText by viewModel.calendarStatsTextFlow.collectAsState(initial = "")
    val streakText by viewModel.streakTextFlow.collectAsState(initial = "")
    val chartData by viewModel.chartDataFlow.collectAsState(initial = emptyList())
    val goalMinutes by viewModel.calendarGoalTimeFlow.collectAsState(initial = 0)
    val appList by viewModel.appUsageListFlow.collectAsState(initial = emptyList())
    val trackedPackages by viewModel.trackedPackagesFlow.collectAsState(initial = emptySet())
    val selectedFilterLabel by viewModel.selectedFilterTextFlow.collectAsState(initial = "전체")
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf<CalendarDay?>(null) }
    var showDailyDetail by remember { mutableStateOf(false) }
    val dailyDetail by viewModel.dailyDetailFlow.collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FilterSpinner(
                appList = appList,
                trackedPackages = trackedPackages,
                selectedLabel = selectedFilterLabel,
                onFilterSelected = { pkgOrNull -> viewModel.setCalendarFilter(pkgOrNull) }
            )
        }

        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                WrappedMaterialCalendar(
                    modifier = Modifier.fillMaxWidth(),
                    decoratorData = decoratorData,
                    onMonthChanged = { year, month -> viewModel.setSelectedMonth(year, month) },
                    onDateSelected = { date ->
                        selectedDate = date
                        showDailyDetail = true
                        viewModel.loadDailyDetail(date, context)
                    }
                )
            }
        }

        item {
            Text(streakText, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Text(statsText, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
        }

        item {
            Card(Modifier.fillMaxWidth().height(300.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("이번 달 사용 시간", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    WrappedBarChart(modifier = Modifier.fillMaxSize(), chartData = chartData, goalLine = goalMinutes.takeIf { it > 0 }?.toFloat())
                }
            }
        }
    }

    if (showDailyDetail && selectedDate != null) {
        AlertDialog(
            onDismissRequest = { showDailyDetail = false },
            title = { Text("${selectedDate!!.year}-${selectedDate!!.month}-${selectedDate!!.day}") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(dailyDetail) { (appUsage, goalTime) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            appUsage.icon?.let { icon ->
                                Image(
                                    bitmap = icon.toBitmap().asImageBitmap(),
                                    contentDescription = appUsage.appLabel,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(appUsage.appLabel, fontWeight = FontWeight.Bold)
                                val progress = if (goalTime > 0) (appUsage.currentUsage.toFloat() / goalTime).coerceAtMost(1f) else 0f
                                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(12.dp))
                                Text("${formatTime(appUsage.currentUsage)} / ${formatTime(goalTime)}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDailyDetail = false }) { Text("닫기") }
            }
        )
    }

}

// ----------------------
// 시간 포맷
// ----------------------
fun formatTime(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return buildString {
        if (hours > 0) append("${hours}시간 ")
        if (mins > 0 || hours == 0) append("${mins}분")
    }
}

// ----------------------
// 필터 스피너
// ----------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSpinner(
    appList: List<AppUsage>,
    trackedPackages: Set<String>,
    selectedLabel: String,
    onFilterSelected: (String?) -> Unit
) {
    val trackedApps by remember(appList, trackedPackages) {
        derivedStateOf { appList.filter { it.packageName in trackedPackages }.sortedBy { it.appLabel.lowercase() } }
    }

    val options: List<Pair<String?, String>> = remember(trackedApps) {
        buildList {
            add(null to "전체")
            trackedApps.forEach { app -> add(app.packageName to app.appLabel) }
        }
    }

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (pkg, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; onFilterSelected(pkg) })
            }
        }
    }
}