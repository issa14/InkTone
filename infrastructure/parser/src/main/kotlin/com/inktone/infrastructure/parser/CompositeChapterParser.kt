package com.inktone.infrastructure.parser

import com.inktone.domain.model.Chapter
import com.inktone.domain.service.ChapterParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aiguille le chargement paresseux d'un chapitre vers le parseur du format,
 * exactement comme [CompositePublicationParser] le fait pour le parsing
 * initial — un seul [ChapterParser] est injecté dans le domaine.
 *
 * Le format est déduit du href enregistré, pas du nom de fichier : au
 * moment où une page est demandée, `registerPublication` a déjà associé
 * l'identifiant à son URI. Un href `page-N` n'appartient qu'au PDF (voir
 * [pageHref]), et il est le seul discriminant nécessaire — pas besoin de
 * réinterroger le `FileStorageService` à chaque page.
 */
@Singleton
class CompositeChapterParser @Inject constructor(
    private val epubChapterParser: EpubChapterParser,
    private val pdfChapterParser: PdfChapterParser,
) : ChapterParser {

    /** Publications enregistrées, pour ne relayer `invalidate` qu'aux concernés. */
    private val registered = ConcurrentHashMap<String, Unit>()

    override fun registerPublication(publicationId: String, fileUri: String) {
        registered[publicationId] = Unit
        // Enregistré des DEUX côtés : le format n'est pas connu ici, et
        // chaque délégué ne fait qu'un `put` en mémoire — aucun fichier
        // n'est ouvert avant la première page réellement demandée.
        epubChapterParser.registerPublication(publicationId, fileUri)
        pdfChapterParser.registerPublication(publicationId, fileUri)
    }

    override suspend fun parseChapter(
        publicationId: String,
        chapterHref: String,
        fragment: String?,
    ): Chapter = delegateFor(chapterHref).parseChapter(publicationId, chapterHref, fragment)

    override fun preload(
        publicationId: String,
        chapterHref: String,
        scope: CoroutineScope,
    ): Job = delegateFor(chapterHref).preload(publicationId, chapterHref, scope)

    override fun invalidate(publicationId: String) {
        if (registered.remove(publicationId) == null) return
        epubChapterParser.invalidate(publicationId)
        pdfChapterParser.invalidate(publicationId)
    }

    private fun delegateFor(chapterHref: String): ChapterParser =
        if (pageIndexOf(chapterHref) != null) pdfChapterParser else epubChapterParser
}
