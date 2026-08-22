package com.inktone.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.StatusBarColorEffect
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.LibraryItem
import com.inktone.domain.model.LibraryItemFilter
import com.inktone.domain.model.LibraryItemSortOrder
import com.inktone.domain.model.LibraryItemType
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Lot 4, tâche 4.6 — reconstruction complète de l'écran (remplace
 * `GlobalBookmarksScreen`, qui n'affichait que les signets alors que le
 * titre promettait aussi les notes — voir `LOT_4_MARQUE_PAGES_NOTES.md`).
 * Scaffold propre (pas `BackScaffold`, trop rigide pour la recherche
 * repliable et le menu de tri de la cible).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryItemsScreen(
    onMenuClick: () -> Unit,
    onNavigateToReader: (publicationId: String, resourceHref: String, chapterIndex: Int, charOffset: Int) -> Unit,
    viewModel: LibraryItemsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LibraryItemsEffect.NavigateToReader ->
                    onNavigateToReader(effect.publicationId, effect.resourceHref, effect.chapterIndex, effect.charOffset)
            }
        }
    }

    // Etend la couleur de la barre du haut a la barre de statut Android :
    // sans cela, le bandeau systeme garde le creme fige de `themes.xml`
    // au-dessus d'une TopAppBar `primary` (voir StatusBarColorEffect).
    StatusBarColorEffect(MaterialTheme.colorScheme.primary)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.isSearchExpanded) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.onIntent(LibraryItemsIntent.SetSearchQuery(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Rechercher un extrait, une note, un titre") },
                            singleLine = true,
                        )
                    } else {
                        Text("Marque-pages et notes")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        AppIcon(AppSymbol.Menu, contentDescription = "Ouvrir le menu")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onIntent(LibraryItemsIntent.ToggleSearchExpanded) }) {
                        AppIcon(AppSymbol.Search,  contentDescription = if (state.isSearchExpanded) "Fermer la recherche" else "Rechercher")
                    }
                    SortMenuButton(
                        sortOrder = state.sortOrder,
                        onSortOrderSelected = { viewModel.onIntent(LibraryItemsIntent.SetSortOrder(it)) },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            FilterChipsRow(
                selected = state.filter,
                onSelected = { viewModel.onIntent(LibraryItemsIntent.SetFilter(it)) },
            )

            when {
                // Audit v1.0.0 (AUDIT_CONSOLIDATION_V1.md, M5) : l'écran
                // s'ouvrait BLANC pendant le chargement (isLoading -> Unit) ;
                // la première émission Room peut prendre un instant.
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> LibraryItemsEmptyState(
                    isFiltered = state.searchQuery.isNotBlank() || state.filter != LibraryItemFilter.ALL,
                )
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(state.items, key = { it.id }) { item ->
                        LibraryItemRow(
                            item = item,
                            onClick = { viewModel.onIntent(LibraryItemsIntent.OpenItem(item)) },
                            onTogglePin = { viewModel.onIntent(LibraryItemsIntent.TogglePin(item)) },
                            onRequestDelete = { viewModel.onIntent(LibraryItemsIntent.RequestDelete(item)) },
                        )
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { item ->
        DeleteLibraryItemDialog(
            item = item,
            onConfirm = { viewModel.onIntent(LibraryItemsIntent.ConfirmDelete) },
            onDismiss = { viewModel.onIntent(LibraryItemsIntent.CancelDelete) },
        )
    }
}

/**
 * État vide, structuré (retour Issa, vérification device) : plus de
 * texte flottant seul au centre — icône + titre en gras + sous-titre
 * explicatif, même patron que l'état vide de la Bibliothèque.
 */
@Composable
private fun LibraryItemsEmptyState(isFiltered: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                AppIcons.Bookmark,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (isFiltered) "Aucun résultat" else "Aucun marque-page ni note",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isFiltered) "Essayez un autre filtre ou une autre recherche." else "Vos marque-pages et notes apparaîtront ici.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun FilterChipsRow(selected: LibraryItemFilter, onSelected: (LibraryItemFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val entries = listOf(
            LibraryItemFilter.ALL to "Tous",
            LibraryItemFilter.BOOKMARK to "Signets",
            LibraryItemFilter.HIGHLIGHT to "Surlignages",
            LibraryItemFilter.NOTE to "Notes",
        )
        entries.forEach { (filter, label) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortMenuButton(sortOrder: LibraryItemSortOrder, onSortOrderSelected: (LibraryItemSortOrder) -> Unit) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isMenuExpanded = true }) {
            AppIcon(AppSymbol.Sort, contentDescription = "Trier")
        }
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Chronologique") },
                onClick = { onSortOrderSelected(LibraryItemSortOrder.CHRONOLOGICAL); isMenuExpanded = false },
                trailingIcon = { if (sortOrder == LibraryItemSortOrder.CHRONOLOGICAL) AppIcon(AppSymbol.Success,  contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("Alphabétique") },
                onClick = { onSortOrderSelected(LibraryItemSortOrder.ALPHABETICAL); isMenuExpanded = false },
                trailingIcon = { if (sortOrder == LibraryItemSortOrder.ALPHABETICAL) AppIcon(AppSymbol.Success,  contentDescription = null) },
            )
        }
    }
}

