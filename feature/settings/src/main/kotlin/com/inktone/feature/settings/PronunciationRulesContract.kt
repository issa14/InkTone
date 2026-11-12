package com.inktone.feature.settings

import com.inktone.domain.model.PronunciationRuleProblem

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
