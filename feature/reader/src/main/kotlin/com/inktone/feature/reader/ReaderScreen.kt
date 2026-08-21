package com.inktone.feature.reader

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.inktone.feature.reader.transition.ChapterTransitionConnection
import com.inktone.feature.reader.transition.ChapterTransitionDirection
import com.inktone.feature.reader.transition.ChapterTransitionIndicator
import com.inktone.feature.reader.transition.ChapterTransitionMath
import com.inktone.feature.reader.transition.ChapterTransitionState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.inktone.core.designsystem.rememberAppHaptics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.reducedMotionDuration
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.Sentence
import com.inktone.feature.reader.pagination.rememberChapterPaginationState
import coil.ImageLoader
import coil.compose.LocalImageLoader
import com.inktone.feature.reader.rendering.BookBlockItem
import com.inktone.feature.reader.rendering.BookBlockStyleMapper
import com.inktone.feature.reader.rendering.EpubImageFetcher
import kotlinx.coroutines.delay
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
 * est maintenant rendu — toutes les `Sentence` du chapitre, un
 * `Paragraph` par item de `LazyColumn`.
 *
 * **Migration `verticalScroll` → `LazyColumn` (virtualisation).** Le rendu
 * SCROLL a longtemps été un `FlowRow` dans un `Modifier.verticalScroll`,
 * choix assumé à l'époque pour imiter le texte continu. Conséquence
 * mesurée sur appareil (V2206, Android 14) avant migration : composition
 * EAGER du chapitre entier, donc des centaines de `BasicTextField`
 * simultanés — **100 % de frames en gigue, médiane à 150 ms, 99e centile à
 * 600 ms**, pour seulement 3-7 ms de GPU (tout le coût en
 * composition/layout CPU). Le palier 3f.3bis (grain phrase → paragraphe)
 * avait réduit le facteur sans traiter la cause : le nombre d'instances
 * restait proportionnel à la LONGUEUR DU CHAPITRE, pas à ce qui est
 * visible. `LazyColumn` le rend proportionnel à l'écran.
 *
 * Deux conséquences structurelles de la virtualisation, traitées ici :
 * un item est DÉTRUIT hors écran (l'état de sélection est donc hissé au
 * ViewModel et relu à la recomposition, voir `restoredSelection` dans
 * `ParagraphText` — la sortie de composition n'efface plus rien), et un
 * lazy layout n'expose aucun offset absolu de contenu (l'auto-scroll TTS
 * et la détection de position visent donc l'ITEM, plus un pixel).
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
 * centaines d'instances simultanées sur un chapitre typique, et
 * `BasicTextField` est nettement plus lourd qu'un `Text`. Bug réel trouvé
 * sur appareil : glissement de sélection ET défilement simple saccadés.
 * Regroupé par `Paragraph` — frontière déjà utilisée pour les images EPUB
 * intercalées, et devenue depuis l'unité d'item du `LazyColumn`.
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

    // P1 — la notification média est la voie de contrôle en arrière-plan et
    // sur écran verrouillé. Sur Android 13+, elle n'apparaît qu'avec
    // POST_NOTIFICATIONS : demandée ici, au moment précis où l'utilisateur
    // lance une narration, jamais au lancement de l'application.
    val requestNotificationPermission = rememberTtsNotificationPermissionRequest()
    val togglePlayback = {
        if (!state.isPlaying) requestNotificationPermission()
        viewModel.onIntent(if (state.isPlaying) ReaderIntent.Pause else ReaderIntent.PlayCurrentSentence)
    }

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
    val haptics = rememberAppHaptics()
    ReaderBrightnessEffect(value = state.readerBrightness)
    // P4 — retiré avec l'écran de lecture (DisposableEffect) : le maintien ne
    // doit jamais survivre au Lecteur.
    KeepScreenOnEffect(enabled = state.keepScreenOn)

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
            ) { handleReadingAreaTap() },
    ) {
        // A.3 — État d'erreur : affiché quand le parsing ou l'ouverture
        // échoue. Audit v1.0.0 (AUDIT_CONSOLIDATION_V1.md, B3) : deux
        // boutons RÉELS — « Réessayer » relance l'ouverture de la
        // publication, « Retour à la bibliothèque » navigue réellement
        // (avant l'audit, un seul bouton mal libellé effaçait l'erreur
        // sans rien faire, laissant un écran vide).
        val errorMessage = state.errorMessage
        if (errorMessage != null) {
            ErrorState(
                message = errorMessage,
                onRetry = { viewModel.onIntent(ReaderIntent.RetryOpen) },
                onBack = onBack,
            )
            return@Box
        }

        // Migration LazyColumn (voir doc de tête) : l'état de défilement du
        // mode SCROLL est désormais un `LazyListState` — un paragraphe = un
        // item, jamais plus tout le chapitre composé d'un coup.
        val scrollState = rememberLazyListState()
        LaunchedEffect(state.currentChapterIndex) { scrollState.scrollToItem(0) }

        val freeSelectedRange = state.freeSelectionRange

        // A.1 / Tache 9bis.3.6 - position Y de la phrase active, LOCALE au
        // paragraphe qui la contient (voir ParagraphText.onCurrentLineY) —
        // sert la réglette de lecture. N'est plus une cible de défilement :
        // un `LazyColumn` n'a pas d'offset absolu de contenu, l'auto-scroll
        // TTS vise désormais l'ITEM (voir ci-dessous).
        var currentLineYDp by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current

        // 3c.1 — drapeau posé explicitement autour du seul appel
        // programmatique au défilement (auto-scroll TTS), levé à sa fin.
        // Discrimine l'origine réelle du défilement pour la détection de
        // position ci-dessous : `isScrollInProgress` vaut `true` pour CE
        // défilement programmatique aussi bien que pour un drag
        // utilisateur — il ne permet donc pas de distinguer les deux.
        var isProgrammaticScroll by remember { mutableStateOf(false) }

        // A.1 — Auto-scroll vers la phrase active pendant la lecture TTS.
        // `animateScrollToItem` remplace `animateScrollTo(offsetAbsolu)` :
        // un lazy layout ne connaît pas la hauteur de ce qu'il n'a pas
        // composé, il ne peut donc pas viser un pixel absolu. Vise le
        // paragraphe porteur de la phrase — grain suffisant, et au passage
        // plus correct que l'ancien calcul qui passait un Y LOCAL au
        // paragraphe comme s'il était absolu au chapitre.
        LaunchedEffect(state.currentSentenceIndex) {
            if (!state.isPlaying) return@LaunchedEffect
            val targetBlock = state.currentChapter?.sentences
                ?.getOrNull(state.currentSentenceIndex)?.blockIndex
                ?.takeIf { it >= 0 } ?: return@LaunchedEffect
            isProgrammaticScroll = true
            try {
                scrollState.animateScrollToItem(targetBlock)
            } finally {
                isProgrammaticScroll = false
            }
        }

        val topmostVisibleSentenceIndex by remember(state.currentChapter) {
            derivedStateOf {
                if (isProgrammaticScroll) return@derivedStateOf null
                val firstVisibleBlock = scrollState.layoutInfo.visibleItemsInfo
                    .firstOrNull()?.index ?: return@derivedStateOf null
                // Trouver la première phrase appartenant à ce bloc
                state.currentChapter?.sentences
                    ?.firstOrNull { it.blockIndex == firstVisibleBlock }
                    ?.index
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
        var pagedFreeSelectionBounds by remember { mutableStateOf<SelectionPopupBounds?>(null) }
        var scrollFreeSelectionBounds by remember(state.currentChapterIndex) { mutableStateOf<SelectionPopupBounds?>(null) }
        val selectionBoundsInWindow = when (state.readingMode) {
            ReadingMode.SCROLL -> scrollFreeSelectionBounds?.boundsInWindow
            ReadingMode.PAGED -> pagedFreeSelectionBounds?.boundsInWindow
        }

        // Phase 4 — purge complète de l'état transitoire de sélection,
        // synchrone : état global effacé ET popup détruit dans le même
        // callback. C'est la disparition de ces bornes (jamais une
        // réaction différée à `freeSelectionRange`) qui démonte le popup,
        // exactement comme un `hide()` du toolbar natif ; l'unité
        // adressable, elle, rétracte sa sélection locale à la même
        // recomposition puisqu'elle ne possède plus l'état global (Phase 1,
        // `PageBlock`/`ParagraphText`).
        fun clearSelectionAndPopup() {
            viewModel.onIntent(ReaderIntent.ClearFreeSelection)
            pagedFreeSelectionBounds = null
            scrollFreeSelectionBounds = null
        }
        val selectedText = remember(freeSelectedRange, state.currentChapter) {
            val freeRange = freeSelectedRange ?: return@remember ""
            val chapterSentences = state.currentChapter?.sentences ?: emptyList()
            sliceChapterText(chapterSentences, freeRange.first, freeRange.last + 1)
        }

        // 3b.5 — barre du haut : appartient au HUD, apparaît/disparaît
        // avec le panneau, jamais indépendamment (même gate isHudVisible).
        // Overlay pur (`align(TopCenter)`) : ne modifie plus jamais les
        // bornes de la zone de lecture (voir commentaire de tête ci-dessus).
        // Lot 14 — masquée dès le lancement TTS (lecture immersive) ; ne
        // revient que via l'overlay panneau complet (showFullPanelOverlay),
        // qui conserve l'accès au bouton retour pendant la lecture.
        if (isHudVisible && (!state.isPlaying || showFullPanelOverlay)) {
            // Bug réel trouvé sur appareil : déclarée avant la zone de
            // lecture (Box.fillMaxSize() plus bas dans ce même Box), la
            // topbar se retrouvait dessous dans l'ordre d'empilement de
            // Compose (un enfant déclaré plus tard est peint ET intercepte
            // les taps AU-DESSUS des précédents, sans zIndex) — la flèche
            // retour ne recevait donc jamais le tap, absorbé par
            // onClick = handleReadingAreaTap() du bloc/pager en dessous.
            // zIndex la remonte au-dessus sans changer sa position.
            ReaderTopBar(
                title = state.title,
                author = state.author,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter).zIndex(1f),
                surfaceColor = ThemeColors.barSurface(state.resolvedTheme),
                contentColor = ThemeColors.barContent(state.resolvedTheme),
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
        // P4 — marge latérale réglable. UNE seule valeur, consommée à la fois
        // par la mesure de pagination et par le rendu (PagedChapterContent) :
        // deux sources distinctes feraient mesurer une page plus large que
        // celle réellement dessinée, donc déborder le texte.
        val readerMargin = readerMarginFor(state.readerMarginStep)
        val paginationPaddingPx = with(density) { readerMargin.roundToPx() }
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
        // Lot 12, tache 12.9 — jamais de mesure de pagination texte pour
        // un PDF (chapter = null neutralise rememberChapterPaginationState,
        // deja nullable) : ce format est rendu par FixedPageContent
        // (bitmap), la pagination EPUB/TXT n'a pas de sens pour lui.
        val isPdf = state.publicationFormat == PublicationFormat.PDF
        val pagination = rememberChapterPaginationState(
            chapter = if (isPdf) null else state.currentChapter,
            nextChapter = if (isPdf) null else state.chapters.getOrNull(state.currentChapterIndex + 1),
            currentSentenceIndex = state.currentSentenceIndex,
            fontSizeSp = state.effectiveSettings.fontSize,
            lineHeightSp = lineHeightSp,
            viewportWidthPx = readingAreaSize.width,
            viewportHeightPx = readingAreaSize.height,
            paddingPx = paginationPaddingPx,
            fontFamily = effectiveFontFamily,
            // P4 — la césure change les points de coupure de ligne, donc la
            // pagination : elle doit faire partie du style de MESURE, jamais
            // seulement du rendu.
            justified = state.isTextJustified,
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
            if (pagination.isMeasurementComplete(chapter)) {
                viewModel.onIntent(ReaderIntent.ChapterLayoutCompleted(chapter.index))
            }
        }

        // Bug réel trouvé sur appareil : aucune image EPUB ne s'affichait
        // jamais (ni couverture intégrée au flux, ni illustrations) — le
        // chaînon D5 du plan (construire un ImageLoader Coil avec
        // EpubImageFetcher.Factory(resolver) enregistré) n'était jamais
        // implémenté. AsyncImage retombait alors sur l'ImageLoader PAR
        // DÉFAUT de l'app, qui ignore totalement EpubImageKey (aucun
        // Fetcher.Factory compatible) — la requête échouait silencieusement,
        // BookBlockItem ne réagissant pas à l'état d'erreur de Coil.
        // null pour PDF/TXT (pas de resolver) : CompositionLocalProvider
        // n'est alors pas ouvert plus bas, Coil retombe sur son
        // ImageLoader par défaut — comportement inchangé pour ces formats.
        val context = LocalContext.current
        val epubImageLoader = remember(state.epubResourceResolver) {
            state.epubResourceResolver?.let { resolver ->
                ImageLoader.Builder(context)
                    .components { add(EpubImageFetcher.Factory(resolver)) }
                    .build()
            }
        }

        CompositionLocalProvider(
            LocalImageLoader provides (epubImageLoader ?: LocalImageLoader.current),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates -> readingAreaSize = coordinates.size },
        ) {
            if (state.isReadingRulerEnabled && !isPdf) {
                ReadingRuler(currentLineY = currentLineYDp, enabled = true)
            }

            if (isPdf) {
                // Lot 12, tache 12.9 — branchement par format, jamais de
                // reflow (ADR-017). Le chrome (TopBar, panneaux, ligne de
                // statut) reste commun aux deux formats, defini plus bas
                // dans ce meme composable — aucune duplication.
                FixedPageContent(
                    pageCount = state.chapters.size,
                    currentPageIndex = state.currentChapterIndex,
                    onPageIndexChanged = { pageIndex -> viewModel.onIntent(ReaderIntent.JumpToChapter(pageIndex)) },
                    onPageOffsetChanged = { offsetY -> viewModel.onIntent(ReaderIntent.UpdatePageOffset(offsetY)) },
                    renderPage = viewModel::renderPdfPage,
                    // Tâche 12.11 — inversion de luminance pour les thèmes
                    // sombres (Obsidienne, Noir Absolu AMOLED) et Sépia
                    // Vintage sur les pages vectorielles. Les pages
                    // scannées (sans texte) ne sont pas inversées par
                    // défaut — sauf si l'utilisateur a activé l'option
                    // « Forcer l'inversion » (ToggleForcePdfInversion).
                    invertColors = { pageIndex ->
                        val chapter = state.chapters.getOrNull(pageIndex)
                        val hasText = chapter?.sentences?.isNotEmpty() == true
                        if (hasText) {
                            val bgHex = state.resolvedTheme.backgroundColorHex
                            bgHex == "#000000" || state.resolvedTheme.id == "sepia_vintage"
                        } else {
                            state.forcePdfInversion
                        }
                    },
                    reduceMotion = state.reduceMotion,
                )
            } else {
                when (state.readingMode) {
                ReadingMode.SCROLL -> {
                    val chapter = state.currentChapter
                    val blocks = (chapter?.content as? ChapterContent.Rich)?.blocks.orEmpty()
                    val textStyle = TextStyle(
                        fontSize = state.effectiveSettings.fontSize.sp,
                        color = ThemeColors.text(state.resolvedTheme),
                        fontFamily = effectiveFontFamily,
                        // P4 — mêmes règles qu'en mode paginé (où elles
                        // viennent de `pagination.baseTextStyle`) : un réglage
                        // de lecture ne doit jamais dépendre du mode choisi.
                        textAlign = if (state.isTextJustified) TextAlign.Justify else TextAlign.Unspecified,
                        hyphens = if (state.isTextJustified) Hyphens.Auto else Hyphens.None,
                        lineBreak = if (state.isTextJustified) LineBreak.Paragraph else LineBreak.Unspecified,
                    )

                    // Parité avec le mode PAGED (absoluteHighlightedRange dans
                    // PagedChapterContent) : offset absolu (espace chapitre)
                    // du mot TTS actif, dérivé de Chapter.sentences —
                    // aligné avec ChapterTextMeasurer depuis la correction
                    // du séparateur de bloc (voir ChapterTextMeasurer.kt).
                    val scrollHighlightedRange = state.highlightedWordRange?.let { wordRange ->
                        chapter?.sentences?.getOrNull(state.currentSentenceIndex)?.startOffset?.let { sentenceStart ->
                            (sentenceStart + wordRange.first)..(sentenceStart + wordRange.last)
                        }
                    }
                    // Lues en phase de dessin (drawWithContent dans
                    // BookBlockItem), jamais en paramètre direct — un mot
                    // prononcé n'invalide donc que le dessin, jamais la
                    // mesure du BasicTextField (même contrainte que PageBlock).
                    val scrollHighlightedRangeState = rememberUpdatedState(scrollHighlightedRange)
                    val scrollFreeSelectedRangeState = rememberUpdatedState(freeSelectedRange)

                    // Transition de chapitre par résistance spatiale (overscroll).
                    // État local au geste — jamais dans ReaderUiState (MVI) : la
                    // navigation réelle reste ReaderIntent.Next/PreviousChapter.
                    val chapterTransition = remember { ChapterTransitionState() }
                    LaunchedEffect(readingAreaSize) {
                        chapterTransition.thresholdPx = readingAreaSize.height * 0.25f
                    }

                    // Décalage visuel : suit le doigt instantanément (snapTo) pendant
                    // le tirage, puis rebond élastique (spring) au relâchement.
                    val visualPull = remember { Animatable(0f) }
                    LaunchedEffect(chapterTransition.isDragging) {
                        if (chapterTransition.isDragging) {
                            snapshotFlow { chapterTransition.pullPx }.collect { visualPull.snapTo(it) }
                        } else {
                            visualPull.animateTo(
                                0f,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                    }

                    val latestState = rememberUpdatedState(state)
                    val chapterTransitionConnection = remember(chapterTransition, scrollState) {
                        ChapterTransitionConnection(
                            state = chapterTransition,
                            orientation = Orientation.Vertical,
                            canPullPrevious = { !scrollState.canScrollBackward && latestState.value.hasPreviousChapter },
                            canPullNext = { !scrollState.canScrollForward && latestState.value.hasNextChapter },
                            // Bug réel trouvé à l'audit : sans cette garde, un
                            // glissement de sélection de texte au bord du
                            // chapitre pouvait être capté par ce geste de
                            // tirage plutôt que par le champ de texte.
                            isSelectionActive = { scrollFreeSelectedRangeState.value != null },
                            onCommit = { direction ->
                                val target = when (direction) {
                                    ChapterTransitionDirection.PREVIOUS ->
                                        (latestState.value.currentChapterIndex - 1).coerceAtLeast(0)
                                    ChapterTransitionDirection.NEXT ->
                                        (latestState.value.currentChapterIndex + 1)
                                            .coerceAtMost(latestState.value.chapters.lastIndex)
                                }
                                chapterTransition.beginLoading(target)
                                viewModel.onIntent(
                                    if (direction == ChapterTransitionDirection.PREVIOUS)
                                        ReaderIntent.PreviousChapter else ReaderIntent.NextChapter
                                )
                            },
                            onCancel = { chapterTransition.cancel() },
                        )
                    }

                    // Spinner maintenu jusqu'à ce que le chapitre cible soit prêt
                    // (parse paresseux), avec un minimum de 400 ms anti-flash.
                    LaunchedEffect(chapterTransition.isLoading) {
                        if (!chapterTransition.isLoading) return@LaunchedEffect
                        val target = chapterTransition.targetChapterIndex
                        val startedAt = SystemClock.uptimeMillis()
                        snapshotFlow {
                            val chapter = latestState.value.chapters.getOrNull(target)
                            chapter != null &&
                                (chapter.content as? ChapterContent.Rich)?.blocks?.isNotEmpty() == true
                        }.first { it }
                        val elapsed = SystemClock.uptimeMillis() - startedAt
                        if (elapsed < ChapterTransitionMath.MIN_LOADING_MS) {
                            delay(ChapterTransitionMath.MIN_LOADING_MS - elapsed)
                        }
                        chapterTransition.finish()
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                                state = scrollState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationY = visualPull.value }
                                    .nestedScroll(chapterTransitionConnection),
                                userScrollEnabled = freeSelectedRange == null,
                                // P4 — même marge qu'en mode paginé : un
                                // réglage de lecture ne doit pas dépendre du
                                // mode choisi. Cette valeur était en dur, si
                                // bien que le cran de marge n'avait aucun
                                // effet en défilement (défaut trouvé à la
                                // vérification device).
                                contentPadding = PaddingValues(readerMargin),
                            ) {
                                items(
                                    items = blocks,
                                    key = { block ->
                                        val range = block.globalOffsetRange
                                        if (range != null) "${state.currentChapterIndex}-${range.first}"
                                        else "${state.currentChapterIndex}-img-${(block as? com.inktone.domain.model.BookBlock.ImageBlock)?.href ?: "sep"}"
                                    },
                                ) { block ->
                                    BookBlockItem(
                                        block = block,
                                        baseTextStyle = textStyle,
                                        resolver = state.epubResourceResolver,
                                        publicationId = state.publicationId,
                                        chapterIndex = state.currentChapterIndex,
                                        annotations = state.annotations,
                                        highlightedRange = scrollHighlightedRangeState,
                                        freeSelectedRange = scrollFreeSelectedRangeState,
                                        onFreeSelectionChanged = { anchor, focus ->
                                            viewModel.onIntent(ReaderIntent.SetFreeSelection(anchor, focus))
                                        },
                                        onFreeSelectionCleared = { viewModel.onIntent(ReaderIntent.ClearFreeSelection) },
                                        onFreeSelectionBoundsInWindow = { ownerKey, bounds ->
                                            scrollFreeSelectionBounds = resolveSelectionPopupBounds(
                                                current = scrollFreeSelectionBounds,
                                                ownerKey = ownerKey,
                                                bounds = bounds,
                                            )
                                        },
                                        onClick = { handleReadingAreaTap() },
                                        isReadingRulerEnabled = state.isReadingRulerEnabled,
                                        onCurrentLineY = { y -> currentLineYDp = y },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }

                        val transitionDirection = chapterTransition.direction
                        if (transitionDirection != null) {
                            ChapterTransitionIndicator(
                                direction = transitionDirection,
                                fraction = ChapterTransitionMath.fraction(
                                    visualPull.value,
                                    chapterTransition.thresholdPx,
                                ),
                                isLoading = chapterTransition.isLoading,
                                reduceMotion = state.reduceMotion,
                                contentColor = ThemeColors.text(state.resolvedTheme),
                                surfaceColor = ThemeColors.barSurface(state.resolvedTheme),
                                modifier = Modifier
                                    .align(
                                        if (transitionDirection == ChapterTransitionDirection.PREVIOUS)
                                            Alignment.TopCenter else Alignment.BottomCenter
                                    )
                                    .padding(vertical = 8.dp),
                            )
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
                        chapterCount = state.chapters.size,
                        textColor = ThemeColors.text(state.resolvedTheme),
                        isReadingRulerEnabled = state.isReadingRulerEnabled,
                        contentPadding = readerMargin,
                        onClick = { handleReadingAreaTap() },
                        onNextChapter = { viewModel.onIntent(ReaderIntent.NextChapter) },
                        onPreviousChapter = { viewModel.onIntent(ReaderIntent.PreviousChapter) },
                        hasPreviousChapter = state.hasPreviousChapter,
                        hasNextChapter = state.hasNextChapter,
                        reduceMotion = state.reduceMotion,
                        surfaceColor = ThemeColors.barSurface(state.resolvedTheme),
                        isChapterReady = { index ->
                            val c = state.chapters.getOrNull(index)
                            c != null && (c.content as? ChapterContent.Rich)?.blocks?.isNotEmpty() == true
                        },
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
                        onFreeSelectionBoundsInWindow = { ownerKey, bounds ->
                            pagedFreeSelectionBounds = resolveSelectionPopupBounds(
                                current = pagedFreeSelectionBounds,
                                ownerKey = ownerKey,
                                bounds = bounds,
                            )
                        },
                    )
                }
                }
            }

            // B.7 — Overlay visuel des captions retiré (UX §Lecture — couche
            // TTS : jugé trop encombrant, notamment sur les phrases longues).
            // L'annonce TalkBack par nœud liveRegion, ajoutée pour ne pas
            // perdre l'accessibilité, a été retirée après vérification sur
            // appareil (lot 1, point 11) : elle fait doublon avec la voix
            // TTS déjà active et les deux se chevauchent à l'oreille.
        }
        }

        // 3c.4 — remplace AnnotationColorPicker (position fixe basse
        // d'écran, Confirmer/Annuler) par le popup Copier/Surligner/Note
        // positionné près de la sélection réelle.
        //
        // Phase 2 — le popup n'est monté QUE si l'unité adressable a
        // remonté des bornes fenêtre depuis `TextToolbar.showMenu()`
        // (geste de sélection terminé, doigt levé). Une sélection active
        // sans bornes = glissement de poignée en cours : rien à l'écran,
        // la loupe native reste dégagée.
        if (freeSelectedRange != null && selectionBoundsInWindow != null) {
            SelectionActionPopup(
                selectedText = selectedText,
                selectionBoundsInWindow = selectionBoundsInWindow,
                // Phase 4 — l'intent est dispatché AVANT la purge :
                // `ReaderViewModel.confirmAnnotation` lit
                // `freeSelectionRange` et résout ses locators de façon
                // SYNCHRONE (voir sa KDoc), le `ClearFreeSelection` qui
                // suit ne peut donc pas lui retirer les offsets sous les
                // pieds. L'ordre inverse perdrait l'annotation.
                onHighlight = { color ->
                    viewModel.onIntent(ReaderIntent.ConfirmAnnotation(color))
                    clearSelectionAndPopup()
                },
                onSaveNote = { content, color ->
                    viewModel.onIntent(ReaderIntent.ConfirmAnnotation(color, content))
                    clearSelectionAndPopup()
                },
                onDismiss = { clearSelectionAndPopup() },
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
                    // Lot 12, tache 12.10 — la barre pilule TTS n'a pas
                    // lieu d'apparaitre pour un PDF (isPlaying reste faux,
                    // playCurrentSentence se neutralise pour ce format,
                    // decision actee 16) : garde explicite, pas seulement
                    // transitive.
                    if (state.isPlaying && !showFullPanelOverlay && !isPdf) {
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
                                        togglePlayback()
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
                                togglePlayback()
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
                            showTtsControls = !isPdf,
                            // Audit v1.0.0 (M6) : pas de sommaire pour PDF/TXT.
                            showToc = state.publicationFormat == PublicationFormat.EPUB,
                            surfaceColor = ThemeColors.barSurface(state.resolvedTheme),
                            accentColor = ThemeColors.accent(state.resolvedTheme),
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
                    // Mesure complète uniquement : tant que la pagination
                    // n'a pas couvert toutes les phrases du chapitre,
                    // `pageCount` et `pageIndexAt` reflètent un préfixe
                    // borné (3a.3) — les présenter ferait un faux total
                    // (« Chapitre 12 (54/54) » alors que le chapitre
                    // continue). Sans compteur, seule la ligne « Chapitre N »
                    // est affichée (voir StatusLineBar.chapterCounterText).
                    val complete = pagination.isMeasurementComplete(chapter)
                    val pageCountInChapter = if (complete) pagination.pageCount(chapter.index) else 0
                    val pageIndexInChapter = if (complete) {
                        when (state.readingMode) {
                            ReadingMode.PAGED -> pagedLivePageIndex
                            ReadingMode.SCROLL -> pagination.pageIndexAt(chapter.index, state.currentSentenceIndex)
                        }
                    } else {
                        0
                    }
                    StatusLineBar(
                        chapterNumber = state.currentChapterIndex + 1,
                        pageInChapter = pageIndexInChapter + 1,
                        pageCountInChapter = pageCountInChapter,
                        bookProgression = state.bookProgression,
                        showPageCounter = complete,
                        contentColor = ThemeColors.barContent(state.resolvedTheme),
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
            val previewSentences = state.currentChapter?.sentences.orEmpty()
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
                currentMarginStep = state.readerMarginStep,
                isTextJustified = state.isTextJustified,
                keepScreenOn = state.keepScreenOn,
                onMarginStepChange = { step -> viewModel.onIntent(ReaderIntent.SetReaderMarginStep(step)) },
                onTextJustifiedChange = { justified -> viewModel.onIntent(ReaderIntent.SetTextJustified(justified)) },
                onKeepScreenOnChange = { enabled -> viewModel.onIntent(ReaderIntent.SetKeepScreenOn(enabled)) },
                onDismiss = { showSettingsPanel = false },
            )
        }

        // B.3 — Panneau TTS in-reader
        if (showTtsPanel) {
            val sentences = state.currentChapter?.sentences ?: emptyList()
            ReaderTtsPanel(
                isPlaying = state.isPlaying,
                currentSentenceIndex = state.currentSentenceIndex,
                totalSentences = sentences.size,
                activeVoiceProfile = state.activeVoiceProfile,
                availableVoiceProfiles = state.availableVoiceProfiles,
                onPlayPause = {
                    togglePlayback()
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
                showSleepTimer = !isPdf,
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
                    onToggleBookmark = {
                        // P5 — confirmation tactile d'une action qui aboutit,
                        // distincte du simple cran de page (`tick`).
                        haptics.confirm()
                        viewModel.onIntent(ReaderIntent.ToggleBookmarkAtCurrentPosition)
                    },
                    onClose = { viewModel.onIntent(ReaderIntent.ToggleBookmarkList) },
                )
            }
        }

        // Lot 10 (restauré au Lot 20) — proposition proactive de la voix
        // neuronale au premier usage réel du TTS. « Télécharger » ouvre
        // les Réglages (carte Lecture) où le téléchargement réel
        // (voix upmc + modèle CTC, ~183 Mo) se confirme et se suit —
        // pas de logique de téléchargement dupliquée ici.
        if (state.showVoiceDownloadPrompt) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.onIntent(ReaderIntent.DismissVoiceDownloadPrompt) },
                title = { Text("Voix neuronale disponible") },
                text = {
                    Text("Une voix française plus naturelle (Jessica & Pierre) peut être téléchargée (environ 183 Mo, une seule fois). La lecture visuelle et la voix actuelle restent disponibles sans cela.")
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

internal data class SelectionPopupBounds(val ownerKey: Int, val boundsInWindow: Rect)

/**
 * Arbitre une écriture de bornes venant de l'unité [ownerKey].
 *
 * - Bornes non nulles (`TextToolbar.showMenu`) : le dernier geste terminé
 *   gagne toujours, quel que soit le propriétaire précédent.
 * - Bornes nulles (`TextToolbar.hide`, nettoyage de sortie d'écran) :
 *   n'efface QUE si l'unité émettrice est encore propriétaire. Sans cette
 *   garde, sélectionner un mot dans le paragraphe B pendant que A est
 *   sélectionné pouvait faire disparaître le popup de B — le `hide()` de
 *   A, déclenché par la perte de focus, arrivant après le `showMenu()` de
 *   B. L'ordre exact de ces deux appels internes à `BasicTextField` n'est
 *   pas un contrat : cette fonction rend le résultat indépendant de
 *   l'ordre plutôt que de parier dessus.
 */
internal fun resolveSelectionPopupBounds(
    current: SelectionPopupBounds?,
    ownerKey: Int,
    bounds: Rect?,
): SelectionPopupBounds? = when {
    bounds != null -> SelectionPopupBounds(ownerKey, bounds)
    current == null || current.ownerKey == ownerKey -> null
    else -> current
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
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
            Text("Réessayer")
        }
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text("Retour à la bibliothèque")
        }
    }
}
