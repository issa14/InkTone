package com.inktone.feature.reader

data class ReaderUiState(
    val sentenceText: String = "",
    val highlightedWordRange: IntRange? = null,
    val isPlaying: Boolean = false,
)

sealed interface ReaderIntent {
    data class LoadSentence(val text: String) : ReaderIntent
    data object PlayCurrentSentence : ReaderIntent
    data object Pause : ReaderIntent
}
