package com.inktone.feature.reader

import com.inktone.domain.model.SleepTimerState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vue structurelle de `ReaderScreen` : un changement de champ VIVANT ne doit
 * pas invalider l'écran, un changement de structure si. Garde-fou de la
 * liste de [withoutLiveFields] — un champ vivant oublié recomposerait de
 * nouveau tout l'écran à chaque paragraphe ou à chaque seconde.
 */
class ReaderLiveFieldsTest {

    private val base = ReaderUiState(title = "Titre", currentChapterIndex = 3, currentSentenceIndex = 10)

    @Test
    fun chaque_champ_vivant_seul_ne_change_pas_la_structure() {
        val variants = listOf(
            base.copy(currentSentenceIndex = 11),
            base.copy(isAudioActive = true),
            base.copy(sleepTimer = SleepTimerState(remainingMs = 59_000, totalMs = 60_000)),
            base.copy(eyeRestReminderCountdownS = 5),
            base.copy(pageOffsetY = 0.5f),
        )
        variants.forEach { variant ->
            assertTrue("champ vivant traité comme structurel : $variant", ReaderStructuralPolicy.equivalent(base, variant))
        }
    }

    @Test
    fun un_champ_structurel_change_la_structure() {
        val variants = listOf(
            base.copy(currentChapterIndex = 4),
            base.copy(title = "Autre"),
            base.copy(isPlaying = true),
            base.copy(readingMode = ReadingMode.PAGED),
            base.copy(freeSelectionAnchorOffset = 0, freeSelectionFocusOffset = 3),
        )
        variants.forEach { variant ->
            assertFalse("champ structurel ignoré : $variant", ReaderStructuralPolicy.equivalent(base, variant))
        }
    }
}
