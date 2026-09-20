package com.example.bugtracker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = IssueRepository.getInstance(applicationContext)

        val allSynced = try {
            repository.syncAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

        return when {
            allSynced -> Result.success()
            runAttemptCount >= MAX_ATTEMPTS -> {
                repository.markUnsyncedAsFailed()
                Result.failure()
            }
            else -> Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "bugtracker_sync"
        private const val MAX_ATTEMPTS = 5

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}