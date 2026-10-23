package com.example.razoproject

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PendingReportUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        CityCareApiService.loadApiBaseUrl(applicationContext)
        CityCareApiService.loadUserSession(applicationContext)

        return if (CityCareApiService.syncPendingReports(applicationContext)) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "citycare-pending-report-upload"
    }
}
