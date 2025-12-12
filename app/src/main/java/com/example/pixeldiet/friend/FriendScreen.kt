package com.example.pixeldiet.ui.friend

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.pixeldiet.data.DatabaseProvider
import com.example.pixeldiet.data.TrackedAppEntity
import com.example.pixeldiet.friend.AddFriendDialog
import com.example.pixeldiet.friend.FriendRecord
import com.example.pixeldiet.friend.FriendRepository
import com.example.pixeldiet.friend.FriendViewModelFactory
import com.example.pixeldiet.friend.group.CreateGroupDialog
import com.example.pixeldiet.friend.group.GroupDetailScreen
import com.example.pixeldiet.friend.group.GroupRecord
import com.example.pixeldiet.friend.group.GroupRepository
import com.example.pixeldiet.friend.group.GroupViewModel
import com.example.pixeldiet.friend.group.GroupViewModelFactory
import com.example.pixeldiet.friend.group.getAppIcon
import com.example.pixeldiet.friend.group.getAppLabel
import com.example.pixeldiet.friend.group.toPainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image

@Composable
fun FriendScreen(
    navController: NavHostController,
    viewModel: FriendViewModel,
    groupViewModel: GroupViewModel
) {
    FriendScreenContent(
        viewModel = viewModel,
        groupViewModel = groupViewModel,
        navController = navController
    )
}

@Composable
fun FriendScreenContent(
    viewModel: FriendViewModel,
    groupViewModel: GroupViewModel,
    navController: NavHostController
) {
    val friendRequestsReceived by viewModel.friendRequestsReceived.collectAsState()
    val friendRequestsSent by viewModel.friendRequestsSent.collectAsState()
    val friendList by viewModel.friendList.collectAsState(initial = emptyList())
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val groupList by groupViewModel.groupList.collectAsState()
    val showCreateDialog by groupViewModel.showCreateDialog.collectAsState()
    val appList by groupViewModel.appList.collectAsState()
    val appMap = appList.associateBy { it.packageName }


    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        groupViewModel.loadMyGroups()
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ----------------------------
        // 1. 친구 리스트
        // ----------------------------
        item {
            Text("내 친구들", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (friendList.isEmpty()) {
            item {
                Text(
                    "아직 친구가 없습니다.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            items(friendList) { friend ->
                FriendCard(
                    friend = friend,
                    onRemove = { viewModel.removeFriend(friend.uid) }
                )
            }
        }

        // ----------------------------
        // 2. 친구 추가 버튼
        // ----------------------------
        item {
            Button(
                onClick = { viewModel.openAddDialog() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("친구 추가")
            }
        }

        // ----------------------------
        // 3. 받은 친구 요청
        // ----------------------------
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "받은 친구 요청",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (friendRequestsReceived.isEmpty()) {
                        Text(
                            "받은 요청이 없습니다.",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        friendRequestsReceived.forEach { request ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(request.fromName, fontSize = 16.sp)

                                Button(
                                    onClick = {
                                        viewModel.acceptFriendRequest(request) // 여기서 Repository 로직까지 한 번에 처리
                                    }
                                ) {
                                    Text("수락")
                                }
                            }
                            Divider()
                        }
                    }
                }
            }
        }

        // ----------------------------
        // 4. 보낸 친구 요청
        // ----------------------------
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "보낸 친구 요청",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (friendRequestsSent.isEmpty()) {
                        Text(
                            "보낸 요청이 없습니다.",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        friendRequestsSent.forEach { request ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(request.toName, fontSize = 16.sp)

                                Button(
                                    onClick = {
                                        viewModel.cancelFriendRequest(request)
                                    }
                                ) {
                                    Text("취소")
                                }
                            }
                            Divider()
                        }
                    }
                }
            }
        }
        // 내 그룹
        item {
            Text("내 그룹", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (groupList.isEmpty()) {
            item {
                Text(
                    "아직 그룹이 없습니다.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            items(groupList) { group ->
                val context = LocalContext.current

                // 앱 아이콘과 이름 가져오기
                val appPainter = group.appId?.let { packageName ->
                    getAppIcon(context, packageName)?.toPainter()
                }
                val appLabel = group.appId?.let { packageName ->
                    getAppLabel(context, packageName)
                }

                GroupCard(
                    group = group,
                    appLabel = appLabel,
                    appPainter = appPainter,
                    onOpen = {
                        groupViewModel.openGroup(group)
                        navController.navigate("groupDetail")
                    },
                    onLeave = { groupViewModel.leaveGroup(group) }
                )
            }
        }

        // 그룹 생성 버튼
        item {
            Button(
                onClick = { groupViewModel.openCreateGroupDialog() },
                modifier = Modifier.fillMaxWidth()

            ) {
                Text("그룹 생성")
            }
        }
    }

    if (showAddDialog) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        Log.d("DEBUG", "currentUser: $currentUser")

        AddFriendDialog(
            onConfirm = { uid ->
                viewModel.sendFriendRequest(uid)
                viewModel.closeAddDialog()
            },
            onDismiss = { viewModel.closeAddDialog() }
        )
    }
    if (showCreateDialog) {
        CreateGroupDialog(
            appList = appList, // ✅ 필수 인자 전달
            onConfirm = { groupName, appList ->
                groupViewModel.createGroup(groupName, appList)
                groupViewModel.closeCreateGroupDialog()
            },
            onDismiss = { groupViewModel.closeCreateGroupDialog() }
        )
    }

    // CreateGroupDialog 표시
    if (showCreateDialog) {
        CreateGroupDialog(
            appList = appList,
            onConfirm = { name, appId ->
                groupViewModel.createGroup(name, appId)
                groupViewModel.closeCreateGroupDialog()
                Log.d("GroupDebug", "group.appId=${appId}")
                Log.d("GroupDebug", "appMap keys=${appMap.keys.joinToString()}")
            },
            onDismiss = { groupViewModel.closeCreateGroupDialog() }
        )
    }
}

@Composable
fun FriendCard(friend: FriendRecord, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                if (!friend.photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = friend.photoUrl,
                        contentDescription = "친구 프로필",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "기본 프로필",
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.White
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(friend.name, fontSize = 16.sp)
        }
        TextButton(onClick = onRemove) { Text("삭제") }
    }
    Divider()
}

@Composable
fun GroupCard(
    group: GroupRecord,
    appLabel: String?,
    appPainter: Painter?,
    onOpen: () -> Unit,
    onLeave: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Gray, shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (appPainter != null) {
                    Image(
                        painter = appPainter,
                        contentDescription = appLabel,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Text(
                        text = appLabel ?: "앱 없음",
                        color = Color.White,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = onOpen) { Text("열기") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onLeave) { Text("나가기", color = Color.Red) }
                }
            }
        }
    }
}
