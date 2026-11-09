package com.bobby.cadence.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bobby.cadence.CadenceApp
import java.util.concurrent.TimeUnit

/**
 * Safety net that runs about every 30 minutes: re-arms every reminder alarm and shows any reminder
 * that should have appeared but did not. Some phone makers drop alarms when an app is swiped away;
 * this brings them back within half an hour even if the app is never opened.
 */
class RescheduleWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CadenceApp
        return try {
            app.repo.deliverMissed()
            app.repo.rescheduleAll()
            com.bobby.cadence.data.SyncRunner.run(app)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK = "cadence_reschedule"

        fun enqueue(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RescheduleWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
