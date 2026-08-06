package com.inktone.feature.reader

import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Bookmark
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.EffectiveReadingSettings
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.SleepTimerState
import com.inktone.domain.model.TableOfContentsEntry
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.valueobject.Locator

/** B.1 — Mode de lecture : défilement vertical ou paginé horizontal. */
enum class ReadingMode { SCROLL, PAGED }

data class ReaderUiState(
    // 3b.3 — titre/auteur du livre, alimentés à l'ouverture de la
    // publication (ReaderViewModel.openPublication). Source unique pour
    // la barre du haut (3b.5) : jamais rechargés depuis un repository
    // dans le composable.
    val title: String? = null,
    val author: String? = null,
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
    // Surcharge par publication actuellement active (Tache 8.2) - null =
    // aucune surcharge, les reglages globaux s'appliquent tels quels.
    val currentOverrides: ReadingOverrides? = null,
    // Tache 9bis.3.3 - null = minuteur desactive.
    val sleepTimer: SleepTimerState? = null,
    // Tache 9bis.3.6 - observe en continu (UserPreferences.readingRulerEnabled,
    // Tache 9bis.5), pas resolu une seule fois comme effectiveSettings :
    // c'est un reglage global, pas une cascade overrides/preferences.
    val isReadingRulerEnabled: Boolean = false,
    // A.3 — Message d'erreur surfacé quand le parsing ou l'ouverture
    // échoue. null = pas d'erreur, l'écran affiche le contenu normal.
    val errorMessage: String? = null,
    // B.1 — Mode de lecture actif (scroll vertical ou paginé horizontal).
    val readingMode: ReadingMode = ReadingMode.SCROLL,
    // 3d.1 — profil vocal actif résolu (même repli que playCurrentSentence,
    // voir ReaderViewModel.resolveVoiceProfile) : source unique pour la
    // vitesse affichée et le nom de voix, jamais recalculé localement dans
    // ReaderTtsPanel.
    val activeVoiceProfile: VoiceProfile? = null,
    val availableVoiceProfiles: List<VoiceProfile> = emptyList(),
    // 3d.2 — réglage global (UserPreferences.lineHeightMultiplier), observé
    // en continu comme isReadingRulerEnabled : pas de surcharge par
    // publication, voir doc du lot 3d.
    val lineHeightMultiplier: Float = 1.4f,
) {
    val currentChapter: Chapter? get() = chapters.getOrNull(currentChapterIndex)
    val hasNextChapter: Boolean get() = currentChapterIndex < chapters.lastIndex
    val hasPreviousChapter: Boolean get() = currentChapterIndex > 0

    /**
     * Tache 9bis.3.2 — progression du LIVRE ENTIER (`Locator.computeProgression`,
     * ecrite en Tache 1.1, jamais branchee avant cette tache), pas la
     * progression par chapitre du legacy. Recalculee a chaque acces plutot
     * que mise en cache a l'ecriture : le `DocumentModel` complet est deja
     * en memoire (Tache 4.6), la somme des longueurs de phrase reste bon
     * marche meme pour un roman long - pas de mise en cache prematuree
     * tant qu'un cout reel n'est pas mesure.
     */
    val bookProgression: Float
        get() {
            val chapter = currentChapter ?: return 0f
            val sentence = chapter.paragraphs.flatMap { it.sentences }.getOrNull(currentSentenceIndex)
            val locator = Locator(
                resourceHref = chapter.href,
                chapterIndex = chapter.index,
                charOffset = sentence?.startOffset ?: 0,
            )
            val totalCharsBeforeChapter = chapters.take(currentChapterIndex).sumOf(::chapterCharCount)
            val totalCharsInPublication = chapters.sumOf(::chapterCharCount)
            return Locator.computeProgression(locator, totalCharsBeforeChapter, totalCharsInPublication)
        }

    val selectedSentenceRange: IntRange?
        get() {
            val anchor = selectionAnchorIndex ?: return null
            val focus = selectionFocusIndex ?: anchor
            return minOf(anchor, focus)..maxOf(anchor, focus)
        }

    /**
     * Tâche 3c.3 — état du toggle « Marquer cette page » du panneau
     * Marque-pages. Adressage par `Locator`/`Sentence` (Blueprint K3 :
     * jamais de numéro de page pour un signet), pas par page virtuelle —
     * un signet existe « à cette position » s'il tombe dans la phrase
     * actuellement ciblée (`currentSentenceIndex`), source unique de
     * position déjà utilisée par la création de signet et la persistance
     * (voir `ReaderViewModel.persistPosition`).
     */
    val isCurrentPageBookmarked: Boolean
        get() {
            val chapter = currentChapter ?: return false
            val sentence = chapter.paragraphs.flatMap { it.sentences }.getOrNull(currentSentenceIndex) ?: return false
            return bookmarks.any { bookmark ->
                bookmark.locator.chapterIndex == chapter.index &&
                    bookmark.locator.charOffset in sentence.startOffset until sentence.endOffset
            }
        }

    // 3b.4 — le micro-indicateur ETA (B.6) est retiré : absent de la
    // cible, il occupait la même zone que StatusLineBar quand le HUD est
    // masqué. Retrait consigné dans UX_FLOW_DESIGN.md (3b.8) : l'ETA
    // était une information réelle, sa suppression est un choix
    // d'alignement, pas une évidence.
}

