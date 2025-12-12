package com.example.pixeldiet.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DailyUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.failure()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())

        // Room/SharedPreferences에서 목표 시간 가져오기
        val db = com.example.pixeldiet.data.DatabaseProvider.getDatabase(applicationContext)
        val trackedApps = db.trackedAppDao().getAllTrackedAppsOnce()  // suspend 함수 가정
        val goalsMap = trackedApps.associate { it.packageName to it.goalTime }

        // Firestore 업로드
        val data = mapOf(
            "date" to today,
            "appUsages" to goalsMap
        )

        try {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("goalHistory")
                .document(today)
                .set(data)
                .await()
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    companion object {
        fun scheduleDailyGoalUpload(context: Context) {
            val now = Calendar.getInstance()
            val nextMidnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val initialDelay = nextMidnight.timeInMillis - now.timeInMillis

            val workRequest = PeriodicWorkRequestBuilder<DailyUploadWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "DailyGoalUpload",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}