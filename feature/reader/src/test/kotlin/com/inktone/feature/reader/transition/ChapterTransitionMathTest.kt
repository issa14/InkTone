package com.inktone.feature.reader.transition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterTransitionMathTest {

    @Test
    fun `accumulate amortit le delta de moitie`() {
        assertEquals(50f, ChapterTransitionMath.accumulate(0f, 100f), 0.001f)
        assertEquals(75f, ChapterTransitionMath.accumulate(50f, 50f), 0.001f)
        assertEquals(-50f, ChapterTransitionMath.accumulate(0f, -100f), 0.001f)
    }

    @Test
    fun `fraction est bornee et symetrique`() {
        assertEquals(0.5f, ChapterTransitionMath.fraction(100f, 200f), 0.001f)
        assertEquals(0.5f, ChapterTransitionMath.fraction(-100f, 200f), 0.001f)
        assertEquals(1f, ChapterTransitionMath.fraction(500f, 200f), 0.001f)
        assertEquals(0f, ChapterTransitionMath.fraction(0f, 200f), 0.001f)
        assertEquals(0f, ChapterTransitionMath.fraction(100f, 0f), 0.001f)
    }

    @Test
    fun `clamp plafonne le tirage au ratio max`() {
        val threshold = 200f
        assertEquals(240f, ChapterTransitionMath.clamp(999f, threshold), 0.001f)
        assertEquals(-240f, ChapterTransitionMath.clamp(-999f, threshold), 0.001f)
        assertEquals(0f, ChapterTransitionMath.clamp(50f, 0f), 0.001f)
    }

    @Test
    fun `shouldCommit valide par distance par velocite ou par hysteresis`() {
        assertTrue(ChapterTransitionMath.shouldCommit(200f, 200f, 0f, false)) // distance = seuil
        assertTrue(ChapterTransitionMath.shouldCommit(100f, 200f, 1500f, false)) // vélocité, même sens
        assertTrue(ChapterTransitionMath.shouldCommit(50f, 200f, 0f, true)) // hystérésis
        assertFalse(ChapterTransitionMath.shouldCommit(100f, 200f, 500f, false)) // sous les deux
    }

    @Test
    fun `shouldCommit ignore une velocite de sens oppose au tirage`() {
        // Bug réel trouvé à l'audit : un flick rapide en sens INVERSE du
        // tirage (geste d'annulation) ne doit jamais confirmer la
        // transition, même si sa magnitude dépasse MIN_FLING_VELOCITY_PX_S.
        assertFalse(ChapterTransitionMath.shouldCommit(100f, 200f, -1500f, false))
        assertFalse(ChapterTransitionMath.shouldCommit(-100f, 200f, 1500f, false))
        // L'hystérésis (committed = true) reste prioritaire, quel que soit le sens.
        assertTrue(ChapterTransitionMath.shouldCommit(100f, 200f, -1500f, true))
    }
}