private fun chapterCharCount(chapter: Chapter): Int =
    chapter.paragraphs.sumOf { paragraph -> paragraph.sentences.sumOf { it.text.length } }

sealed interface ReaderIntent {
    /**
     * `targetResourceHref`/`targetChapterIndex`/`targetCharOffset` :
     * arrivée depuis un résultat de recherche (Tâche 7.5), position à
     * rejoindre après ouverture. Décomposés en primitifs plutôt qu'un
     * `Locator` — `MainActivity` (module `app`) construit cet intent et
     * n'a pas le droit de dépendre de `domain` directement (Blueprint
     * §12.4) ; `Locator` est reconstruit ici, dans `feature/reader`, qui
     * en a le droit.
     */
    data class OpenPublication(
        val publicationId: String,
        val targetResourceHref: String? = null,
        val targetChapterIndex: Int? = null,
        val targetCharOffset: Int? = null,
    ) : ReaderIntent

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

    /**
     * Tâche 3c.4 — `content` : texte de la note associée (popup de
     * sélection, action « Note »), `null` pour un simple surlignage
     * (action « Surligner »). `Annotation.content` existe depuis la
     * Tâche 7.1 (`content: String?`), jamais rempli avant ce lot — pas de
     * migration Room nécessaire, `null` reste distinct d'une note vidée
     * volontairement (voir `Annotation.kt`).
     */
    data class ConfirmAnnotation(val color: AnnotationColor, val content: String? = null) : ReaderIntent

    /**
     * Tâche 3c.3 — remplace `CreateBookmark` (Tâche 7.2) : le bouton du
     * panneau Marque-pages est un **toggle**, pas un simple ajout — il
     * retire le signet existant à la position courante s'il y en a déjà
     * un, plutôt que d'en créer un doublon.
     */
    data object ToggleBookmarkAtCurrentPosition : ReaderIntent
    data object ToggleBookmarkList : ReaderIntent
    data class DeleteBookmark(val id: String) : ReaderIntent

    /** Navigue vers un `Locator` arbitraire — signet (Tâche 7.2) ou résultat de recherche (Tâche 7.5). */
    data class NavigateToLocator(val locator: Locator) : ReaderIntent

    /**
     * Tâche 8.2 — écrit la surcharge par publication (`ReadingState.overrides`)
     * via `UpdateReadingStateUseCase`. `null` efface la surcharge : les
     * réglages globaux (`UserPreferences`) reprennent la main.
     */
    data class SetOverrides(val overrides: ReadingOverrides?) : ReaderIntent

    /**
     * Tache 9bis.3.3 — `minutes = null` desactive le minuteur en cours.
     * A expiration, met en Pause (fondu sonore non implemente, voir
     * `SleepTimerState.fadeOutEnabled`).
     */
    data class SetSleepTimer(val minutes: Int?) : ReaderIntent

    /** A.3 — Efface le message d'erreur affiché dans le Reader. */
    data object DismissError : ReaderIntent

    /**
     * Panneau TTS (Tâche B.3) — recule/avance d'une phrase dans le
     * chapitre courant. Coupe l'audio en cours ; reprend la lecture sur
     * la nouvelle phrase seulement si elle était déjà en cours (sinon se
     * contente de déplacer la position, sans déclencher l'audio).
     */
    data object SkipToPreviousSentence : ReaderIntent
    data object SkipToNextSentence : ReaderIntent

    /** B.1 — Bascule entre mode scroll et mode paginé. */
    data object ToggleReadingMode : ReaderIntent

    /**
     * Tâche 3c.1/3c.1bis — remonte la position atteinte par un geste
     * manuel : la phrase la plus haute visible pendant un défilement
     * (mode SCROLL), ou la première phrase de la page affichée après un
     * swipe (mode PAGED). Source unique de `currentSentenceIndex` avec la
     * navigation manuelle et le TTS : jamais un second système de
     * position. `ReaderScreen`/`PagedChapterContent` ne l'émettent que
     * pour un geste d'origine utilisateur (jamais l'auto-scroll/auto-page
     * TTS, voir gardes `isProgrammaticScroll`/`isProgrammaticPageChange`).
     */
    data class UpdateScrollPosition(val sentenceIndex: Int) : ReaderIntent

    /**
     * 3d.1 — écrit la vitesse sur le profil vocal actif (jamais sur
     * `UserPreferences` : la vitesse appartient au profil, voir doc du lot
     * 3d, tâche 3d.1). Persiste immédiatement, contrairement à l'ancien
     * `onSpeedChange` vide.
     */
    data class SetTtsSpeed(val speed: Float) : ReaderIntent

    /** 3d.1 — change le profil vocal actif (préférence globale). */
    data class SetActiveVoiceProfile(val profileId: String) : ReaderIntent

    /** 3d.2 — réglage global d'interligne, voir `ReaderUiState.lineHeightMultiplier`. */
    data class SetLineHeight(val multiplier: Float) : ReaderIntent
}
