package com.readflow.ui.screen.reader

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.readflow.domain.model.Chapter

// ─────────────────────────────────────────────────────
//  READER SCREEN — Immersif, style Moon+ Reader
// ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val book = state.book
    val chapter = state.currentChapter

    var showHud by remember { mutableStateOf(false) }
    var showTtsPanel by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }

    // Mode immersif : masquer barres système
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    // Auto-hide du HUD après 4 secondes
    LaunchedEffect(showHud) {
        if (showHud) {
            kotlinx.coroutines.delay(4000)
            showHud = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D)) // fond lecture sombre
    ) {
        // ── ZONE 0 : TEXTE IMMERSIF ──────────────────
        when {
            state.isLoading -> LoadingIndicator()
            state.error != null -> ErrorMessage(state.error!!)
            chapter != null -> ImmersiveText(
                chapter = chapter,
                currentSentenceIndex = state.currentSentenceIndex,
                isPlaying = state.isPlaying,
                onTap = { showHud = !showHud }
            )
        }

        // ── ZONE 1 : TOP BAR (overlay animé) ──────────
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                title = book?.title ?: "",
                chapterLabel = if (book != null) "Ch. ${state.currentChapterIndex + 1}/${book.totalChapters}" else "",
                onBack = onBack,
                onToc = { showToc = true }
            )
        }

        // ── ZONE 1 : BOTTOM BAR (overlay animé) ───────
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                progress = if (chapter != null && chapter.sentences.isNotEmpty())
                    state.currentSentenceIndex.toFloat() / chapter.sentences.size else 0f,
                percentage = if (chapter != null && chapter.sentences.isNotEmpty())
                    (state.currentSentenceIndex * 100 / chapter.sentences.size) else 0,
                onTtsClick = { showTtsPanel = true }
            )
        }
    }

    // ── ZONE 2 : MODAL BOTTOM SHEET TTS ──────────────
    if (showTtsPanel) {
        ModalBottomSheet(
            onDismissRequest = { showTtsPanel = false },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
        ) {
            TtsPanel(
                chapterTitle = chapter?.title ?: "",
                sentenceIndex = state.currentSentenceIndex,
                totalSentences = chapter?.sentences?.size ?: 0,
                isPlaying = state.isPlaying,
                onPlay = { viewModel.play() },
                onPause = { viewModel.pause() },
                onStop = { viewModel.stop() },
                onPrevious = { /* TODO */ },
                onNext = { /* TODO */ },
                onSpeedChange = { viewModel.setSpeed(it) },
                onVoiceChange = { viewModel.setVoice(it) },
                currentSpeed = viewModel.currentSpeed,
                currentVoice = viewModel.currentVoice
            )
        }
    }

    // ── ZONE 3 : TABLE DES MATIÈRES ─────────────────
    if (showToc && book != null) {
        ModalBottomSheet(
            onDismissRequest = { showToc = false },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
        ) {
            ChapterPicker(
                totalChapters = book.totalChapters,
                currentChapter = state.currentChapterIndex,
                chapterTitle = chapter?.title ?: "",
                onSelect = { idx ->
                    viewModel.goToChapter(idx)
                    showToc = false
                    showHud = false
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
    chapterTitle: String,
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
private fun ReaderTopBar(
    title: String,
    chapterLabel: String,
    onBack: () -> Unit,
    onToc: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0A0A0A).copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour",
                    tint = Color.White.copy(alpha = 0.85f))
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(chapterLabel, color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall)
            }
            @Suppress("DEPRECATION")
            IconButton(onClick = onToc) {
                Icon(Icons.Default.List, "TOC",
                    tint = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────
//  BOTTOM BAR — Progression + bouton TTS
// ─────────────────────────────────────────────────────

@Composable
private fun ReaderBottomBar(
    progress: Float,
    percentage: Int,
    onTtsClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0A0A0A).copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Slider de progression
            Slider(
                value = progress,
                onValueChange = {},
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFB74D),
                    activeTrackColor = Color(0xFFFFB74D),
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                )
            )
            Spacer(Modifier.width(10.dp))
            Text("$percentage%", color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall)

            Spacer(Modifier.width(12.dp))

            // Bouton TTS (FAB)
            FloatingActionButton(
                onClick = onTtsClick,
                modifier = Modifier.size(42.dp),
                containerColor = Color(0xFFFFB74D),
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
            ) {
                Icon(Icons.Outlined.Headphones, "Audio",
                    tint = Color(0xFF0D0D0D), modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────
//  TEXTE IMMERSIF — Plein écran, scrollable
// ─────────────────────────────────────────────────────

@Composable
private fun ImmersiveText(
    chapter: Chapter,
    currentSentenceIndex: Int,
    isPlaying: Boolean,
    onTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // pointerInput en PREMIER (inner), verticalScroll en DERNIER (outer)
            // → scroll reçoit les événements en priorité, tap détecté sans bloquer
            .pointerInput(Unit) {
                detectTapGestures { onTap() }
            }
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // Titre du chapitre
        Text(
            chapter.title,
            style = MaterialTheme.typography.headlineSmall.copy(
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp
            )
        )
        Spacer(Modifier.height(24.dp))

        // Phrases
        chapter.sentences.forEachIndexed { index, sentence ->
            val highlighted = index == currentSentenceIndex && isPlaying
            Text(
                text = sentence.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = when {
                        highlighted -> Color(0xFFFFB74D)
                        else -> Color.White.copy(alpha = 0.82f)
                    },
                    fontSize = 17.sp,
                    lineHeight = 28.sp,
                    fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal
                ),
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }

        Spacer(Modifier.height(120.dp))
    }
}

// ─────────────────────────────────────────────────────
//  PANNEAU TTS — Modal Bottom Sheet
// ─────────────────────────────────────────────────────

@Composable
private fun TtsPanel(
    chapterTitle: String,
    sentenceIndex: Int,
    totalSentences: Int,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVoiceChange: (Int) -> Unit,
    currentSpeed: Float,
    currentVoice: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Titre
        Text(chapterTitle, color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text("Phrase ${sentenceIndex + 1} / $totalSentences",
            color = Color.White.copy(alpha = 0.35f),
            style = MaterialTheme.typography.labelSmall)

        Spacer(Modifier.height(24.dp))

        // Contrôles lecture
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.SkipPrevious, "Précédent",
                    tint = Color.White.copy(alpha = 0.5f))
            }

            Spacer(Modifier.width(24.dp))

            FilledIconButton(
                onClick = { if (isPlaying) onPause() else onPlay() },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFFFFB74D)
                )
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Lire",
                    modifier = Modifier.size(32.dp),
                    tint = Color(0xFF0D0D0D)
                )
            }

            Spacer(Modifier.width(24.dp))

            IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.SkipNext, "Suivant",
                    tint = Color.White.copy(alpha = 0.5f))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Stop discret
        TextButton(onClick = onStop) {
            Text("⏹ Arrêter", color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(20.dp))

        // Vitesse
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Vitesse", color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall)
            Slider(
                value = currentSpeed,
                onValueChange = onSpeedChange,
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFB74D),
                    activeTrackColor = Color(0xFFFFB74D)
                )
            )
            Text("${"%.1f".format(currentSpeed)}x",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(12.dp))

        // Voix
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Voix", color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(12.dp))
            FilterChip(
                selected = currentVoice == 0,
                onClick = { onVoiceChange(0) },
                label = { Text("♀ Jessica", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFFB74D).copy(alpha = 0.25f),
                    selectedLabelColor = Color.White
                )
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = currentVoice == 1,
                onClick = { onVoiceChange(1) },
                label = { Text("♂ Pierre", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFFB74D).copy(alpha = 0.25f),
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────
//  ÉTATS : chargement, erreur
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


