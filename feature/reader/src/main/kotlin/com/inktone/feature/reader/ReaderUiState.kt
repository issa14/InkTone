package com.inktone.feature.reader

import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Bookmark
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.EffectiveReadingSettings
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.TableOfContentsEntry
import com.inktone.domain.valueobject.Locator

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
    // Selection personnalisee par phrase (Tache 7.0) - remplace la
    // selection Compose native, Selection/SelectionContainer controle
    // etant internal a androidx.compose.foundation:foundation:1.7.2
    // (verifie par le compilateur, pas suppose). anchorIndex = phrase du
    // premier appui long ; focusIndex = derniere phrase touchee par une
    // extension (peut etre avant ou apres l'ancre).
    val selectionAnchorIndex: Int? = null,
    val selectionFocusIndex: Int? = null,
    val annotations: List<Annotation> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val isBookmarkListVisible: Boolean = false,
) {
    val currentChapter: Chapter? get() = chapters.getOrNull(currentChapterIndex)
    val hasNextChapter: Boolean get() = currentChapterIndex < chapters.lastIndex
    val hasPreviousChapter: Boolean get() = currentChapterIndex > 0

    val selectedSentenceRange: IntRange?
        get() {
            val anchor = selectionAnchorIndex ?: return null
            val focus = selectionFocusIndex ?: anchor
            return minOf(anchor, focus)..maxOf(anchor, focus)
        }
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

    /** Appui long sur une phrase (Tâche 7.0/7.1) : démarre une sélection. */
    data class BeginSentenceSelection(val sentenceIndex: Int) : ReaderIntent

    /** Appui simple sur une autre phrase pendant qu'une sélection est active : l'étend. */
    data class ExtendSentenceSelection(val sentenceIndex: Int) : ReaderIntent
    data object ClearSentenceSelection : ReaderIntent
    data class ConfirmAnnotation(val color: AnnotationColor) : ReaderIntent

    /** Tâche 7.2 — capture la position courante, pas de plage à résoudre (contrairement à l'annotation). */
    data object CreateBookmark : ReaderIntent
    data object ToggleBookmarkList : ReaderIntent
    data class DeleteBookmark(val id: String) : ReaderIntent
    data class NavigateToBookmark(val locator: Locator) : ReaderIntent
}
