package com.inktone.domain.model

data class PronunciationRule(
    val id: String,
    val originalText: String,
    val replacementText: String,
    val isRegex: Boolean = false,
    val isEnabled: Boolean = true,
)
