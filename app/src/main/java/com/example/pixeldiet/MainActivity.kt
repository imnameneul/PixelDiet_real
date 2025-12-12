package com.example.pixeldiet

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
//import androidx.lifecycle.Lifecycle
//import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
//import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ViewModelProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.example.pixeldiet.ui.navigation.AppNavigation
import com.example.pixeldiet.ui.notification.NotificationHelper
import com.example.pixeldiet.ui.theme.PixelDietTheme
import com.example.pixeldiet.viewmodel.SharedViewModel
import com.example.pixeldiet.worker.UsageCheckWorker
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import androidx.lifecycle.ProcessLifecycleOwner
//import androidx.lifecycle.lifecycleScope
//import com.example.pixeldiet.backup.BackupManager
import com.example.pixeldiet.backup.DailyUploadWorker
//import com.example.pixeldiet.data.AppDatabase
//import com.example.pixeldiet.data.DailyUsageDao
//import com.example.pixeldiet.data.GroupDao
//import com.example.pixeldiet.data.TrackedAppDao
//import com.example.pixeldiet.data.UserProfileDao
//import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    private val viewModel: SharedViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    // 알림 권한 요청 런처
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 권한 허용됨 -> WorkManager 시작
            startUsageCheckWorker()
        } else {
            // 권한 거부됨
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DailyUploadWorker.scheduleDailyGoalUpload(this)
        // ⭐️ 1. [순서 변경] "사용 시간 권한"을 묻기 전에, "알림 채널"부터 만든다!
        //    (이래야 시스템이 스위치를 안 막습니다)
        NotificationHelper.createNotificationChannel(this)

        // ⭐️ 2. [순서 변경] "알림 권한"을 먼저 물어봅니다.
        checkNotificationPermission()

        // ⭐️ 3. "사용 시간 권한"은 그 다음에 확인합니다.
        checkUsageStatsPermission()

        setContent {
            PixelDietTheme {
                AppNavigation()
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
    }

    override fun onResume() {
        super.onResume()
        if (hasUsageStatsPermission()) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                viewModel.refreshData()
            }
        }
    }

    // 알림 권한 확인 및 요청 함수
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 (API 33) 이상
            // 런처를 통해 알림 권한 팝업을 띄움
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Android 12 이하는 권한이 자동 허용
            startUsageCheckWorker()
        }
    }

    // ⭐️ [이름 변경] 알림 및 워커 설정 함수 -> 워커 "시작" 함수로 변경
    private fun startUsageCheckWorker() {
        // 15분마다 실행될 백그라운드 작업 생성
        val usageCheckWorkRequest =
            PeriodicWorkRequest.Builder(UsageCheckWorker::class.java, 15, TimeUnit.MINUTES)
                .build()

        // WorkManager에 "UsageCheck"라는 고유 이름으로 작업 등록
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UsageCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            usageCheckWorkRequest
        )
    }

    // --- 사용 시간 권한 확인 로직 (동일) ---
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            AlertDialog.Builder(this)
                .setTitle("권한 필요")
                .setMessage("앱 사용 시간 정보를 가져오기 위해 '사용 정보 접근' 권한이 필요합니다.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton("취소") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    inner class AppLifecycleObserver : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            super.onStop(owner)
            Log.d("MainActivity", "앱이 백그라운드로 이동했습니다. 백업 실행")
            viewModel.uploadDailyUsageToFirebase()
        }
    }
}