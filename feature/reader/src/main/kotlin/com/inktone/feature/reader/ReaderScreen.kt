package com.inktone.feature.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.reducedMotionDuration
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Paragraph
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.Sentence
import com.inktone.feature.reader.pagination.rememberChapterPaginationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
 * **Palier 3f.1-3f.3 — sélection libre au mot, PAGED et SCROLL.** Un
 * `BasicTextField` en lecture seule par unité adressable (une page en
 * PAGED, voir `PagedChapterContent.PageBlock` ; un `Paragraph` en SCROLL,
 * voir `ParagraphText` ci-dessous) délègue à la sélection NATIVE de
 * Compose (appui long calé au mot, poignées et loupe natives, glissement
 * caractère par caractère) plutôt que de la réimplémenter à la main —
 * mécanisme identique dans les deux modes, seul le grain de l'unité
 * adressable diffère. Sélection bornée à cette unité par construction :
 * jamais à cheval sur deux pages (PAGED) ni sur deux paragraphes (SCROLL),
 * limite structurelle plutôt qu'un choix produit séparé à instrumenter.
 * `ReaderUiState.freeSelectionRange` (offsets de caractère absolus au
 * chapitre) est le seul état, commun aux deux modes.
 *
 * **Palier 3f.3bis — grain SCROLL, phrase → paragraphe.** Un
 * `BasicTextField` par `Sentence` (grain initial de 3f.3) composait des
 * centaines d'instances simultanées sur un chapitre typique — `FlowRow`
 * et `verticalScroll` composent tout le chapitre d'un coup (pas de
 * `LazyColumn`, voir ci-dessus), et `BasicTextField` est nettement plus
 * lourd qu'un `Text`. Bug réel trouvé sur appareil : glissement de
 * sélection ET défilement simple saccadés. Regroupé par `Paragraph` —
 * facteur 3-8x moins d'instances pour un chapitre typique, aligné avec la
 * frontière déjà utilisée pour les images EPUB intercalées.
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
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    // ───── Lot Sessions : pause/reprise sur changement de visibilité ─────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackground()
                Lifecycle.Event.ON_START -> viewModel.onAppForeground()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // ───── Fin Lot Sessions ─────

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
    // 3d.3 — visibilité locale de la barre de luminosité, même patron que
    // les panneaux ci-dessus : purement une décision d'affichage, pas un
    // état MVI (ReaderUiState.readerBrightness porte la vraie donnée).
    //
    // Bug réel trouvé à la vérification device (lot 3d) : la barre ne
    // disparaissait plus jamais une fois ouverte, y compris aux
    // réapparitions ultérieures du HUD. Corrigé : ouvrir la barre masque
    // le HUD (au lieu de le garder visible) et démarre un délai
    // d'auto-masquage — même principe que hudActivityTick/ImmersiveReaderChrome,
    // relancé à chaque ajustement (brightnessBarActivityTick).
    var showBrightnessBar by remember { mutableStateOf(false) }
    var brightnessBarActivityTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(showBrightnessBar, brightnessBarActivityTick) {
        if (showBrightnessBar) {
            delay(3000)
            showBrightnessBar = false
        }
    }

    // 3e.1 (point A5) — pendant la lecture TTS, le panneau unifié est
    // remplacé par la barre pilule (voir plus bas) ; ce drapeau local
    // rappelle le panneau complet par-dessus la barre le temps d'un appui,
    // pour atteindre Sommaire/TT/etc. sans interrompre la lecture.
    var showFullPanelOverlay by remember { mutableStateOf(false) }
    // 3e.2 — repli de la barre pilule en bouton unique après 4s
    // d'inactivité, réutilisant le délai d'ImmersiveReaderChrome
    // (onAutoHide ci-dessous) : pas de second minuteur. isHudVisible
    // reste vrai pendant le repli — seule la forme de son contenu change,
    // ce n'est pas un masquage complet.
    var isPillCollapsed by remember { mutableStateOf(false) }
    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) {
            showFullPanelOverlay = false
            isPillCollapsed = false
        }
    }

    // Ferme la barre de luminosité si elle est ouverte, sinon applique le
    // basculement HUD habituel — un appui sur la zone de lecture pendant
    // l'ajustement de luminosité doit fermer la barre, pas rouvrir le HUD
    // par-dessus dans le même geste.
    fun handleReadingAreaTap() {
        if (showBrightnessBar) {
            showBrightnessBar = false
        } else if (state.isPlaying) {
            // Pendant le TTS, la barre pilule remplace le panneau unifié
            // (retiré du HUD standard) : un premier appui rappelle le
            // panneau complet, un second referme tout, un troisième
            // ramène la barre pilule — le même rythme à trois temps que le
            // cycle HUD habituel, appliqué à un état de plus.
            if (showFullPanelOverlay) {
                showFullPanelOverlay = false
                isHudVisible = false
            } else if (isHudVisible) {
                showFullPanelOverlay = true
                isPillCollapsed = false
            } else {
                keepHudVisible()
                isPillCollapsed = false
            }
        } else if (isHudVisible) {
            isHudVisible = false
        } else {
            keepHudVisible()
        }
    }
    // 3d.4 — remplace le cycle nextSleepTimerMinutes : le tap sur Veille
    // ouvre désormais un panneau (puces + roue), au lieu de cycler
    // silencieusement 15→30→45→60 sans rien afficher.
    var showSleepTimerPanel by remember { mutableStateOf(false) }

    ImmersiveReaderChrome(
        isHudVisible = isHudVisible,
        hudActivityTick = hudActivityTick,
        onAutoHide = {
            // 3e.2 — pendant le TTS (barre pilule affichée, pas l'overlay
            // panneau complet), l'inactivité replie la barre au lieu de
            // masquer tout le HUD : la lecture et le surlignage restent
            // visibles en permanence via le bouton replié.
            if (state.isPlaying && !showFullPanelOverlay) {
                isPillCollapsed = true
            } else {
                isHudVisible = false
            }
        },
    ) {
    // 3d.3 — applique la luminosité choisie à la fenêtre du lecteur
    // seulement, restaurée à la sortie (voir ReaderBrightnessEffect).
    ReaderBrightnessEffect(value = state.readerBrightness)

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

    // Refonte HUD/viewport (voir NOTE_REGRESSION_CLIGNOTEMENT_PAGE_HUD.md,
    // option 3) : Box plutôt que Column — la zone de lecture occupe
    // désormais toute la surface disponible en permanence (fillMaxSize,
    // ci-dessous), et le HUD (barre du haut, panneau/pilule, ligne de
    // statut) flotte PAR-DESSUS en pur overlay (`Modifier.align`), sans
    // jamais redistribuer cet espace. Avant ce correctif, ReaderTopBar et
    // UnifiedControlPanel étaient montés/démontés dans la même Column que
    // la zone de lecture (`Modifier.weight(1f)`) : chaque bascule du HUD
    // redimensionnait donc la zone de lecture, ce qui redéclenchait une
    // remesure complète de la pagination (`readingAreaSize` → `styleKey`,
    // voir `ChapterPaginationState`) pendant que l'utilisateur lisait —
    // cause racine du clignotement de page documenté dans cette note.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(sharedElementMod)
            .background(ThemeColors.background(state.resolvedTheme))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { handleReadingAreaTap() }
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
            return@Box
        }

        val scrollState = rememberScrollState()
        LaunchedEffect(state.currentChapterIndex) { scrollState.scrollTo(0) }

        val freeSelectedRange = state.freeSelectionRange

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
        // (depuis `ParagraphText.onSentenceTopOffsets`, une seule fois par
        // paragraphe au layout — pas par phrase depuis le palier 3f.3bis,
        // ni à chaque frame de défilement) n'ont pas besoin de déclencher
        // de recomposition, seule la lecture dans le derivedStateOf
        // ci-dessous compte, déjà réactive via scrollState.value.
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
        //
        // Bornes fenêtre de la sélection libre active, par mode — un seul
        // Rect (pas une map à unioner) puisque la sélection libre au mot
        // est bornée à une seule unité adressable (une page en PAGED, un
        // paragraphe en SCROLL — voir PagedChapterContent.PageBlock /
        // ReaderScreen.ParagraphText, doc de tête), jamais à cheval sur
        // plusieurs à unir.
        var pagedFreeSelectionBounds by remember { mutableStateOf<Rect?>(null) }
        var scrollFreeSelectionBounds by remember(state.currentChapterIndex) { mutableStateOf<Rect?>(null) }
        val selectionBoundsInWindow = when (state.readingMode) {
            ReadingMode.SCROLL -> scrollFreeSelectionBounds
            ReadingMode.PAGED -> pagedFreeSelectionBounds
        }
        val selectedText = remember(freeSelectedRange, state.currentChapter) {
            val freeRange = freeSelectedRange ?: return@remember ""
            val chapterSentences = state.currentChapter?.paragraphs?.flatMap { it.sentences } ?: emptyList()
            sliceChapterText(chapterSentences, freeRange.first, freeRange.last + 1)
        }

        // 3b.5 — barre du haut : appartient au HUD, apparaît/disparaît
        // avec le panneau, jamais indépendamment (même gate isHudVisible).
        // Overlay pur (`align(TopCenter)`) : ne modifie plus jamais les
        // bornes de la zone de lecture (voir commentaire de tête ci-dessus).
        if (isHudVisible) {
            ReaderTopBar(
                title = state.title,
                author = state.author,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // 3b.1 — état de pagination hissé au-dessus du choix de mode :
        // sert la ligne de statut (3b.4, tous modes) ET PagedChapterContent
        // (mode pagé), un seul calcul. Mesuré au même endroit de
        // l'arborescence pour les deux modes (ce Box), formule unique de
        // hauteur utile (voir ChapterPaginationState).
        //
        // readingAreaSize mesure désormais la zone de lecture SEULE
        // (fillMaxSize du Box ci-dessous, plus jamais weight(1f) partagé
        // avec le HUD) : elle ne varie plus qu'au redimensionnement réel de
        // la fenêtre (rotation...) ou au changement de padding/police —
        // jamais à la bascule du HUD. Corrige la régression documentée dans
        // docs/execution/NOTE_REGRESSION_CLIGNOTEMENT_PAGE_HUD.md (option 3
        // retenue) : la remesure de pagination déclenchée par chaque
        // masquage/réapparition du HUD, dont les étapes intermédiaires
        // faisaient clignoter la page affichée, ne se produit plus.
        var readingAreaSize by remember { mutableStateOf(IntSize.Zero) }
        val paginationPaddingPx = with(density) { 16.dp.roundToPx() }
        // 3d.2 — interligne en sp, combiné à fontSize (multiplicateur
        // global, voir UserPreferences.lineHeightMultiplier) : seul point de
        // calcul, consommé à la fois par la mesure de pagination et par le
        // rendu en mode SCROLL (ParagraphText) ci-dessous.
        val lineHeightSp = (state.effectiveSettings.fontSize * state.lineHeightMultiplier).roundToInt()
        // Lot 9 — police effective (préférence explicite si définie, sinon
        // celle du thème actif) : entre dans la clé d'invalidation de la
        // pagination via `baseTextStyle`, jamais les couleurs.
        val effectiveFontFamily = remember(state.effectiveSettings.fontFamily, state.resolvedTheme.fontFamily) {
            ThemeColors.toComposeFontFamily(ThemeColors.effectiveFontFamily(state.effectiveSettings, state.resolvedTheme))
        }
        val pagination = rememberChapterPaginationState(
            chapter = state.currentChapter,
            nextChapter = state.chapters.getOrNull(state.currentChapterIndex + 1),
            currentSentenceIndex = state.currentSentenceIndex,
            fontSizeSp = state.effectiveSettings.fontSize,
            lineHeightSp = lineHeightSp,
            viewportWidthPx = readingAreaSize.width,
            viewportHeightPx = readingAreaSize.height,
            paddingPx = paginationPaddingPx,
            fontFamily = effectiveFontFamily,
        )

        // Lot 4, tâche 4.7 — signale la fin de mise en page du chapitre
        // affiché seulement quand la mesure couvre la totalité de ses
        // phrases (pas seulement la première page, mesurée en priorité —
        // voir ChapterPaginationState) : c'est ce qui rend le flash fiable
        // même sur un chapitre long où la mesure complète prend du temps.
        val pendingHighlightTarget = state.pendingHighlightTarget
        LaunchedEffect(pendingHighlightTarget, pagination.measurement, state.currentChapterIndex) {
            val target = pendingHighlightTarget ?: return@LaunchedEffect
            val chapter = state.currentChapter ?: return@LaunchedEffect
            if (chapter.index != target.chapterIndex) return@LaunchedEffect
            val totalSentences = chapter.paragraphs.sumOf { it.sentences.size }
            val measuredSentences = pagination.measurement?.sentenceStartOffsets?.size ?: 0
            if (measuredSentences >= totalSentences) {
                viewModel.onIntent(ReaderIntent.ChapterLayoutCompleted(chapter.index))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
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
                            val paragraphStartIndex = globalIndex
                            globalIndex += paragraph.sentences.size
                            if (paragraph.sentences.isNotEmpty()) {
                                ParagraphText(
                                    paragraph = paragraph,
                                    globalStartIndex = paragraphStartIndex,
                                    currentSentenceIndex = state.currentSentenceIndex,
                                    highlightedWordRange = state.highlightedWordRange,
                                    annotations = state.annotations,
                                    chapterIndex = state.currentChapterIndex,
                                    freeSelectionRange = freeSelectedRange,
                                    fontSizeSp = state.effectiveSettings.fontSize,
                                    lineHeightSp = lineHeightSp,
                                    textColor = ThemeColors.text(state.resolvedTheme),
                                    fontFamily = effectiveFontFamily,
                                    // Le FlowRow couvre la quasi-totalité de la zone de
                                    // lecture : sans ce relais, le tap est consommé par
                                    // ParagraphText et ne remonte jamais au Column parent,
                                    // rendant le HUD quasiment impossible à rappeler une
                                    // fois masqué (bug réel trouvé à l'audit).
                                    onClick = { handleReadingAreaTap() },
                                    onFreeSelectionChanged = { anchor, focus ->
                                        viewModel.onIntent(ReaderIntent.SetFreeSelection(anchor, focus))
                                    },
                                    onFreeSelectionCleared = { viewModel.onIntent(ReaderIntent.ClearFreeSelection) },
                                    onFreeSelectionBoundsInWindow = { bounds -> scrollFreeSelectionBounds = bounds },
                                    onSentenceTopOffsets = { offsets -> sentenceTopOffsetsPx.putAll(offsets) },
                                    onCurrentLineY = { y -> currentLineYDp = y },
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
                        annotations = state.annotations,
                        currentChapterIndex = state.currentChapterIndex,
                        textColor = ThemeColors.text(state.resolvedTheme),
                        isReadingRulerEnabled = state.isReadingRulerEnabled,
                        onClick = { handleReadingAreaTap() },
                        onNextChapter = { viewModel.onIntent(ReaderIntent.NextChapter) },
                        onCurrentLineY = { y -> currentLineYDp = y },
                        onPageChanged = { pageIndex -> pagedLivePageIndex = pageIndex },
                        onManualPageChange = { sentenceIndex ->
                            viewModel.onIntent(ReaderIntent.UpdateScrollPosition(sentenceIndex))
                        },
                        freeSelectedRange = freeSelectedRange,
                        onFreeSelectionChanged = { anchor, focus ->
                            viewModel.onIntent(ReaderIntent.SetFreeSelection(anchor, focus))
                        },
                        onFreeSelectionCleared = { viewModel.onIntent(ReaderIntent.ClearFreeSelection) },
                        onFreeSelectionBoundsInWindow = { bounds -> pagedFreeSelectionBounds = bounds },
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
        if (freeSelectedRange != null) {
            SelectionActionPopup(
                selectedText = selectedText,
                selectionBoundsInWindow = selectionBoundsInWindow,
                onHighlight = { color ->
                    viewModel.onIntent(ReaderIntent.ConfirmAnnotation(color))
                },
                onSaveNote = { content, color ->
                    viewModel.onIntent(ReaderIntent.ConfirmAnnotation(color, content))
                },
                onDismiss = { viewModel.onIntent(ReaderIntent.ClearFreeSelection) },
            )
        }

        // Pile basse du HUD (panneau/pilule TTS, barre de luminosité, ligne
        // de statut) : overlay pur (`align(BottomCenter)`), plus jamais un
        // enchaînement de siblings dans la Column de lecture (voir
        // commentaire de tête ci-dessus). Ordre visuel préservé à
        // l'identique (panneau/pilule au-dessus de la barre de luminosité,
        // elle-même au-dessus de la ligne de statut) via une simple Column
        // interne. `StatusLineBar` est la seule pièce de cette pile sans
        // fond propre (Row nue) — auparavant hors de tout risque de
        // chevauchement puisque la Column réservait son propre espace ; en
        // overlay, un dégradé discret en arrière-plan (scrim) lui redonne
        // la même lisibilité garantie face au texte qui peut désormais se
        // trouver juste derrière.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ThemeColors.background(state.resolvedTheme).copy(alpha = 0.9f),
                            ),
                        ),
                    ),
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                if (isHudVisible) {
                    // 3e.1 — pendant le TTS, la barre pilule remplace le panneau
                    // unifié (navigation par chapitre, retirée du panneau au lot
                    // 3b) ; le panneau complet ne revient que par l'overlay A5 ou
                    // à l'arrêt de la lecture.
                    if (state.isPlaying && !showFullPanelOverlay) {
                        // 3e.2 — repli en bouton unique après 4s (isPillCollapsed,
                        // voir onAutoHide plus haut).
                        //
                        // Bug réel trouvé à la vérification device : un Crossfade
                        // partagé sur une seule position centrée faisait replier le
                        // FAB au centre au lieu du coin inférieur droit attendu, et
                        // le redéploiement se voyait glisser du centre vers la
                        // gauche — Crossfade redimensionne sa boîte interne vers la
                        // taille de la cible pendant la transition, donc un enfant
                        // bien plus étroit (le FAB, 56dp) que l'autre (la barre,
                        // ~250dp) décale visuellement le centrage réel en cours de
                        // fondu. Deux positions fixes dans un Box commun (Center
                        // pour la barre, CenterEnd — coin droit — pour le FAB),
                        // chacune animée en opacité seule via animateFloatAsState :
                        // aucune position n'est jamais interpolée, un pur fondu.
                        // AnimatedVisibility n'a pas d'overload BoxScope (seulement
                        // Column/RowScope) : inapplicable ici.
                        val fadeDuration = if (state.reduceMotion) 0 else 200
                        val barAlpha by animateFloatAsState(
                            targetValue = if (isPillCollapsed) 0f else 1f,
                            animationSpec = tween(fadeDuration),
                            label = "TtsPillBarAlpha",
                        )
                        val fabAlpha by animateFloatAsState(
                            targetValue = if (isPillCollapsed) 1f else 0f,
                            animationSpec = tween(fadeDuration),
                            label = "TtsPillBarCollapsedAlpha",
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (barAlpha > 0f) {
                                TtsPillBar(
                                    modifier = Modifier.align(Alignment.Center).graphicsLayer(alpha = barAlpha),
                                    isPlaying = state.isPlaying,
                                    isAudioActive = state.isAudioActive,
                                    reduceMotion = state.reduceMotion,
                                    hasPreviousChapter = state.hasPreviousChapter,
                                    hasNextChapter = state.hasNextChapter,
                                    onPreviousChapter = { keepHudVisible(); viewModel.onIntent(ReaderIntent.PreviousChapter) },
                                    onPreviousSentence = { keepHudVisible(); viewModel.onIntent(ReaderIntent.SkipToPreviousSentence) },
                                    onPlayPause = {
                                        keepHudVisible()
                                        viewModel.onIntent(if (state.isPlaying) ReaderIntent.Pause else ReaderIntent.PlayCurrentSentence)
                                    },
                                    onNextSentence = { keepHudVisible(); viewModel.onIntent(ReaderIntent.SkipToNextSentence) },
                                    onNextChapter = { keepHudVisible(); viewModel.onIntent(ReaderIntent.NextChapter) },
                                    // 3e.3 — balayage redéfini en pause (voir KDoc de
                                    // TtsPillBarCollapsed) : pas de keepHudVisible, le
                                    // passage à isPlaying=false referme déjà la barre
                                    // et fait revenir le panneau unifié.
                                    onSwipeDown = { viewModel.onIntent(ReaderIntent.Pause) },
                                )
                            }
                            if (fabAlpha > 0f) {
                                TtsPillBarCollapsed(
                                    modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer(alpha = fabAlpha),
                                    isAudioActive = state.isAudioActive,
                                    reduceMotion = state.reduceMotion,
                                    onExpand = {
                                        isPillCollapsed = false
                                        keepHudVisible()
                                    },
                                    onSwipeDown = { viewModel.onIntent(ReaderIntent.Pause) },
                                )
                            }
                        }
                    } else {
                        UnifiedControlPanel(
                            isPlaying = state.isPlaying,
                            sleepTimerActive = state.sleepTimer != null,
                            bookProgression = state.bookProgression,
                            onPlayPause = {
                                keepHudVisible()
                                viewModel.onIntent(if (state.isPlaying) ReaderIntent.Pause else ReaderIntent.PlayCurrentSentence)
                            },
                            onSleepTimerClick = { keepHudVisible(); showSleepTimerPanel = true },
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
                            onBrightnessClick = {
                                // Bug réel corrigé (vérification device, lot 3d) : masque le
                                // HUD au lieu de le garder visible — la barre prend sa place
                                // plutôt que de s'empiler dessous.
                                isHudVisible = false
                                showBrightnessBar = true
                                brightnessBarActivityTick++
                            },
                        )
                    }
                }

                // 3d.3 — barre flottante à la place du panneau unifié (HUD masqué
                // pendant l'ajustement), pas un panneau séparé (UX_FLOW_DESIGN.md
                // §Luminosité). S'auto-masque après 3s d'inactivité (voir
                // LaunchedEffect ci-dessus), relancé à chaque ajustement.
                if (showBrightnessBar) {
                    ReaderBrightnessBar(
                        value = state.readerBrightness,
                        onValueChange = { value ->
                            viewModel.onIntent(ReaderIntent.SetReaderBrightness(value))
                            brightnessBarActivityTick++
                        },
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
            }
        }

        // B.2/3d.2 — Panneau réglages de typographie in-reader (TT). Le
        // thème n'y vit plus (bascule cyclique du lot 3b, cartes devenues
        // redondantes) — seuls taille et interligne restent, avec un
        // aperçu du texte RÉELLEMENT en cours de lecture, pas un exemple
        // inventé.
        if (showSettingsPanel) {
            val previewSentences = state.currentChapter?.paragraphs?.flatMap { it.sentences }.orEmpty()
            val previewText = previewSentences
                .drop(state.currentSentenceIndex)
                .ifEmpty { previewSentences }
                .take(3)
                .joinToString(" ") { it.text }
            ReaderSettingsPanel(
                currentFontSize = state.effectiveSettings.fontSize,
                currentLineHeightMultiplier = state.lineHeightMultiplier,
                previewText = previewText,
                previewTextColor = ThemeColors.text(state.resolvedTheme),
                previewBackgroundColor = ThemeColors.background(state.resolvedTheme),
                onFontSizeChange = { size ->
                    val overrides = (state.currentOverrides ?: ReadingOverrides()).copy(fontSize = size)
                    viewModel.onIntent(ReaderIntent.SetOverrides(overrides))
                },
                onLineHeightChange = { multiplier -> viewModel.onIntent(ReaderIntent.SetLineHeight(multiplier)) },
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

        // 3d.4/3d.5 — Panneau Minuteur (remplace le cycle sur l'icône Veille) + repos oculaire
        if (showSleepTimerPanel) {
            SleepTimerPanel(
                remainingMinutes = state.sleepTimer?.let { (it.remainingMs / 60_000L).toInt() },
                onSetSleepTimer = { minutes -> viewModel.onIntent(ReaderIntent.SetSleepTimer(minutes)) },
                eyeRestReminderEnabled = state.eyeRestReminderEnabled,
                eyeRestReminderIntervalMinutes = state.eyeRestReminderIntervalMinutes,
                onSetEyeRestReminderEnabled = { enabled -> viewModel.onIntent(ReaderIntent.SetEyeRestReminderEnabled(enabled)) },
                onSetEyeRestReminderInterval = { minutes -> viewModel.onIntent(ReaderIntent.SetEyeRestReminderInterval(minutes)) },
                onDismiss = { showSleepTimerPanel = false },
            )
        }

        // 3d.5 — popup de rappel de repos oculaire, indépendant du HUD :
        // doit rester visible même si isHudVisible est déjà retombé à
        // faux (contrairement aux panneaux ci-dessus, ouverts par une
        // action HUD explicite).
        if (state.isEyeRestReminderVisible) {
            EyeRestReminderDialog(
                countdownSeconds = state.eyeRestReminderCountdownS,
                onResume = { viewModel.onIntent(ReaderIntent.ResumeFromEyeRestReminder) },
                onSnooze = { viewModel.onIntent(ReaderIntent.SnoozeEyeRestReminder) },
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

        // Lot 10 — retour Issa (vérification device) : proposition
        // proactive de la voix neuronale au premier usage réel du TTS.
        // "Télécharger" ouvre les Réglages (carte Lecture) où le
        // téléchargement réel se confirme et se suit — pas de logique de
        // téléchargement dupliquée ici.
        if (state.showVoiceDownloadPrompt) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.onIntent(ReaderIntent.DismissVoiceDownloadPrompt) },
                title = { Text("Voix neuronale disponible") },
                text = {
                    Text("Une voix plus naturelle peut être téléchargée (environ 126 Mo, une seule fois). La lecture visuelle et la voix actuelle restent disponibles sans cela.")
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.onIntent(ReaderIntent.DismissVoiceDownloadPrompt)
                        onOpenSettings()
                    }) { Text("Télécharger") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { viewModel.onIntent(ReaderIntent.DismissVoiceDownloadPrompt) }) {
                        Text("Plus tard")
                    }
                },
            )
        }
    }
    }
}

