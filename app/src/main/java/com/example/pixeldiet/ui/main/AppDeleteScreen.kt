package com.example.pixeldiet.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pixeldiet.model.AppUsage
import com.example.pixeldiet.viewmodel.SharedViewModel
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDeleteScreen(
    viewModel: SharedViewModel,
    onDone: () -> Unit
) {
    val allApps by viewModel.appUsageListFlow.collectAsState()
    val trackedPackages by viewModel.trackedPackagesFlow.collectAsState()

    // ✅ 삭제 화면에서는 "추적 중인 앱"만 보여주기
    val deleteList = remember(allApps, trackedPackages) {
        if (trackedPackages.isEmpty()) emptyList()
        else allApps
            .filter { it.packageName in trackedPackages }
            .sortedByDescending { it.currentUsage } // 사용시간 기준 정렬(원하면 변경)
    }

    // 삭제 확인 다이얼로그용 상태
    var pendingDelete by remember { mutableStateOf<AppUsage?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("추적 앱 삭제") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    TextButton(onClick = onDone) { Text("완료") }
                }
            )
        }
    ) { innerPadding ->

        if (deleteList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("삭제할 추적 앱이 없습니다.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = deleteList,
                    key = { it.packageName }
                ) { app ->
                    AppDeleteRow(
                        app = app,
                        onDeleteClick = { pendingDelete = app }
                    )
                }
            }
        }

        // ✅ 삭제 확인 다이얼로그
        val target = pendingDelete
        if (target != null) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("삭제하시겠습니까?") },
                text = { Text("‘${target.appLabel}’을(를) 추적 앱에서 삭제합니다.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteTrackedApp(target.packageName)
                            pendingDelete = null
                        }
                    ) { Text("삭제") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("취소") }
                }
            )
        }
    }
}

@Composable
private fun AppDeleteRow(
    app: AppUsage,
    onDeleteClick: () -> Unit
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

            // 휴지통 버튼
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = "삭제")
            }
        }
    }
}
