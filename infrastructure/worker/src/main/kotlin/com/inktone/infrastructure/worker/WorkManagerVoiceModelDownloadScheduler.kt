package com.inktone.infrastructure.worker

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.inktone.domain.service.VoiceDownloadProgress
import com.inktone.domain.service.VoiceModelDownloadScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerVoiceModelDownloadScheduler @Inject constructor(
    private val workManager: WorkManager,
) : VoiceModelDownloadScheduler {

    override fun start() {
        val request = OneTimeWorkRequestBuilder<VoiceModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // KEEP : un second appui pendant le téléchargement ne le relance pas.
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    override val progress: Flow<VoiceDownloadProgress?> =
        workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
            val info = infos.lastOrNull() ?: return@map null
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> VoiceDownloadProgress.InProgress(0, 0)
                WorkInfo.State.RUNNING -> VoiceDownloadProgress.InProgress(
                    info.progress.getLong(VoiceModelDownloadWorker.KEY_BYTES, 0),
                    info.progress.getLong(VoiceModelDownloadWorker.KEY_TOTAL, 0),
                )
                WorkInfo.State.SUCCEEDED -> VoiceDownloadProgress.Complete
                WorkInfo.State.FAILED -> VoiceDownloadProgress.Failed(
                    info.outputData.getString(VoiceModelDownloadWorker.KEY_ERROR) ?: "Téléchargement interrompu.",
                )
                WorkInfo.State.CANCELLED -> null
            }
        }

    private companion object {
        const val WORK_NAME = "voice-model-download"
    }
}
