package com.inktone.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import com.inktone.domain.model.TableOfContentsEntry

/**
 * Tache 9bis.3.2 — TOC hierarchique : `TableOfContentsEntry.children`
 * (domaine) est aplati par [flattenWithDepth] pour l'indentation.
 *
 * Verifie contre un vrai EPUB a hierarchie NCX imbriquee sur 2 niveaux
 * (Tache 4.11, `TableOfContentsChildrenTest`, Gutenberg #17489 — Les
 * Miserables Tome I) : `children` EST peuple par le parseur reel
 * (`DocumentModelExtractor.toTocEntry`, recursif). L'ancien TODO
 * signalant ce point comme jamais verifie est donc perime — l'aplatissement
 * ci-dessous s'exerce sur de vraies donnees hierarchiques, pas seulement
 * sur la forme du modele.
 */
private data class FlatTocEntry(val entry: TableOfContentsEntry, val depth: Int)

private fun flattenWithDepth(entries: List<TableOfContentsEntry>, depth: Int = 0): List<FlatTocEntry> =
    entries.flatMap { entry -> listOf(FlatTocEntry(entry, depth)) + flattenWithDepth(entry.children, depth + 1) }

/**
 * Tâche 3c.2 — bottom sheet (`skipPartiallyExpanded = true`, sans quoi la
 * feuille s'ouvre à mi-écran et un sommaire hiérarchique long y est
 * tronqué à l'ouverture) : ne démonte plus le lecteur (avant ce lot,
 * `ReaderScreen` faisait `return@Column`, HUD compris, avant même
 * d'atteindre ce composable). Titre cible confirmé
 * (`UX_FLOW_DESIGN.md` § Sommaire) : « Table des matières », y compris
 * pour un livre à hiérarchie (Tome/Livre/Chapitre) — pas de variante
 * selon la structure du livre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableOfContentsSheet(
    entries: List<TableOfContentsEntry>,
    currentChapterIndex: Int,
    onEntryClick: (chapterIndex: Int) -> Unit,
    onClose: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        TableOfContentsSheetContent(entries = entries, currentChapterIndex = currentChapterIndex, onEntryClick = onEntryClick, onClose = onClose)
    }
}

@Composable
private fun TableOfContentsSheetContent(
    entries: List<TableOfContentsEntry>,
    currentChapterIndex: Int,
    onEntryClick: (chapterIndex: Int) -> Unit,
    onClose: () -> Unit,
) {
    val listState = rememberLazyListState()
    val flatEntries = remember(entries) { flattenWithDepth(entries) }

    // Scroll vers le chapitre courant a l'ouverture — pas juste afficher
    // la liste depuis le debut a chaque fois (Blueprint §7.6).
    LaunchedEffect(currentChapterIndex, flatEntries) {
        val targetIndex = flatEntries.indexOfFirst { it.entry.chapterIndex == currentChapterIndex }
        if (targetIndex >= 0) listState.scrollToItem(targetIndex)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            AppIcon(AppSymbol.Back,  contentDescription = "Fermer le sommaire")
        }
        Text("Table des matières", style = MaterialTheme.typography.titleMedium)
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
