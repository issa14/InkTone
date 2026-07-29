package com.inktone.feature.settings

import com.inktone.domain.model.PronunciationRule

data class PronunciationRulesUiState(
    val rules: List<PronunciationRule> = emptyList(),
    val editingOriginalText: String = "",
    val editingReplacementText: String = "",
    val editingIsRegex: Boolean = false,
)

sealed interface PronunciationRulesIntent {
    data class SetEditingOriginalText(val text: String) : PronunciationRulesIntent
    data class SetEditingReplacementText(val text: String) : PronunciationRulesIntent
    data class SetEditingIsRegex(val isRegex: Boolean) : PronunciationRulesIntent
    object Save : PronunciationRulesIntent
    data class ToggleEnabled(val rule: PronunciationRule) : PronunciationRulesIntent
    data class Delete(val id: String) : PronunciationRulesIntent
}
