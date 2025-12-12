package com.example.pixeldiet.friend

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddFriendDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var uid by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("친구 추가") },
        text = {
            Column {
                Text("추가할 친구의 UID를 입력하세요")
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = uid,
                    onValueChange = { uid = it },
                    placeholder = { Text("UID 입력") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (uid.isNotBlank()) {
                        onConfirm(uid.trim())
                        onDismiss() // 요청 보내고 Dialog 닫기
                    }
                }
            ) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("취소")
            }
        }
    )
}