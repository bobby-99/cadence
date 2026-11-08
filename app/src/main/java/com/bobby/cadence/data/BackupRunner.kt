package com.bobby.cadence.data

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bobby.cadence.CadenceApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object BackupRunner {
    private const val WORK = "cadence_auto_backup"

    /** Call whenever settings may have changed. Safe to call repeatedly. */
    fun schedule(ctx: Context, s: AppSettings) {
        try {
            val wm = WorkManager.getInstance(ctx)
            if (!s.backupEnabled || s.backupFolderUri.isBlank()) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val req = PeriodicWorkRequestBuilder<BackupWorker>(s.backupEveryDays.coerceAtLeast(1).toLong(), TimeUnit.DAYS).build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        } catch (e: Exception) {
        }
    }

    /** Runs one backup now. Records the result in settings so the Settings screen can show it. */
    suspend fun run(app: CadenceApp): Boolean = withContext(Dispatchers.IO) {
        val s = app.prefs.state.value
        val ok = if (s.backupFolderUri.isBlank()) false else try {
            BackupFolder.write(app, Uri.parse(s.backupFolderUri), app.repo.exportJson())
        } catch (e: Exception) {
            false
        }
        app.prefs.update {
            it.copy(
                lastBackupAt = if (ok) System.currentTimeMillis() else it.lastBackupAt,
                lastBackupMsg = if (ok) "" else "Could not write to the folder. Choose it again.",
            )
        }
        ok
    }
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CadenceApp
        return if (BackupRunner.run(app)) Result.success()
        else if (runAttemptCount >= 3) Result.failure()
        else Result.retry()
    }
}
