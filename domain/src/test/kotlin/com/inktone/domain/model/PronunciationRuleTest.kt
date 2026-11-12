package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Une règle invalide était enregistrée, affichée comme active, puis ignorée
 * en silence à chaque synthèse (`PronunciationRuleApplier` rattrape
 * l'exception). La vérification à la saisie doit donc refuser exactement ce
 * que la synthèse ne saurait pas appliquer.
 */
class PronunciationRuleTest {

    @Test
    fun une_regle_litterale_n_est_refusee_que_si_le_texte_d_origine_est_vide() {
        assertNull(PronunciationRule.problemOf("Dr.", "Docteur", isRegex = false))
        // Un littéral n'est jamais interprété : « [ » ou « $1 » sont du texte.
        assertNull(PronunciationRule.problemOf("[", "$1", isRegex = false))
        assertEquals(PronunciationRuleProblem.BLANK_ORIGINAL, PronunciationRule.problemOf("  ", "x", isRegex = false))
    }

    @Test
    fun un_motif_mal_forme_est_refuse() {
        assertEquals(PronunciationRuleProblem.INVALID_PATTERN, PronunciationRule.problemOf("[a-", "x", isRegex = true))
        assertEquals(PronunciationRuleProblem.INVALID_PATTERN, PronunciationRule.problemOf("(ab", "x", isRegex = true))
    }

    @Test
    fun un_motif_qui_correspond_au_texte_vide_est_refuse() {
        assertEquals(PronunciationRuleProblem.MATCHES_EMPTY_TEXT, PronunciationRule.problemOf("a*", "x", isRegex = true))
        assertEquals(PronunciationRuleProblem.MATCHES_EMPTY_TEXT, PronunciationRule.problemOf("(Mme)?", "x", isRegex = true))
    }

    @Test
    fun une_reference_a_un_groupe_absent_est_refusee() {
        assertEquals(PronunciationRuleProblem.INVALID_GROUP_REFERENCE, PronunciationRule.problemOf("Dr", "$1", isRegex = true))
        assertEquals(PronunciationRuleProblem.INVALID_GROUP_REFERENCE, PronunciationRule.problemOf("(D)r", "\${nom}", isRegex = true))
        assertEquals(PronunciationRuleProblem.INVALID_GROUP_REFERENCE, PronunciationRule.problemOf("Dr", "fin\\", isRegex = true))
        assertEquals(PronunciationRuleProblem.INVALID_GROUP_REFERENCE, PronunciationRule.problemOf("Dr", "prix $", isRegex = true))
    }

    @Test
    fun la_verification_accepte_exactement_ce_que_la_synthese_sait_appliquer() {
        // Même appel que PronunciationRuleApplier : replaceFirst(Regex, String).
        val cases = listOf(
            "(D)r" to "$1octeur",
            "(D)r" to "$10",
            "(?<titre>D)r" to "\${titre}octeur",
            "Dr" to "Docteur",
            "Dr" to "\\$5",
            "Dr" to "$1",
            "(D)r" to "$2",
            "(?<titre>D)r" to "\${autre}",
            "Dr" to "fin\\",
            "Dr" to "prix $",
        )
        cases.forEach { (pattern, replacement) ->
            val appliesAtSynthesis = runCatching { "Dr Martin".replaceFirst(Regex(pattern), replacement) }.isSuccess
            val acceptedAtInput = PronunciationRule.problemOf(pattern, replacement, isRegex = true) == null
            assertEquals("« $pattern » → « $replacement »", appliesAtSynthesis, acceptedAtInput)
        }
    }
}
