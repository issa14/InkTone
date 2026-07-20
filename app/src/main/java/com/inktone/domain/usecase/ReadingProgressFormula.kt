package com.inktone.domain.usecase

import com.inktone.domain.model.TocEntry

/**
 * Fraction de progression dans le livre (0.0 à 1.0), pondérée par la longueur réelle des
 * chapitres ([TocEntry.charCount]) plutôt que par leur simple position dans la table des
 * matières.
 *
 * Dégrade vers une formule non pondérée par position de chapitre si les métadonnées de
 * longueur ne sont pas encore disponibles (livre importé avant leur introduction) — pas de
 * blocage ni de division par zéro, pas de ré-import forcé.
 *
 * Fonction pure partagée entre [CalculateReadingProgressUseCase] (scroll manuel) et
 * `PlaybackOrchestrator` (lecture TTS), pour que les deux chemins d'écriture de
 * `reading_progress` produisent la même valeur et n'entrent jamais en conflit sur ce champ.
 */
fun computeReadingProgressFraction(
    tocEntries: List<TocEntry>,
    chapterIndex: Int,
    characterOffset: Int,
    sentenceIndex: Int,
    totalSentences: Int,
    totalChapters: Int
): Float {
    val totalBookChars = tocEntries.sumOf { it.charCount }
    return if (totalBookChars > 0) {
        val before = tocEntries.take(chapterIndex).sumOf { it.charCount }
        ((before + characterOffset).toFloat() / totalBookChars).coerceIn(0f, 1f)
    } else {
        val totalSent = totalSentences.coerceAtLeast(1)
        val totalChap = totalChapters.coerceAtLeast(1)
        ((chapterIndex.toFloat() + sentenceIndex.toFloat() / totalSent) / totalChap).coerceIn(0f, 1f)
    }
}
