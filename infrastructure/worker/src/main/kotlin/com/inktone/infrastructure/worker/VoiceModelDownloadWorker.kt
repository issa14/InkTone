package com.inktone.infrastructure.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.inktone.domain.service.VoiceDownloadProgress
import com.inktone.domain.service.VoiceModelDownloadService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Télécharge et installe la voix neuronale (183 Mo) hors de tout écran (audit
 * UX A7). Au premier plan, avec une notification de progression : sans cela,
 * WorkManager borne l'exécution à une dizaine de minutes, trop peu pour ce
 * volume sur un réseau mobile.
 */
@HiltWorker
class VoiceModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadService: VoiceModelDownloadService,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { setForeground(foregroundInfo(bytes = 0, total = 0)) }
        var result: Result = Result.failure(workDataOf(KEY_ERROR to "Téléchargement interrompu."))
        var lastPercent = -1
        downloadService.downloadDefaultVoiceModel().collect { progress ->
            when (progress) {
                is VoiceDownloadProgress.InProgress -> {
                    setProgress(workDataOf(KEY_BYTES to progress.bytesDownloaded, KEY_TOTAL to progress.totalBytes))
                    val percent = if (progress.totalBytes > 0) (progress.bytesDownloaded * 100 / progress.totalBytes).toInt() else 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        runCatching { setForeground(foregroundInfo(progress.bytesDownloaded, progress.totalBytes)) }
                    }
                }
                VoiceDownloadProgress.Complete -> result = Result.success()
                is VoiceDownloadProgress.Failed -> result = Result.failure(workDataOf(KEY_ERROR to progress.message))
            }
        }
        return result
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(bytes = 0, total = 0)

    private fun foregroundInfo(bytes: Long, total: Long): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Téléchargement de la voix", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Téléchargement de la voix neuronale")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(PROGRESS_MAX, if (total > 0) (bytes * PROGRESS_MAX / total).toInt() else 0, total <= 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "voice_model_download"
        private const val NOTIFICATION_ID = 7_301
        private const val PROGRESS_MAX = 1_000
    }
}
