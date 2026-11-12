package com.inktone.feature.settings

import com.inktone.domain.model.PronunciationRule
import com.inktone.domain.model.PronunciationRuleProblem

data class PronunciationRulesUiState(
    val rules: List<PronunciationRule> = emptyList(),
    val editingOriginalText: String = "",
    val editingReplacementText: String = "",
    val editingIsRegex: Boolean = false,
) {
    /** Même vérification que l'écran Réglages — voir [PronunciationRule.problemOf]. */
    val editingProblem: PronunciationRuleProblem?
        get() = PronunciationRule.problemOf(editingOriginalText, editingReplacementText, editingIsRegex)
}

/**
 * Message affiché sous le formulaire de saisie. `null` pour un texte
 * d'origine vide : le bouton désactivé suffit, un message rouge dès
 * l'ouverture du formulaire vide serait un reproche avant toute saisie.
 */
internal fun PronunciationRuleProblem.message(): String? = when (this) {
    PronunciationRuleProblem.BLANK_ORIGINAL -> null
    PronunciationRuleProblem.INVALID_PATTERN -> "Expression régulière invalide"
    PronunciationRuleProblem.MATCHES_EMPTY_TEXT -> "Ce motif correspond à un texte vide : il s'appliquerait entre chaque lettre"
    PronunciationRuleProblem.INVALID_GROUP_REFERENCE -> "Le remplacement cite un groupe absent du motif ($ ou \\ à vérifier)"
}

sealed interface PronunciationRulesIntent {
    data class SetEditingOriginalText(val text: String) : PronunciationRulesIntent
    data class SetEditingReplacementText(val text: String) : PronunciationRulesIntent
    data class SetEditingIsRegex(val isRegex: Boolean) : PronunciationRulesIntent
    object Save : PronunciationRulesIntent
    data class ToggleEnabled(val rule: PronunciationRule) : PronunciationRulesIntent
    data class Delete(val id: String) : PronunciationRulesIntent
}
