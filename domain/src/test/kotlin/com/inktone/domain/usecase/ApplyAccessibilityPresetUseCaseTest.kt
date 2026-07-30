package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.ReadingTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyAccessibilityPresetUseCaseTest {

    @Test
    fun applique_tous_les_reglages_du_preregalage_en_un_geste() = runTest {
        val repository = FakePreferencesRepository()
        val useCase = ApplyAccessibilityPresetUseCase(repository)

        useCase()

        val result = repository.get()
        assertEquals(24, result.fontSize)
        assertEquals(ReadingTheme.LIGHT, result.theme)
        assertEquals(FontFamily.OPEN_DYSLEXIC, result.fontFamily)
        assertTrue(result.reduceMotion)
        // Tache 9bis.3.6 - extension notee dans le plan Phase 9bis.
        assertTrue(result.readingRulerEnabled)
    }
}
