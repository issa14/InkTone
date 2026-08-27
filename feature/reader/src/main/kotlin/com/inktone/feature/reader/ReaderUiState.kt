package com.inktone.feature.reader

import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.AnnotationKind
import com.inktone.domain.model.Bookmark
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.EffectiveReadingSettings
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.SleepTimerState
import com.inktone.domain.model.TableOfContentsEntry
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.EpubResourceResolver
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
    // 3e.3 — distinct de isPlaying : isPlaying passe à vrai avant la
    // synthèse (ReaderViewModel.playCurrentSentence), isAudioActive ne
    // l'est que pendant que l'AudioTrack de la phrase en cours joue
    // réellement. Sert l'onde sonore de TtsPillBar — K « un moteur ne
    // fait jamais semblant » : l'indicateur ne doit pas animer pendant un
    // blanc de synthèse.
    val isAudioActive: Boolean = false,
    val isTocVisible: Boolean = false,
    // Deja resolu via EffectiveReadingSettings.resolve() (Tache 1.3) au
    // moment de l'ouverture (Tache 4.7) - ReaderScreen ne connait jamais
    // ReadingOverrides ni UserPreferences separement, seulement ce
    // resultat final.
    val effectiveSettings: EffectiveReadingSettings = EffectiveReadingSettings(ReadingTheme.DEFAULT.id, 18),
    // Lot 9 — ReadingTheme complet (couleurs + police) résolu depuis
    // `effectiveSettings.theme` (un id) via ThemeRepository. Source
    // unique de rendu couleur/police du lecteur — ThemeColors.kt en
    // dérive, jamais un `when` sur un enum fermé.
    val resolvedTheme: ReadingTheme = ReadingTheme.DEFAULT,
    // Palier 3f.1/3f.3 — sélection libre au mot (appui long + glissement,
    // PAGED et SCROLL), seul modèle de sélection de texte du lecteur
    // depuis le retrait de l'ancien modèle par phrase (palier 3f.5 —
    // Selection/SelectionContainer contrôlé de Compose étant internal,
    // voir git blame pour l'historique). Offsets de caractère ABSOLUS
    // dans le chapitre, calés sur les bornes de mot
    // (TextLayoutResult.getWordBoundary par l'appelant).
    val freeSelectionAnchorOffset: Int? = null,
    val freeSelectionFocusOffset: Int? = null,
    // Lot 24, tâche 1 — id de l'annotation créée par le popup de sélection
    // en cours (null = aucune annotation appliquée pour cette sélection).
    // Un second tap sur une couleur/un type modifie CETTE annotation
    // (UpdateAnnotationUseCase) plutôt que d'en créer une nouvelle.
    // Réinitialisé par ClearFreeSelection et par tout changement de
    // freeSelectionRange (nouvelle sélection = nouvelle annotation).
    val pendingAnnotationId: String? = null,
    val annotations: List<Annotation> = emptyList(),
    // Lot 22, tâche 12 — réglage global (UserPreferences.recentAnnotationColors),
    // observé en continu, même patron que readerMarginStep/isTextJustified.
    val recentAnnotationColors: List<AnnotationColor> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val isBookmarkListVisible: Boolean = false,
    // Lot 21, tâche 5 — id du signet venant d'être créé par le toggle, en
    // attente d'une note OPTIONNELLE (dialogue non bloquant, fermable).
    // null = aucun dialogue à proposer. Consommé une seule fois par
    // ReaderScreen (SaveBookmarkNote ou DismissBookmarkNotePrompt).
    val pendingBookmarkNoteId: String? = null,
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
    // P4 (plan polissage Pareto) — confort de lecture visuelle, même patron
    // d'observation continue que lineHeightMultiplier (réglage global, pas de
    // surcharge par publication).
    //
    // `readerMarginStep` alimente une seule valeur en dp côté ReaderScreen,
    // consommée à la fois par la MESURE de pagination et par le rendu : deux
    // sources distinctes feraient déborder le texte hors de la page mesurée.
    val readerMarginStep: Int = UserPreferences.MARGIN_STEP_DEFAULT,
    /** Justification + césure (les deux ensemble, voir UserPreferences.textJustified). */
    val isTextJustified: Boolean = false,
    /** Empêche l'extinction de l'écran pendant la lecture visuelle. */
    val keepScreenOn: Boolean = false,
    // 3d.3 — réglage global (UserPreferences.readerBrightness), même
    // patron d'observation continue que lineHeightMultiplier. null =
    // valeur système, appliqué à la fenêtre par ReaderBrightnessEffect.
    val readerBrightness: Float? = null,
    // 3d.5 — rappel de repos oculaire, indépendant du minuteur de sommeil
    // TTS (voir SleepTimerPanel, section 2). enabled/intervalMinutes :
    // réglage global miroir de UserPreferences (même patron que
    // lineHeightMultiplier/readerBrightness). isVisible/countdownS :
    // état ponctuel du popup, jamais persisté.
    val eyeRestReminderEnabled: Boolean = true,
    val eyeRestReminderIntervalMinutes: Int = 60,
    val isEyeRestReminderVisible: Boolean = false,
    val eyeRestReminderCountdownS: Int = EYE_REST_REMINDER_COUNTDOWN_S,
    // 3e.2 — réglage global (UserPreferences.reduceMotion), même patron
    // d'observation continue que isReadingRulerEnabled/lineHeightMultiplier.
    // Distinct de reducedMotionDuration() (core/designsystem) qui lit le
    // réglage système d'accessibilité, pas ce réglage applicatif — les deux
    // coexistent, le surlignage mot-à-mot ne respecte aujourd'hui que le
    // premier.
    val reduceMotion: Boolean = false,
    // Lot 21, tâche 9 — vitesse d'auto-scroll visuel (mode SCROLL
    // uniquement), réglage global observé en continu comme reduceMotion.
    // 0 = désactivé ; le rendu n'auto-scrolle JAMAIS quand reduceMotion est
    // actif, quelle que soit cette valeur.
    val autoScrollSpeed: Int = 0,
    // Lot 4, tâche 4.7 — cible de flash en attente de la fin de mise en
    // page du chapitre visé (la mesure est asynchrone depuis 3a, voir
    // ChapterPaginationState). Consommée une seule fois par
    // ReaderViewModel.onChapterLayoutCompleted, jamais rejouée ensuite.
    val pendingHighlightTarget: PendingHighlightTarget? = null,
    // Lot 10 (restauré au Lot 20) — proposition proactive de la voix
    // neuronale au premier usage réel du TTS (voir
    // ReaderViewModel.checkVoiceDownloadPrompt) : posée une seule fois,
    // jamais reproposée (UserPreferences.hasPromptedVoiceDownload).
    val showVoiceDownloadPrompt: Boolean = false,
    // Lot 12, Palier 2 (tache 12.9) — format de la publication ouverte,
    // jamais reporte dans l'etat avant ce lot. EPUB par defaut : c'est le
    // seul format ouvert avant que ce champ n'existe, aucune valeur
    // initiale ambigue introduite pour l'existant.
    val publicationFormat: PublicationFormat = PublicationFormat.EPUB,
    /**
     * Le document de rendu PDF (PDFium) est ouvert et utilisable.
     *
     * Bug reel trouve sur appareil (2026-08-26) : `openPublication` peuple
     * l'etat AVANT d'ouvrir le document (volontairement, pour qu'un echec
     * s'affiche sur le meme ecran). L'ecran composait donc, mesurait son
     * viewport et demandait sa premiere page pendant que `fixedPageDocument`
     * etait encore nul — `renderPdfPage` rendait `null`, et l'effet de rendu
     * ne repartait JAMAIS, ses cles (page, taille) ne changeant plus. Page
     * noire definitive, sans erreur ni log : le symptome « la lecture de PDF
     * ne marche pas » remonte par les premiers beta-testeurs.
     *
     * Ce drapeau est une cle d'effet : il fait repartir le rendu au moment
     * ou le document devient reellement disponible.
     */
    val isFixedPageReady: Boolean = false,
    // Plan v3, Palier 3.6 — résolveur d'images EPUB, null pour PDF/TXT
    val epubResourceResolver: EpubResourceResolver? = null,
    // Plan v3, Palier 3.6 — ID de la publication ouverte (pour EpubImageKey)
    val publicationId: String = "",
    // Ratio de defilement vertical [0f..1f] au sein de la page PDF
    // courante (decision actee 7 du plan, Palier 1) - seul equivalent de
    // `currentSentenceIndex` pour un format sans notion de phrase.
    val pageOffsetY: Float = 0f,
    // Lot 12, tâche 12.11 — inversion forcee sur pages scannees (sans
    // texte extrait). Desactivee par defaut : une page image pure
    // conserve son rendu original. Activee manuellement via l'intent
    // ToggleForcePdfInversion (pas de persistence, ecart declare).
    val forcePdfInversion: Boolean = false,
) {
    val currentChapter: Chapter? get() = chapters.getOrNull(currentChapterIndex)
    val hasNextChapter: Boolean get() = currentChapterIndex < chapters.lastIndex
    val hasPreviousChapter: Boolean get() = currentChapterIndex > 0

    /**
     * ADR-017 volet 2, premier temps — le TTS est désormais disponible sur
     * PDF, à la granularité de la phrase (les `Sentence` sont extraites
     * page par page par `PdfPublicationParser`). Le surlignage mot-à-mot
     * reste hors périmètre : il demande des `BoundingBox` par mot, que ce
     * volet ne fournit pas encore — un PDF s'écoute, il ne se surligne pas.
     *
     * Un PDF entièrement scanné (aucune page ne porte de texte) n'a rien à
     * narrer : les commandes TTS sont alors masquées plutôt que présentes
     * et sans effet. Le test ne vaut QUE pour le PDF, dont les chapitres
     * sont chargés d'un bloc à l'ouverture ; pour un EPUB, `sentences` est
     * vide tant que le chapitre n'a pas été parsé paresseusement, et s'y
     * fier masquerait le bouton sur tous les livres.
     */
    val supportsTts: Boolean
        get() = publicationFormat != PublicationFormat.PDF || chapters.any { it.sentences.isNotEmpty() }

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
            // Lot 12, tache 12.9 — branche dediee au format PDF : page =
            // chapitre (Palier 1), pageOffsetY remplace le role de
            // currentSentenceIndex. N'affecte pas Locator.computeProgression,
            // qui reste le chemin EPUB/TXT inchange ci-dessous (decision
            // actee du plan).
            if (publicationFormat == PublicationFormat.PDF) {
                if (chapters.isEmpty()) return 0f
                return ((currentChapterIndex + pageOffsetY) / chapters.size).coerceIn(0f, 1f)
            }
            val chapter = currentChapter ?: return 0f
            val sentence = chapter.sentences.getOrNull(currentSentenceIndex)
            val locator = Locator(
                resourceHref = chapter.href,
                chapterIndex = chapter.index,
                charOffset = sentence?.startOffset ?: 0,
            )
            val totalCharsBeforeChapter = chapters.take(currentChapterIndex).sumOf(::chapterCharCount)
            val totalCharsInPublication = chapters.sumOf(::chapterCharCount)
            return Locator.computeProgression(locator, totalCharsBeforeChapter, totalCharsInPublication)
        }

    /** Plage de caractères (bornes inclusives, offsets absolus au chapitre) de la sélection libre en cours, ou `null`. */
    val freeSelectionRange: IntRange?
        get() {
            val anchor = freeSelectionAnchorOffset ?: return null
            val focus = freeSelectionFocusOffset ?: anchor
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
            // Lot 12, tache 12.9, decision actee 21 — granularite page,
            // pas phrase : un PDF n'a pas de « phrase courante » suivie en
            // navigation manuelle.
            if (publicationFormat == PublicationFormat.PDF) {
                return bookmarks.any { it.locator.chapterIndex == currentChapterIndex }
            }
            val chapter = currentChapter ?: return false
            val sentence = chapter.sentences.getOrNull(currentSentenceIndex) ?: return false
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

/** 3d.5 — durée du compte à rebours du popup de repos oculaire (UX_FLOW_DESIGN.md §Minuteur). */
const val EYE_REST_REMINDER_COUNTDOWN_S = 60

/**
 * Lot 4, tâche 4.7 — cible exprimée en [Locator], jamais en index de page
 * (un index de page ne vaut que pour un couple style/viewport donné,
 * invariant posé au lot 3b). `sentenceIndex` est déjà résolu par
 * `navigateToLocator` au moment de la navigation, pas recalculé ici.
 */
data class PendingHighlightTarget(val chapterIndex: Int, val sentenceIndex: Int)

private fun chapterCharCount(chapter: Chapter): Int =
    chapter.sentences.sumOf { it.text.length }

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
        /** Lot 4, tâche 4.7 — arrivée depuis « Marque-pages et notes » : flash différé du passage visé. */
        val flashOnArrival: Boolean = false,
        /** Bouton Play de la carte Reprendre : démarre le TTS automatiquement à l'arrivée. */
        val autoStartTts: Boolean = false,
    ) : ReaderIntent

    data object NextChapter : ReaderIntent
    data object PreviousChapter : ReaderIntent
    data class JumpToChapter(val chapterIndex: Int) : ReaderIntent
    data object ToggleToc : ReaderIntent
    data object PlayCurrentSentence : ReaderIntent
    data object Pause : ReaderIntent

    /**
     * Sélection libre au mot (appui long + glissement, PAGED et SCROLL).
     * `anchorOffset`/`focusOffset` : offsets de caractère absolus dans le
     * chapitre, déjà calés sur des bornes de mot par l'appelant
     * (`PagedChapterContent.PageBlock`/`ReaderScreen.ParagraphText`, via
     * la sélection native de `BasicTextField`) — ce ViewModel ne fait que
     * les stocker.
     */
    data class SetFreeSelection(val anchorOffset: Int, val focusOffset: Int) : ReaderIntent
    data object ClearFreeSelection : ReaderIntent

    /**
     * Tâche 3c.4 — `content` : texte de la note associée (popup de
     * sélection, action « Note »), `null` pour un simple surlignage
     * (action « Surligner »). `Annotation.content` existe depuis la
     * Tâche 7.1 (`content: String?`), jamais rempli avant ce lot — pas de
     * migration Room nécessaire, `null` reste distinct d'une note vidée
     * volontairement (voir `Annotation.kt`).
     */
    // Lot 23, tâche 4 — `kind` comble le trou trouvé à la vérification
    // device du Lot 22 : `AnnotationKind` existait déjà (rendu +
    // migration) mais rien ne permettait de le choisir, toute annotation
    // devenait `HIGHLIGHT` par défaut.
    data class ConfirmAnnotation(
        val color: AnnotationColor,
        val kind: AnnotationKind = AnnotationKind.HIGHLIGHT,
        val content: String? = null,
    ) : ReaderIntent

    /**
     * Tâche 3c.3 — remplace `CreateBookmark` (Tâche 7.2) : le bouton du
     * panneau Marque-pages est un **toggle**, pas un simple ajout — il
     * retire le signet existant à la position courante s'il y en a déjà
     * un, plutôt que d'en créer un doublon.
     */
    data object ToggleBookmarkAtCurrentPosition : ReaderIntent
    data object ToggleBookmarkList : ReaderIntent
    data class DeleteBookmark(val id: String) : ReaderIntent

    /**
     * Lot 21, tâche 5 — note optionnelle d'un signet. Le signet est créé
     * immédiatement par le toggle (le geste rapide reste rapide) ; ce
     * dialogue n'est qu'une PROPOSITION, fermable sans conséquence via
     * [DismissBookmarkNotePrompt]. `note` vide ou blanche → note nulle,
     * le signet reste valide.
     */
    data class SaveBookmarkNote(val note: String) : ReaderIntent
    data object DismissBookmarkNotePrompt : ReaderIntent

    /**
     * Lot 22, tâche 11 — édition de la note d'un signet existant depuis
     * `BookmarkPanel`, distinct de [SaveBookmarkNote] (celui-ci répond à
     * [pendingBookmarkNoteId], propre à la proposition post-création via
     * Snackbar — la réutiliser ici redéclencherait ce Snackbar). `note`
     * vide ou blanche → note nulle, même règle que [SaveBookmarkNote].
     */
    data class EditBookmarkNote(val id: String, val note: String) : ReaderIntent

    /** Lot 22, tâche 11 — suppression depuis `BookmarkPanel` (onglets Notes/Surlignages). */
    data class DeleteAnnotation(val id: String) : ReaderIntent

    /** Lot 22, tâche 11 — édition de note depuis `BookmarkPanel` (onglet Notes). */
    data class UpdateAnnotationNote(val id: String, val content: String?) : ReaderIntent

    /**
     * Lot 21, tâche 9 — règle la vitesse d'auto-scroll visuel
     * (`UserPreferences.autoScrollSpeed`, `0` = désactivé). Le réglage est
     * global (même patron que reduceMotion), pas une surcharge par
     * publication.
     */
    data class SetAutoScrollSpeed(val speed: Int) : ReaderIntent

    /** Navigue vers un `Locator` arbitraire — signet (Tâche 7.2) ou résultat de recherche (Tâche 7.5). */
    data class NavigateToLocator(val locator: Locator) : ReaderIntent

    /**
     * Lot 4, tâche 4.7 — envoyé par `ReaderScreen` quand la mise en page du
     * chapitre [chapterIndex] est confirmée complète (voir
     * `ChapterPaginationState.measurement`). Ne déclenche le flash que si
     * une cible en attente vise ce chapitre précis.
     */
    data class ChapterLayoutCompleted(val chapterIndex: Int) : ReaderIntent

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

    /** Lot 10 (restauré au Lot 20) — ferme la proposition de téléchargement de voix. */
    data object DismissVoiceDownloadPrompt : ReaderIntent

    /**
     * A.3 — Audit v1.0.0 (AUDIT_CONSOLIDATION_V1.md, B3) : ré-essaie
     * d'ouvrir la publication courante après un échec d'ouverture/parsing
     * (efface l'erreur puis relance [ReaderIntent.OpenPublication]).
     * Avant l'audit, le seul bouton d'erreur se contentait d'effacer le
     * message, laissant un écran vide sans CTA.
     */
    data object RetryOpen : ReaderIntent

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
     * Lot 12, tache 12.9 — miroir de [UpdateScrollPosition] pour le
     * format PDF : remonte le ratio de defilement vertical au sein de la
     * page courante (geste de panoramique, `FixedPageContent`), jamais
     * emis par les autres formats.
     */
    data class UpdatePageOffset(val offsetY: Float) : ReaderIntent

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

    /** P4 — cran de marge latérale (voir `UserPreferences.MARGIN_STEP_RANGE`). */
    data class SetReaderMarginStep(val step: Int) : ReaderIntent

    /** P4 — justification du texte, césure comprise. */
    data class SetTextJustified(val justified: Boolean) : ReaderIntent

    /**
     * Correctif Lot 21 — jusqu'ici aucun écran ne permettait de choisir
     * la famille de police depuis le Lecteur : `SettingsIntent.SetFontFamily`
     * (feature/settings) n'était dispatché par aucune UI, rendant
     * OpenDyslexic (hors préréglage d'accessibilité) et Source Serif 4
     * inatteignables malgré leur rendu réel (Lot 21, tâches 1 et 10).
     */
    data class SetFontFamily(val fontFamily: FontFamily) : ReaderIntent

    /** P4 — maintien de l'écran allumé pendant la lecture visuelle. */
    data class SetKeepScreenOn(val enabled: Boolean) : ReaderIntent

    /** 3d.3 — luminosité de la fenêtre du lecteur. `null` = valeur système. */
    data class SetReaderBrightness(val value: Float?) : ReaderIntent

    /** 3d.5 — active/désactive le rappel de repos oculaire (réglage global). */
    data class SetEyeRestReminderEnabled(val enabled: Boolean) : ReaderIntent

    /** 3d.5 — intervalle du rappel de repos oculaire, en minutes (stepper ±15min). */
    data class SetEyeRestReminderInterval(val minutes: Int) : ReaderIntent

    /**
     * 3d.5 — « Reprendre » du popup de repos oculaire : reprend le TTS
     * immédiatement s'il était actif, relance l'intervalle complet.
     */
    data object ResumeFromEyeRestReminder : ReaderIntent

    /**
     * 3d.5 — « Reporter » du popup de repos oculaire : ne reprend PAS le
     * TTS, reprogramme l'échéance à +10 min (snooze court, distinct de
     * l'intervalle configuré).
     */
    data object SnoozeEyeRestReminder : ReaderIntent

    /**
     * Lot 12, tâche 12.11 — force l'inversion de luminance sur les pages
     * PDF scannées (sans texte extrait), normalement exclues de
     * l'inversion automatique. Désactivé par défaut, non persisté
     * (écart déclaré : pas d'UI de réglage encore exposée).
     */
    data object ToggleForcePdfInversion : ReaderIntent
}
