package com.inktone.feature.reader

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import com.inktone.feature.reader.transition.ChapterTransitionConnection
import com.inktone.feature.reader.transition.ChapterTransitionDirection
import com.inktone.feature.reader.transition.ChapterTransitionIndicator
import com.inktone.feature.reader.transition.ChapterTransitionMath
import com.inktone.feature.reader.transition.ChapterTransitionState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.inktone.core.designsystem.Motion
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
import androidx.compose.ui.text.intl.LocaleList
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
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.reducedMotionDuration
import com.inktone.core.designsystem.WindowBackgroundColorEffect
import com.inktone.core.designsystem.SystemBarIconsEffect
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Bookmark
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.Sentence
import com.inktone.feature.reader.pagination.rememberChapterPaginationState
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.memory.MemoryCache
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
 *
 * [isForeground] — vrai tant que le Lecteur est la destination COURANTE de
 * la navigation. Il retombe à faux dès le début d'une transition sortante
 * (retour à la Bibliothèque, ouverture des Réglages ou de la Recherche),
 * alors que cet écran reste composé jusqu'à la FIN du fondu. Tout ce que le
 * Lecteur impose à la FENÊTRE (barres système masquées, fond de fenêtre,
 * contraste des icônes, luminosité, écran maintenu allumé) en dépend : lié
 * à la seule durée de vie du composable, ce réglage n'était rendu qu'après
 * l'animation, et l'écran d'arrivée se dessinait pendant tout le fondu sur
 * le fond coloré du thème de lecture, sans barre de statut.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel = hiltViewModel(),
    onSearchClick: () -> Unit = {},
    onBack: () -> Unit = {},
    onOpenPronunciationRules: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    isForeground: Boolean = true,
) {
    // État du ViewModel, gardé en `State` : les champs VIVANTS (phrase
    // courante, minuteur, audio actif, compte à rebours, décalage PDF) n'y
    // sont lus qu'aux endroits précis qui les affichent ou y réagissent —
    // effets en `snapshotFlow`, `WithLiveValue` —, jamais dans le corps de
    // l'écran.
    val uiState = viewModel.state.collectAsStateWithLifecycle()
    // Vue STRUCTURELLE de ce même état : ne change pas quand seul un champ
    // vivant change (voir `ReaderUiState.isStructurallyEquivalentTo`). C'est
    // elle que lit tout le corps de l'écran : un paragraphe franchi, une
    // phrase lue ou un tic du minuteur ne le recomposent plus (mesuré : 60 %
    // des images > 32 ms du défilement venaient de ces recompositions).
    //
    // Ses champs vivants sont donc PÉRIMÉS par construction : ne jamais y
    // lire `currentSentenceIndex`, `bookProgression`, `sleepTimer`,
    // `isAudioActive`, `eyeRestReminderCountdownS`, `pageOffsetY` ni
    // `isCurrentPageBookmarked` — passer par `uiState.value`.
    val state by remember(uiState) { derivedStateOf(ReaderStructuralPolicy) { uiState.value } }
    // §3.3 — le surlignage mot-à-mot est collecté séparément : sa cadence
    // (un mot prononcé, 3 à 5 fois/s) ne recompose plus le corps de l'écran.
    // Gardé en `State` et jamais lu ici : seuls le dessin des blocs et des
    // collecteurs le lisent. Le déléguer (`by`) et le lire dans le corps de
    // l'écran recomposait toute la zone de lecture à chaque mot.
    val highlightedWordRangeState = viewModel.highlightedWordRange.collectAsStateWithLifecycle()

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

    // États d'affichage (HUD, panneaux, dialogues, popups) : voir
    // ReaderChromeState, qui porte aussi leurs règles de bascule.
    val chrome = rememberReaderChromeState()

    // Correctif Lot 21, tâche 5 — la note de signet n'ouvre plus un
    // AlertDialog modal à CHAQUE création (le plan proscrit d'en faire un
    // dialogue obligatoire) : un Snackbar transitoire porte l'action
    // « Ajouter une note », même patron que OpdsEffect.DownloadComplete
    // (CatalogDashboardScreen). La saisie ne s'ouvre que si l'utilisateur
    // la demande explicitement.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.pendingBookmarkNoteId) {
        if (state.pendingBookmarkNoteId == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Signet ajouté",
            actionLabel = "Ajouter une note",
        )
        if (result == SnackbarResult.ActionPerformed) {
            chrome.showBookmarkNoteDialog = true
        } else {
            // Ignoré ou expiré : le signet reste sans note, comme avant.
            viewModel.onIntent(ReaderIntent.DismissBookmarkNotePrompt)
        }
    }

    // 3d.3 — auto-masquage de la barre de luminosité, relancé à chaque
    // ajustement (voir ReaderChromeState.showBrightnessBar).
    LaunchedEffect(chrome.showBrightnessBar, chrome.brightnessBarActivityTick) {
        if (chrome.showBrightnessBar) {
            delay(3000)
            chrome.showBrightnessBar = false
        }
    }

    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) chrome.onPlaybackStopped()
    }

    ImmersiveReaderChrome(
        isHudVisible = chrome.isHudVisible,
        hudActivityTick = chrome.hudActivityTick,
        immersive = isForeground,
        onAutoHide = { chrome.onHudAutoHide(state.isPlaying) },
    ) {
    // 3d.3 — applique la luminosité choisie à la fenêtre du lecteur
    // seulement, restaurée à la sortie (voir ReaderBrightnessEffect).
    val haptics = rememberAppHaptics()
    // Armé ou non : change rarement, contrairement au temps restant (une
    // valeur par seconde), lu seulement par le panneau du minuteur.
    val isSleepTimerArmed by remember(uiState) { derivedStateOf { uiState.value.sleepTimer != null } }
    // Effets de FENÊTRE ci-dessous : appelés seulement tant que le Lecteur
    // est au premier plan (voir [isForeground]). Leur sortie de composition
    // déclenche leur `onDispose` — restauration immédiate, au début de la
    // transition sortante plutôt qu'à sa fin.
    if (isForeground) {
    ReaderBrightnessEffect(value = state.readerBrightness)
    // P4 — retiré avec l'écran de lecture (DisposableEffect) : le maintien ne
    // doit jamais survivre au Lecteur.
    KeepScreenOnEffect(enabled = state.keepScreenOn)
    // Le Lecteur masque les barres système, mais l'inset de barre de statut
    // reste consommé : le fond de FENÊTRE (crème `brand_background` de
    // `themes.xml`) transparaissait dans cette bande, au-dessus de la barre
    // du haut, quel que soit le thème de lecture. Colorer `statusBarColor`
    // n'y pouvait rien — la barre est masquée, c'est le fond de fenêtre qui
    // peint là. La bande suit donc le HUD : la couleur de la barre du haut
    // quand elle est visible (les deux se confondent), celle de la page
    // sinon — `barSurface` est un lerp à 10 % vers la couleur du texte,
    // nettement visible en bande claire sur un fond noir sans HUD.
    // Seule exception au contraste centralisé par `InkToneNavHost` : celui du
    // Lecteur dépend du thème de LECTURE, que le NavHost ne connaît pas.
    // Aucun conflit d'écriture possible — le drawer n'est jamais ouvrable
    // depuis cet écran, et dès que le Lecteur n'est plus au premier plan cet
    // effet n'est plus appelé : le `SystemBarIconsEffect` du NavHost
    // redevient seul écrivain, y compris pendant le fondu de sortie (où cet
    // écran recompose encore). Sert les barres qui reparaissent
    // transitoirement au balayage.
    SystemBarIconsEffect(ThemeColors.barSurface(state.resolvedTheme))
    WindowBackgroundColorEffect(
        if (chrome.isHudVisible) {
            ThemeColors.barSurface(state.resolvedTheme)
        } else {
            ThemeColors.background(state.resolvedTheme)
        },
    )
    }

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
            .background(ThemeColors.background(state.resolvedTheme))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { chrome.onReadingAreaTap(state.isPlaying) },
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

        // Diagnostic reprise de lecture (mode SCROLL) — bug réel trouvé à
        // l'audit : `LaunchedEffect(state.currentChapterIndex) {
        // scrollState.scrollToItem(0) }` ramenait TOUJOURS la liste en haut
        // du chapitre à l'ouverture, y compris quand `currentSentenceIndex`
        // restauré (K3) pointait plus loin. Pire : `topmostVisibleSentenceIndex`
        // ci-dessous observait alors la phrase 0 réellement visible et
        // réémettait `UpdateScrollPosition(0)`, qui ÉCRASAIT en base la vraie
        // position sauvegardée — la position ne se contentait pas de ne pas
        // s'afficher, elle était détruite dès l'ouverture.
        //
        // Corrigé en donnant au mode SCROLL le même ancrage que
        // `PagedChapterContent` pour le mode PAGED (`pageIndexAt`, granularité
        // page) : ici, granularité bloc (paragraphe) — un `LazyColumn` ne
        // connaît qu'un index d'item, pas un offset caractère. Même garde
        // anti-écho (`lastManuallyEmittedSentenceIndex`) : distingue un
        // changement de `currentSentenceIndex` provoqué par NOTRE PROPRE
        // remontée de scroll manuel (topmostVisibleSentenceIndex plus bas) —
        // qu'il ne faut pas recontredire — d'une navigation externe (TTS,
        // signet, restauration) qui doit, elle, repositionner la liste.
        var lastManuallyEmittedSentenceIndex by remember { mutableStateOf<Int?>(null) }
        var previousChapterIndexForAnchor by remember { mutableStateOf<Int?>(null) }
        // Déclencheurs COLLECTÉS (`snapshotFlow`), jamais lus en clés
        // d'effet : une clé se lit pendant la composition et recomposait
        // tout l'écran à chaque phrase. `collectLatest` conserve la sémantique
        // de relance d'un effet (le traitement précédent est annulé).
        LaunchedEffect(scrollState) {
            snapshotFlow {
                val live = uiState.value
                Triple(live.currentChapterIndex, live.currentSentenceIndex, live.currentChapter?.sentences?.size)
            }.collectLatest { (chapterIndex, sentenceIndex, _) ->
            val live = uiState.value
            // L'auto-scroll TTS (effet suivant, `animateScrollToItem`) est
            // seul responsable pendant la lecture — ne pas le contredire ici.
            if (live.isPlaying) return@collectLatest

            val chapterChanged = chapterIndex != previousChapterIndexForAnchor
            previousChapterIndexForAnchor = chapterIndex

            if (!chapterChanged && sentenceIndex == lastManuallyEmittedSentenceIndex) {
                lastManuallyEmittedSentenceIndex = null
                return@collectLatest
            }

            val targetBlock = live.currentChapter?.sentences
                ?.getOrNull(sentenceIndex)?.blockIndex
                ?.takeIf { it >= 0 }
            if (targetBlock == null) {
                // Contenu du chapitre pas encore chargé (Rich vide) : sur un
                // vrai changement de chapitre, démarrer en haut le temps que
                // le contenu arrive — cet effet se redéclenchera dès que
                // `sentences.size` changera. Sur un simple chargement
                // asynchrone dans le MÊME chapitre, ne rien faire : ne pas
                // ramener une liste déjà positionnée en haut.
                if (chapterChanged) scrollState.scrollToItem(0)
                return@collectLatest
            }
            isProgrammaticScroll = true
            try {
                scrollState.scrollToItem(targetBlock)
            } finally {
                isProgrammaticScroll = false
            }
            }
        }

        // A.1 — Auto-scroll vers la phrase active pendant la lecture TTS.
        // `animateScrollToItem` remplace `animateScrollTo(offsetAbsolu)` :
        // un lazy layout ne connaît pas la hauteur de ce qu'il n'a pas
        // composé, il ne peut donc pas viser un pixel absolu. Vise le
        // paragraphe porteur de la phrase — grain suffisant, et au passage
        // plus correct que l'ancien calcul qui passait un Y LOCAL au
        // paragraphe comme s'il était absolu au chapitre.
        LaunchedEffect(scrollState) {
            snapshotFlow { uiState.value.currentSentenceIndex }.collectLatest { sentenceIndex ->
                val live = uiState.value
                if (!live.isPlaying) return@collectLatest
                val targetBlock = live.currentChapter?.sentences
                    ?.getOrNull(sentenceIndex)?.blockIndex
                    ?.takeIf { it >= 0 } ?: return@collectLatest
                isProgrammaticScroll = true
                try {
                    scrollState.animateScrollToItem(targetBlock)
                } finally {
                    isProgrammaticScroll = false
                }
            }
        }

        // Index O(1) `blockIndex` → index de la première phrase de ce bloc,
        // construit UNE FOIS par chapitre. L'ancien
        // `sentences.firstOrNull { it.blockIndex == firstVisibleBlock }`
        // rescanait TOUTES les phrases du chapitre à chaque frame de
        // défilement (O(N) par frame) — coût réel sur les chapitres longs,
        // contribuant à la gigue du mode SCROLL.
        val firstSentenceIndexByBlock = remember(state.currentChapter) {
            buildMap {
                state.currentChapter?.sentences?.forEach { sentence ->
                    if (sentence.blockIndex >= 0) putIfAbsent(sentence.blockIndex, sentence.index)
                }
            }
        }
        val topmostVisibleSentenceIndex = remember(state.currentChapter) {
            derivedStateOf {
                if (isProgrammaticScroll) return@derivedStateOf null
                val firstVisibleBlock = scrollState.layoutInfo.visibleItemsInfo
                    .firstOrNull()?.index ?: return@derivedStateOf null
                firstSentenceIndexByBlock[firstVisibleBlock]
            }
        }

        // 3c.1 — antipattern legacy corrigé : en mode SCROLL, seuls le TTS
        // ou une navigation explicite faisaient avancer currentSentenceIndex
        // ; un défilement manuel silencieux n'était jamais reflété, ni
        // persisté. Ignoré si `null` (aucune phrase encore mesurée) ou hors
        // mode SCROLL (le pager pilote sa propre remontée en mode PAGED).
        //
        // Collecté, jamais lu en clé : le premier paragraphe visible change à
        // chaque paragraphe franchi, et sa lecture en clé d'effet recomposait
        // tout l'écran à ce rythme (cause mesurée, en plus de l'état).
        LaunchedEffect(topmostVisibleSentenceIndex) {
            snapshotFlow { topmostVisibleSentenceIndex.value to uiState.value.readingMode }
                .collect { (index, readingMode) ->
                    if (index != null && readingMode == ReadingMode.SCROLL) {
                        lastManuallyEmittedSentenceIndex = index
                        viewModel.onIntent(ReaderIntent.UpdateScrollPosition(index))
                    }
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
        if (chrome.isHudVisible && (!state.isPlaying || chrome.showFullPanelOverlay)) {
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
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
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
        var measuredAreaSize by remember { mutableStateOf(IntSize.Zero) }
        // Hors premier plan (fondu de sortie), la fenêtre peut changer de
        // taille sous cet écran — barres système rétablies, mini-lecteur qui
        // réapparaît sous le NavHost. Propager cette taille relancerait une
        // pagination complète du chapitre pour un écran qui disparaît : elle
        // est donc GELÉE à sa dernière valeur de premier plan.
        //
        // Gel dérivé ici, jamais en filtrant le callback de mesure : un
        // callback ignoré n'est pas rejoué. Filtré, il laissait la taille à
        // zéro si la première mesure tombait avant que le Lecteur soit
        // reconnu au premier plan — mode paginé vide jusqu'à la relayout
        // suivante (masquage du HUD, ~4 s).
        val frozenAreaSize = remember(isForeground) { if (isForeground) null else measuredAreaSize }
        val readingAreaSize = frozenAreaSize ?: measuredAreaSize
        // P4 — marge latérale réglable. UNE seule valeur, consommée à la fois
        // par la mesure de pagination et par le rendu (PagedChapterContent) :
        // deux sources distinctes feraient mesurer une page plus large que
        // celle réellement dessinée, donc déborder le texte.
        val readerMargin = readerMarginFor(state.readerMarginStep)
        val paginationPaddingPx = with(density) { readerMargin.roundToPx() }
        // 3d.2 — interligne en sp, combiné à fontSize (multiplicateur
        // global, voir UserPreferences.lineHeightMultiplier) : seul point de
        // calcul, consommé par la mesure de pagination (mode paginé) ET par le
        // style de rendu du mode défilement plus bas.
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
            currentSentenceIndex = { uiState.value.currentSentenceIndex },
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

        // Lot 21, tâche 9 — auto-scroll visuel (mode SCROLL uniquement),
        // corrigé (revue post-lot 21) : la boucle vit directement dans le
        // LaunchedEffect plutôt que dans un Job suivi à la main. Un `Job`
        // partagé entre l'effet (qui l'annule et le réaffecte) et son
        // propre `finally` (qui le remet à `null`) créait une course :
        // `cancel()` n'étant pas synchrone, le `finally` de l'ANCIEN job
        // s'exécutait après l'affectation du NOUVEAU et l'écrasait par
        // `null` — l'arrêt au toucher (pointerInput ci-dessous) ne
        // trouvait alors plus de job actif. Ici, Compose annule lui-même
        // la coroutine de l'effet à chaque re-clé ou sortie de
        // composition : aucune référence mutée depuis deux endroits.
        var autoScrollRunning by remember { mutableStateOf(false) }
        // Un appui sur la zone de lecture arrête l'auto-scroll pour la
        // vitesse/le mode courants ; remis à `false` par le panneau de
        // réglages quand l'utilisateur reclique un cran (même identique),
        // ce qui permet de relancer l'auto-scroll sans changer de valeur.
        var autoScrollUserStopped by remember { mutableStateOf(false) }
        LaunchedEffect(state.autoScrollSpeed, state.readingMode, state.reduceMotion, isPdf) {
            autoScrollUserStopped = false
        }
        LaunchedEffect(
            state.autoScrollSpeed, state.readingMode, state.reduceMotion, isPdf, autoScrollUserStopped,
        ) {
            autoScrollRunning = false
            if (
                state.autoScrollSpeed <= 0 || state.readingMode != ReadingMode.SCROLL ||
                state.reduceMotion || isPdf || autoScrollUserStopped
            ) {
                return@LaunchedEffect
            }
            val pxPerSecond = with(density) { autoScrollDpPerSecond(state.autoScrollSpeed).dp.toPx() }
            if (pxPerSecond <= 0f) return@LaunchedEffect
            autoScrollRunning = true
            try {
                // Cadence sur l'horloge de frame plutôt qu'un `delay` fixe
                // (16 ms dérive systématiquement au-dessus sous charge) :
                // le delta appliqué est calculé sur le temps réellement
                // écoulé entre deux frames, la vitesse en dp/s reste donc
                // fidèle quelle que soit la cadence de rendu effective.
                var lastFrameNanos = 0L
                while (isActive) {
                    // `canScrollForward` est `false` tant que la LazyColumn
                    // n'a pas encore mesuré son contenu (chapitre en
                    // chargement lazy) : on attend sa première mesure,
                    // borné à 2 s, au lieu de sortir d'emblée — un
                    // auto-scroll réglé juste après l'ouverture d'un
                    // chapitre s'arrêtait autrefois net et ne repartait
                    // jamais. Une fois la boucle lancée, `false` signifie
                    // la fin du chapitre : on sort.
                    if (!scrollState.canScrollForward) {
                        val measured = withTimeoutOrNull(2_000) {
                            snapshotFlow { scrollState.layoutInfo.totalItemsCount }.first { it > 0 }
                        } ?: break
                        continue
                    }
                    withFrameNanos { frameNanos ->
                        if (lastFrameNanos != 0L) {
                            val deltaSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
                            // Delta brut sans animation : `LazyListState` n'a
                            // pas de `scrollBy` (contrairement à `ScrollState`)
                            // — `dispatchRawDelta` avance le défilement du
                            // delta en pixels sans course d'animation.
                            scrollState.dispatchRawDelta(pxPerSecond * deltaSeconds)
                        }
                        lastFrameNanos = frameNanos
                    }
                }
            } finally {
                autoScrollRunning = false
            }
        }

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
                    // AUDIT_REACTIVITE_UX §5.1 — sans budget explicite, ce
                    // second cache mémoire (en plus de celui de l'ImageLoader
                    // par défaut, utilisé par les couvertures de la
                    // Bibliothèque) se dimensionne lui aussi à ~25% du tas
                    // disponible : les deux ensemble aggravent la pression
                    // mémoire sur un aller-retour Bibliothèque ↔ Lecteur, sur
                    // un appareil à faible mémoire. Borné explicitement, pas
                    // de mise en cache prématurée au-delà de ce qu'un
                    // chapitre illustré affiche réellement à l'écran.
                    .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.10).build() }
                    .build()
            }
        }

        CompositionLocalProvider(
            LocalImageLoader provides (epubImageLoader ?: LocalImageLoader.current),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates -> measuredAreaSize = coordinates.size },
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
                    isRenderReady = state.isFixedPageReady,
                )
            } else {
                when (state.readingMode) {
                ReadingMode.SCROLL -> {
                    val chapter = state.currentChapter
                    val blocks = (chapter?.content as? ChapterContent.Rich)?.blocks.orEmpty()
                    // AUDIT_REACTIVITE_UX §6.3 — le style de rendu du mode
                    // défilement était reconstruit à chaque recomposition de
                    // l'écran (dont à chaque mot prononcé pendant le TTS, via
                    // §3.3), puis redistribué à chaque BookBlockItem visible.
                    // `remember` sur les valeurs dont il dérive : un changement
                    // de réglage le recrée, un mot prononcé ne le recrée plus.
                    val textStyle = remember(
                        state.effectiveSettings.fontSize,
                        lineHeightSp,
                        state.resolvedTheme,
                        effectiveFontFamily,
                        state.isTextJustified,
                    ) {
                        TextStyle(
                            fontSize = state.effectiveSettings.fontSize.sp,
                            // Bug réel signalé à la vérification device : le
                            // réglage d'interligne n'avait AUCUN effet en mode
                            // défilement. `lineHeightSp` n'alimentait que la mesure
                            // de pagination (mode paginé) ; le style de rendu du
                            // défilement ne le posait nulle part, et le commentaire
                            // de sa déclaration affirmait pourtant le contraire —
                            // resté vrai à l'époque de `ParagraphText`, faux depuis
                            // la migration vers LazyColumn/BookBlockItem.
                            lineHeight = lineHeightSp.sp,
                            color = ThemeColors.text(state.resolvedTheme),
                            fontFamily = effectiveFontFamily,
                            // P4 — mêmes règles qu'en mode paginé (où elles
                            // viennent de `pagination.baseTextStyle`) : un réglage
                            // de lecture ne doit jamais dépendre du mode choisi.
                            textAlign = if (state.isTextJustified) TextAlign.Justify else TextAlign.Unspecified,
                            hyphens = if (state.isTextJustified) Hyphens.Auto else Hyphens.None,
                            lineBreak = if (state.isTextJustified) LineBreak.Paragraph else LineBreak.Unspecified,
                            // Lot 21 (correctif) — même locale que le style de
                            // MESURE (`pagination.baseTextStyle`), y compris la
                            // condition `justified` : une divergence ferait
                            // césurer le rendu différemment de la mesure, donc
                            // déborder les pages calculées. Voir le commentaire
                            // de `ChapterPaginationState.kt` pour le choix de
                            // ne pas l'appliquer hors justification.
                            localeList = if (state.isTextJustified) LocaleList("fr") else null,
                        )
                    }

                    // Parité avec le mode PAGED (absoluteHighlightedRange dans
                    // PagedChapterContent) : offset absolu (espace chapitre)
                    // du mot TTS actif, dérivé de Chapter.sentences —
                    // aligné avec ChapterTextMeasurer depuis la correction
                    // du séparateur de bloc (voir ChapterTextMeasurer.kt).
                    val currentSentenceStart = remember(uiState) {
                        derivedStateOf {
                            val live = uiState.value
                            live.currentChapter?.sentences?.getOrNull(live.currentSentenceIndex)?.startOffset
                        }
                    }
                    // Lues en phase de dessin (drawWithContent dans
                    // BookBlockItem), jamais en paramètre direct — un mot
                    // prononcé n'invalide donc que le dessin, jamais la
                    // mesure du BasicTextField (même contrainte que PageBlock).
                    val scrollHighlightedRangeState = remember(highlightedWordRangeState) {
                        derivedStateOf {
                            val wordRange = highlightedWordRangeState.value ?: return@derivedStateOf null
                            val sentenceStart = currentSentenceStart.value ?: return@derivedStateOf null
                            (sentenceStart + wordRange.first)..(sentenceStart + wordRange.last)
                        }
                    }
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
                    // Lot 21 — même discipline qu'en mode paginé :
                    // Motion.gestureSpring + préférence applicative
                    // reduceMotion (tâche 4), plus de spring en dur.
                    // Calculée dans la composition (spec @Composable).
                    val pullBackSpec = gesturePullBackSpec(state.reduceMotion)
                    LaunchedEffect(chapterTransition.isDragging) {
                        if (chapterTransition.isDragging) {
                            snapshotFlow { chapterTransition.pullPx }.collect { visualPull.snapTo(it) }
                        } else {
                            visualPull.animateTo(0f, pullBackSpec)
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
                                    .nestedScroll(chapterTransitionConnection)
                                    // Lot 21, tâche 9 — arrêt de l'auto-scroll
                                    // à la première interaction : tout appui
                                    // sur la zone défilable annule le job (le
                                    // down n'est pas consommé, le geste
                                    // utilisateur continue normalement).
                                    .pointerInput(autoScrollRunning) {
                                        if (autoScrollRunning) {
                                            awaitPointerEventScope {
                                                awaitFirstDown()
                                                autoScrollUserStopped = true
                                            }
                                        }
                                    },
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
                                        onClick = { chrome.onReadingAreaTap(state.isPlaying) },
                                        onAnnotationTapped = { annotation, bounds ->
                                            if (bounds != null) chrome.showAnnotationMenu(annotation, bounds)
                                        },
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
                    WithLiveValue({ uiState.value.currentSentenceIndex }) { live ->
                        PagedChapterContent(
                            chapter = state.currentChapter,
                            pagination = pagination,
                            currentSentenceIndex = live,
                            highlightedWordRange = highlightedWordRangeState,
                            annotations = state.annotations,
                            currentChapterIndex = state.currentChapterIndex,
                            chapterCount = state.chapters.size,
                            textColor = ThemeColors.text(state.resolvedTheme),
                            isReadingRulerEnabled = state.isReadingRulerEnabled,
                            contentPadding = readerMargin,
                            onClick = { chrome.onReadingAreaTap(state.isPlaying) },
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
                // Lot 24, tâche 2 — application immédiate SANS purger la
                // sélection : le popup reste ouvert (décision 1, Lot 24),
                // un tap suivant sur une autre couleur/type modifie la même
                // annotation (`ReaderViewModel.confirmAnnotation`,
                // `pendingAnnotationId`). Seule la fermeture externe du
                // popup (tap hors sélection, `onDismiss` ci-dessous) purge
                // désormais l'état — plus cette closure elle-même.
                onHighlight = { color, kind ->
                    viewModel.onIntent(ReaderIntent.ConfirmAnnotation(color = color, kind = kind))
                },
                onSaveNote = { content, color, kind ->
                    viewModel.onIntent(ReaderIntent.ConfirmAnnotation(color = color, kind = kind, content = content))
                    clearSelectionAndPopup()
                },
                onDismiss = { clearSelectionAndPopup() },
                // Lot 21, tâche 7 — contexte du partage : titre — auteur —
                // chapitre courant. Toujours au moins le chapitre/la page,
                // donc jamais vide. Extrait en fonction pure testable
                // (correctif) : c'était auparavant écrit en ligne dans ce
                // composable, donc invérifiable sans instrumentation.
                shareContext = buildShareContext(
                    title = state.title,
                    author = state.author,
                    chapterTitle = state.currentChapter?.title,
                    chapterIndex = state.currentChapterIndex,
                    isPdf = isPdf,
                ),
                recentColors = state.recentAnnotationColors,
            )
        }

        // Pile basse du HUD (panneau/pilule TTS, barre de luminosité, ligne
        // de statut) : overlay pur (`align(BottomCenter)`), plus jamais un
        // enchaînement de siblings dans la Column de lecture (voir
        // commentaire de tête ci-dessus). Ordre visuel préservé à
        // l'identique (panneau/pilule au-dessus de la barre de luminosité,
        // elle-même au-dessus de la ligne de statut) via une simple Column
        // interne.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (chrome.isHudVisible) {
                    // 3e.1 — pendant le TTS, la barre pilule remplace le panneau
                    // unifié (navigation par chapitre, retirée du panneau au lot
                    // 3b) ; le panneau complet ne revient que par l'overlay A5 ou
                    // à l'arrêt de la lecture.
                    // ADR-017 volet 2 — la barre pilule TTS suit desormais
                    // la disponibilite reelle de la narration, pas le format :
                    // un PDF qui porte du texte l'affiche comme un EPUB, un
                    // PDF entierement scanne ne la voit jamais (isPlaying
                    // reste faux, playCurrentSentence n'a aucune phrase).
                    if (state.isPlaying && !chrome.showFullPanelOverlay && state.supportsTts) {
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
                        // P5 — deux signaux distincts et complémentaires :
                        // `state.reduceMotion` est la préférence APPLICATIVE,
                        // `Motion.tween` applique en plus le réglage SYSTÈME
                        // d'échelle d'animation. Remplacer l'un par l'autre
                        // ferait perdre un des deux.
                        val fadeSpec = if (state.reduceMotion) tween<Float>(0) else Motion.tween<Float>()
                        val barAlpha by animateFloatAsState(
                            targetValue = if (chrome.isPillCollapsed) 0f else 1f,
                            animationSpec = fadeSpec,
                            label = "TtsPillBarAlpha",
                        )
                        val fabAlpha by animateFloatAsState(
                            targetValue = if (chrome.isPillCollapsed) 1f else 0f,
                            animationSpec = fadeSpec,
                            label = "TtsPillBarCollapsedAlpha",
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (barAlpha > 0f) {
                                WithLiveValue({ uiState.value.isAudioActive }) { live ->
                                    TtsPillBar(
                                        modifier = Modifier.align(Alignment.Center).graphicsLayer(alpha = barAlpha),
                                        isPlaying = state.isPlaying,
                                        isAudioActive = live,
                                        hasPreviousChapter = state.hasPreviousChapter,
                                        hasNextChapter = state.hasNextChapter,
                                        onPreviousChapter = { chrome.keepHudVisible(); viewModel.onIntent(ReaderIntent.PreviousChapter) },
                                        onPreviousSentence = { chrome.keepHudVisible(); viewModel.onIntent(ReaderIntent.SkipToPreviousSentence) },
                                        onPlayPause = {
                                            chrome.keepHudVisible()
                                            togglePlayback()
                                        },
                                        onNextSentence = { chrome.keepHudVisible(); viewModel.onIntent(ReaderIntent.SkipToNextSentence) },
                                        onNextChapter = { chrome.keepHudVisible(); viewModel.onIntent(ReaderIntent.NextChapter) },
                                        // 3e.3 — balayage redéfini en pause (voir KDoc de
                                        // TtsPillBarCollapsed) : pas de keepHudVisible, le
                                        // passage à isPlaying=false referme déjà la barre
                                        // et fait revenir le panneau unifié.
                                        onSwipeDown = { viewModel.onIntent(ReaderIntent.Pause) },
                                    )
                                }
                            }
                            if (fabAlpha > 0f) {
                                WithLiveValue({ uiState.value.isAudioActive }) { live ->
                                    TtsPillBarCollapsed(
                                        modifier = Modifier.align(Alignment.CenterEnd).graphicsLayer(alpha = fabAlpha),
                                        isAudioActive = live,
                                        onExpand = { chrome.expandPill() },
                                        onSwipeDown = { viewModel.onIntent(ReaderIntent.Pause) },
                                    )
                                }
                            }
                        }
                    } else {
                        WithLiveValue({ uiState.value.bookProgression }) { live ->
                            UnifiedControlPanel(
                                isPlaying = state.isPlaying,
                                sleepTimerActive = isSleepTimerArmed,
                                bookProgression = live,
                                onPlayPause = {
                                    chrome.keepHudVisible()
                                    togglePlayback()
                                },
                                onSleepTimerClick = { chrome.keepHudVisible(); chrome.showSleepTimerPanel = true },
                                onSearchClick = { chrome.keepHudVisible(); onSearchClick() },
                                onBookmarksClick = { chrome.keepHudVisible(); viewModel.onIntent(ReaderIntent.ToggleBookmarkList) },
                                onTocClick = { chrome.keepHudVisible(); viewModel.onIntent(ReaderIntent.ToggleToc) },
                                onThemeCycle = {
                                    chrome.keepHudVisible()
                                    val overrides = (state.currentOverrides ?: ReadingOverrides())
                                        .copy(theme = nextReadingTheme(state.effectiveSettings.theme))
                                    viewModel.onIntent(ReaderIntent.SetOverrides(overrides))
                                },
                                onAaClick = { chrome.keepHudVisible(); chrome.showSettingsPanel = true },
                                onTtsClick = { chrome.keepHudVisible(); chrome.showTtsPanel = true },
                                onReadingModeClick = { chrome.keepHudVisible(); viewModel.onIntent(ReaderIntent.ToggleReadingMode) },
                                // Indicateur du mode courant (lot HUD) : l'icône
                                // « Mode » doit refléter le mode ACTIF, pas un
                                // glyphe figé.
                                readingMode = state.readingMode,
                                showTtsControls = state.supportsTts,
                                showReadingModeToggle = !isPdf,
                                // Audit v1.0.0 (M6) : pas de sommaire pour PDF/TXT.
                                showToc = state.publicationFormat == PublicationFormat.EPUB,
                                surfaceColor = ThemeColors.barSurface(state.resolvedTheme),
                                accentColor = ThemeColors.accent(state.resolvedTheme),
                                onBrightnessClick = {
                                    // Bug réel corrigé (vérification device, lot 3d) : masque le
                                    // HUD au lieu de le garder visible — la barre prend sa place
                                    // plutôt que de s'empiler dessous.
                                    chrome.openBrightnessBar()
                                },
                            )
                        }
                    }
                }

                // 3d.3 — barre flottante à la place du panneau unifié (HUD masqué
                // pendant l'ajustement), pas un panneau séparé (UX_FLOW_DESIGN.md
                // §Luminosité). S'auto-masque après 3s d'inactivité (voir
                // LaunchedEffect ci-dessus), relancé à chaque ajustement.
                if (chrome.showBrightnessBar) {
                    ReaderBrightnessBar(
                        value = state.readerBrightness,
                        onValueChange = { value ->
                            viewModel.onIntent(ReaderIntent.SetReaderBrightness(value))
                            chrome.onBrightnessAdjusted()
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
                    //
                    // Valeurs vivantes (page courante, progression) calculées
                    // dans `WithLiveValue` : seule la ligne de statut se
                    // recompose quand elles changent, jamais l'écran.
                    val readingMode = state.readingMode
                    WithLiveValue({
                        val live = uiState.value
                        val complete = pagination.isMeasurementComplete(chapter)
                        val pageIndexInChapter = if (complete) {
                            when (readingMode) {
                                ReadingMode.PAGED -> pagedLivePageIndex
                                ReadingMode.SCROLL -> pagination.pageIndexAt(chapter.index, live.currentSentenceIndex)
                            }
                        } else {
                            0
                        }
                        StatusLineValues(
                            pageInChapter = pageIndexInChapter + 1,
                            pageCountInChapter = if (complete) pagination.pageCount(chapter.index) else 0,
                            showPageCounter = complete,
                            // Arrondie à la précision AFFICHÉE (dixième de
                            // pourcent, même arrondi que `formatProgressionFr`) :
                            // la valeur brute change à chaque paragraphe, la
                            // valeur affichée bien plus rarement — la ligne de
                            // statut ne se recompose plus que lorsqu'un chiffre
                            // visible change.
                            bookProgression = (live.bookProgression * 1000).roundToInt() / 1000f,
                        )
                    }) { values ->
                        StatusLineBar(
                            chapterNumber = state.currentChapterIndex + 1,
                            pageInChapter = values.pageInChapter,
                            pageCountInChapter = values.pageCountInChapter,
                            bookProgression = values.bookProgression,
                            showPageCounter = values.showPageCounter,
                            contentColor = ThemeColors.barContent(state.resolvedTheme),
                            backgroundColor = ThemeColors.barSurface(state.resolvedTheme),
                        )
                    }
                }
            }
        }

        // B.2/3d.2 — Panneau réglages de typographie in-reader (TT). Le
        // thème n'y vit plus (bascule cyclique du lot 3b, cartes devenues
        // redondantes) — seuls taille et interligne restent, avec un
        // aperçu du texte RÉELLEMENT en cours de lecture, pas un exemple
        // inventé.
        if (chrome.showSettingsPanel) {
            val previewSentences = state.currentChapter?.sentences.orEmpty()
            val previewText = previewSentences
                .drop(uiState.value.currentSentenceIndex)
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
                // Correctif Lot 21 — sélecteur de police, jusqu'ici
                // inatteignable (aucune UI ne dispatchait
                // SettingsIntent.SetFontFamily). `effectiveSettings`
                // reflète déjà `UserPreferences.fontFamily`, aucune
                // surcharge par publication (voir EffectiveReadingSettings).
                currentFontFamily = state.effectiveSettings.fontFamily,
                // Lot 21, tâche 9 — auto-scroll visuel.
                autoScrollSpeed = state.autoScrollSpeed,
                reduceMotion = state.reduceMotion,
                isScrollMode = state.readingMode == ReadingMode.SCROLL,
                onMarginStepChange = { step -> viewModel.onIntent(ReaderIntent.SetReaderMarginStep(step)) },
                onTextJustifiedChange = { justified -> viewModel.onIntent(ReaderIntent.SetTextJustified(justified)) },
                onKeepScreenOnChange = { enabled -> viewModel.onIntent(ReaderIntent.SetKeepScreenOn(enabled)) },
                onFontFamilyChange = { family -> viewModel.onIntent(ReaderIntent.SetFontFamily(family)) },
                onAutoScrollSpeedChange = { speed ->
                    // Reclique sur le même cran après un arrêt au toucher :
                    // relance sans attendre un changement de valeur.
                    autoScrollUserStopped = false
                    viewModel.onIntent(ReaderIntent.SetAutoScrollSpeed(speed))
                },
                onDismiss = { chrome.showSettingsPanel = false },
            )
        }

        // B.3 — Panneau TTS in-reader
        if (chrome.showTtsPanel) {
            val sentences = state.currentChapter?.sentences ?: emptyList()
            ReaderTtsPanel(
                isPlaying = state.isPlaying,
                currentSentenceIndex = uiState.value.currentSentenceIndex,
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
                onDismiss = { chrome.showTtsPanel = false },
            )
        }

        // 3d.4/3d.5 — Panneau Minuteur (remplace le cycle sur l'icône Veille) + repos oculaire
        if (chrome.showSleepTimerPanel) {
            WithLiveValue({ uiState.value.sleepTimer }) { live ->
                SleepTimerPanel(
                    remainingMs = live?.remainingMs,
                    armedMinutes = live?.let { (it.totalMs / 60_000L).toInt() },
                    onSetSleepTimer = { minutes -> viewModel.onIntent(ReaderIntent.SetSleepTimer(minutes)) },
                    eyeRestReminderEnabled = state.eyeRestReminderEnabled,
                    eyeRestReminderIntervalMinutes = state.eyeRestReminderIntervalMinutes,
                    onSetEyeRestReminderEnabled = { enabled -> viewModel.onIntent(ReaderIntent.SetEyeRestReminderEnabled(enabled)) },
                    onSetEyeRestReminderInterval = { minutes -> viewModel.onIntent(ReaderIntent.SetEyeRestReminderInterval(minutes)) },
                    onDismiss = { chrome.showSleepTimerPanel = false },
                    showSleepTimer = state.supportsTts,
                )
            }
        }

        // 3d.5 — popup de rappel de repos oculaire, indépendant du HUD :
        // doit rester visible même si isHudVisible est déjà retombé à
        // faux (contrairement aux panneaux ci-dessus, ouverts par une
        // action HUD explicite).
        if (state.isEyeRestReminderVisible) {
            WithLiveValue({ uiState.value.eyeRestReminderCountdownS }) { live ->
                EyeRestReminderDialog(
                    countdownSeconds = live,
                    onResume = { viewModel.onIntent(ReaderIntent.ResumeFromEyeRestReminder) },
                    onSnooze = { viewModel.onIntent(ReaderIntent.SnoozeEyeRestReminder) },
                )
            }
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
                    isCurrentPageBookmarked = uiState.value.isCurrentPageBookmarked,
                    onBookmarkClick = { bookmark -> viewModel.onIntent(ReaderIntent.NavigateToLocator(bookmark.locator)) },
                    onAnnotationClick = { annotation -> viewModel.onIntent(ReaderIntent.NavigateToLocator(annotation.startLocator)) },
                    onToggleBookmark = {
                        // P5 — confirmation tactile d'une action qui aboutit,
                        // distincte du simple cran de page (`tick`).
                        haptics.confirm()
                        viewModel.onIntent(ReaderIntent.ToggleBookmarkAtCurrentPosition)
                    },
                    onClose = { viewModel.onIntent(ReaderIntent.ToggleBookmarkList) },
                    onDeleteAnnotation = { annotation -> viewModel.onIntent(ReaderIntent.DeleteAnnotation(annotation.id)) },
                    onEditAnnotationNote = { annotation -> chrome.editingAnnotation = annotation },
                    onDeleteBookmark = { bookmark -> viewModel.onIntent(ReaderIntent.DeleteBookmark(bookmark.id)) },
                    onEditBookmarkNote = { bookmark -> chrome.editingBookmark = bookmark },
                )
            }
        }

        // Lot 23, tâche 12 — menu contextuel in-situ, tap sur une
        // annotation déjà posée. `onEdit` réutilise `editingAnnotation`
        // (même dialogue que depuis BookmarkPanel, Lot 22 tâche 11).
        val currentTappedAnnotation = chrome.tappedAnnotation
        val currentTappedBounds = chrome.tappedAnnotationBounds
        if (currentTappedAnnotation != null && currentTappedBounds != null) {
            AnnotationTapPopup(
                boundsInWindow = currentTappedBounds,
                onEdit = {
                    chrome.editingAnnotation = currentTappedAnnotation
                    chrome.dismissAnnotationMenu()
                },
                onDelete = {
                    viewModel.onIntent(ReaderIntent.DeleteAnnotation(currentTappedAnnotation.id))
                    chrome.dismissAnnotationMenu()
                },
                onDismiss = { chrome.dismissAnnotationMenu() },
            )
        }

        if (chrome.editingAnnotation != null) {
            EditNoteDialog(
                title = "Modifier la note",
                initialNote = chrome.editingAnnotation?.content.orEmpty(),
                onSave = { note ->
                    viewModel.onIntent(ReaderIntent.UpdateAnnotationNote(chrome.editingAnnotation!!.id, note.trim().ifBlank { null }))
                    chrome.editingAnnotation = null
                },
                onDismiss = { chrome.editingAnnotation = null },
            )
        }

        if (chrome.editingBookmark != null) {
            EditNoteDialog(
                title = "Modifier la note",
                initialNote = chrome.editingBookmark?.note.orEmpty(),
                onSave = { note ->
                    viewModel.onIntent(ReaderIntent.EditBookmarkNote(chrome.editingBookmark!!.id, note))
                    chrome.editingBookmark = null
                },
                onDismiss = { chrome.editingBookmark = null },
            )
        }

        // Lot 21, tâche 5 (corrigé) — saisie de note OPTIONNELLE : n'apparaît
        // que si l'utilisateur a explicitement demandé « Ajouter une note »
        // depuis le Snackbar ci-dessus, jamais imposée au geste de créer un
        // signet. Fermer (« Plus tard », tap hors champ) laisse le signet
        // sans note ; la note vide est enregistrée comme `null`.
        if (chrome.showBookmarkNoteDialog && state.pendingBookmarkNoteId != null) {
            BookmarkNoteDialog(
                onSave = { note ->
                    viewModel.onIntent(ReaderIntent.SaveBookmarkNote(note))
                    chrome.showBookmarkNoteDialog = false
                },
                onDismiss = {
                    viewModel.onIntent(ReaderIntent.DismissBookmarkNotePrompt)
                    chrome.showBookmarkNoteDialog = false
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

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

/**
 * Lot 21, tâche 7 (extrait, correctif) — contexte « titre — auteur —
 * chapitre » joint au texte partagé. Toujours au moins une entrée (le
 * repli chapitre/page), donc jamais vide. `isPdf` distingue le repli :
 * `chapterIndex` est un index de PAGE sur un PDF, jamais un chapitre —
 * même correction que sur le titre des signets PDF (`ReaderViewModel`).
 */
internal fun buildShareContext(
    title: String?,
    author: String?,
    chapterTitle: String?,
    chapterIndex: Int,
    isPdf: Boolean,
): String = buildList {
    title?.takeIf { it.isNotBlank() }?.let(::add)
    author?.takeIf { it.isNotBlank() }?.let(::add)
    val fallback = if (isPdf) "Page ${chapterIndex + 1}" else "Chapitre ${chapterIndex + 1}"
    add(chapterTitle?.takeIf { it.isNotBlank() } ?: fallback)
}.joinToString(" — ")

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

/**
 * Lot 22, tâche 11 — dialogue d'édition générique (annotation ou signet),
 * distinct de [BookmarkNoteDialog] : celui-ci ÉDITE une note déjà posée
 * (pré-remplie), quand [BookmarkNoteDialog] PROPOSE d'en ajouter une juste
 * après création (jamais pré-rempli, boutons différents).
 */
@Composable
private fun EditNoteDialog(
    title: String,
    initialNote: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf(initialNote) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(note) }) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

/**
 * Lot 21, tâche 5 — saisie de note OPTIONNELLE d'un signet venant d'être
 * créé. Le signet existe déjà (le toggle l'a créé) : ce dialogue ne
 * bloque jamais le geste — fermer (« Plus tard », tap hors champ) laisse
 * le signet sans note. La note vide est enregistrée comme `null`.
 */
@Composable
private fun BookmarkNoteDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note sur ce signet") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Facultatif : ajoutez une note à ce signet pour le retrouver plus facilement.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    placeholder = { Text("Ex. : passage à relire") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(note) }) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Plus tard") }
        },
    )
}


/**
 * Frontière de recomposition pour une valeur VIVANTE (voir
 * `ReaderLiveFields.kt`) : [read] est évalué dans un `derivedStateOf` propre
 * à ce composable, donc seule SA portée se recompose quand la valeur lue
 * change — jamais l'appelant, qui ne la lit pas. [content] reçoit la valeur.
 */
@Composable
private fun <T> WithLiveValue(read: () -> T, content: @Composable (T) -> Unit) {
    val latestRead = rememberUpdatedState(read)
    val value by remember { derivedStateOf { latestRead.value() } }
    content(value)
}

/** Valeurs vivantes de la ligne de statut (page courante, progression). */
private data class StatusLineValues(
    val pageInChapter: Int,
    val pageCountInChapter: Int,
    val showPageCounter: Boolean,
    val bookProgression: Float,
)
