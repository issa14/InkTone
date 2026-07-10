package com.readflow.ui.screen.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.readflow.domain.model.Chapter
import com.readflow.service.audio.PlaybackState
import com.readflow.service.audio.PlaybackStatus
import com.readflow.ui.theme.OpenDyslexicFamily

private const val BLUETOOTH_LATENCY_COMPENSATION_MS = 180L

@Composable
fun ReaderContent(
    chapter: Chapter,
    playbackState: PlaybackState,
    textColor: Color,
    accentColor: Color,
    useOpenDyslexic: Boolean = false,
    onTap: (Offset) -> Unit
) {
    val bodyFont = if (useOpenDyslexic) OpenDyslexicFamily else FontFamily.Serif
    val listState = rememberLazyListState()
    val sentences = chapter.sentences
    val activeIdx = playbackState.activeSentenceIndex
    val isSpeaking = playbackState.status == PlaybackStatus.PLAYING

    LaunchedEffect(activeIdx, isSpeaking) {
        if (isSpeaking && activeIdx in sentences.indices) {
            kotlinx.coroutines.delay(BLUETOOTH_LATENCY_COMPENSATION_MS)
            val targetIndex = activeIdx + 1
            if (targetIndex < listState.layoutInfo.totalItemsCount) {
                listState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = -(listState.layoutInfo.viewportSize.height / 3)
                )
            }
        }
    }

    val highlightBg = accentColor.copy(alpha = 0.12f)

    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onTap(it) } }
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            userScrollEnabled = true
        ) {
            item(key = "title") {
                Spacer(Modifier.height(24.dp))
                Text(chapter.title, fontFamily = bodyFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = textColor.copy(alpha = 0.75f), lineHeight = 1.6.em)
                Spacer(Modifier.height(28.dp))
            }
            itemsIndexed(items = sentences, key = { i, _ -> "sent_$i" }) { index, sentence ->
                val isActive = index == activeIdx && isSpeaking
                val bgModifier = if (isActive) Modifier.background(color = highlightBg, shape = RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                else Modifier.padding(vertical = 2.dp)
                Text(text = sentence.text, fontFamily = bodyFont, fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal, fontSize = 17.sp, lineHeight = 1.6.em, textAlign = TextAlign.Justify, color = if (isActive) accentColor else textColor.copy(alpha = 0.88f), modifier = bgModifier)
            }
            item(key = "bottom_spacer") { Spacer(Modifier.height(120.dp)) }
        }
    }
}
