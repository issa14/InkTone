package com.inktone.feature.search

import com.inktone.core.designsystem.InkToneSpacing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import com.inktone.core.designsystem.AppIcon
import com.inktone.core.designsystem.AppSymbol
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.core.designsystem.InkToneTextField
import com.inktone.domain.service.SearchResult
import java.text.Normalizer
import java.util.Locale

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
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchEffect.NavigateToReader -> onNavigateToReader(
                    effect.publicationId, effect.locator.resourceHref, effect.locator.chapterIndex, effect.locator.charOffset,
                )
            }
        }
    }

    // Clavier ouvert à l'arrivée : l'écran n'a pas d'autre usage que la
    // saisie. Pas au retour d'un résultat (requête déjà tapée) : le clavier
    // recouvrirait la liste qu'on vient de quitter.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (state.query.isEmpty()) focusRequester.requestFocus()
    }

    Column(Modifier.fillMaxSize().padding(InkToneSpacing.lg)) {
        // D.6 / E.2 — champ de recherche avec bouton effacer et label accessible
        TextField(
            shape = InkToneTextField.searchShape,
            colors = InkToneTextField.searchColors(),
            value = state.query,
            onValueChange = { viewModel.onIntent(SearchIntent.QueryChanged(it)) },
            // Le titre de la barre dit déjà où l'on cherche : pas d'étiquette qui le répète.
            placeholder = { Text("Mot ou expression") },
            supportingText = if (state.query.length == 1) {
                { Text("Au moins 2 caractères") }
            } else {
                null
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onIntent(SearchIntent.QueryChanged("")) }) {
                        AppIcon(AppSymbol.Close, contentDescription = "Effacer")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            singleLine = true,
        )

        // Barre fine sous le champ : elle ne décale pas la liste, contrairement à un disque.
        if (state.isSearching) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        // D.6 — État vide quand la recherche ne donne rien
        if (!state.isSearching && state.query.length >= 2 && state.results.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(AppSymbol.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(InkToneSpacing.md))
                Text(
                    "Aucun résultat pour « ${state.query} »",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!state.isSearching && state.results.isNotEmpty()) {
            Text(
                resultCountLabel(state.results.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = InkToneSpacing.lg, bottom = InkToneSpacing.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        LazyColumn {
            items(state.results, key = { "${it.publicationId}:${it.locator.chapterIndex}:${it.locator.charOffset}" }) { result ->
                SearchResultItem(
                    result = result,
                    bookTitle = state.bookTitles[result.publicationId],
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
private fun SearchResultItem(result: SearchResult, bookTitle: String?, query: String, onClick: () -> Unit) {
    // La position distingue des résultats dont les extraits se ressemblent.
    ListItem(
        overlineContent = { Text("Chapitre ${result.locator.chapterIndex + 1}") },
        headlineContent = {
            Text(
                // Couleur du schéma : un jaune fixe tombait à 4,3:1 sous le texte en thème sombre.
                text = highlightSnippet(
                    result.snippet,
                    query,
                    SpanStyle(
                        background = MaterialTheme.colorScheme.tertiaryContainer,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = if (!bookTitle.isNullOrBlank()) {
            { Text(bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

/** En-tête de la liste, annoncé par TalkBack quand il change. */
internal fun resultCountLabel(count: Int): String = if (count == 1) "1 résultat" else "$count résultats"

/**
 * Surligne dans [snippet] chaque occurrence de [query], sans tenir compte
 * de la casse ni des accents — comme le moteur de recherche : chercher
 * « eleve » trouve « élève », il doit donc aussi le surligner.
 */
private fun highlightSnippet(snippet: String, query: String, highlight: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        append(snippet)
        matchRanges(snippet, query).forEach { addStyle(highlight, it.first, it.last + 1) }
    }

/** Plages de [text] (indices d'origine) où [query] apparaît, accents et casse ignorés. */
internal fun matchRanges(text: String, query: String): List<IntRange> {
    val needle = foldForMatch(query.trim())
    if (needle.length < 2) return emptyList()
    // Chaîne repliée + indice d'origine de chacun de ses caractères : une
    // lettre accentuée peut se décomposer, la correspondance doit retomber
    // sur le texte affiché.
    val folded = StringBuilder()
    val origin = ArrayList<Int>(text.length)
    text.forEachIndexed { index, char ->
        foldForMatch(char.toString()).forEach {
            folded.append(it)
            origin.add(index)
        }
    }
    val ranges = mutableListOf<IntRange>()
    var from = folded.indexOf(needle)
    while (from >= 0) {
        val end = from + needle.length - 1
        // Un accent combinant qui suit la dernière lettre en fait partie : ne pas l'en séparer.
        var last = origin[end]
        while (last + 1 < text.length && Character.getType(text[last + 1]) == Character.NON_SPACING_MARK.toInt()) last++
        ranges += origin[from]..last
        from = folded.indexOf(needle, end + 1)
    }
    return ranges
}

private val combiningMarks = Regex("\\p{Mn}+")

private fun foldForMatch(value: String): String =
    combiningMarks.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "").lowercase(Locale.FRENCH)
