package com.inktone.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppIcons
import com.inktone.core.designsystem.AppSymbol

/**
 * Écran de détail Séries/Tags (UX §Menu déroulant du titre, écran de
 * détail partagé) — un seul écran pour les deux catégories, lot 2a.4.
 * Icônes conservées : recherche et filtre uniquement, ni 3-points ni
 * hamburger (on n'est plus au niveau racine de la Bibliothèque).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryDetailScreen(
    category: LibraryDetailCategory,
    value: String,
    onNavigateToReader: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: LibraryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(category, value) {
        viewModel.onIntent(LibraryDetailIntent.Load(category, value))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LibraryDetailEffect.NavigateToReader -> onNavigateToReader(effect.publicationId)
            }
        }
    }

    var isSearchActive by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.onIntent(LibraryDetailIntent.SetSearchQuery(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Rechercher titre ou auteur") },
                            singleLine = true,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.onIntent(LibraryDetailIntent.SetSearchQuery(""))
                        }) {
                            AppIcon(AppSymbol.Back, contentDescription = "Fermer")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (category == LibraryDetailCategory.SERIES) "SÉRIES" else "TAGS",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            )
                            Text(
                                value,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            AppIcon(AppSymbol.Back, contentDescription = "Retour")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            AppIcon(AppSymbol.Search, contentDescription = "Rechercher")
                        }
                        IconButton(onClick = { showFilterDialog = true }) {
                            AppIcon(AppSymbol.Filter,  contentDescription = "Filtrer")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(state.displayedPublications, key = { it.id }) { publication ->
                    PublicationListRow(
                        publication = publication,
                        onClick = { viewModel.onIntent(LibraryDetailIntent.OpenPublication(publication.id)) },
                        onToggleFavorite = {
                            viewModel.onIntent(LibraryDetailIntent.ToggleFavorite(publication.id, !publication.isFavorite))
                        },
                        progressPercent = state.progressMap[publication.id] ?: 0,
                        onTogglePin = {
                            viewModel.onIntent(LibraryDetailIntent.TogglePin(publication.id, !publication.isPinned))
                        },
                        onDelete = { viewModel.onIntent(LibraryDetailIntent.DeletePublication(publication.id)) },
                    )
                }
            }
        }
    }

    if (showFilterDialog) {
        LibraryFilterDialog(
            sortOrder = state.sortOrder,
            onSortOrderChange = { viewModel.onIntent(LibraryDetailIntent.SetSortOrder(it)) },
            selectedFormats = state.selectedFormats,
            onToggleFormat = { viewModel.onIntent(LibraryDetailIntent.ToggleFileFormat(it)) },
            onClearFormats = { viewModel.onIntent(LibraryDetailIntent.ClearFileFormats) },
            onDismiss = { showFilterDialog = false },
            showStatusFilter = false,
            showLayoutSection = false,
        )
    }
}