/**
 * Tâche 3b.6/9.2 — bascule cyclique du thème (icône Thème du panneau) :
 * Papier Clair → Obsidienne → Sépia Vintage → Papier Clair, sans ouvrir
 * de panneau, sans retour visuel autre que le changement lui-même.
 *
 * Lot 9 — tranché : cycle borné sur `ReadingTheme.CYCLE` (3 ambiances de
 * référence), pas sur l'ensemble ouvert des thèmes personnalisés — un
 * cycle sur un catalogue de taille arbitraire serait impraticable au
 * geste rapide (l'utilisateur passerait N fois pour revenir). Reprend
 * exactement le mapping de l'ancien enum LIGHT→DARK→SEPIA→LIGHT, aucune
 * surprise. Un id hors cycle (thème personnalisé actif, ou ancien
 * `SYSTEM` jamais réglé par ce cycle) repart sur la première ambiance du
 * cycle plutôt que de rester coincé hors cycle.
 */
internal fun nextReadingTheme(currentId: String): String {
    val cycle = ReadingTheme.CYCLE.map { it.id }
    val index = cycle.indexOf(currentId)
    return if (index == -1 || index == cycle.lastIndex) cycle.first() else cycle[index + 1]
}

/**
 * Palier 3f.3bis — correctif de performance (diagnostic sur appareil :
 * glissement de sélection ET défilement simple saccadés). L'unité
 * adressable de la sélection libre au mot en SCROLL n'est plus la
 * `Sentence` (un `BasicTextField` par phrase, potentiellement des
 * centaines simultanément puisque `FlowRow`/`verticalScroll` composent
 * tout le chapitre d'un coup — voir doc de tête du fichier) mais le
 * `Paragraph` : un facteur 3-8x moins d'instances pour un chapitre
 * typique (nombre moyen de phrases par paragraphe), et une frontière déjà
 * naturelle (les images EPUB sont déjà intercalées entre paragraphes).
 * Conséquence assumée : la sélection libre au mot est bornée au
 * PARAGRAPHE, pas à la phrase — plus permissif qu'avant, limite
 * structurelle du découpage comme en PAGED (bornée à la page).
 *
 * `mapping` (voir [buildParagraphTextMapping]) reconstruit le texte du
 * paragraphe en joignant ses phrases par un espace simple — même
 * approximation déjà utilisée ailleurs dans ce fichier
 * (`selectedText`/`sliceChapterText`) pour l'espacement inter-phrases,
 * pas les espaces/retours réels du texte source — et porte la
 * correspondance offset LOCAL (dans ce texte reconstruit) ↔ offset
 * ABSOLU (chapitre), utilisée pour DEUX choses distinctes : convertir la
 * sélection locale en offsets absolus (`onFreeSelectionChanged`), et
 * répartir les styles (annotations, sélection, mot TTS, alpha par
 * phrase) sur leurs sous-plages locales exactes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ParagraphText(
    paragraph: Paragraph,
    globalStartIndex: Int,
    currentSentenceIndex: Int,
    highlightedWordRange: IntRange?,
    annotations: List<Annotation>,
    chapterIndex: Int,
    // Palier 3f.3bis — offsets de caractère ABSOLUS au chapitre (comme
    // `ReaderUiState.freeSelectionRange`), pas locaux à ce paragraphe :
    // convertis en interne via `mapping`, seule cette fonction connaît la
    // position du paragraphe dans le chapitre.
    freeSelectionRange: IntRange?,
    fontSizeSp: Int,
    lineHeightSp: Int = fontSizeSp,
    textColor: Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    onClick: () -> Unit,
    onFreeSelectionChanged: (anchorOffset: Int, focusOffset: Int) -> Unit = { _, _ -> },
    onFreeSelectionCleared: () -> Unit = {},
    onFreeSelectionBoundsInWindow: (Rect?) -> Unit = {},
    // 3c.1 — remplace l'ancien `onGloballyPositioned` par phrase : un seul
    // calcul par paragraphe (voir plus bas), qui redonne la position de
    // CHAQUE phrase via `TextLayoutResult.getLineForOffset`, pas un
    // `onGloballyPositioned` par composable.
    onSentenceTopOffsets: (Map<Int, Int>) -> Unit = {},
    onCurrentLineY: (Dp) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val mapping = remember(paragraph) { buildParagraphTextMapping(paragraph) }
    val paragraphStartOffset = paragraph.sentences.firstOrNull()?.startOffset ?: 0
    val paragraphEndOffset = paragraph.sentences.lastOrNull()?.endOffset ?: 0
    val density = LocalDensity.current

    // B.4 — Style enrichi selon le type de paragraphe (une fois par
    // paragraphe désormais, plus une fois par phrase redondamment).
    val styleModifier = when (paragraph.style) {
        com.inktone.domain.model.ParagraphStyle.HEADING -> Modifier.padding(top = 8.dp, bottom = 4.dp)
        com.inktone.domain.model.ParagraphStyle.BLOCK_QUOTE -> Modifier
            .padding(start = 8.dp)
            .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
        com.inktone.domain.model.ParagraphStyle.POEM_LINE -> Modifier.padding(start = 16.dp)
        com.inktone.domain.model.ParagraphStyle.NORMAL -> Modifier
    }

    val isCurrentSentenceInParagraph = currentSentenceIndex >= globalStartIndex &&
        currentSentenceIndex < globalStartIndex + paragraph.sentences.size

    // Tache 9bis.3.5 — transition douce entre mots plutot qu'un changement
    // brut : le legacy n'avait pas de vrais timestamps CTC (surlignage
    // necessairement plus simple), on a maintenant de vrais WordTimestamp
    // (ADR-022). reducedMotionDuration (Tache 8.4) respecte le reglage
    // systeme, pas juste une preference applicative. Calculé une fois par
    // paragraphe (plus par phrase) — seule la phrase en cours de lecture,
    // si elle appartient à ce paragraphe, s'en sert.
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

    var selection by remember(paragraphStartOffset) { mutableStateOf(TextRange.Zero) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var pendingShowJob by remember(paragraphStartOffset) { mutableStateOf<Job?>(null) }

    // Resynchronisation externe → locale (même raison qu'en PAGED, voir
    // `PageBlock`) : si `freeSelectionRange` (état remonté par le
    // ViewModel) ne pointe plus vers CE paragraphe — devenu `null` (bouton
    // Annuler du popup, `ClearFreeSelection`) OU pointant désormais vers
    // un AUTRE paragraphe (l'utilisateur a sélectionné un mot ailleurs) —
    // la sélection LOCALE de ce champ doit être effacée, sinon ses
    // poignées natives resteraient affichées sur une sélection que le
    // reste de l'écran a déjà oubliée ou transférée ailleurs.
    LaunchedEffect(freeSelectionRange, paragraphStartOffset, paragraphEndOffset) {
        val stillOwnsSelection = freeSelectionRange != null &&
            freeSelectionRange.first >= paragraphStartOffset &&
            freeSelectionRange.last < paragraphEndOffset
        if (!stillOwnsSelection && !selection.collapsed) {
            selection = TextRange.Zero
        }
    }

    // 3c.1 — position de CHAQUE phrase du paragraphe (indépendante du
    // défilement, comme avant ce palier) dérivée du layout du paragraphe
    // (une seule mesure) plutôt que d'un `onGloballyPositioned` par
    // phrase — ne se redéclenche QUE quand le layout ou la position du
    // paragraphe change réellement, jamais à chaque tick TTS.
    LaunchedEffect(textLayoutResult, textCoordinates) {
        val layout = textLayoutResult ?: return@LaunchedEffect
        val coords = textCoordinates ?: return@LaunchedEffect
        val paragraphTopPx = coords.positionInParent().y
        val textLength = layout.layoutInput.text.length
        val offsets = mapping.spans.mapIndexed { i, span ->
            val line = layout.getLineForOffset(span.localStart.coerceIn(0, textLength))
            (globalStartIndex + i) to (paragraphTopPx + layout.getLineTop(line)).roundToInt()
        }.toMap()
        onSentenceTopOffsets(offsets)
    }

    // A.1/9bis.3.6 — position Y de la phrase en cours de lecture, pour
    // l'auto-scroll TTS et la réglette : recalculée à chaque changement
    // de phrase active (contrairement à l'effet ci-dessus), mais SEULEMENT
    // pour le paragraphe qui la contient.
    LaunchedEffect(textLayoutResult, currentSentenceIndex) {
        if (!isCurrentSentenceInParagraph) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        val span = mapping.spans.getOrNull(currentSentenceIndex - globalStartIndex) ?: return@LaunchedEffect
        val textLength = layout.layoutInput.text.length
        val line = layout.getLineForOffset(span.localStart.coerceIn(0, textLength))
        onCurrentLineY(with(density) { layout.getLineTop(line).toDp() })
    }

    // Même raison qu'en PAGED (`PageBlock`) : le champ dessine lui-même un
    // fond de sélection natif sur `selection`, qui doublerait le
    // `SpanStyle` de sélection posé ci-dessous dans le texte — fond
    // neutralisé, poignées de glissement inchangées (leur couleur ne
    // dépend pas de `backgroundColor`).
    val selectionColors = LocalTextSelectionColors.current
    val handlesOnlySelectionColors = remember(selectionColors) {
        TextSelectionColors(handleColor = selectionColors.handleColor, backgroundColor = Color.Transparent)
    }

    // Même correctif qu'en PAGED (`PageBlock`) : n'affiche JAMAIS le vrai
    // menu système (barre blanche `ActionMode`, doublon du popup sombre de
    // l'app) ; recalcule les bornes fenêtre via `getPathForRange` +
    // `localToWindow` (le `rect` fourni par `showMenu` n'est pas fiable
    // comme bornes de sélection réelle) ; anti-rebond de
    // `SelectionPopupSettleDelayMs` avant transmission (le popup sombre ne
    // doit apparaître qu'une fois le geste stabilisé, pas pendant un
    // glissement de poignée — `hide()` reste instantané).
    val toolbar = remember(paragraphStartOffset) {
        object : TextToolbar {
            override val status: TextToolbarStatus = TextToolbarStatus.Hidden

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?,
            ) {
                val currentSelection = selection
                val windowRect = if (currentSelection.collapsed) {
                    null
                } else {
                    localSelectionBoundsInWindow(textLayoutResult, textCoordinates, currentSelection)
                }
                pendingShowJob?.cancel()
                pendingShowJob = coroutineScope.launch {
                    delay(SelectionPopupSettleDelayMs)
                    onFreeSelectionBoundsInWindow(windowRect)
                }
            }

            override fun hide() {
                pendingShowJob?.cancel()
                pendingShowJob = null
                onFreeSelectionBoundsInWindow(null)
            }
        }
    }

    val currentWordLocalRange = if (isCurrentSentenceInParagraph && highlightedWordRange != null) {
        val span = mapping.spans.getOrNull(currentSentenceIndex - globalStartIndex)
        val sentenceLength = span?.sentence?.text?.length ?: 0
        if (span != null && sentenceLength > 0) {
            val start = animatedStart.coerceIn(0, sentenceLength - 1)
            val end = animatedEnd.coerceIn(start, sentenceLength - 1)
            (span.localStart + start)..(span.localStart + end)
        } else {
            null
        }
    } else {
        null
    }

    val displayText = buildParagraphDisplayText(
        mapping = mapping,
        globalStartIndex = globalStartIndex,
        currentSentenceIndex = currentSentenceIndex,
        chapterIndex = chapterIndex,
        annotations = annotations,
        textColor = textColor,
        freeSelectionHighlightRange = if (selection.collapsed) null else selection.min until selection.max,
        currentWordRange = currentWordLocalRange,
    )
    val fieldValue = TextFieldValue(displayText, selection)

    CompositionLocalProvider(
        LocalTextSelectionColors provides handlesOnlySelectionColors,
        LocalTextToolbar provides toolbar,
    ) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                // Bug réel trouvé sur appareil (voir PAGED, `PageBlock`,
                // même correctif) : un tap sur la sélection pour l'annuler
                // est traité EN INTERNE par BasicTextField, jamais vu par
                // le pointerInput sibling ci-dessous — sans ceci, le HUD
                // ne revenait pas après une annulation par tap.
                val wasCollapsed = selection.collapsed
                selection = newValue.selection
                if (newValue.selection.collapsed) {
                    onFreeSelectionCleared()
                    if (!wasCollapsed) {
                        onClick()
                    }
                } else {
                    val min = newValue.selection.min
                    val max = newValue.selection.max
                    onFreeSelectionChanged(mapping.absoluteOffsetForLocal(min), mapping.absoluteOffsetForLocal(max) - 1)
                }
            },
            readOnly = true,
            textStyle = TextStyle(fontSize = fontSizeSp.sp, lineHeight = lineHeightSp.sp, color = textColor, fontFamily = fontFamily),
            onTextLayout = { layout -> textLayoutResult = layout },
            modifier = modifier
                .then(styleModifier)
                .onGloballyPositioned { textCoordinates = it }
                .pointerInput(paragraphStartOffset) {
                    detectTapGestures(
                        onTap = {
                            // Même garde qu'en PAGED (`PageBlock`) : ne rien
                            // faire tant qu'une sélection libre est active
                            // sur CE paragraphe, sinon `onClick` (bascule du
                            // HUD) perturberait le geste de sélection natif
                            // en cours.
                            if (!selection.collapsed) return@detectTapGestures
                            onClick()
                        },
                    )
                }
                // Palier 3f.4 (première passe, pas de spike TalkBack dédié
                // — voir CHIFFRAGE_LOT_3F_SELECTION_MOT.md) : même raison
                // qu'en PAGED (`PageBlock`) — un `pointerInput` brut est
                // invisible à l'action « activer » que TalkBack synthétise
                // (double-tap après exploration), seulement à un tap
                // tactile réel. `stateDescription` : signale la position de
                // lecture TTS à l'exploration, sans l'interrompre — a
                // contrario du `liveRegion` déjà essayé et retiré (lot 1,
                // conflit avec la voix TTS active, voir plus bas dans
                // `ReaderScreen`) : `stateDescription` n'est lu que quand
                // l'utilisateur navigue explicitement sur ce noeud, jamais
                // annoncé de force pendant la lecture.
                .semantics {
                    if (isCurrentSentenceInParagraph) {
                        stateDescription = "Lecture en cours"
                    }
                    onClick(label = "Afficher ou masquer les commandes") {
                        if (!selection.collapsed) return@onClick false
                        onClick()
                        true
                    }
                },
        )
    }
}

private val SelectionHighlightColor = Color(0x664FC3F7)

/** Anti-rebond de `showMenu` (voir `ParagraphText`) — même valeur qu'en PAGED (`PagedChapterContent.SelectionPopupSettleDelayMs`) pour un délai perçu identique entre les deux modes. */
private const val SelectionPopupSettleDelayMs = 200L

