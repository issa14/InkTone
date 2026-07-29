package com.inktone.domain.service

import com.inktone.domain.model.PronunciationRule
import com.inktone.domain.repository.PronunciationRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeRuleRepository(rules: List<PronunciationRule>) : PronunciationRuleRepository {
    private val state = MutableStateFlow(rules)
    override fun observeAll(): Flow<List<PronunciationRule>> = state
    override suspend fun save(rule: PronunciationRule) = Unit
    override suspend fun delete(id: String) = Unit
}

class PronunciationRuleApplierTest {

    @Test
    fun une_regle_qui_allonge_le_texte_ne_desaligne_pas_le_surlignage() = runTest {
        val applier = PronunciationRuleApplier(
            FakeRuleRepository(
                listOf(PronunciationRule(id = "r1", originalText = "Dr.", replacementText = "Docteur")),
            ),
        )

        val appliedText = applier.apply("Dr. Martin est arrive.")
        assertEquals("Docteur Martin est arrive.", appliedText.substitutedText)

        // Le moteur TTS ne connait que le texte substitue : "Docteur" est
        // synthetise en premier, aux offsets [0, 7) du texte SUBSTITUE.
        val substitutedWordTimestamp = WordTimestamp(word = "Docteur", startMs = 0, endMs = 500, charOffset = 0)

        val remapped = substitutedWordTimestamp.remapToOriginal(appliedText)

        // Une fois remappe sur le texte AFFICHE ("Dr."), le premier mot
        // doit correspondre a "Dr." (3 caracteres), pas a "Docteur" (7).
        assertEquals(0, remapped.charOffset)
        assertEquals(3, remapped.charOffset + "Dr.".length)
        assertEquals("Dr.", remapped.word)
    }

    @Test
    fun une_regle_desactivee_n_est_pas_appliquee() = runTest {
        val applier = PronunciationRuleApplier(
            FakeRuleRepository(
                listOf(
                    PronunciationRule(id = "r1", originalText = "Dr.", replacementText = "Docteur", isEnabled = false),
                ),
            ),
        )

        val appliedText = applier.apply("Dr. Martin est arrive.")

        assertEquals("Dr. Martin est arrive.", appliedText.substitutedText)
    }

    @Test
    fun une_regex_invalide_est_ignoree_sans_planter() = runTest {
        val applier = PronunciationRuleApplier(
            FakeRuleRepository(
                listOf(PronunciationRule(id = "r1", originalText = "(", replacementText = "x", isRegex = true)),
            ),
        )

        val appliedText = applier.apply("Texte inchange")

        assertEquals("Texte inchange", appliedText.substitutedText)
    }
}
