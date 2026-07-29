package com.inktone.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.domain.service.SearchResult

/**
 * `onNavigateToReader` décomposé en primitifs, pas un `Locator` — `app`
 * (l'appelant final, via `MainActivity`) n'a pas le droit de dépendre de
 * `domain` directement (Blueprint §12.4). `SearchEffect.NavigateToReader`
 * reste typé `Locator` en interne (légitime ici, `feature/search` dépend
 * de `domain`) ; l'aplatissement n'a lieu qu'à cette frontière précise.
 */
@Composable
fun SearchScreen(
    onNavigateToReader: (publicationId: String, resourceHref: String, chapterIndex: Int, charOffset: Int) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchEffect.NavigateToReader -> onNavigateToReader(
                    effect.publicationId, effect.locator.resourceHref, effect.locator.chapterIndex, effect.locator.charOffset,
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextField(
            value = state.query,
            onValueChange = { viewModel.onIntent(SearchIntent.QueryChanged(it)) },
            placeholder = { Text("Rechercher...") },
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.isSearching) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        LazyColumn {
            // Cle = publicationId+locator, pas locator.hashCode() seul
            // (collision possible entre deux publications au meme offset).
            items(state.results, key = { "${it.publicationId}:${it.locator.chapterIndex}:${it.locator.charOffset}" }) { result ->
                SearchResultItem(result, onClick = { viewModel.onIntent(SearchIntent.NavigateToResult(result)) })
            }
        }
    }
}

/**
 * N'affiche que l'extrait — pas le titre de la publication, qui exigerait
 * une jointure ou un appel `PublicationRepository.getById` par résultat
 * (anti-pattern N+1, K8). Limitation connue, pas cachée : à résoudre si
 * besoin par une jointure dédiée côté `SearchService`.
 */
@Composable
private fun SearchResultItem(result: SearchResult, onClick: () -> Unit) {
    Text(
        text = result.snippet,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
    )
}