/** Même mécanisme que `PagedChapterContent.rangeBoundsInWindow`, mais sur une sélection déjà LOCALE (offsets dans le texte de CE paragraphe, pas de conversion supplémentaire à faire ici). */
private fun localSelectionBoundsInWindow(
    layout: TextLayoutResult?,
    coords: LayoutCoordinates?,
    selection: TextRange,
): Rect? {
    if (layout == null || coords == null) return null
    val textLength = layout.layoutInput.text.length
    val start = selection.min.coerceIn(0, textLength)
    val endExclusive = selection.max.coerceIn(start, textLength)
    if (start >= endExclusive) return null
    val localBounds = layout.getPathForRange(start, endExclusive).getBounds()
    val topLeft = coords.localToWindow(localBounds.topLeft)
    val bottomRight = coords.localToWindow(localBounds.bottomRight)
    return Rect(topLeft, bottomRight)
}

/** Une [Sentence] du paragraphe et ses bornes LOCALES (offsets dans le texte reconstruit par [buildParagraphTextMapping]) — `localEndExclusive` exclusif, comme les offsets de fin de [com.inktone.domain.valueobject.Locator]. */
private class ParagraphSentenceSpan(val localStart: Int, val localEndExclusive: Int, val sentence: Sentence)

/**
 * Reconstruit le texte d'un [Paragraph] en joignant ses phrases par un
 * espace simple (même approximation que `selectedText`/`sliceChapterText`
 * ailleurs dans ce fichier — pas les espaces/retours réels du texte
 * source, sans incidence puisque ceci ne sert qu'au rendu et à la
 * sélection, jamais à une réécriture) et porte la correspondance offset
 * LOCAL ↔ ABSOLU nécessaire pour : convertir une sélection locale en
 * offsets de chapitre absolus, et répartir des styles (annotations,
 * sélection, mot TTS) sur leurs sous-plages locales exactes.
 */
