package com.inktone.feature.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Sentence

/**
 * `effectiveSettings` (theme, taille de police) arrive déjà résolu dans
 * `state` — calculé par ReaderViewModel via
 * `EffectiveReadingSettings.resolve()` (Tâche 1.3/4.7). Ce Composable ne
 * connaît ni `ReadingOverrides` ni `UserPreferences` séparément, il
 * n'affiche qu'un résultat déjà tranché — ne jamais recalculer cette
 * cascade de précédence ici.
 *
 * **Rendu continu du chapitre (Tâche 7.0, révision)** — jusqu'ici (Phases
 * 3/4), cet écran n'affichait qu'une seule `Sentence` à la fois (squelette
 * de marche à blanc pour le pipeline TTS), pas la « lecture visuelle »
 * que le Blueprint exige en complément du mode audio. Le chapitre entier
 * est maintenant rendu — toutes les `Sentence` du chapitre, dans un
 * `FlowRow` défilable (`verticalScroll`, jamais `LazyColumn` : la mise en
 * page en flux imite le texte continu tout en gardant chaque phrase comme
 * composable adressable individuellement).
 *
 * **Sélection de texte : par phrase, pas par caractère (Tâche 7.0/7.1)** —
 * `Selection` et le `SelectionContainer(selection, onSelectionChange,
 * content)` contrôlé sont `internal` dans
 * `androidx.compose.foundation:foundation:1.7.2` (BOM 2024.09.02, vérifié
 * par le compilateur Kotlin en écrivant cette tâche : `Cannot access
 * 'data class Selection : Any': it is internal in file` — pas une
 * supposition d'après une doc générique, qui avait justement mal anticipé
 * cette API). Aucune API publique ne donne accès aux offsets d'une
 * sélection de texte native à ce niveau. À la place : appui long sur une
 * phrase pour démarrer une sélection, appui simple sur une autre phrase
 * pour l'étendre — l'index de `Sentence` touché est connu par
 * construction, aucune conversion pixel → offset nécessaire (voir
 * [AnnotationSelectionHandler]).
 *
 * **Limite connue, non résolue ici** : aucun défilement automatique vers
 * la phrase en cours de lecture TTS — la vue défile uniquement sur
 * changement de chapitre. Un chapitre long avec lecture TTS active peut
 * donc surligner un mot hors de l'écran visible.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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

        val scrollState = rememberScrollState()
        LaunchedEffect(state.currentChapterIndex) { scrollState.scrollTo(0) }

        val sentences = state.currentChapter?.paragraphs?.flatMap { it.sentences } ?: emptyList()
        val selectedRange = state.selectedSentenceRange
        var pendingColor by remember { mutableStateOf(AnnotationColor.YELLOW) }

        FlowRow(
            modifier = Modifier.weight(1f).verticalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            sentences.forEachIndexed { index, sentence ->
                SentenceText(
                    sentence = sentence,
                    isCurrentlyPlaying = index == state.currentSentenceIndex,
                    highlightedWordRange = state.highlightedWordRange,
                    isSelected = selectedRange?.contains(index) == true,
                    existingAnnotationColor = annotationColorFor(state.currentChapterIndex, sentence, state.annotations),
                    fontSizeSp = state.effectiveSettings.fontSize,
                    textColor = ThemeColors.text(state.effectiveSettings.theme),
                    onLongClick = { viewModel.onIntent(ReaderIntent.BeginSentenceSelection(index)) },
                    onClick = {
                        if (selectedRange != null) viewModel.onIntent(ReaderIntent.ExtendSentenceSelection(index))
                    },
                )
            }
        }

        if (selectedRange != null) {
            AnnotationColorPicker(
                selected = pendingColor,
                onSelect = { pendingColor = it },
                onConfirm = { viewModel.onIntent(ReaderIntent.ConfirmAnnotation(pendingColor)) },
                onCancel = { viewModel.onIntent(ReaderIntent.ClearSentenceSelection) },
            )
        }

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SentenceText(
    sentence: Sentence,
    isCurrentlyPlaying: Boolean,
    highlightedWordRange: IntRange?,
    isSelected: Boolean,
    existingAnnotationColor: AnnotationColor?,
    fontSizeSp: Int,
    textColor: Color,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val background = when {
        isSelected -> SelectionHighlightColor
        existingAnnotationColor != null -> existingAnnotationColor.toComposeColor()
        else -> Color.Transparent
    }
    Text(
        text = if (isCurrentlyPlaying && highlightedWordRange != null) {
            buildHighlightedSentence(sentence.text, highlightedWordRange)
        } else {
            AnnotatedString(sentence.text)
        },
        modifier = Modifier
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        fontSize = fontSizeSp.sp,
        color = textColor,
    )
}

private val SelectionHighlightColor = Color(0x664FC3F7)

private fun buildHighlightedSentence(text: String, range: IntRange): AnnotatedString = buildAnnotatedString {
    append(text.substring(0, range.first))
    withStyle(SpanStyle(background = Color.Yellow)) {
        append(text.substring(range.first, range.last + 1))
    }
    append(text.substring(range.last + 1))
}

/**
 * Couleur de la première annotation existante couvrant [sentence]
 * (Tâche 7.1, critère de validation : le surlignage doit réapparaître à
 * la réouverture). Les annotations créées par cette UI ne portent
 * aujourd'hui que sur un seul chapitre (sélection par phrase à
 * l'intérieur du chapitre affiché) — comparer `chapterIndex` suffit, pas
 * besoin de gérer une plage à cheval sur plusieurs chapitres pour
 * l'instant.
 */
private fun annotationColorFor(chapterIndex: Int, sentence: Sentence, annotations: List<Annotation>): AnnotationColor? =
    annotations.firstOrNull { annotation ->
        annotation.startLocator.chapterIndex == chapterIndex &&
            sentence.startOffset < annotation.endLocator.charOffset &&
            sentence.endOffset > annotation.startLocator.charOffset
    }?.color
