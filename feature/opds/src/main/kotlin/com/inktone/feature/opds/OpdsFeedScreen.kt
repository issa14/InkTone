package com.inktone.feature.opds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import coil.memory.MemoryCache
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
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
            state.error != null && state.items.isEmpty() -> FeedError(state.error.message ?: "", onRetry)
            state.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Ce dossier est vide.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
private fun FeedError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(AppSymbol.Error, contentDescription = null)
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        // Un flux qui echoue est le plus souvent une coupure reseau ou un
        // serveur momentanement indisponible : sans cette action, le seul
        // recours etait de quitter le catalogue et d'y revenir.
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Réessayer") }
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

    // Pagination — déclenche le chargement de la page suivante quand le
    // dernier élément visible approche de la fin de la grille.
    LaunchedEffect(gridState, state.items.size, state.nextPageUrl, state.isLoadingMore) {
        val nextPageUrl = state.nextPageUrl ?: return@LaunchedEffect
        if (state.isLoadingMore) return@LaunchedEffect
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
        itemsIndexed(state.items, key = { index, item -> "$index:${itemKey(item)}" }) { _, item ->
            when (item) {
                is OpdsItem.Navigation -> DirectoryCard(item, onOpenNavigation)
                is OpdsItem.Book -> BookCard(item, state.catalogId, onDownloadBook)
            }
        }
        if (state.isLoadingMore) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
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
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIcon(AppSymbol.Article, contentDescription = null)
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun BookCard(item: OpdsItem.Book, catalogId: String?, onDownloadBook: (OpdsItem.Book) -> Unit) {
    val coverUrl = item.coverUrl
    Card {
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
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.authors.isNotEmpty()) {
                    Text(
                        item.authors.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.material3.IconButton(onClick = { onDownloadBook(item) }) {
                    AppIcon(AppSymbol.Download, contentDescription = "Télécharger ${item.title}")
                }
            }
        }
    }
}
