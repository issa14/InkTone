package com.inktone.feature.opds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import coil.memory.MemoryCache
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import com.inktone.core.designsystem.InkToneEmptyState
import com.inktone.core.designsystem.InkToneErrorState
import com.inktone.domain.model.OpdsItem
import com.inktone.domain.service.OpdsHttpClient

/**
 * Contenu du flux OPDS (Lot 13, tâche 13.2.4/13.2.5/13.2.6) — grille
 * adaptative de dossiers ([OpdsItem.Navigation]) et de livres
 * ([OpdsItem.Book]), couvertures via Coil avec auth par catalogue
 * ([OpdsCoverFetcher]), pagination déclenchée par le dernier élément
 * visible.
 */
@Composable
fun OpdsFeedScreen(
    state: OpdsUiState.Feed,
    onOpenNavigation: (OpdsItem.Navigation) -> Unit,
    onLoadNextPage: (String) -> Unit,
    onDownloadBook: (OpdsItem.Book) -> Unit,
    onRetry: () -> Unit,
    httpClient: OpdsHttpClient,
) {
    val context = LocalContext.current
    val imageLoader = remember(httpClient) {
        ImageLoader.Builder(context)
            .components { add(OpdsCoverFetcher.Factory(httpClient)) }
            .crossfade(true)
            // AUDIT_REACTIVITE_UX §5.1 — même défaut que l'ImageLoader du
            // Lecteur (non listé dans l'audit, trouvé en marge en
            // appliquant sa correction) : un 3e cache mémoire concurrent
            // au budget par défaut (~25% du tas). Borné explicitement pour
            // la même raison.
            .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.10).build() }
            .build()
    }

    CompositionLocalProvider(LocalImageLoader provides imageLoader) {
        when {
            state.isLoading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            // Un flux qui échoue est le plus souvent une coupure réseau : sans
            // « Réessayer », il fallait quitter le catalogue et y revenir.
            state.error != null && state.items.isEmpty() -> InkToneErrorState(state.error.message ?: "", onRetry)
            state.items.isEmpty() -> InkToneEmptyState(icon = AppSymbol.Library, title = "Ce dossier est vide")
            else -> FeedGrid(
                state = state,
                onOpenNavigation = onOpenNavigation,
                onLoadNextPage = onLoadNextPage,
                onDownloadBook = onDownloadBook,
            )
        }
    }
}

@Composable
private fun FeedGrid(
    state: OpdsUiState.Feed,
    onOpenNavigation: (OpdsItem.Navigation) -> Unit,
    onDownloadBook: (OpdsItem.Book) -> Unit,
    onLoadNextPage: (String) -> Unit,
) {
    val gridState = rememberLazyGridState()
    // Fiche du livre ouverte par un appui sur sa carte (audit UX C10).
    var detail by remember { mutableStateOf<OpdsItem.Book?>(null) }

    // Pagination — déclenche le chargement de la page suivante quand le
    // dernier élément visible approche de la fin de la grille. Après un
    // échec, plus rien d'automatique : sinon chaque échec relance aussitôt
    // la requête, en boucle tant que le réseau manque.
    LaunchedEffect(gridState, state.items.size, state.nextPageUrl, state.isLoadingMore, state.error) {
        val nextPageUrl = state.nextPageUrl ?: return@LaunchedEffect
        if (state.isLoadingMore || state.error != null) return@LaunchedEffect
        snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= gridState.layoutInfo.totalItemsCount - 3
        }.collect { nearEnd ->
            if (nearEnd) onLoadNextPage(nextPageUrl)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(
            state.items,
            key = { index, item -> "$index:${itemKey(item)}" },
            // Un dossier mêlé aux couvertures flottait dans une rangée haute
            // comme un livre : il prend toute la largeur, en ligne de liste.
            span = { _, item -> GridItemSpan(if (item is OpdsItem.Navigation) maxLineSpan else 1) },
        ) { _, item ->
            when (item) {
                is OpdsItem.Navigation -> DirectoryCard(item, onOpenNavigation)
                is OpdsItem.Book -> BookCard(item, state.catalogId, state.downloads[item.acquisitionHref], onDownloadBook, onOpenDetail = { detail = item })
            }
        }
        if (state.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        val nextPageUrl = state.nextPageUrl
        if (state.error != null && nextPageUrl != null && !state.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "La suite de la liste n'a pas pu être chargée.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { onLoadNextPage(nextPageUrl) }) { Text("Réessayer") }
                }
            }
        }
    }

    detail?.let { book ->
        BookDetailSheet(
            item = book,
            catalogId = state.catalogId,
            download = state.downloads[book.acquisitionHref],
            onDownloadBook = onDownloadBook,
            onDismiss = { detail = null },
        )
    }
}

