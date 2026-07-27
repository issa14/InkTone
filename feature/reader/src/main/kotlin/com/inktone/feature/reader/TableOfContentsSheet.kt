package com.inktone.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inktone.domain.model.TableOfContentsEntry

@Composable
fun TableOfContentsSheet(
    entries: List<TableOfContentsEntry>,
    currentChapterIndex: Int,
    onEntryClick: (chapterIndex: Int) -> Unit,
) {
    val listState = rememberLazyListState()

    // Scroll vers le chapitre courant a l'ouverture — pas juste afficher
    // la liste depuis le debut a chaque fois (Blueprint §7.6).
    LaunchedEffect(currentChapterIndex) {
        val targetIndex = entries.indexOfFirst { it.chapterIndex == currentChapterIndex }
        if (targetIndex >= 0) listState.scrollToItem(targetIndex)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        // Bug reel trouve en Tache 4.11 (crash IllegalArgumentException
        // "Key already used") : chapterIndex n'est PAS unique par entree
        // de TOC des qu'un livre reel a plusieurs ancres #fragment dans
        // la meme ressource de spine (Les Miserables Tome I : 153
        // entrees de TOC pour 6 chapitres). La position dans la liste
        // (index), elle, est toujours unique - c'est la seule cle valide
        // ici, meme si plusieurs entrees ciblent le meme chapitre.
        itemsIndexed(entries, key = { index, _ -> index }) { _, entry ->
            val isCurrent = entry.chapterIndex == currentChapterIndex
            Text(
                text = entry.title,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { onEntryClick(entry.chapterIndex) }
                    .padding(16.dp),
            )
        }
    }
}
