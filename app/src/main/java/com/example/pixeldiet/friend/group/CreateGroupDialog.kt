package com.example.pixeldiet.friend.group

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pixeldiet.data.TrackedAppEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupDialog(
    appList: List<TrackedAppEntity>,  // Room DB에서 가져온 앱 리스트
    onConfirm: (groupName: String, selectedAppId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var groupName by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<TrackedAppEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("그룹 생성") },
        text = {
            Column {
                Text("새로운 그룹 이름을 입력하세요.")
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = { Text("그룹 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Text("앱 선택")
                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(appList) { app ->
                        val appLabel = getAppLabel(context, app.packageName)
                        val appIcon = getAppIcon(context, app.packageName)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedApp = app }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedApp == app,
                                onClick = { selectedApp = app }
                            )
                            Spacer(Modifier.width(8.dp))

                            // 아이콘 표시
                            if (appIcon != null) {
                                Image(
                                    painter = appIcon.toPainter(),
                                    contentDescription = appLabel,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }

                            Text(appLabel)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (groupName.isNotBlank() && selectedApp != null) {
                    onConfirm(groupName.trim(), selectedApp!!.packageName)
                }
            }) {
                Text("생성")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

// Drawable -> Painter 변환
fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) {
        return bitmap
    }

    val width = intrinsicWidth.takeIf { it > 0 } ?: 1
    val height = intrinsicHeight.takeIf { it > 0 } ?: 1
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

@Composable
fun Drawable.toPainter(): Painter {
    return BitmapPainter(this.toBitmap().asImageBitmap())
}

// 앱 이름 가져오기
fun getAppLabel(context: Context, packageName: String): String {
    val pm = context.packageManager
    return try {
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        packageName
    }
}

// 앱 아이콘 가져오기
fun getAppIcon(context: Context, packageName: String): Drawable? {
    val pm = context.packageManager
    return try {
        pm.getApplicationIcon(packageName)
    } catch (e: Exception) {
        null
    }
}
