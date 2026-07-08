package com.readflow.ui.screen.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.readflow.domain.model.Book
import com.readflow.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onDebugClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDrawer by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    val epubPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importEpub(it) } }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── FOND ─────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppBackground
        ) {
            Scaffold(
                topBar = {
                    TopBar(
                        onMenu = { showDrawer = true },
                        onFilterToggle = { showFilter = !showFilter },
                        onOverflow = { showOverflow = true }
                    )
                },
                containerColor = AppBackground
            ) { padding ->
                Column(modifier = Modifier.padding(padding)) {
                    state.error?.let { err ->
                        ErrorBanner(err, viewModel::clearError)
                    }

                    when {
                        state.isLoading && state.books.isEmpty() -> LoadingView()
                        state.books.isEmpty() && !state.isLoading -> EmptyView()
                        else -> ShelfGrid(state.books, onBookClick)
                    }
                }
            }
        }

        // ── FILTER DROPDOWN ──────────────────────────
        AnimatedVisibility(
            visible = showFilter,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 55.dp)
        ) {
            FilterDropdown(onDismiss = { showFilter = false })
        }

        // ── OVERFLOW MENU ────────────────────────────
        if (showOverflow) {
            OverflowMenu(
                onDismiss = { showOverflow = false },
                onImport = { epubPicker.launch(arrayOf("application/epub+zip")) }
            )
        }

        // ── NAV DRAWER ───────────────────────────────
        if (showDrawer) {
            DrawerOverlay(onDismiss = { showDrawer = false })
        }
        NavDrawer(
            visible = showDrawer,
            onDismiss = { showDrawer = false },
            onDebug = onDebugClick
        )

        // ── FLOATING CONTROLS ────────────────────────
        FloatingControls(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 20.dp),
            onReadResume = { /* TODO: reprendre dernier livre */ }
        )
    }
}

// ─────────────────────────────────────────────────────
//  TOP BAR — Style prototype (bleue, dropdown trigger)
// ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onMenu: () -> Unit,
    onFilterToggle: () -> Unit,
    onOverflow: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tous les livres", fontWeight = FontWeight.Medium, fontSize = 17.sp)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onFilterToggle, modifier = Modifier.size(18.dp)) {
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, "Menu", tint = Color.White)
            }
        },
        actions = {
            IconButton(onClick = { /* search */ }) {
                Icon(Icons.Default.Search, "Rechercher", tint = Color.White)
            }
            IconButton(onClick = onFilterToggle) {
                Icon(Icons.Default.FilterList, "Filtrer", tint = Color.White)
            }
            IconButton(onClick = onOverflow) {
                Icon(Icons.Default.MoreVert, "Plus", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AccentBlue
        )
    )
}

// ─────────────────────────────────────────────────────
//  SHELF GRID — 3 colonnes, couvertures gradient
// ─────────────────────────────────────────────────────

@Composable
private fun ShelfGrid(books: List<Book>, onBookClick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(bottom = 80.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookCover(
                book = book,
                gradientIndex = book.title.hashCode().mod(CoverGradients.size),
                onClick = { onBookClick(book.id) }
            )
        }
    }
}