/**
 * Balayage puis confirmation (tâche 4.6) : `confirmValueChange` demande
 * toujours la confirmation et renvoie `false`, donc la carte revient
 * visuellement à sa place — la suppression n'a jamais lieu au geste
 * seul, uniquement après acceptation du dialogue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryItemRow(
    item: LibraryItem,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onRequestDelete()
            false
        },
    )
    // Même forme que la carte : sans ce .clip(), les coins carrés de
    // l'arrière-plan de balayage dépassaient derrière les coins arrondis
    // de la carte au repos (artefact rouge visible même sans swiper).
    val cardShape = RoundedCornerShape(12.dp)

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.padding(vertical = 4.dp).clip(cardShape),
        backgroundContent = {
            // Transparent tant que la carte est au repos : sinon le
            // rectangle rouge reste visible en permanence sous la carte,
            // pas seulement pendant le balayage.
            val backgroundColor = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                AppIcon(AppSymbol.Delete,  contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    iconFor(item),
                    contentDescription = null,
                    tint = colorFor(item) ?: MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    // Ligne 1 — extrait / note : le contenu, prioritaire.
                    item.excerpt?.let { excerpt ->
                        Text(excerpt, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    item.note?.let { note ->
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Ligne 2 — titre de l'ouvrage, seul sur sa ligne : peut
                    // être tronqué sans jamais pousser le chapitre hors champ.
                    Text(
                        item.publicationTitle ?: "Ouvrage supprimé",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // Ligne 3 — chapitre + date, toujours entièrement visibles
                    // (jamais concaténés avec le titre, qui peut être long).
                    Text(
                        "Chapitre ${item.startLocator.chapterIndex + 1} · ${formatItemDate(item.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onTogglePin) {
                    AppIcon(AppSymbol.Pin, selected = item.isPinned,
                        contentDescription = if (item.isPinned) "Désépingler" else "Épingler",
                        tint = if (item.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun iconFor(item: LibraryItem) = when (item.type) {
    LibraryItemType.BOOKMARK -> AppIcons.Bookmark
    LibraryItemType.HIGHLIGHT -> AppIcons.Highlight
    LibraryItemType.NOTE -> AppIcons.Note
}

private fun colorFor(item: LibraryItem): Color? = item.color?.let {
    when (it) {
        AnnotationColor.YELLOW -> Color(0xFFFBC02D)
        AnnotationColor.GREEN -> Color(0xFF66BB6A)
        AnnotationColor.BLUE -> Color(0xFF4FC3F7)
        AnnotationColor.PINK -> Color(0xFFF06292)
        AnnotationColor.ORANGE -> Color(0xFFFFA726)
    }
}

@Composable
private fun DeleteLibraryItemDialog(item: LibraryItem, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val label = when (item.type) {
        LibraryItemType.BOOKMARK -> "ce marque-page"
        LibraryItemType.HIGHLIGHT -> "ce surlignage"
        LibraryItemType.NOTE -> "cette note"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer $label ?") },
        text = { Text("Cette action est irréversible.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Supprimer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Même format que `BookmarkPanel.formatAnnotationDate` (UX_FLOW_DESIGN.md § Surlignages) : `25 déc. 2025`. */
private val itemDateFormatter = SimpleDateFormat("d MMM yyyy", Locale.FRANCE)

private fun formatItemDate(epochMillis: Long): String = itemDateFormatter.format(epochMillis)
