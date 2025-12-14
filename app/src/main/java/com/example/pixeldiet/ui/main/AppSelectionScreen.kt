package com.example.pixeldiet.ui.main

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pixeldiet.viewmodel.SharedViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pixeldiet.data.TrackedAppEntity

// 설치된 앱 정보를 담는 로컬 데이터 클래스
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(
    viewModel: SharedViewModel,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    // 현재 추적 중인 패키지들
    val trackedPackages by viewModel.trackedPackagesFlow.collectAsState()
    val trackedApps by viewModel.trackedAppsFlow.collectAsState()

    // ⭐ 검색어 상태 추가
    var searchQuery by remember { mutableStateOf("") }

    // 설치된 앱 로드 (한 번만 로드)
    val installedApps by remember {
        mutableStateOf(
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .filter { it.packageName != context.packageName }
                .map { appInfo ->
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = try { pm.getApplicationIcon(appInfo.packageName) } catch (e: Exception) { null }
                    InstalledApp(appInfo.packageName, label, icon)
                }
                .sortedBy { it.label.lowercase() }
        )
    }

    // ⭐ 검색어에 따라 필터링된 목록 생성
    val filteredApps = if (searchQuery.isEmpty()) {
        installedApps
    } else {
        installedApps.filter {
            it.label.contains(searchQuery, ignoreCase = true)
        }
    }

    // 선택 상태: 처음에는 기존 추적앱으로 초기화
    var selectedPackages by remember { mutableStateOf(mutableSetOf<String>()) }
    LaunchedEffect(trackedPackages) {
        selectedPackages = trackedPackages.toMutableSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("추적할 앱 선택", fontWeight = FontWeight.Bold) }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { onDone() }) {
                    Text("취소")
                }
                Button(onClick = {
                    // 기존 목표시간 유지하면서 선택한 앱만 업데이트
                    val selectedAppsWithGoals = selectedPackages.associateWith { pkg ->
                        trackedApps.find { it.packageName == pkg }?.goalTime ?: 0
                    }

                    viewModel.updateTrackedPackages(selectedPackages)
                    viewModel.saveTrackedAppsWithGoals(selectedAppsWithGoals)
                    onDone()
                }) { Text("저장") }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // 안내 문구
            Text(
                text = "휴대폰에 설치된 앱 중에서\n사용시간을 추적할 앱을 선택해 주세요.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )

            // ⭐ 검색창 (Search Bar) 추가
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("앱 이름 검색...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // 앱 목록 그리드 (필터링된 목록 사용)
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ⭐ filteredApps 사용
                items(filteredApps, key = { it.packageName }) { app ->
                    val isSelected = app.packageName in selectedPackages

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(64.dp)
                            .clickable {
                                selectedPackages = selectedPackages.toMutableSet().apply {
                                    if (isSelected) remove(app.packageName)
                                    else add(app.packageName)
                                }
                            }
                            .then(
                                if (isSelected)
                                    Modifier.background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = MaterialTheme.shapes.small
                                    )
                                else Modifier
                            )
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            border = if (isSelected)
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            else
                                BorderStroke(1.dp, Color.LightGray),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (app.icon != null) {
                                    AsyncImage(
                                        model = app.icon,
                                        contentDescription = app.label,
                                        modifier = Modifier.size(36.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.Gray),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = app.label,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            maxLines = 1,
                            // 긴 이름은 말줄임표 처리
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}