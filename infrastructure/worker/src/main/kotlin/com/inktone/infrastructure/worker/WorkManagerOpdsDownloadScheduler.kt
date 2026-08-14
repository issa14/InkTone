package com.inktone.infrastructure.worker

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.inktone.domain.service.OpdsDownloadScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémente [OpdsDownloadScheduler] via WorkManager (Lot 13, tâche
 * 13.3.2) — un `WorkRequest` par livre, non chaîné (un téléchargement
 * OPDS est unitaire, contrairement à l'import de bibliothèque qui découpe
 * en lots). Retourne l'identifiant de travail (`request.id`), utilisable
 * pour annuler via `WorkManager.cancelWorkById`.
 */
@Singleton
class WorkManagerOpdsDownloadScheduler @Inject constructor(
    private val workManager: WorkManager,
) : OpdsDownloadScheduler {

    override fun enqueue(acquisitionHref: String, catalogId: String?, bookTitle: String): String {
        val data = Data.Builder()
            .putString(OpdsDownloadWorker.KEY_ACQUISITION_HREF, acquisitionHref)
            .putString(OpdsDownloadWorker.KEY_BOOK_TITLE, bookTitle)
            .apply { if (catalogId != null) putString(OpdsDownloadWorker.KEY_CATALOG_ID, catalogId) }
            .build()

        val request = OneTimeWorkRequestBuilder<OpdsDownloadWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        workManager.enqueue(request)
        return request.id.toString()
    }
}
