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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inktone.domain.model.TableOfContentsEntry

/**
 * Tache 9bis.3.2 — TOC hierarchique : `TableOfContentsEntry.children`
 * (domaine, jamais parcouru avant cette tache) est maintenant aplati par
 * [flattenWithDepth] pour l'indentation, au lieu d'ignorer silencieusement
 * les entrees imbriquees comme avant.
 *
 * TODO(verification avec fixture EPUB a hierarchie reelle type Tome/Livre
 * /Chapitre absente du jeu de tests actuel, Tache 9bis.3 restante) :
 * cet aplatissement est correct pour la forme des donnees telle que
 * `TableOfContentsEntry` la modelise, mais aucun parser EPUB reel du
 * projet n'a encore ete observe produire `children` non vide - a
 * verifier avant de clore la Phase 9bis (checklist #6).
 */
private data class FlatTocEntry(val entry: TableOfContentsEntry, val depth: Int)

private fun flattenWithDepth(entries: List<TableOfContentsEntry>, depth: Int = 0): List<FlatTocEntry> =
    entries.flatMap { entry -> listOf(FlatTocEntry(entry, depth)) + flattenWithDepth(entry.children, depth + 1) }

@Composable
fun TableOfContentsSheet(
    entries: List<TableOfContentsEntry>,
    currentChapterIndex: Int,
    onEntryClick: (chapterIndex: Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val flatEntries = remember(entries) { flattenWithDepth(entries) }

    // Scroll vers le chapitre courant a l'ouverture — pas juste afficher
    // la liste depuis le debut a chaque fois (Blueprint §7.6).
    LaunchedEffect(currentChapterIndex, flatEntries) {
        val targetIndex = flatEntries.indexOfFirst { it.entry.chapterIndex == currentChapterIndex }
        if (targetIndex >= 0) listState.scrollToItem(targetIndex)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        // Bug reel trouve en Tache 4.11 (crash IllegalArgumentException
        // "Key already used") : chapterIndex n'est PAS unique par entree
        // de TOC des qu'un livre reel a plusieurs ancres #fragment dans
        // la meme ressource de spine (Les Miserables Tome I : 153
        // entrees de TOC pour 6 chapitres). La position dans la liste
        // aplatie, elle, est toujours unique - c'est la seule cle valide
        // ici, meme si plusieurs entrees ciblent le meme chapitre.
        itemsIndexed(flatEntries, key = { index, _ -> index }) { _, flat ->
            val isCurrent = flat.entry.chapterIndex == currentChapterIndex
            Text(
                text = flat.entry.title,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { onEntryClick(flat.entry.chapterIndex) }
                    .padding(start = (16 + flat.depth * 16).dp, top = 16.dp, bottom = 16.dp, end = 16.dp),
            )
        }
    }
}
