package com.inktone.feature.reader

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.reducedMotionDuration
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.SleepTimerState
import com.inktone.feature.reader.pagination.rememberChapterPaginationState

/**
 * `effectiveSettings` (theme, taille de police) arrive déjà résolu dans
 * `state` — calculé par ReaderViewModel via
 * `EffectiveReadingSettings.resolve()` (Tâche 1.3/4.7). Ce Composable ne
 * connaît ni `ReadingOverrides` ni `UserPreferences` séparément, il
 * n'affiche qu'un résultat déjà tranché — ne jamais recalculer cette
 * cascade de précédence ici.
 *
 * **Rendu continu du chapitre (Tâche 7.0, révision)** — jusqu'ici (Phases
 * 3/4), cet écran n'affichait qu'une seule `Sentence` à la fois (squelette
 * de marche à blanc pour le pipeline TTS), pas la « lecture visuelle »
 * que le Blueprint exige en complément du mode audio. Le chapitre entier
 * est maintenant rendu — toutes les `Sentence` du chapitre, dans un
 * `FlowRow` défilable (`verticalScroll`, jamais `LazyColumn` : la mise en
 * page en flux imite le texte continu tout en gardant chaque phrase comme
 * composable adressable individuellement).
 *
 * **Sélection de texte : par phrase, pas par caractère (Tâche 7.0/7.1)** —
 * `Selection` et le `SelectionContainer(selection, onSelectionChange,
 * content)` contrôlé sont `internal` dans
 * `androidx.compose.foundation:foundation:1.7.2` (BOM 2024.09.02, vérifié
 * par le compilateur Kotlin en écrivant cette tâche : `Cannot access
 * 'data class Selection : Any': it is internal in file` — pas une
 * supposition d'après une doc générique, qui avait justement mal anticipé
 * cette API). Aucune API publique ne donne accès aux offsets d'une
 * sélection de texte native à ce niveau. À la place : appui long sur une
 * phrase pour démarrer une sélection, appui simple sur une autre phrase
 * pour l'étendre — l'index de `Sentence` touché est connu par
 * construction, aucune conversion pixel → offset nécessaire (voir
 * [AnnotationSelectionHandler]).
 *
 * **Limite connue, non résolue ici** : aucun défilement automatique vers
 * la phrase en cours de lecture TTS — la vue défile uniquement sur
 * changement de chapitre. Un chapitre long avec lecture TTS active peut
 * donc surligner un mot hors de l'écran visible.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel = hiltViewModel(),
    onSearchClick: () -> Unit = {},
    onBack: () -> Unit = {},
    onOpenPronunciationRules: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    // Tache 9bis.3.1 : HUD (panneau de controle + boutons) visible par
    // defaut, se masque seul apres 4s (ImmersiveReaderChrome), un appui
    // sur la zone de lecture le fait reapparaitre.
    var isHudVisible by remember { mutableStateOf(true) }
    // Bug réel trouvé à l'audit : le délai de 4s ne redémarrait jamais
    // pendant qu'on interagit avec le HUD (il se masquait sous les
    // doigts de l'utilisateur au milieu d'une action) — ce compteur,
    // incrémenté à chaque interaction, force ImmersiveReaderChrome à
    // relancer son délai.
    var hudActivityTick by remember { mutableIntStateOf(0) }
    fun keepHudVisible() {
        isHudVisible = true
        hudActivityTick++
    }

    // B.2/B.3 — états d'affichage des panneaux de réglages in-reader
    var showSettingsPanel by remember { mutableStateOf(false) }
    var showTtsPanel by remember { mutableStateOf(false) }

    ImmersiveReaderChrome(
        isHudVisible = isHudVisible,
        hudActivityTick = hudActivityTick,
        onAutoHide = { isHudVisible = false },
    ) {
    // C.5 — SharedTransition depuis la couverture de la bibliothèque
    val sharedTransitionScope = runCatching {
        com.inktone.core.designsystem.LocalSharedTransitionScope.current
    }.getOrNull()
    val animatedVisibilityScope = runCatching {
        com.inktone.core.designsystem.LocalAnimatedVisibilityScope.current
    }.getOrNull()
    val sharedElementMod = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "cover-${viewModel.currentPublicationId ?: ""}"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else Modifier

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(sharedElementMod)
            .background(ThemeColors.background(state.effectiveSettings.theme))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { if (isHudVisible) isHudVisible = false else keepHudVisible() }
            .padding(16.dp),
    ) {
        // A.3 — État d'erreur : affiché quand le parsing ou l'ouverture
        // échoue, avec boutons Réessayer et Retour.
        val errorMessage = state.errorMessage
        if (errorMessage != null) {
            ErrorState(
                message = errorMessage,
                onRetry = { viewModel.onIntent(ReaderIntent.DismissError) },
            )
            return@Column
        }

        val scrollState = rememberScrollState()
        LaunchedEffect(state.currentChapterIndex) { scrollState.scrollTo(0) }

        val selectedRange = state.selectedSentenceRange

        // A.1 / Tache 9bis.3.6 - position Y de la phrase active
        var currentLineYDp by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current

        // 3c.1 — drapeau posé explicitement autour du seul appel
        // programmatique à scrollState (auto-scroll TTS), levé à sa fin.
        // Discrimine l'origine réelle du défilement pour la détection de
        // position ci-dessous : `ScrollableState.isScrollInProgress` vaut
        // `true` pour CE défilement programmatique aussi bien que pour un
        // drag utilisateur — il ne permet donc pas de distinguer les deux,
        // contrairement à ce drapeau.
        var isProgrammaticScroll by remember { mutableStateOf(false) }

        // A.1 — Auto-scroll vers la phrase active pendant la lecture TTS
        LaunchedEffect(state.currentSentenceIndex) {
            if (state.isPlaying && currentLineYDp > 0.dp) {
                isProgrammaticScroll = true
                try {
                    scrollState.animateScrollTo(with(density) { currentLineYDp.roundToPx() })
                } finally {
                    isProgrammaticScroll = false
                }
            }
        }

        // 3c.1 — position en contenu (indépendante du défilement, voir
        // currentLineYDp ci-dessus qui utilise déjà cette même valeur comme
        // cible ABSOLUE de scrollTo) de chaque phrase rendue en mode
        // SCROLL. Map ordinaire, pas un State Compose : ses écritures
        // (onGloballyPositioned, une seule fois par phrase au layout, pas
        // à chaque frame de défilement) n'ont pas besoin de déclencher de
        // recomposition, seule la lecture dans le derivedStateOf ci-dessous
        // compte, déjà réactive via scrollState.value.
        val sentenceTopOffsetsPx = remember(state.currentChapterIndex) { mutableMapOf<Int, Int>() }

        // 3c.1 — dérive la phrase la plus haute visible, mais ne propage
        // qu'un changement de PHRASE, jamais de position brute : sans ce
        // derivedStateOf, la lecture de scrollState.value à chaque pixel
        // défilé recalculerait (et recomposerait) au même rythme.
        val topmostVisibleSentenceIndex by remember(state.currentChapterIndex) {
            derivedStateOf {
                if (isProgrammaticScroll) return@derivedStateOf null
                val scrollValue = scrollState.value
                var result: Int? = null
                for ((index, top) in sentenceTopOffsetsPx) {
                    if (top <= scrollValue && (result == null || index > result!!)) result = index
                }
                result
            }
        }

        // 3c.1 — antipattern legacy corrigé : en mode SCROLL, seuls le TTS
        // ou une navigation explicite faisaient avancer currentSentenceIndex
        // ; un défilement manuel silencieux n'était jamais reflété, ni
        // persisté. Ignoré si `null` (aucune phrase encore mesurée) ou hors
        // mode SCROLL (le pager pilote sa propre remontée en mode PAGED).
        LaunchedEffect(topmostVisibleSentenceIndex, state.readingMode) {
            val index = topmostVisibleSentenceIndex
            if (index != null && state.readingMode == ReadingMode.SCROLL) {
                viewModel.onIntent(ReaderIntent.UpdateScrollPosition(index))
            }
        }

        // 3b.4bis — bug réel trouvé sur appareil : le compteur de la
        // ligne de statut restait figé à la page 1 pendant un scroll ou
        // un swipe manuels (sans TTS), car currentSentenceIndex n'est
        // mis à jour que par le TTS ou une navigation explicite — jamais
        // par le simple geste de lecture. Page réellement affichée en
        // mode pagé (swipe manuel inclus, remontée par PagedChapterContent) :
        var pagedLivePageIndex by remember { mutableIntStateOf(0) }
        LaunchedEffect(state.currentChapterIndex) { pagedLivePageIndex = 0 }

        // 3c.4 — bornes fenêtre de la sélection active, par mode. SCROLL :
        // union des bornes des phrases sélectionnées (SnapshotStateMap,
        // ses écritures DOIVENT être observables ici — contrairement à
        // sentenceTopOffsetsPx en 3c.1, on veut justement que le popup
        // suive un défilement pendant que la sélection reste active).
        // PAGED : remonté par PagedChapterContent (conversion locale →
        // fenêtre via TextLayoutResult, seul capable de la produire).
        val scrollSelectionBoundsPx = remember(state.currentChapterIndex) {
            mutableStateMapOf<Int, Rect>()
        }
        var pagedSelectionBounds by remember { mutableStateOf<Rect?>(null) }
        val selectionBoundsInWindow = when (state.readingMode) {
            ReadingMode.SCROLL -> selectedRange
                ?.mapNotNull { scrollSelectionBoundsPx[it] }
                ?.reduceOrNull { a, b ->
                    Rect(
                        left = minOf(a.left, b.left),
                        top = minOf(a.top, b.top),
                        right = maxOf(a.right, b.right),
                        bottom = maxOf(a.bottom, b.bottom),
                    )
                }
            ReadingMode.PAGED -> pagedSelectionBounds
        }
        val selectedText = remember(selectedRange, state.currentChapter) {
            val range = selectedRange ?: return@remember ""
            val sentences = state.currentChapter?.paragraphs?.flatMap { it.sentences } ?: emptyList()
            range.mapNotNull { sentences.getOrNull(it)?.text }.joinToString(" ")
        }

        // 3b.5 — barre du haut : appartient au HUD, apparaît/disparaît
        // avec le panneau, jamais indépendamment (même gate isHudVisible).
        if (isHudVisible) {
            ReaderTopBar(title = state.title, author = state.author, onBack = onBack)
        }

        // 3b.1 — état de pagination hissé au-dessus du choix de mode :
        // sert la ligne de statut (3b.4, tous modes) ET PagedChapterContent
        // (mode pagé), un seul calcul. Mesuré au même endroit de
        // l'arborescence pour les deux modes (ce Box), formule unique de
        // hauteur utile (voir ChapterPaginationState).
        //
        // Régression connue, documentée, non corrigée (voir
        // docs/execution/NOTE_REGRESSION_CLIGNOTEMENT_PAGE_HUD.md) :
        // readingAreaSize dépend de isHudVisible (ReaderTopBar/
        // UnifiedControlPanel montés/démontés redistribuent l'espace de
        // ce Box weight(1f)) — chaque bascule du HUD redéclenche une
        // remesure complète de la pagination en mode pagé, dont les
        // étapes intermédiaires peuvent faire clignoter la page affichée.
        var readingAreaSize by remember { mutableStateOf(IntSize.Zero) }
        val paginationPaddingPx = with(density) { 16.dp.roundToPx() }
        val pagination = rememberChapterPaginationState(
            chapter = state.currentChapter,
            nextChapter = state.chapters.getOrNull(state.currentChapterIndex + 1),
            currentSentenceIndex = state.currentSentenceIndex,
            fontSizeSp = state.effectiveSettings.fontSize,
            viewportWidthPx = readingAreaSize.width,
            viewportHeightPx = readingAreaSize.height,
            paddingPx = paginationPaddingPx,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { coordinates -> readingAreaSize = coordinates.size },
        ) {
            if (state.isReadingRulerEnabled) {
                ReadingRuler(currentLineY = currentLineYDp, enabled = true)
            }

            when (state.readingMode) {
                ReadingMode.SCROLL -> {
                    FlowRow(
                        modifier = Modifier.verticalScroll(scrollState),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        var globalIndex = 0
                        state.currentChapter?.paragraphs?.forEach { paragraph ->
                            paragraph.sentences.forEach { sentence ->
                                val index = globalIndex++
                                val isCurrentlyPlaying = index == state.currentSentenceIndex
                                val isSentenceSelected = selectedRange?.contains(index) == true
                                // B.5 — piste de lecture : opacité différenciée
                                val trailAlpha = when {
                                    isCurrentlyPlaying -> 1.0f
                                    index < state.currentSentenceIndex -> 0.40f
                                    else -> 0.88f
                                }
                                SentenceText(
                                    sentence = sentence,
                                    paragraphStyle = paragraph.style,
                                    isCurrentlyPlaying = isCurrentlyPlaying,
                                    highlightedWordRange = state.highlightedWordRange,
                                    isSelected = isSentenceSelected,
                                    existingAnnotationColor = annotationColorFor(state.currentChapterIndex, sentence, state.annotations),
                                    fontSizeSp = state.effectiveSettings.fontSize,
                                    textColor = ThemeColors.text(state.effectiveSettings.theme).copy(alpha = trailAlpha),
                                    onLongClick = { viewModel.onIntent(ReaderIntent.BeginSentenceSelection(index)) },
                                    onClick = {
                                        if (selectedRange != null) {
                                            viewModel.onIntent(ReaderIntent.ExtendSentenceSelection(index))
                                        } else {
                                            // Le FlowRow couvre la quasi-totalité de la
                                            // zone de lecture : sans ce relais, le tap est
                                            // consommé par SentenceText et ne remonte
                                            // jamais au Column parent, rendant le HUD
                                            // quasiment impossible à rappeler une fois
                                            // masqué (bug réel trouvé à l'audit).
                                            if (isHudVisible) isHudVisible = false else keepHudVisible()
                                        }
                                    },
                                    // 3c.1 — position de contenu (indépendante du défilement,
                                    // stable tant que le texte ne se re-layoute pas) de
                                    // CHAQUE phrase, pas seulement celle en cours de lecture
                                    // TTS : nécessaire pour dériver la phrase la plus haute
                                    // visible pendant un défilement manuel silencieux.
                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                        val topPx = coordinates.positionInParent().y
                                        sentenceTopOffsetsPx[index] = topPx.toInt()
                                        if (isCurrentlyPlaying) {
                                            currentLineYDp = with(density) { topPx.toDp() }
                                        }
                                        // 3c.4 — bornes fenêtre, seulement pour les phrases
                                        // sélectionnées (popup de sélection) ; suit le
                                        // défilement puisque boundsInWindow() en dépend,
                                        // contrairement à positionInParent() ci-dessus.
                                        if (isSentenceSelected) {
                                            scrollSelectionBoundsPx[index] = coordinates.boundsInWindow()
                                        } else {
                                            scrollSelectionBoundsPx.remove(index)
                                        }
                                    },
                                )
                            }
                            // B.4 — Images EPUB après le paragraphe
                            val imagesAfterParagraph = state.currentChapter?.structuralBlocks
                                ?.filterIsInstance<com.inktone.domain.model.StructuralBlock.EpubImage>()
                                ?.filter { it.anchorAfterParagraphIndex == paragraph.index }
                            imagesAfterParagraph?.forEach { image ->
                                EpubImagePlaceholder(href = image.href, altText = image.altText)
                            }
                        }
                    }
                }
                ReadingMode.PAGED -> {
                    PagedChapterContent(
                        chapter = state.currentChapter,
                        pagination = pagination,
                        currentSentenceIndex = state.currentSentenceIndex,
                        highlightedWordRange = state.highlightedWordRange,
                        selectedRange = selectedRange,
                        annotations = state.annotations,
                        currentChapterIndex = state.currentChapterIndex,
                        textColor = ThemeColors.text(state.effectiveSettings.theme),
                        isReadingRulerEnabled = state.isReadingRulerEnabled,
                        onSentenceLongClick = { index -> viewModel.onIntent(ReaderIntent.BeginSentenceSelection(index)) },
                        onSentenceClick = { index ->
                            if (selectedRange != null) {
                                viewModel.onIntent(ReaderIntent.ExtendSentenceSelection(index))
                            } else {
                                if (isHudVisible) isHudVisible = false else keepHudVisible()
                            }
                        },
                        onNextChapter = { viewModel.onIntent(ReaderIntent.NextChapter) },
                        onCurrentLineY = { y -> currentLineYDp = y },
                        onPageChanged = { pageIndex -> pagedLivePageIndex = pageIndex },
                        onManualPageChange = { sentenceIndex ->
                            viewModel.onIntent(ReaderIntent.UpdateScrollPosition(sentenceIndex))
                        },
                        onSelectionBoundsInWindow = { bounds -> pagedSelectionBounds = bounds },
                    )
                }
            }

            // B.7 — Overlay visuel des captions retiré (UX §Lecture — couche
            // TTS : jugé trop encombrant, notamment sur les phrases longues).
            // L'annonce TalkBack par nœud liveRegion, ajoutée pour ne pas
            // perdre l'accessibilité, a été retirée après vérification sur
            // appareil (lot 1, point 11) : elle fait doublon avec la voix
            // TTS déjà active et les deux se chevauchent à l'oreille.
        }

        // 3c.4 — remplace AnnotationColorPicker (position fixe basse
        // d'écran, Confirmer/Annuler) par le popup Copier/Surligner/Note
        // positionné près de la sélection réelle.
        if (selectedRange != null) {
            SelectionActionPopup(
                selectedText = selectedText,
                selectionBoundsInWindow = selectionBoundsInWindow,
                onHighlight = { color ->
                    viewModel.onIntent(ReaderIntent.ConfirmAnnotation(color))
                },
                onSaveNote = { content, color ->
                    viewModel.onIntent(ReaderIntent.ConfirmAnnotation(color, content))
                },
                onDismiss = { viewModel.onIntent(ReaderIntent.ClearSentenceSelection) },
            )
        }

        if (isHudVisible) {
            UnifiedControlPanel(
                isPlaying = state.isPlaying,
                sleepTimerActive = state.sleepTimer != null,
                bookProgression = state.bookProgression,
                onPlayPause = {
                    keepHudVisible()
                    viewModel.onIntent(if (state.isPlaying) ReaderIntent.Pause else ReaderIntent.PlayCurrentSentence)
                },
                onSleepTimerClick = {
                    keepHudVisible()
                    viewModel.onIntent(ReaderIntent.SetSleepTimer(nextSleepTimerMinutes(state.sleepTimer)))
                },
                onSearchClick = { keepHudVisible(); onSearchClick() },
                onBookmarksClick = { keepHudVisible(); viewModel.onIntent(ReaderIntent.ToggleBookmarkList) },
                onTocClick = { keepHudVisible(); viewModel.onIntent(ReaderIntent.ToggleToc) },
                onThemeCycle = {
                    keepHudVisible()
                    val overrides = (state.currentOverrides ?: ReadingOverrides())
                        .copy(theme = nextReadingTheme(state.effectiveSettings.theme))
                    viewModel.onIntent(ReaderIntent.SetOverrides(overrides))
                },
                onAaClick = { keepHudVisible(); showSettingsPanel = true },
                onTtsClick = { keepHudVisible(); showTtsPanel = true },
                onReadingModeClick = { keepHudVisible(); viewModel.onIntent(ReaderIntent.ToggleReadingMode) },
            )
        }

        // 3b.4/3c.1 — ligne de statut persistante, hors HUD : visible en
        // permanence, y compris panneau masqué. Le compteur de pages vient
        // du contrat VirtualPagination (via l'état hissé ci-dessus),
        // jamais d'un calcul local.
        //
        // Bug réel trouvé sur appareil : dériver la page courante
        // uniquement de currentSentenceIndex (via pageIndexAt) la laissait
        // figée pendant un scroll/swipe manuel sans TTS, puisque
        // currentSentenceIndex n'était mis à jour que par le TTS ou une
        // navigation explicite. En pagé, la page réellement affichée par le
        // pager (pagedLivePageIndex, mise à jour aussi bien par un swipe
        // manuel que par le suivi TTS). En défilement, currentSentenceIndex
        // est désormais tenu à jour par le défilement manuel lui-même
        // (topmostVisibleSentenceIndex ci-dessus) : pageIndexAt en dérive
        // donc une page EXACTE, la même source que le mode pagé — plus
        // d'estimation par fraction de défilement, qui supposait à tort une
        // densité de texte uniforme sur le chapitre.
        state.currentChapter?.let { chapter ->
            val pageCountInChapter = pagination.pageCount(chapter.index)
            val pageIndexInChapter = when (state.readingMode) {
                ReadingMode.PAGED -> pagedLivePageIndex
                ReadingMode.SCROLL -> pagination.pageIndexAt(chapter.index, state.currentSentenceIndex)
            }
            StatusLineBar(
                chapterNumber = state.currentChapterIndex + 1,
                pageInChapter = pageIndexInChapter + 1,
                pageCountInChapter = pageCountInChapter,
                bookProgression = state.bookProgression,
            )
        }

        // B.2 — Panneau réglages lecture in-reader
        if (showSettingsPanel) {
            ReaderSettingsPanel(
                currentTheme = state.effectiveSettings.theme,
                currentFontSize = state.effectiveSettings.fontSize,
                onThemeChange = { theme ->
                    val overrides = (state.currentOverrides ?: ReadingOverrides()).copy(theme = theme)
                    viewModel.onIntent(ReaderIntent.SetOverrides(overrides))
                },
                onFontSizeChange = { size ->
                    val overrides = (state.currentOverrides ?: ReadingOverrides()).copy(fontSize = size)
                    viewModel.onIntent(ReaderIntent.SetOverrides(overrides))
                },
                onDismiss = { showSettingsPanel = false },
            )
        }

        // B.3 — Panneau TTS in-reader
        if (showTtsPanel) {
            val sentences = state.currentChapter?.paragraphs?.flatMap { it.sentences } ?: emptyList()
            ReaderTtsPanel(
                isPlaying = state.isPlaying,
                currentSentenceIndex = state.currentSentenceIndex,
                totalSentences = sentences.size,
                activeVoiceProfile = state.activeVoiceProfile,
                availableVoiceProfiles = state.availableVoiceProfiles,
                onPlayPause = {
                    viewModel.onIntent(if (state.isPlaying) ReaderIntent.Pause else ReaderIntent.PlayCurrentSentence)
                },
                onPreviousSentence = { viewModel.onIntent(ReaderIntent.SkipToPreviousSentence) },
                onNextSentence = { viewModel.onIntent(ReaderIntent.SkipToNextSentence) },
                onSpeedChange = { speed -> viewModel.onIntent(ReaderIntent.SetTtsSpeed(speed)) },
                onSelectVoiceProfile = { profileId -> viewModel.onIntent(ReaderIntent.SetActiveVoiceProfile(profileId)) },
                onOpenPronunciationRules = onOpenPronunciationRules,
                onDismiss = { showTtsPanel = false },
            )
        }

        // 3c.2 — Sommaire en bottom sheet : superposé, ne démonte plus le
        // lecteur (avant ce lot, `return@Column` remplaçait tout l'écran,
        // HUD compris). Même pattern que ReaderSettingsPanel/ReaderTtsPanel
        // ci-dessus : ModalBottomSheet se rend dans sa propre fenêtre, il
        // ne consomme pas d'espace dans cette Column.
        if (state.isTocVisible) {
            TableOfContentsSheet(
                entries = state.tableOfContents,
                currentChapterIndex = state.currentChapterIndex,
                onEntryClick = { chapterIndex -> viewModel.onIntent(ReaderIntent.JumpToChapter(chapterIndex)) },
                onClose = { viewModel.onIntent(ReaderIntent.ToggleToc) },
            )
        }

        // 3c.3 — Marque-pages en panneau latéral (≈85% de la largeur,
        // depuis la gauche) : superposé, ne démonte plus le lecteur, même
        // principe que le Sommaire ci-dessus.
        //
        // Bug réel trouvé sur appareil pendant la vérification du lot 3c :
        // contrairement à TableOfContentsSheet (ModalBottomSheet, rendu
        // dans sa propre fenêtre via Popup), BookmarkPanel était appelé
        // directement comme enfant de cette Column. Une Column place ses
        // enfants les uns SOUS les autres, elle ne les superpose jamais —
        // un enfant `fillMaxSize()` en dernière position se voyait donc
        // placé sous UnifiedControlPanel/StatusLineBar (toujours visibles
        // pendant 4s, la ligne de statut apparaissant au-dessus du panneau)
        // plutôt que par-dessus tout l'écran. `Dialog` (comme
        // ModalBottomSheet en interne) rend dans une fenêtre séparée,
        // superposée par construction, indépendamment de sa position dans
        // cette Column.
        if (state.isBookmarkListVisible) {
            Dialog(
                onDismissRequest = { viewModel.onIntent(ReaderIntent.ToggleBookmarkList) },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                BookmarkPanel(
                    bookmarks = state.bookmarks,
                    annotations = state.annotations,
                    isCurrentPageBookmarked = state.isCurrentPageBookmarked,
                    onBookmarkClick = { bookmark -> viewModel.onIntent(ReaderIntent.NavigateToLocator(bookmark.locator)) },
                    onAnnotationClick = { annotation -> viewModel.onIntent(ReaderIntent.NavigateToLocator(annotation.startLocator)) },
                    onToggleBookmark = { viewModel.onIntent(ReaderIntent.ToggleBookmarkAtCurrentPosition) },
                    onClose = { viewModel.onIntent(ReaderIntent.ToggleBookmarkList) },
                )
            }
        }
    }
    }
}

private val SLEEP_TIMER_OPTIONS_MINUTES = listOf(15, 30, 45, 60)

/**
 * Tâche 3b.6 — bascule cyclique du thème (icône Thème du panneau) : Clair
 * → Sombre → Sépia → Clair, sans ouvrir de panneau, sans retour visuel
 * autre que le changement lui-même. SYSTEM (jamais réglé par ce cycle,
 * seulement possible comme état initial hérité des préférences globales)
 * repart sur Clair plutôt que de rester coincé hors cycle.
 */
