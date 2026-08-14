package com.inktone.infrastructure.worker

import android.content.Context
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.inktone.domain.service.OpdsDownloadEvent
import com.inktone.domain.service.OpdsDownloadObserver
import com.inktone.domain.service.OpdsDownloadResult
import com.inktone.domain.service.OpdsHttpClient
import com.inktone.domain.usecase.ImportPublicationUseCase
import com.inktone.domain.usecase.ImportResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Téléchargement d'un livre OPDS en tâche de fond (Lot 13, tâche 13.3.2) —
 * réutilise le pipeline d'import EPUB existant ([ImportPublicationUseCase],
 * qui détecte le DRM — K7 — et normalise les hrefs — K6), jamais un second
 * chemin. Le fichier est écrit dans le stockage privé de l'app
 * (`getExternalFilesDir`, jamais `MANAGE_EXTERNAL_STORAGE`, K5 respecté),
 * exposé en `content://` via un `FileProvider` app-scopé, puis purgé une
 * fois l'import terminé (succès ou échec).
 */
@HiltWorker
class OpdsDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val httpClient: OpdsHttpClient,
    private val importPublication: ImportPublicationUseCase,
    private val downloadObserver: OpdsDownloadObserver,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val href = inputData.getString(KEY_ACQUISITION_HREF) ?: return Result.failure()
        val catalogId = inputData.getString(KEY_CATALOG_ID)
        val bookTitle = inputData.getString(KEY_BOOK_TITLE).orEmpty()

        // 1. Téléchargement (annulable de façon coopérative via `isStopped`).
        val download = httpClient.download(href, catalogId)
        if (download is OpdsDownloadResult.Failure) {
            downloadObserver.publish(OpdsDownloadEvent(bookTitle, null, false))
            return Result.failure()
        }
        if (isStopped) return Result.failure()

        // 2. Écriture dans le stockage privé de l'app.
        val file = withContext(Dispatchers.IO) {
            val dir = File(applicationContext.getExternalFilesDir(null), DIR_DOWNLOADS)
            dir.mkdirs()
            val safeName = bookTitle.replace(Regex("[^A-Za-z0-9._ -]"), "_")
                .take(MAX_NAME_LENGTH).ifBlank { "download" }
            val target = File(dir, "$safeName.epub")
            target.writeBytes((download as OpdsDownloadResult.Success).bytes)
            target
        }
        if (isStopped) {
            file.delete() // fichier partiel nettoyé, pas d'import
            return Result.failure()
        }

        // 3. Import via le pipeline EPUB existant — l'URI FileProvider
        // app-scopée est acceptée par `persistReadPermission` en no-op.
        val uri = FileProvider.getUriForFile(
            applicationContext,
            "${applicationContext.packageName}.fileprovider",
            file,
        )
        val result = importPublication(uri.toString())

        // 4. Purge du stockage privé (succès OU échec).
        file.delete()

        return when (result) {
            is ImportResult.Success -> {
                downloadObserver.publish(OpdsDownloadEvent(bookTitle, result.publication.id, true))
                Result.success()
            }
            is ImportResult.Duplicate -> {
                downloadObserver.publish(OpdsDownloadEvent(bookTitle, result.existingPublicationId, true))
                Result.success()
            }
            else -> {
                downloadObserver.publish(OpdsDownloadEvent(bookTitle, null, false))
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_ACQUISITION_HREF = "acquisition_href"
        const val KEY_CATALOG_ID = "catalog_id"
        const val KEY_BOOK_TITLE = "book_title"
        private const val DIR_DOWNLOADS = "opds_downloads"
        private const val MAX_NAME_LENGTH = 100
    }
}
