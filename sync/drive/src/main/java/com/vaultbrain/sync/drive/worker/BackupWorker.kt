package com.vaultbrain.sync.drive.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaultbrain.sync.drive.DriveBackupManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that triggers a periodic encrypted backup to Google Drive.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val driveBackupManager: DriveBackupManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!driveBackupManager.isSignedIn()) {
            return Result.failure()
        }

        return try {
            val result = driveBackupManager.requestBackup()
            if (result.isSuccess && result.getOrThrow()) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
