package com.inktone.feature.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Sentence

/**
 * B.1 — Contenu paginé par swipe horizontal. Découpe les phrases en
 * pages par accumulation de caractères (estimation nombre de caractères
 * par page basée sur la taille de police), hors thread UI.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PagedChapterContent(
    sentences: List<Sentence>,
    currentSentenceIndex: Int,
    highlightedWordRange: IntRange?,
    selectedRange: IntRange?,
    annotations: List<com.inktone.domain.model.Annotation>,
    currentChapterIndex: Int,
    fontSizeSp: Int,
    textColor: Color,
    isPlaying: Boolean,
    isReadingRulerEnabled: Boolean,
    onSentenceLongClick: (Int) -> Unit,
    onSentenceClick: (Int) -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Estimation : ~800 caractères par page à 18sp, ajusté proportionnellement
    val charsPerPage = remember(fontSizeSp) { (800 * 18 / fontSizeSp).coerceIn(200, 2000) }

    // Découpage en pages par accumulation de caractères
    val pages = remember(sentences, charsPerPage) {
        val result = mutableListOf<List<Sentence>>()
        var currentPage = mutableListOf<Sentence>()
        var charCount = 0
        var sentenceOffset = 0 // offset global de la première phrase de la page

        for (i in sentences.indices) {
            val s = sentences[i]
            if (charCount + s.text.length > charsPerPage && currentPage.isNotEmpty()) {
                result.add(currentPage.toList())
                currentPage = mutableListOf()
                charCount = 0
            }
            currentPage.add(s)
            charCount += s.text.length
        }
        if (currentPage.isNotEmpty()) result.add(currentPage.toList())
        if (result.isEmpty()) result.add(emptyList())
        result
    }

    // Une page fantôme au-delà de la dernière (pageCount = pages.size + 1,
    // jamais rendue — `pages.getOrNull` la traite comme vide) : signal non
    // ambigu d'un swipe VERS L'AVANT volontaire au-delà du chapitre.
    //
    // Bug réel trouvé à l'audit avec l'ancienne condition
    // (`currentPage >= pages.size - 1 && isScrollInProgress`) : au moment
    // exact où currentPage atteint la DERNIÈRE vraie page (simple arrivée
    // par swipe avant, sans intention d'aller plus loin), le fling de
    // settle a souvent encore isScrollInProgress=true — ça avançait au
    // chapitre suivant sans que l'utilisateur l'ait demandé, lui faisant
    // sauter le reste de la dernière page.
    val pagerState = rememberPagerState(pageCount = { pages.size + 1 })
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage >= pages.size) {
            onNextChapter()
        }
    }

    val density = LocalDensity.current
    var currentLineYDp by remember { mutableStateOf(0.dp) }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { pageIndex ->
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pages.getOrNull(pageIndex)?.forEach { sentence ->
                    val globalIndex = sentences.indexOf(sentence)
                    val isCurrentlyPlaying = globalIndex == currentSentenceIndex
                    SentenceText(
                        sentence = sentence,
                        paragraphStyle = com.inktone.domain.model.ParagraphStyle.NORMAL,
                        isCurrentlyPlaying = isCurrentlyPlaying,
                        highlightedWordRange = highlightedWordRange,
                        isSelected = selectedRange?.contains(globalIndex) == true,
                        existingAnnotationColor = annotationColorFor(currentChapterIndex, sentence, annotations),
                        fontSizeSp = fontSizeSp,
                        textColor = textColor,
                        onLongClick = { onSentenceLongClick(globalIndex) },
                        onClick = { onSentenceClick(globalIndex) },
                        modifier = if (isCurrentlyPlaying && isReadingRulerEnabled) {
                            Modifier.onGloballyPositioned { coordinates ->
                                currentLineYDp = with(density) { coordinates.positionInParent().y.toDp() }
                            }
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

private fun annotationColorFor(chapterIndex: Int, sentence: Sentence, annotations: List<com.inktone.domain.model.Annotation>): AnnotationColor? =
    annotations.firstOrNull { annotation ->
        annotation.startLocator.chapterIndex == chapterIndex &&
            sentence.startOffset < annotation.endLocator.charOffset &&
            sentence.endOffset > annotation.startLocator.charOffset
    }?.color
