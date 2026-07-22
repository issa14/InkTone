package com.inktone.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.inktone.data.epub.resolveEpubFileName
import com.inktone.data.epub.resolveEpubSourceFolder
import com.inktone.domain.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import javax.inject.Inject

/**
 * Fan-out de l'import par lot (concurrence limitée à 3, un id généré par livre avant même le
 * début de son import) — extrait de `LibraryViewModel.importBooks()` pour être réutilisable à
 * la fois par le ViewModel (avant la Phase WorkManager) et par [com.inktone.data.work.EpubImportWorker]
 * (voir PLAN import EPUB §4), sans dupliquer la logique entre les deux.
 */
class ImportBooksUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository
) {
    /** @param importingBookIds ids dont l'import n'est pas encore terminé, à cet instant. */
    data class Progress(
        val completed: Int,
        val total: Int,
        val overallProgress: Float,
        val importingBookIds: Set<String>
    )

    /** @return les noms de fichiers dont l'import a échoué. */
    suspend operator fun invoke(
        uris: List<Uri>,
        onProgress: suspend (Progress) -> Unit
    ): List<String> = coroutineScope {
        val total = uris.size
        val perFileProgress = java.util.concurrent.atomic.AtomicReferenceArray(Array(total) { 0f })
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val failedFiles = java.util.Collections.synchronizedList(mutableListOf<String>())
        val importingBookIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

        suspend fun publishProgress() {
            var sum = 0f
            for (i in 0 until total) sum += perFileProgress.get(i)
            onProgress(Progress(completedCount.get(), total, sum / total, importingBookIds.toSet()))
        }

        val importDispatcher = Dispatchers.IO.limitedParallelism(3)
        uris.mapIndexed { index, uri ->
            // Id généré avant même l'appel à importEpub() pour que la grille puisse afficher
            // l'indicateur "en cours" dès l'apparition du livre (premier insert en base), pas
            // seulement à partir du résultat final — voir PLAN import EPUB §2.
            val bookId = UUID.randomUUID().toString()
            importingBookIds.add(bookId)
            async(importDispatcher) {
                val fileName = resolveEpubFileName(context, uri) ?: "inconnu.epub"
                try {
                    val sourceFolder = resolveEpubSourceFolder(uri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        bookRepository.importEpub(bookId, stream, fileName, sourceFolder) { progress, _ ->
                            perFileProgress.set(index, progress)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ImportBooksUseCase", "Échec import $fileName", e)
                    failedFiles.add(fileName)
                } finally {
                    importingBookIds.remove(bookId)
                    perFileProgress.set(index, 1f)
                    completedCount.incrementAndGet()
                    publishProgress()
                }
            }
        }.awaitAll()

        failedFiles.toList()
    }

    /** Persiste la permission SAF pour les réimports/relectures futurs — à appeler avant [invoke]. */
    fun takePersistablePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Permission non persistable (ex: URI temporaire) — on continue
        }
    }
}
