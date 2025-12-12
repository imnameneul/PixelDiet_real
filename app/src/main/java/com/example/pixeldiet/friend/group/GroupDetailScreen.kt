package com.example.pixeldiet.friend.group

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pixeldiet.friend.FriendRecord
import com.example.pixeldiet.ui.friend.FriendViewModel
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun GroupDetailScreen(
    viewModel: FriendViewModel,
    groupviewModel: GroupViewModel
) {
    val context = LocalContext.current
    val group by groupviewModel.selectedGroup.collectAsState()
    val members by groupviewModel.groupMembers.collectAsState()
    val showAddDialog by groupviewModel.showAddMemberDialog.collectAsState()
    val showGoalDialog by groupviewModel.showGoalDialog.collectAsState() // GoalSettingDialog 표시 상태
    val selectedApp by groupviewModel.selectedApp.collectAsState()
    val goalMinutes by groupviewModel.goalMinutes.collectAsState()

    // 그룹 정보 초기 로딩
    LaunchedEffect(group) {
        group?.let { g ->
            groupviewModel.loadSelectedApp(g.groupId)
            groupviewModel.getGoalMinutes(g.groupId)
            groupviewModel.loadGroupMembers(g.groupId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        group?.let { g ->

            // 🔥 그룹 이름 + 앱 아이콘
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    val appIcon = selectedApp?.let { getAppIcon(context, it) }
                    if (appIcon != null) {
                        Image(
                            painter = appIcon.toPainter(),
                            contentDescription = "앱 아이콘",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.LightGray)
                        )
                        Spacer(Modifier.width(12.dp))
                    }

                    Text(
                        text = g.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 🔥 목표 시간 설정 버튼
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { groupviewModel.openGoalDialog() },
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    val hours = goalMinutes / 60
                    val minutes = goalMinutes % 60
                    Text("목표 시간: ${hours}시간 ${minutes}분", fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Text("수정", color = Color.Blue, fontSize = 14.sp)
                }
            }


            // 🔥 멤버 리스트
            Text(
                "멤버 목록",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                val sortedMembers = members.sortedBy { it.usage }
                itemsIndexed(sortedMembers) { index, member ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}등", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    member.name.firstOrNull()?.uppercase() ?: "",
                                    color = Color.White
                                )
                            }

                            Spacer(Modifier.width(12.dp))
                            Text(member.name, fontSize = 16.sp)
                            Spacer(Modifier.weight(1f))
                            Text("${member.usage}분", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }


            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { groupviewModel.openAddMemberDialog() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("멤버 추가")
            }
        }
    }

    // 🔥 멤버 추가 다이얼로그
    if (showAddDialog) {
        AddMemberDialog(
            groupViewModel = groupviewModel,
            friendViewModel = viewModel,
            group = group!!,
            onDismiss = { groupviewModel.closeAddMemberDialog() }
        )
    }

    // 🔥 목표 시간 설정 다이얼로그
    if (showGoalDialog && group != null) {
        GoalTimeDialog(
            initialMinutes = goalMinutes,
            onDismiss = { groupviewModel.closeGoalDialog() },
            onSave = { totalMinutes ->
                groupviewModel.updateGoalMinutes(group!!.groupId, totalMinutes)
                groupviewModel.closeGoalDialog()
            }
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberDialog(
    groupViewModel: GroupViewModel,
    friendViewModel: FriendViewModel,
    group: GroupRecord,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // 친구 목록 Flow 구독
    val friends by friendViewModel.friendList.collectAsState(initial = emptyList())
    val selectedIds = remember { mutableStateListOf<String>() } // 선택된 친구 ID

    // 친구 목록 불러오기
    LaunchedEffect(Unit) {
        friendViewModel.loadFriends() // Room DB에서 친구 목록 가져오기
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("멤버 추가") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(friends) { friend ->
                    val isSelected = selectedIds.contains(friend.uid)
                    Log.d("AddMemberDialog", "Displaying friend: ${friend.name} (${friend.uid})")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) selectedIds.remove(friend.uid)
                                else selectedIds.add(friend.uid)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = {
                                if (it) selectedIds.add(friend.uid)
                                else selectedIds.remove(friend.uid)
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(friend.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedIds.isNotEmpty()) {
                        groupViewModel.addSelectedMembers(group.groupId, selectedIds.toList())
                        onDismiss()
                    }
                }
            ) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
@Composable
fun GoalTimeDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var hoursInput by remember { mutableStateOf((initialMinutes / 60).toString()) }
    var minutesInput by remember { mutableStateOf((initialMinutes % 60).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("목표 시간 설정") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = hoursInput,
                    onValueChange = { hoursInput = it.filter { c -> c.isDigit() } },
                    label = { Text("시간") },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.width(8.dp))
                Text("시간")
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = minutesInput,
                    onValueChange = { minutesInput = it.filter { c -> c.isDigit() } },
                    label = { Text("분") },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.width(8.dp))
                Text("분")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = hoursInput.toIntOrNull() ?: 0
                val m = minutesInput.toIntOrNull() ?: 0
                val totalMinutes = h * 60 + m
                onSave(totalMinutes)
            }) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}