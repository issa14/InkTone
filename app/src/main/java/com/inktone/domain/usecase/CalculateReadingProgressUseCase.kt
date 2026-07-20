package com.inktone.domain.usecase

import com.inktone.data.database.entity.ReadingProgress
import com.inktone.domain.model.Book
import com.inktone.domain.repository.BookRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calcule la progression de lecture pondérée (voir [computeReadingProgressFraction]) et
 * persiste le résultat dans la table unifiée `reading_progress` via [BookRepository.saveProgress].
 *
 * Utilisée pour le chemin de navigation manuelle (scroll/tap sans lecture TTS active — tâche
 * 1.4). Le chemin TTS persiste directement via `PlaybackOrchestrator.saveProgressAsync`, qui
 * appelle la même formule pure [computeReadingProgressFraction] : les deux chemins d'écriture
 * ne se chevauchent jamais (le scroll manuel est ignoré tant qu'un scroll programmatique déclenché
 * par le TTS est en cours), donc pas de risque d'écrasement mutuel d'un champ par l'autre.
 */
@Singleton
class CalculateReadingProgressUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    /**
     * Calcule et persiste la progression de lecture.
     *
     * @param book Le livre en cours de lecture.
     * @param chapterIndex L'index du chapitre courant (0-based).
     * @param sentenceIndex L'index de la phrase courante dans le chapitre (0-based).
     * @param totalSentences Nombre total de phrases dans le chapitre.
     * @param characterOffset Offset caractère de la phrase courante dans le chapitre (pour la pondération).
     * @param source "TTS" | "MANUAL_SCROLL" — informatif, voir [ReadingProgress.source].
     * @return La fraction de progression calculée [0, 1].
     */
    suspend operator fun invoke(
        book: Book,
        chapterIndex: Int,
        sentenceIndex: Int,
        totalSentences: Int,
        characterOffset: Int = 0,
        source: String = "MANUAL_SCROLL"
    ): Float {
        val fraction = computeReadingProgressFraction(
            tocEntries = book.tocEntries,
            chapterIndex = chapterIndex,
            characterOffset = characterOffset,
            sentenceIndex = sentenceIndex,
            totalSentences = totalSentences,
            totalChapters = book.totalChapters
        )

        bookRepository.saveProgress(
            ReadingProgress(
                bookId = book.id,
                chapterIndex = chapterIndex,
                sentenceIndex = sentenceIndex,
                characterOffset = characterOffset,
                totalProgressFraction = fraction,
                updatedAt = System.currentTimeMillis(),
                source = source
            )
        )
        return fraction
    }
}
