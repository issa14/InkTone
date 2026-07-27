package com.inktone.feature.reader

import com.inktone.domain.model.Chapter
import com.inktone.domain.model.EffectiveReadingSettings
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.TableOfContentsEntry

data class ReaderUiState(
    val chapters: List<Chapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val tableOfContents: List<TableOfContentsEntry> = emptyList(),
    val currentSentenceIndex: Int = 0,
    val highlightedWordRange: IntRange? = null,
    val isPlaying: Boolean = false,
    val isTocVisible: Boolean = false,
    // Deja resolu via EffectiveReadingSettings.resolve() (Tache 1.3) au
    // moment de l'ouverture (Tache 4.7) - ReaderScreen ne connait jamais
    // ReadingOverrides ni UserPreferences separement, seulement ce
    // resultat final.
    val effectiveSettings: EffectiveReadingSettings = EffectiveReadingSettings(ReadingTheme.SYSTEM, 18),
) {
    val currentChapter: Chapter? get() = chapters.getOrNull(currentChapterIndex)
    val hasNextChapter: Boolean get() = currentChapterIndex < chapters.lastIndex
    val hasPreviousChapter: Boolean get() = currentChapterIndex > 0
}

sealed interface ReaderIntent {
    data class OpenPublication(val publicationId: String) : ReaderIntent

    /**
     * Scaffolding de marche à blanc (hérité de la Phase 3) : bootstrap
     * défensif d'une Publication à partir d'un fichier déjà copié en
     * cache, avant ouverture. `MainActivity` ne peut pas injecter
     * `PublicationRepository` par champ directement (KSP
     * `error.NonExistentClass`, cause racine non identifiée — voir
     * PHASE_3_MARCHE_A_BLANC.md) ; passer par cet intent contourne le
     * problème en gardant l'injection par constructeur dans le
     * ViewModel. À retirer quand `feature/library` (Phase 6) fournira
     * un import réel.
     */
    data class BootstrapAndOpenFixture(val publicationId: String, val fileUri: String) : ReaderIntent
    data object NextChapter : ReaderIntent
    data object PreviousChapter : ReaderIntent
    data class JumpToChapter(val chapterIndex: Int) : ReaderIntent
    data object ToggleToc : ReaderIntent
    data object PlayCurrentSentence : ReaderIntent
    data object Pause : ReaderIntent
}
