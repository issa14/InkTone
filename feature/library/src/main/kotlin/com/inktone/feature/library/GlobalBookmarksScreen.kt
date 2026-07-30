package com.inktone.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.AppIcons
import com.inktone.domain.model.Bookmark

/**
 * Tache 9bis.6 — signets de TOUS les livres, port de `AllBookmarksPanel`
 * (legacy) accessible depuis le drawer de la bibliotheque (Tache 9bis.4).
 */
@Composable
fun GlobalBookmarksScreen(
    onNavigateToReader: (publicationId: String, resourceHref: String, chapterIndex: Int, charOffset: Int) -> Unit,
    viewModel: GlobalBookmarksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GlobalBookmarksEffect.NavigateToReader ->
                    onNavigateToReader(effect.publicationId, effect.resourceHref, effect.chapterIndex, effect.charOffset)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextField(
            value = state.searchQuery,
            onValueChange = { viewModel.onIntent(GlobalBookmarksIntent.SetSearchQuery(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Rechercher par titre de livre") },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
            singleLine = true,
        )

        when {
            state.isLoading -> Unit
            state.displayedBookmarks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.searchQuery.isBlank()) "Aucun signet. Ajoutez-en depuis le lecteur." else "Aucun résultat pour « ${state.searchQuery} ».",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.displayedBookmarks, key = { it.bookmark.id }) { entry ->
                    GlobalBookmarkRow(
                        entry = entry,
                        onClick = { viewModel.onIntent(GlobalBookmarksIntent.OpenBookmark(entry.bookmark)) },
                        onDelete = { viewModel.onIntent(GlobalBookmarksIntent.DeleteBookmark(entry.bookmark.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalBookmarkRow(entry: BookmarkWithPublicationTitle, onClick: () -> Unit, onDelete: () -> Unit) {
    val bookmark: Bookmark = entry.bookmark
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(entry.publicationTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                bookmark.title?.takeIf { it.isNotBlank() } ?: "Chapitre ${bookmark.locator.chapterIndex + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Supprimer le signet")
        }
    }
}