private fun nextReadingTheme(current: ReadingTheme): ReadingTheme = when (current) {
    ReadingTheme.LIGHT -> ReadingTheme.DARK
    ReadingTheme.DARK -> ReadingTheme.SEPIA
    ReadingTheme.SEPIA, ReadingTheme.SYSTEM -> ReadingTheme.LIGHT
}

/**
 * Tache 9bis.3.3 — un appui sur l'icone Veille fait cycler les durees
 * proposees puis desactive le minuteur (pas de sheet de selection dediee
 * pour l'instant, hors perimetre de cette tache).
 */
private fun nextSleepTimerMinutes(current: SleepTimerState?): Int? {
    if (current == null) return SLEEP_TIMER_OPTIONS_MINUTES.first()
    val currentMinutes = (current.remainingMs / 60_000L).toInt()
    val currentIndex = SLEEP_TIMER_OPTIONS_MINUTES.indexOf(currentMinutes)
    val nextIndex = currentIndex + 1
    return SLEEP_TIMER_OPTIONS_MINUTES.getOrNull(nextIndex)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SentenceText(
    sentence: Sentence,
    paragraphStyle: com.inktone.domain.model.ParagraphStyle = com.inktone.domain.model.ParagraphStyle.NORMAL,
    isCurrentlyPlaying: Boolean,
    highlightedWordRange: IntRange?,
    isSelected: Boolean,
    existingAnnotationColor: AnnotationColor?,
    fontSizeSp: Int,
    textColor: Color,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        isSelected -> SelectionHighlightColor
        existingAnnotationColor != null -> existingAnnotationColor.toComposeColor()
        else -> Color.Transparent
    }

    // B.4 — Style enrichi selon le type de paragraphe
    val styleModifier = when (paragraphStyle) {
        com.inktone.domain.model.ParagraphStyle.HEADING -> Modifier.padding(top = 8.dp, bottom = 4.dp)
        com.inktone.domain.model.ParagraphStyle.BLOCK_QUOTE -> Modifier
            .padding(start = 8.dp)
            .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
        com.inktone.domain.model.ParagraphStyle.POEM_LINE -> Modifier.padding(start = 16.dp)
        com.inktone.domain.model.ParagraphStyle.NORMAL -> Modifier
    }

    // Tache 9bis.3.5 — transition douce entre mots plutot qu'un changement
    // brut : le legacy n'avait pas de vrais timestamps CTC (surlignage
    // necessairement plus simple), on a maintenant de vrais WordTimestamp
    // (ADR-022). reducedMotionDuration (Tache 8.4) respecte le reglage
    // systeme, pas juste une preference applicative.
    val animationSpec = tween<Int>(durationMillis = reducedMotionDuration(150))
    val animatedStart by animateIntAsState(
        targetValue = highlightedWordRange?.first ?: 0,
        animationSpec = animationSpec,
        label = "highlightStart",
    )
    val animatedEnd by animateIntAsState(
        targetValue = highlightedWordRange?.last ?: 0,
        animationSpec = animationSpec,
        label = "highlightEnd",
    )

    Text(
        text = if (isCurrentlyPlaying && highlightedWordRange != null && sentence.text.isNotEmpty()) {
            val start = animatedStart.coerceIn(0, sentence.text.length - 1)
            val end = animatedEnd.coerceIn(start, sentence.text.length - 1)
            buildHighlightedSentence(sentence.text, start..end)
        } else {
            AnnotatedString(sentence.text)
        },
        modifier = modifier
            .then(styleModifier)
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        fontSize = fontSizeSp.sp,
        color = textColor,
    )
}