private class ParagraphTextMapping(val text: String, val spans: List<ParagraphSentenceSpan>) {
    /** Offset ABSOLU (chapitre) correspondant à l'offset LOCAL [local]. Un offset tombant dans l'espace synthétique entre deux phrases est calé sur la borne de la phrase suivante — un tel offset ne peut de toute façon jamais être qu'une borne de sélection, jamais un caractère réel sélectionné. */
    fun absoluteOffsetForLocal(local: Int): Int {
        if (spans.isEmpty()) return 0
        val clamped = local.coerceIn(0, text.length)
        for (span in spans) {
            if (clamped <= span.localEndExclusive) {
                val withinSentence = (clamped - span.localStart).coerceIn(0, span.sentence.text.length)
                return span.sentence.startOffset + withinSentence
            }
        }
        return spans.last().sentence.endOffset
    }

    /** Sous-plages LOCALES (une par phrase touchée) de l'intersection entre `[absoluteStart, absoluteEndExclusive)` et ce paragraphe — une plage absolue peut chevaucher plusieurs phrases du même paragraphe. */
    fun localSubRangesFor(absoluteStart: Int, absoluteEndExclusive: Int): List<IntRange> {
        val result = mutableListOf<IntRange>()
        for (span in spans) {
            val start = maxOf(absoluteStart, span.sentence.startOffset)
            val endExclusive = minOf(absoluteEndExclusive, span.sentence.endOffset)
            if (start < endExclusive) {
                val localFrom = span.localStart + (start - span.sentence.startOffset)
                val localToExclusive = span.localStart + (endExclusive - span.sentence.startOffset)
                result.add(localFrom until localToExclusive)
            }
        }
        return result
    }
}