private fun itemKey(item: OpdsItem): String = when (item) {
    is OpdsItem.Navigation -> "nav:${item.href}"
    is OpdsItem.Book -> "book:${item.acquisitionHref}"
}

@Composable
private fun DirectoryCard(
    item: OpdsItem.Navigation,
    onOpenNavigation: (OpdsItem.Navigation) -> Unit,
) {
    Card(onClick = { onOpenNavigation(item) }) {
        ListItem(
            headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            leadingContent = { AppIcon(AppSymbol.Article, contentDescription = null) },
            trailingContent = { AppIcon(AppSymbol.ChevronRight, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun BookCard(
    item: OpdsItem.Book,
    catalogId: String?,
    download: OpdsBookDownload?,
    onDownloadBook: (OpdsItem.Book) -> Unit,
    onOpenDetail: () -> Unit,
) {
    val coverUrl = item.coverUrl
    // Card(onClick) et non clickable posé dessus : l'ondulation suit les coins
    // arrondis et la carte réagit à l'appui.
    Card(
        onClick = onOpenDetail,
        modifier = Modifier.semantics { onClick(label = "Voir la fiche de ${item.title}", action = null) },
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (coverUrl != null) {
                AsyncImage(
                    model = OpdsCoverKey(coverUrl, catalogId),
                    contentDescription = "Couverture de ${item.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppSymbol.Article, contentDescription = null)
                }
            }
            Column(Modifier.padding(12.dp)) {
                // Hauteurs fixes en lignes : les cartes d'une rangée restent alignées
                // quels que soient la longueur du titre et l'état du téléchargement.
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.authors.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
                    DownloadControl(item, download, onDownloadBook)
                }
            }
        }
    }
}

@Composable
private fun DownloadControl(
    item: OpdsItem.Book,
    download: OpdsBookDownload?,
    onDownloadBook: (OpdsItem.Book) -> Unit,
) {
    when (download) {
                    OpdsBookDownload.IN_PROGRESS -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Téléchargement…", style = MaterialTheme.typography.bodySmall)
                    }
                    OpdsBookDownload.DONE -> Text(
                        "Téléchargé",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    null -> androidx.compose.material3.IconButton(onClick = { onDownloadBook(item) }) {
                        AppIcon(AppSymbol.Download, contentDescription = "Télécharger ${item.title}")
                    }
                }
}

/** Fiche d'un livre du catalogue : couverture, auteurs, format, description et téléchargement (audit UX C10). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookDetailSheet(
    item: OpdsItem.Book,
    catalogId: String?,
    download: OpdsBookDownload?,
    onDownloadBook: (OpdsItem.Book) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                item.coverUrl?.let { url ->
                    AsyncImage(
                        model = OpdsCoverKey(url, catalogId),
                        contentDescription = "Couverture de ${item.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 96.dp, height = 144.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    if (item.authors.isNotEmpty()) {
                        Text(
                            item.authors.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    formatLabel(item.mimeType)?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            DownloadControl(item, download, onDownloadBook)
            Text(
                item.summary ?: "Aucune description fournie par le catalogue.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatLabel(mimeType: String): String? = when {
    mimeType.contains("epub", ignoreCase = true) -> "EPUB"
    mimeType.contains("pdf", ignoreCase = true) -> "PDF"
    else -> null
}
