package com.inktone.data.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.inktone.MainActivity
import com.inktone.domain.usecase.ImportBooksUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Exécute l'import par lot en arrière-plan via WorkManager, avec notification persistante —
 * survit à la navigation, à la mise en arrière-plan et (grâce à la persistance propre à
 * WorkManager) à un redémarrage du process, contrairement à l'ancienne exécution dans
 * `viewModelScope` (voir PLAN import EPUB §4).
 *
 * La logique de fan-out elle-même vit dans [ImportBooksUseCase] (partagée, pas dupliquée). Ce
 * worker se contente de traduire sa progression en notification + `Data` observable via
 * `WorkInfo` par [com.inktone.ui.screen.library.LibraryViewModel].
 */
@HiltWorker
class EpubImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val importBooksUseCase: ImportBooksUseCase
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "epub_batch_import"
        const val KEY_URIS = "uris"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val KEY_PROGRESS = "progress"
        const val KEY_IMPORTING_IDS = "importing_ids"
        const val KEY_FAILED_NAMES = "failed_names"

        private const val CHANNEL_ID = "inktone_import"
        private const val CHANNEL_NAME = "Import de livres"
        private const val NOTIFICATION_ID = 2001
    }

    override suspend fun doWork(): Result {
        val uris = inputData.getStringArray(KEY_URIS)?.map { Uri.parse(it) } ?: emptyList()
        if (uris.isEmpty()) return Result.success()

        setForeground(createForegroundInfo(0, uris.size))

        val failedFiles = importBooksUseCase(uris) { progress ->
            setProgress(
                workDataOf(
                    KEY_COMPLETED to progress.completed,
                    KEY_TOTAL to progress.total,
                    KEY_PROGRESS to progress.overallProgress,
                    KEY_IMPORTING_IDS to progress.importingBookIds.joinToString(",")
                )
            )
            setForeground(createForegroundInfo(progress.completed, progress.total))
        }

        return Result.success(
            workDataOf(KEY_FAILED_NAMES to failedFiles.joinToString(","))
        )
    }

    private fun createForegroundInfo(completed: Int, total: Int): ForegroundInfo {
        ensureNotificationChannel()
        val notification = buildNotification(completed, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(completed: Int, total: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Import de livres en cours")
            .setContentText(if (total > 0) "$completed/$total livres importés" else "Préparation...")
            // Icône système standard "téléchargement/import" — même convention que
            // AudioPlaybackService, qui réutilise déjà une icône système plutôt qu'un asset dédié.
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setProgress(total, completed, total == 0)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progression de l'import de livres EPUB"
                setShowBadge(false)
                setSound(null, null)
            }
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
