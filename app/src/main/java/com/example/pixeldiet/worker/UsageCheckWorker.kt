package com.example.pixeldiet.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * ✅ DEPRECATED / DISABLED
 * Foreground Service(UsageTrackerService)로 사용시간 측정 + 알림/진행바를 전담하도록 변경했음.
 *
 * - 과거 WorkManager에 등록된 작업이 남아있더라도,
 *   이 Worker는 아무 작업도 하지 않고 success로 종료되게 해서
 *   중복 측정/중복 알림/중복 업로드를 방지한다.
 */
class UsageCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return Result.success()
    }
}
