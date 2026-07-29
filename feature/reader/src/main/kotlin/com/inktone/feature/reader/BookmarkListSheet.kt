package com.inktone.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inktone.domain.model.Bookmark

/**
 * Liste des signets (Tâche 7.2) — `LazyColumn` sans risque ici,
 * contrairement au chapitre lui-même (Tâche 7.0) : pas de sélection de
 * texte impliquée, l'avertissement `SelectionContainer`/`LazyColumn` ne
 * s'applique pas.
 */
@Composable
fun BookmarkListSheet(
    bookmarks: List<Bookmark>,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkDelete: (Bookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        Text("Aucun signet pour ce livre.", modifier = Modifier.padding(16.dp))
        return
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(bookmarks, key = { it.id }) { bookmark ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onBookmarkClick(bookmark) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = bookmark.title?.takeIf { it.isNotBlank() }
                        ?: "Chapitre ${bookmark.locator.chapterIndex + 1}",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = { onBookmarkDelete(bookmark) }) { Text("Supprimer") }
            }
        }
    }
}
