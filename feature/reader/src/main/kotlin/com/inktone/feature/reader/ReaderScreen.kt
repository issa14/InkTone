package com.inktone.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * `effectiveSettings` (theme, taille de police) arrive déjà résolu dans
 * `state` — calculé par ReaderViewModel via
 * `EffectiveReadingSettings.resolve()` (Tâche 1.3/4.7). Ce Composable ne
 * connaît ni `ReadingOverrides` ni `UserPreferences` séparément, il
 * n'affiche qu'un résultat déjà tranché — ne jamais recalculer cette
 * cascade de précédence ici.
 */
@Composable
fun ReaderScreen(viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeColors.background(state.effectiveSettings.theme))
            .padding(16.dp),
    ) {
        if (state.isTocVisible) {
            TableOfContentsSheet(
                entries = state.tableOfContents,
                currentChapterIndex = state.currentChapterIndex,
                onEntryClick = { chapterIndex -> viewModel.onIntent(ReaderIntent.JumpToChapter(chapterIndex)) },
            )
            return@Column
        }

        val sentenceText = state.currentChapter
            ?.paragraphs?.flatMap { it.sentences }
            ?.getOrNull(state.currentSentenceIndex)?.text.orEmpty()

        Text(
            text = buildHighlightedText(sentenceText, state.highlightedWordRange),
            fontSize = state.effectiveSettings.fontSize.sp,
            color = ThemeColors.text(state.effectiveSettings.theme),
        )

        Row {
            Button(onClick = { viewModel.onIntent(ReaderIntent.PreviousChapter) }, enabled = state.hasPreviousChapter) {
                Text("Precedent")
            }
            Button(onClick = { viewModel.onIntent(ReaderIntent.PlayCurrentSentence) }) {
                Text(if (state.isPlaying) "En lecture..." else "Lire")
            }
            Button(onClick = { viewModel.onIntent(ReaderIntent.NextChapter) }, enabled = state.hasNextChapter) {
                Text("Suivant")
            }
            Button(onClick = { viewModel.onIntent(ReaderIntent.ToggleToc) }) {
                Text("Sommaire")
            }
        }
    }
}

private fun buildHighlightedText(text: String, range: IntRange?): AnnotatedString = buildAnnotatedString {
    if (range == null || text.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    append(text.substring(0, range.first))
    withStyle(SpanStyle(background = Color.Yellow)) {
        append(text.substring(range.first, range.last + 1))
    }
    append(text.substring(range.last + 1))
}
