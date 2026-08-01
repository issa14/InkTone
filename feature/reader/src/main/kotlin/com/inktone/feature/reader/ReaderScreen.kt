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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.reducedMotionDuration
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.SleepTimerState

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
fun ReaderScreen(viewModel: ReaderViewModel = hiltViewModel(), onSearchClick: () -> Unit = {}) {
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
        BookProgressBar(progression = state.bookProgression)

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

        if (state.isTocVisible) {
            TableOfContentsSheet(
                entries = state.tableOfContents,
                currentChapterIndex = state.currentChapterIndex,
                onEntryClick = { chapterIndex -> viewModel.onIntent(ReaderIntent.JumpToChapter(chapterIndex)) },
            )
            return@Column
        }

        if (state.isBookmarkListVisible) {
            BookmarkListSheet(
                bookmarks = state.bookmarks,
                onBookmarkClick = { bookmark -> viewModel.onIntent(ReaderIntent.NavigateToLocator(bookmark.locator)) },
                onBookmarkDelete = { bookmark -> viewModel.onIntent(ReaderIntent.DeleteBookmark(bookmark.id)) },
            )
            return@Column
        }

        val scrollState = rememberScrollState()
        LaunchedEffect(state.currentChapterIndex) { scrollState.scrollTo(0) }

        val sentences = state.currentChapter?.paragraphs?.flatMap { it.sentences } ?: emptyList()
        val selectedRange = state.selectedSentenceRange
        var pendingColor by remember { mutableStateOf(AnnotationColor.YELLOW) }

        // A.1 / Tache 9bis.3.6 - position Y de la phrase active
        var currentLineYDp by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current

        // A.1 — Auto-scroll vers la phrase active pendant la lecture TTS
        LaunchedEffect(state.currentSentenceIndex) {
            if (state.isPlaying && currentLineYDp > 0.dp) {
                scrollState.animateScrollTo(with(density) { currentLineYDp.roundToPx() })
            }
        }

        // B.6 — ETA micro-indicateur quand HUD masqué
        val etaText = state.etaText

        Box(modifier = Modifier.weight(1f)) {
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
                                    isSelected = selectedRange?.contains(index) == true,
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
                                    modifier = if (isCurrentlyPlaying) {
                                        Modifier.onGloballyPositioned { coordinates ->
                                            currentLineYDp = with(density) { coordinates.positionInParent().y.toDp() }
                                        }
                                    } else {
                                        Modifier
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
                        sentences = sentences,
                        currentSentenceIndex = state.currentSentenceIndex,
                        highlightedWordRange = state.highlightedWordRange,
                        selectedRange = selectedRange,
                        annotations = state.annotations,
                        currentChapterIndex = state.currentChapterIndex,
                        fontSizeSp = state.effectiveSettings.fontSize,
                        textColor = ThemeColors.text(state.effectiveSettings.theme),
                        isPlaying = state.isPlaying,
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
                    )
                }
            }

            // B.6 — Micro-indicateur ETA quand HUD masqué
            if (!isHudVisible && etaText.isNotEmpty() && state.readingMode == ReadingMode.SCROLL) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = etaText,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // B.7 — Captions TTS (overlay sous-titres)
            if (state.isPlaying && selectedRange == null) {
                val captionText = sentences.getOrNull(state.currentSentenceIndex)?.text
                if (captionText != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            text = captionText,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        if (selectedRange != null) {
            AnnotationColorPicker(
                selected = pendingColor,
                onSelect = { pendingColor = it },
                onConfirm = { viewModel.onIntent(ReaderIntent.ConfirmAnnotation(pendingColor)) },
                onCancel = { viewModel.onIntent(ReaderIntent.ClearSentenceSelection) },
            )
        }

        if (isHudVisible) {
            UnifiedControlPanel(
                isPlaying = state.isPlaying,
                sleepTimerActive = state.sleepTimer != null,
                onPlayPause = {
                    keepHudVisible()
                    viewModel.onIntent(if (state.isPlaying) ReaderIntent.Pause else ReaderIntent.PlayCurrentSentence)
                },
                onPreviousChapter = { keepHudVisible(); viewModel.onIntent(ReaderIntent.PreviousChapter) },
                onNextChapter = { keepHudVisible(); viewModel.onIntent(ReaderIntent.NextChapter) },
                onSleepTimerClick = {
                    keepHudVisible()
                    viewModel.onIntent(ReaderIntent.SetSleepTimer(nextSleepTimerMinutes(state.sleepTimer)))
                },
                onSearchClick = { keepHudVisible(); onSearchClick() },
                onBookmarksClick = { keepHudVisible(); viewModel.onIntent(ReaderIntent.ToggleBookmarkList) },
                onTocClick = { keepHudVisible(); viewModel.onIntent(ReaderIntent.ToggleToc) },
                onAaClick = { keepHudVisible(); showSettingsPanel = true },
                onTtsClick = { keepHudVisible(); showTtsPanel = true },
                onReadingModeClick = { keepHudVisible(); viewModel.onIntent(ReaderIntent.ToggleReadingMode) },
                hasPreviousChapter = state.hasPreviousChapter,
                hasNextChapter = state.hasNextChapter,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { keepHudVisible(); viewModel.onIntent(ReaderIntent.CreateBookmark) }) {
                    Text("+ Signet")
                }
            }
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
                currentSpeed = 1.0f, // TODO: lire depuis preferences TTS speed
                onPlayPause = {
                    viewModel.onIntent(if (state.isPlaying) ReaderIntent.Pause else ReaderIntent.PlayCurrentSentence)
                },
                onStop = { viewModel.onIntent(ReaderIntent.Pause) },
                onPreviousSentence = { viewModel.onIntent(ReaderIntent.SkipToPreviousSentence) },
                onNextSentence = { viewModel.onIntent(ReaderIntent.SkipToNextSentence) },
                onSpeedChange = { /* TODO: UpdatePreferencesUseCase(speed) */ },
                onSleepTimer = { minutes ->
                    viewModel.onIntent(ReaderIntent.SetSleepTimer(minutes))
                },
                currentSleepTimerMinutes = state.sleepTimer?.let {
                    ((it.remainingMs / 60_000L).toInt())
                },
                onDismiss = { showTtsPanel = false },
            )
        }
    }
    }
}

private val SLEEP_TIMER_OPTIONS_MINUTES = listOf(15, 30, 45, 60)

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
