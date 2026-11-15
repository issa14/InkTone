package com.inktone.domain.usecase

import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.plainText
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.service.PreAnalysisStore
import com.inktone.domain.valueobject.Locator
import com.inktone.domain.valueobject.LocatorReanchoring
import com.inktone.domain.valueobject.LocatorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Ancre les positions de chaque EPUB sur le texte actuel de ses chapitres
 * (ADR-027). Un seul traitement pour les deux besoins :
 *
 * - **rattrapage** : une position sans contexte textuel reçoit celui du texte
 *   actuel, sans bouger ;
 * - **ré-ancrage** : une position qui a un contexte est recalée dans le texte
 *   actuel, ce qui la déplace seulement si le texte a changé (nouveau parseur).
 *
 * Il ne traite que les livres dont `locatorAnchorVersion` est inférieure à
 * [currentParserVersion], puis l'y porte : idempotent, et repris s'il est
 * interrompu. Le rattrapage doit avoir tourné AVANT le premier changement de
 * version du parseur, sans quoi les positions existantes ne seraient pas
 * réparables. Un livre dont la pré-analyse n'est pas à jour est laissé pour le
 * passage suivant. Une position n'est jamais supprimée.
 */
class ReanchorLocatorsUseCase(
    private val publicationRepository: PublicationRepository,
    private val preAnalysisStore: PreAnalysisStore,
    private val readingStateRepository: ReadingStateRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val annotationRepository: AnnotationRepository,
    private val currentParserVersion: Int,
) {

    data class Outcome(val books: Int, val reanchored: Int, val notReanchored: Int, val pending: Int, val failed: Int)

    suspend operator fun invoke(): Outcome {
        var books = 0
        var reanchored = 0
        var notReanchored = 0
        var pending = 0
        var failed = 0
        for (publication in publicationRepository.observeAll().first()) {
            if (publication.format != PublicationFormat.EPUB) continue
            if (publication.locatorAnchorVersion >= currentParserVersion) continue
            if (!preAnalysisStore.isFresh(publication.id, publication.fileHash)) {
                pending++
                continue
            }
            try {
                val texts = HashMap<String, String?>()
                suspend fun textOf(href: String): String? = texts.getOrPut(href) {
                    preAnalysisStore.loadChapter(publication.id, publication.fileHash, href)?.plainText()
                }
                suspend fun fix(locator: Locator): Locator {
                    val text = textOf(locator.resourceHref) ?: return locator
                    return if (locator.text == null) {
                        locator.copy(text = LocatorText.capture(text, locator.charOffset))
                    } else {
                        val result = LocatorReanchoring.reanchor(locator, text)
                        if (result.anchored) reanchored++ else notReanchored++
                        result.locator
                    }
                }

                readingStateRepository.get(publication.id)?.let { state ->
                    // Calculé avant l'écriture atomique : le texte vient d'un accès suspendu.
                    val fixed = fix(state.locator)
                    readingStateRepository.update(publication.id) { current ->
                        // La lecture a pu avancer entre-temps : sa position plus récente prime.
                        if (current != null && current.locator != state.locator) current else (current ?: state).copy(locator = fixed)
                    }
                }
                for (bookmark in bookmarkRepository.observeForPublication(publication.id).first()) {
                    bookmarkRepository.update(bookmark.copy(locator = fix(bookmark.locator)))
                }
                for (annotation in annotationRepository.observeForPublication(publication.id).first()) {
                    annotationRepository.update(
                        annotation.copy(startLocator = fix(annotation.startLocator), endLocator = fix(annotation.endLocator)),
                    )
                }
                publicationRepository.setLocatorAnchorVersion(publication.id, currentParserVersion)
                books++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
            }
        }
        return Outcome(books, reanchored, notReanchored, pending, failed)
    }
}
