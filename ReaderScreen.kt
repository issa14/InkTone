package com.readflow.ui.screen.reader

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.readflow.data.database.entity.AnnotationEntity
import com.readflow.data.database.entity.HighlightEntity
import com.readflow.data.database.entity.PronunciationRule
import com.readflow.domain.model.Chapter
import com.readflow.service.audio.PlaybackState
import com.readflow.service.audio.PlaybackStatus
import com.readflow.ui.theme.OpenDyslexicFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────
//  READER SCREEN — Immersif, style Moon+ Reader
// ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    onBookmarksClick: (String) -> Unit = {},
    onSearchClick: (String) -> Unit = {},
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val highlights by viewModel.highlights.collectAsState()
    val chapterHighlights by viewModel.chapterHighlights.collectAsState()
    val scrollTarget by viewModel.scrollTarget.collectAsState()
    val chapter = state.currentChapter
    val book = state.book

    // Mode immersif
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    // Auto-hide du HUD
    LaunchedEffect(state.isHudVisible) {
        if (state.isHudVisible) {
            kotlinx.coroutines.delay(4000)
            viewModel.hideHud()
        }
    }

    // Couleurs selon le thème
    val (bgColor, textColor, accentColor) = when (state.readerTheme) {
        ReaderTheme.NIGHT -> Triple(Color(0xFF0D0D0D), Color.White, Color(0xFFFFB74D))
        ReaderTheme.DAY -> Triple(Color(0xFFFAFAFA), Color(0xFF1A1A1A), Color(0xFF0091EA))
        ReaderTheme.SEPIA -> Triple(Color(0xFFF4ECD8), Color(0xFF3C2F2F), Color(0xFFB65D30))
    }

    // Taille de l'écran pour le tiers central
    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .onSizeChanged { screenSize = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull() ?: continue
                        // Ne traiter que les taps non consommés (scroll et sélection passent avant)
                        if (!change.isConsumed && change.pressed.not()) {
                            change.consume()
                            val offset = change.position
                            if (screenSize.width > 0) {
                                val left = screenSize.width / 3f
                                val right = 2f * screenSize.width / 3f
                                if (offset.x in left..right) {
                                    viewModel.toggleHud()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // ── COUCHE 0 : Texte (100% espace, jamais ne bouge) ─
        when {
            state.isLoading -> LoadingIndicator()
            state.error != null -> ErrorMessage(state.error!!)
            chapter != null -> ImmersiveText(
                chapter = chapter,
                playbackState = playbackState,
                textColor = textColor,
                accentColor = accentColor,
                useOpenDyslexic = state.useOpenDyslexic,
                chapterHighlights = chapterHighlights,
                currentChapterIndex = state.currentChapterIndex,
                onSaveHighlight = { sentIdx, start, end, text, color ->
                    viewModel.saveHighlight(sentIdx, start, end, text, color)
                },
                onDeleteHighlight = { viewModel.deleteHighlight(it) },
                onSaveNote = { id, note -> viewModel.saveNoteForHighlight(id, note) },
                onPronounceSelection = { text ->
                    viewModel.pronounceSelectedText(text)
                },
                scrollTarget = scrollTarget,
                onConsumeScrollTarget = { viewModel.consumeScrollTarget() }
            )
        }

        // ── Micro-indicateur (HUD masqué) ────
        if (!state.isHudVisible && chapter != null) {
            val pct = if (state.totalSentences > 0)
                (state.currentSentenceIndex * 100.0 / state.totalSentences) else 0.0
            Text(
                "${"%.1f".format(pct)}%",
                color = textColor.copy(alpha = 0.4f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                    .padding(bottom = 8.dp)
            )
        }

        // ── TopBar (overlay) ─────────────────────────
        AnimatedVisibility(
            visible = state.isHudVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                title = book?.title ?: "",
                subtitle = "Ch. ${state.currentChapterIndex + 1}/${book?.totalChapters ?: 0}",
                onBack = onBack,
                onToc = { viewModel.showTocSheet() },
                onBookmarks = { book?.title?.let { onBookmarksClick(it) } },
                onSearch = { book?.title?.let { onSearchClick(it) } },
                onAnnotations = { viewModel.showAnnotationsSheet() }
            )
        }

        // ── UnifiedControlPanel (overlay) ────────────
        val panelBg = when (state.readerTheme) {
            ReaderTheme.SEPIA -> Color(0xFFE8DCC8)
            else -> Color(0xFF0A0A0A)
        }
        AnimatedVisibility(
            visible = state.isHudVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            UnifiedControlPanel(
                isPlaying = state.isPlaying,
                accentColor = accentColor,
                panelBg = panelBg,
                useOpenDyslexic = state.useOpenDyslexic,
                onTtsClick = { if (state.isPlaying) viewModel.pause() else viewModel.play() },
                onThemeCycle = { viewModel.cycleTheme() },
                onFontToggle = { viewModel.toggleOpenDyslexic() },
                onPrevChapter = { viewModel.previousChapter() },
                onNextChapter = { viewModel.nextChapter() },
                onSettingsClick = { viewModel.showTtsSheet() }
            )
        }
    }

    // ── Panneau TTS ───────────────────────
    if (state.isTtsSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideTtsSheet() },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
        ) {
            TtsPanel(
                chapterTitle = chapter?.title ?: "",
                sentenceIndex = state.currentSentenceIndex,
                totalSentences = state.totalSentences,
                isPlaying = state.isPlaying,
                onPlay = { viewModel.play() },
                onPause = { viewModel.pause() },
                onStop = { viewModel.stop() },
                onPrevious = { viewModel.previousSentence() },
                onNext = { viewModel.nextSentence() },
                onSpeedChange = { viewModel.setSpeed(it) },
                onVoiceChange = { viewModel.setVoice(it) },
                currentSpeed = state.speed,
                currentVoice = state.voice,
                // Sleep timer
                sleepTimerRemaining = viewModel.sleepTimerRemaining.collectAsState().value,
                onStartSleepTimer = { viewModel.startSleepTimer(it) },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                // Pronunciation
                pronunciationRules = viewModel.pronunciationRules.collectAsState().value,
                onAddRule = { pattern, replacement, isRegex ->
                    viewModel.addPronunciationRule(pattern, replacement, isRegex)
                },
                onDeleteRule = { viewModel.deletePronunciationRule(it) },
                onToggleRule = { viewModel.togglePronunciationRule(it) }
            )
        }
    }

    // ── COUCHE 2 : Table des matières ────────────────
    if (state.isTocSheetVisible && book != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideTocSheet() },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
        ) {
            ChapterPicker(
                totalChapters = book?.totalChapters ?: 0,
                currentChapter = state.currentChapterIndex,
                onSelect = { idx ->
                    viewModel.goToChapter(idx)
                    viewModel.hideTocSheet()
                    viewModel.hideHud()
                }
            )
        }
    }

    // ── COUCHE 3 : Annotations & Surlignages ────────
    if (state.isAnnotationsSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideAnnotationsSheet() },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
        ) {
            AnnotationsDrawer(
                highlights = highlights,
                onHighlightClick = { hl ->
                    viewModel.scrollToSentence(hl.chapterIndex, hl.sentenceIndex)
                    viewModel.hideAnnotationsSheet()
                    viewModel.hideHud()
                },
                onDeleteHighlight = { viewModel.deleteHighlight(it) },
                onUpdateNote = { hl, note ->
                    viewModel.saveNoteForHighlight(hl.id, note)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────
//  CHAPTER PICKER — Liste des chapitres
// ─────────────────────────────────────────────────────

@Composable
private fun ChapterPicker(
    totalChapters: Int,
    currentChapter: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Table des matières",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 12.dp))
        for (i in 0 until totalChapters) {
            val isCurrent = i == currentChapter
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                color = if (isCurrent) Color(0xFFFFB74D).copy(alpha = 0.15f)
                        else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                onClick = { onSelect(i) }
            ) {
                Text(
                    "Chapitre ${i + 1}",
                    color = if (isCurrent) Color(0xFFFFB74D)
                            else Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────
//  TOP BAR — Semi-transparente, titre centré
// ─────────────────────────────────────────────────────

@Composable
private fun LoadingIndicator() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun ErrorMessage(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("❌ $msg", color = Color(0xFFFF6B6B),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(24.dp))
    }
}


