package com.inktone.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.InkToneTopBar
import com.inktone.core.designsystem.InkToneEmptyState

/**
 * Lot 8 — écran Récents (UX §Récents). Topbar simplifiée (flèche de
 * retour + titre seuls, ni recherche ni filtre) : volontairement **pas**
 * une extension de [LibraryDetailScreen], qui porte une topbar à deux
 * niveaux et les icônes recherche/filtre — les ajouter ici aurait
 * transformé ce composant en aiguillage conditionné par catégorie
 * plutôt qu'un écran à part entière (voir `LOT_8_RECENTS.md`, Tâche 8.2).
 *
 * Vue **Liste forcée**, réutilise [PublicationListRow] tel quel — même
 * cœur, menu 3-points et barre de progression que Bibliothèque/Détail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    onNavigateToReader: (String) -> Unit,
    onMenuClick: () -> Unit,
    viewModel: RecentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RecentsEffect.NavigateToReader -> onNavigateToReader(effect.publicationId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Récents") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        AppIcon(AppSymbol.Menu, contentDescription = "Ouvrir le menu")
                    }
                },
                colors = InkToneTopBar.colors(),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (!state.isLoading && state.displayedPublications.isEmpty()) {
                RecentsEmptyState()
            } else {
                LazyColumn(contentPadding = PaddingValues(8.dp)) {
                    items(state.displayedPublications, key = { it.id }) { publication ->
                        PublicationListRow(
                            publication = publication,
                            onClick = { viewModel.onIntent(RecentsIntent.OpenPublication(publication.id)) },
                            onToggleFavorite = {
                                viewModel.onIntent(RecentsIntent.ToggleFavorite(publication.id, !publication.isFavorite))
                            },
                            progressPercent = state.progressMap[publication.id] ?: 0,
                            onTogglePin = {
                                viewModel.onIntent(RecentsIntent.TogglePin(publication.id, !publication.isPinned))
                            },
                            onDelete = { viewModel.onIntent(RecentsIntent.DeletePublication(publication.id)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * État vide **sans bouton d'action** — décision explicite de la cible :
 * un utilisateur sans lecture récente a déjà la Bibliothèque pour
 * importer, un CTA ici le dupliquerait.
 */
@Composable
private fun RecentsEmptyState() {
    InkToneEmptyState(
        icon = AppSymbol.Recents,
        title = "Aucune lecture récente",
        message = "Les livres que vous ouvrez apparaîtront ici.",
    )
}
