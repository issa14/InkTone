package com.inktone.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
        // D.6 / E.2 — champ de recherche avec bouton effacer et label accessible
        TextField(
            value = state.query,
            onValueChange = { viewModel.onIntent(SearchIntent.QueryChanged(it)) },
            placeholder = { Text("Rechercher...") },
            label = { Text("Rechercher dans vos livres") },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onIntent(SearchIntent.QueryChanged("")) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Effacer")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        if (state.isSearching) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        // D.6 — État vide quand la recherche ne donne rien
        if (!state.isSearching && state.query.length >= 2 && state.results.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.SearchOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Aucun résultat pour « ${state.query} »",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn {
            items(state.results, key = { "${it.publicationId}:${it.locator.chapterIndex}:${it.locator.charOffset}" }) { result ->
                SearchResultItem(
                    result = result,
                    query = state.query,
                    onClick = { viewModel.onIntent(SearchIntent.NavigateToResult(result)) },
                )
            }
        }
    }
}

/**
 * D.6 — Snippet avec surlignage du terme recherché.
 */
@Composable
private fun SearchResultItem(result: SearchResult, query: String, onClick: () -> Unit) {
    Text(
        text = highlightSnippet(result.snippet, query),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * D.6 — Surligne toutes les occurrences de [query] dans [snippet]
 * (insensible à la casse).
 */
private fun highlightSnippet(snippet: String, query: String): AnnotatedString {
    if (query.length < 2) return AnnotatedString(snippet)
    return buildAnnotatedString {
        val lower = snippet.lowercase()
        val q = query.lowercase()
        var start = 0
        var idx = lower.indexOf(q, start)
        while (idx >= 0) {
            append(snippet.substring(start, idx))
            withStyle(SpanStyle(background = androidx.compose.ui.graphics.Color(0x66FFEB3B))) {
                append(snippet.substring(idx, idx + q.length))
            }
            start = idx + q.length
            idx = lower.indexOf(q, start)
        }
        append(snippet.substring(start))
    }
}