@Composable
private fun BookCover(
    book: Book,
    gradientIndex: Int,
    onClick: () -> Unit
) {
    val gradient = CoverGradients[gradientIndex.coerceIn(0, CoverGradients.lastIndex)]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Couverture avec gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.linearGradient(gradient))
        ) {
            // Titre sur la couverture
            Text(
                book.title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.95f),
                lineHeight = 14.sp,
                modifier = Modifier.padding(8.dp),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )

            // Badge progression (%)
            // TODO: utiliser la progression réelle
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(26.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "0%",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Dots de statut
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == 0) Color(0xFF00E676)
                                else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }

        // Titre + auteur sous la couverture
        Spacer(Modifier.height(4.dp))
        Text(
            book.title,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = TextMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            book.author,
            fontSize = 10.sp,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────
//  FILTER DROPDOWN — 2 colonnes
// ─────────────────────────────────────────────────────

@Composable
private fun FilterDropdown(onDismiss: () -> Unit) {
    var selectedFilter by remember { mutableIntStateOf(0) }
    val categories = listOf(
        "Tous les livres", "Favoris", "Séries", "Auteur", "Tags", "Dossiers", "Classements"
    )
    val subItems = listOf(
        listOf(), listOf(),
        listOf("Black Wings" to 1, "Contes et nouvelles" to 5, "Epub commercial" to 1),
        listOf(), listOf(), listOf(), listOf()
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp)),
        color = SurfaceRaised,
        tonalElevation = 8.dp
    ) {
        Row(modifier = Modifier.height(340.dp)) {
            // Colonne gauche
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .verticalScroll(rememberScrollState())
            ) {
                categories.forEachIndexed { i, cat ->
                    Surface(
                        color = if (i == selectedFilter) SurfaceDark else Color.Transparent,
                        onClick = { selectedFilter = i }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(cat, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = if (i == selectedFilter) AccentBlue else TextMain)
                            if (i == selectedFilter) {
                                Text("›", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Colonne droite
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                subItems.getOrNull(selectedFilter)?.forEach { (name, count) ->
                    Surface(
                        color = SurfaceRaised,
                        onClick = onDismiss
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(name, fontSize = 13.sp, color = TextMain,
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$count", fontSize = 11.sp, color = TextMuted,
                                modifier = Modifier.background(ShelfOverlay, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────
//  OVERFLOW MENU
// ─────────────────────────────────────────────────────

@Composable
private fun OverflowMenu(onDismiss: () -> Unit, onImport: () -> Unit) {
    // Overlay pour fermer au tap extérieur
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 55.dp, end = 8.dp)
                .width(260.dp),
            color = Color.White,
            shape = RoundedCornerShape(4.dp),
            tonalElevation = 8.dp
        ) {
            Column {
                OverflowMenuItem("Importer des livres", Icons.Default.FileUpload) {
                    onDismiss(); onImport()
                }
                OverflowMenuItem("Couverture par défaut", Icons.Default.Image) { onDismiss() }
                OverflowMenuItem("Reconstruire les couvertures", Icons.Default.Refresh) { onDismiss() }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                OverflowMenuItem("Synchroniser avec le cloud", Icons.Default.CloudUpload,
                    color = AccentBlue) { onDismiss() }
            }
        }
    }
}

@Composable
private fun OverflowMenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                              color: Color = Color(0xFF333333), onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFF757575), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, color = color)
    }
}

// ─────────────────────────────────────────────────────
//  NAV DRAWER — Tiroir latéral gauche
// ─────────────────────────────────────────────────────

@Composable
private fun DrawerOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss)
    )
}

@Composable
private fun NavDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    onDebug: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { -it },
        exit = slideOutHorizontally { -it }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(310.dp),
            color = Color.White
        ) {
            Column {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF1A237E), Color(0xFF3F51B5))))
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text("ReadFlow", color = Color.White,
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }

                // Menu items
                Column(modifier = Modifier.weight(1f)) {
                    DrawerItem("Liste des récents", Icons.Default.Schedule)
                    DrawerItem("Bibliothèque", Icons.Default.Book, active = true)
                    DrawerItem("Fichiers", Icons.Default.Folder)
                    DrawerItem("Catalogues OPDS", Icons.Default.Language)
                    DrawerItem("Marque-pages et notes", Icons.Default.Bookmark)
                }

                // Footer
                Surface(color = Color(0xFFF5F5F5)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DrawerFooterBtn("Options", Icons.Default.Settings, onDismiss)
                        DrawerFooterBtn("À propos", Icons.Default.Info, onDismiss)
                        DrawerFooterBtn("Thème", Icons.Default.DarkMode, onDismiss)
                        DrawerFooterBtn("Debug", Icons.Default.Build) { onDismiss(); onDebug() }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                        active: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (active) Modifier.background(Color(0xFFE8F0FE)) else Modifier)
            .clickable { }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (active) AccentBlue else Color(0xFF757575),
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(24.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            color = if (active) AccentBlue else Color(0xFF444444))
    }
}

@Composable
private fun DrawerFooterBtn(text: String,
                             icon: androidx.compose.ui.graphics.vector.ImageVector,
                             onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(icon, null, tint = Color(0xFF616161), modifier = Modifier.size(16.dp))
        Text(text, fontSize = 9.sp, color = Color(0xFF616161))
    }
}

// ─────────────────────────────────────────────────────
//  FLOATING CONTROLS — Pill TTS + FAB
// ─────────────────────────────────────────────────────

@Composable
private fun FloatingControls(
    modifier: Modifier = Modifier,
    onReadResume: () -> Unit
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Pill TTS
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceRaised,
            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Piper TTS", fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, color = TextMuted)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.Headphones, "TTS",
                    tint = AccentTts, modifier = Modifier.size(14.dp))
            }
        }

        Spacer(Modifier.width(10.dp))

        // FAB
        FloatingActionButton(
            onClick = onReadResume,
            containerColor = AccentBlue,
            shape = CircleShape,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Default.MenuBook, "Lire",
                tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────
//  ÉTATS : vide, chargement, erreur
// ─────────────────────────────────────────────────────

@Composable
private fun EmptyView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.MenuBook, null, Modifier.size(64.dp),
                tint = TextMuted.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))
            Text("Bibliothèque vide", color = TextMuted, fontSize = 16.sp)
        }
    }
}

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentBlue)
    }
}

@Composable
private fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    Surface(color = Color(0x33FF6B6B)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("❌ $error", color = Color(0xFFFF6B6B), modifier = Modifier.weight(1f), fontSize = 13.sp)
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