private val SelectionHighlightColor = Color(0x664FC3F7)

private fun buildHighlightedSentence(text: String, range: IntRange): AnnotatedString = buildAnnotatedString {
    append(text.substring(0, range.first))
    withStyle(SpanStyle(background = Color.Yellow)) {
        append(text.substring(range.first, range.last + 1))
    }
    append(text.substring(range.last + 1))
}

/**
 * Couleur de la première annotation existante couvrant [sentence]
 * (Tâche 7.1, critère de validation : le surlignage doit réapparaître à
 * la réouverture). Les annotations créées par cette UI ne portent
 * aujourd'hui que sur un seul chapitre (sélection par phrase à
 * l'intérieur du chapitre affiché) — comparer `chapterIndex` suffit, pas
 * besoin de gérer une plage à cheval sur plusieurs chapitres pour
 * l'instant.
 */
private fun annotationColorFor(chapterIndex: Int, sentence: Sentence, annotations: List<Annotation>): AnnotationColor? =
    annotations.firstOrNull { annotation ->
        annotation.startLocator.chapterIndex == chapterIndex &&
            sentence.startOffset < annotation.endLocator.charOffset &&
            sentence.endOffset > annotation.startLocator.charOffset
    }?.color

/**
 * B.4 — Placeholder pour une image EPUB. En attendant l'intégration de
 * Coil dans `feature/reader`, affiche l'alt text comme contenu de repli.
 */
@Composable
private fun EpubImagePlaceholder(href: String, altText: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                AppIcons.Reading,
                contentDescription = altText ?: "Image",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            if (altText != null) {
                Text(
                    altText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A.3 — État d'erreur affiché quand le parsing ou l'ouverture d'une
 * publication échoue. Affiche le message et un bouton pour réessayer
 * (retour à la bibliothèque implicite via [onRetry]).
 */
@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Retour à la bibliothèque")
        }
    }
}