private fun buildParagraphTextMapping(paragraph: Paragraph): ParagraphTextMapping {
    val builder = StringBuilder()
    val spans = ArrayList<ParagraphSentenceSpan>(paragraph.sentences.size)
    paragraph.sentences.forEachIndexed { i, sentence ->
        if (i > 0) builder.append(' ')
        val start = builder.length
        builder.append(sentence.text)
        spans.add(ParagraphSentenceSpan(start, builder.length, sentence))
    }
    return ParagraphTextMapping(builder.toString(), spans)
}

/**
 * Combine, dans un seul `AnnotatedString`, l'alpha différentiel par
 * phrase (B.5, piste de lecture — plus par `Modifier.background`/couleur
 * de noeud entier comme avant le regroupement par paragraphe), le fond
 * des annotations existantes (offsets exacts — une annotation peut
 * chevaucher plusieurs phrases du paragraphe, d'où potentiellement
 * plusieurs sous-plages), la sélection libre au mot en cours et le mot
 * actuellement prononcé par le TTS (`currentWordRange`, animé) — posés
 * dans cet ordre, le mot TTS toujours au-dessus, même ordre qu'en mode
 * PAGED (`PageBlock.drawWithContent`).
 */
private fun buildParagraphDisplayText(
    mapping: ParagraphTextMapping,
    globalStartIndex: Int,
    currentSentenceIndex: Int,
    chapterIndex: Int,
    annotations: List<Annotation>,
    textColor: Color,
    freeSelectionHighlightRange: IntRange?,
    currentWordRange: IntRange?,
): AnnotatedString = buildAnnotatedString {
    append(mapping.text)

    // B.5 — piste de lecture : opacité différenciée par phrase.
    mapping.spans.forEachIndexed { i, span ->
        val globalIndex = globalStartIndex + i
        val alpha = when {
            globalIndex == currentSentenceIndex -> 1.0f
            globalIndex < currentSentenceIndex -> 0.40f
            else -> 0.88f
        }
        addStyle(SpanStyle(color = textColor.copy(alpha = alpha)), span.localStart, span.localEndExclusive)
    }

    for (annotation in annotations) {
        if (annotation.startLocator.chapterIndex != chapterIndex) continue
        for (range in mapping.localSubRangesFor(annotation.startLocator.charOffset, annotation.endLocator.charOffset)) {
            addStyle(SpanStyle(background = annotation.color.toComposeColor()), range.first, range.last + 1)
        }
    }

    freeSelectionHighlightRange?.let { range ->
        val start = range.first.coerceIn(0, mapping.text.length)
        val endExclusive = (range.last + 1).coerceIn(start, mapping.text.length)
        if (start < endExclusive) {
            addStyle(SpanStyle(background = SelectionHighlightColor), start, endExclusive)
        }
    }

    currentWordRange?.let { range ->
        addStyle(SpanStyle(background = Color.Yellow), range.first, range.last + 1)
    }
}

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
