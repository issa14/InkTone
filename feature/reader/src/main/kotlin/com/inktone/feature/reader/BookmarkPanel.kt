package com.inktone.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.Motion
import com.inktone.core.designsystem.AppSymbol
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Bookmark
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Tâche 3c.3 — remplace `BookmarkListSheet` (liste de signets plein
 * écran, aucun onglet). Panneau latéral s'ouvrant depuis la gauche,
 * ≈85 % de la largeur (`UX_FLOW_DESIGN.md` § Marque-pages — panneau
 * latéral), reste de l'écran visible en scrim assombri — le lecteur
 * n'est jamais démonté (contrairement à l'ancien `return@Column`).
 *
 * Trois onglets : Notes / Surlignages / Marque-pages — tous dérivés de
 * l'état déjà présent (`bookmarks`, `annotations`), aucune nouvelle
 * source de données. Le toggle « Marquer cette page » reste visible quel
 * que soit l'onglet actif (cible confirmée), indépendant du filtre.
 */
private enum class BookmarkPanelTab(val label: String) {
    NOTES("Notes"),
    HIGHLIGHTS("Surlignages"),
    BOOKMARKS("Marque-pages"),
}

@Composable
fun BookmarkPanel(
    bookmarks: List<Bookmark>,
    annotations: List<Annotation>,
    isCurrentPageBookmarked: Boolean,
    onBookmarkClick: (Bookmark) -> Unit,
    onAnnotationClick: (Annotation) -> Unit,
    onToggleBookmark: () -> Unit,
    onClose: () -> Unit,
    // Lot 22, tâche 11 — édition de note et suppression depuis ce panneau
    // (jusqu'ici en lecture seule). `onEditAnnotationNote` ne concerne que
    // l'onglet Notes ; la suppression s'applique aux trois onglets.
    onDeleteAnnotation: (Annotation) -> Unit,
    onEditAnnotationNote: (Annotation) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onEditBookmarkNote: (Bookmark) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(BookmarkPanelTab.NOTES.ordinal) }
    val notes = remember(annotations) { annotations.filter { !it.content.isNullOrBlank() } }
    val highlights = remember(annotations) { annotations.sortedByDescending { it.createdAt } }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim : le reste de l'écran de lecture reste visible en dessous,
        // assombri — un tap dessus referme le panneau, comme un
        // ModalBottomSheet le ferait pour son propre scrim.
        AnimatedVisibility(
            visible = true,
            // P5 — durée du système de design. Ces animations étaient écrites
            // en dur (200 ms) et ne respectaient AUCUN réglage de mouvement
            // réduit ; `Motion.tween` l'applique par construction.
            enter = fadeIn(Motion.tween()),
            exit = fadeOut(Motion.tween()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
                    .clickable(onClick = onClose),
            )
        }

        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally(Motion.tween()) { -it },
            exit = slideOutHorizontally(Motion.tween()) { -it },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // Dialog edge-to-edge (decorFitsSystemWindows=false) :
                        // la barre de statut et la barre de navigation
                        // mordaient sur le panneau (titre coupé en haut,
                        // bouton sous la barre de navigation en bas).
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClose) {
                            AppIcon(AppSymbol.Back,  contentDescription = "Fermer les marque-pages")
                        }
                        Text("Marque-pages et notes", style = MaterialTheme.typography.titleMedium)
                    }

                    TabRow(selectedTabIndex = selectedTab) {
                        BookmarkPanelTab.entries.forEach { tab ->
                            Tab(
                                selected = selectedTab == tab.ordinal,
                                onClick = { selectedTab = tab.ordinal },
                                text = {
                                    // « Surlignages »/« Marque-pages » ne
                                    // tenaient pas sur une ligne : force une
                                    // seule ligne, police réduite d'un cran.
                                    Text(
                                        tab.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                        }
                    }

                    // Contenu de l'onglet actif — occupe tout l'espace restant.
                    Box(modifier = Modifier.weight(1f)) {
                        when (BookmarkPanelTab.entries[selectedTab]) {
                            BookmarkPanelTab.NOTES -> NotesTab(notes, onAnnotationClick, onDeleteAnnotation, onEditAnnotationNote)
                            BookmarkPanelTab.HIGHLIGHTS -> HighlightsTab(highlights, onAnnotationClick, onDeleteAnnotation)
                            BookmarkPanelTab.BOOKMARKS -> BookmarksTab(bookmarks, onBookmarkClick, onDeleteBookmark, onEditBookmarkNote)
                        }
                    }

                    // Toggle « Marquer cette page » — déplacé tout en bas
                    // (demande UX), visible quel que soit l'onglet actif.
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (isCurrentPageBookmarked) {
                            Button(
                                onClick = onToggleBookmark,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            ) {
                                AppIcon(AppSymbol.Pin,  contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text("Page marquée — retirer")
                            }
                        } else {
                            OutlinedButton(onClick = onToggleBookmark, modifier = Modifier.fillMaxWidth()) {
                                AppIcon(AppSymbol.Pin,  contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text("Marquer cette page")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesTab(
    notes: List<Annotation>,
    onClick: (Annotation) -> Unit,
    onDelete: (Annotation) -> Unit,
    onEdit: (Annotation) -> Unit,
) {
    if (notes.isEmpty()) {
        EmptyTabMessage("Aucune note pour ce livre.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(notes, key = { it.id }) { annotation ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(annotation) }
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        annotation.content.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    annotation.excerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            excerpt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Chapitre ${annotation.startLocator.chapterIndex + 1} · ${formatAnnotationDate(annotation.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onEdit(annotation) }) {
                    AppIcon(AppSymbol.Edit, contentDescription = "Modifier la note")
                }
                IconButton(onClick = { onDelete(annotation) }) {
                    AppIcon(AppSymbol.Delete, contentDescription = "Supprimer la note")
                }
            }
        }
    }
}

@Composable
private fun HighlightsTab(highlights: List<Annotation>, onClick: (Annotation) -> Unit, onDelete: (Annotation) -> Unit) {
    if (highlights.isEmpty()) {
        EmptyTabMessage("Aucun surlignage pour ce livre.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(highlights, key = { it.id }) { annotation ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(annotation) }
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Barre de couleur verticale (cible confirmée) — hauteur
                // étirée sur tout le bloc extrait+métadonnées, pas de fond
                // coloré derrière le texte (contrairement au surlignage
                // rendu dans le texte du chapitre lui-même).
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(annotation.color.toComposeColor()),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        annotation.excerpt?.takeIf { it.isNotBlank() }
                            ?: "Chapitre ${annotation.startLocator.chapterIndex + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Chapitre ${annotation.startLocator.chapterIndex + 1} · ${formatAnnotationDate(annotation.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onDelete(annotation) }) {
                    AppIcon(AppSymbol.Delete, contentDescription = "Supprimer le surlignage")
                }
            }
        }
    }
}

@Composable
private fun BookmarksTab(
    bookmarks: List<Bookmark>,
    onClick: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onEdit: (Bookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        EmptyTabMessage("Aucun marque-page pour ce livre.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(bookmarks, key = { it.id }) { bookmark ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(bookmark) }
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(AppSymbol.Bookmark,  contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        bookmark.title?.takeIf { it.isNotBlank() } ?: "Chapitre ${bookmark.locator.chapterIndex + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Correctif Lot 21 — `title` est désormais toujours
                    // rempli (excerpt EPUB, "Page N" PDF), donc le repli
                    // `?: "Chapitre N"` ci-dessus ne s'affiche plus jamais :
                    // la référence de position disparaissait du panneau.
                    // Ligne dédiée, toujours affichée.
                    Text(
                        "Chapitre ${bookmark.locator.chapterIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Lot 21, tâche 5 — la note du signet est visible dans
                    // le panneau (saisie optionnelle à la création).
                    bookmark.note?.takeIf { it.isNotBlank() }?.let { note ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatAnnotationDate(bookmark.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onEdit(bookmark) }) {
                    AppIcon(AppSymbol.Edit, contentDescription = "Modifier la note")
                }
                IconButton(onClick = { onDelete(bookmark) }) {
                    AppIcon(AppSymbol.Delete, contentDescription = "Supprimer le marque-page")
                }
            }
        }
    }
}

@Composable
private fun EmptyTabMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Format cible confirmé (`UX_FLOW_DESIGN.md` § Surlignages) : `25 déc. 2025`. */
private val annotationDateFormatter = SimpleDateFormat("d MMM yyyy", Locale.FRANCE)

private fun formatAnnotationDate(epochMillis: Long): String = annotationDateFormatter.format(epochMillis)
