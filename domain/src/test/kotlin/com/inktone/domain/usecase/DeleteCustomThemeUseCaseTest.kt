package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeThemeRepository
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.valueobject.Locator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Lot 9, Tâche 9.5 — « zone de danger » signalée explicitement par le
 * plan : supprimer un thème personnalisé actif ne doit jamais laisser
 * l'application dans un état orphelin. Ces tests couvrent les trois
 * surfaces où une référence peut pointer vers l'id supprimé : la
 * préférence globale, une surcharge par publication, et le thème lui-même.
 */
class DeleteCustomThemeUseCaseTest {

    private fun customTheme(id: String) = ReadingTheme(
        id = id, displayName = "Thème perso", isBuiltIn = false,
        backgroundColorHex = "#123456", textColorHex = "#FFFFFF",
        accentColorHex = "#ABCDEF", highlightColorHex = "#FEDCBA",
        fontFamily = com.inktone.domain.model.FontFamily.DEFAULT,
    )

    @Test
    fun supprimer_le_theme_personnalise_actif_replie_la_preference_globale_sur_le_defaut() = runTest {
        val themeRepository = FakeThemeRepository()
        val preferencesRepository = FakePreferencesRepository()
        val readingStateRepository = FakeReadingStateRepository()
        val theme = customTheme("mon-theme")
        themeRepository.saveCustom(theme)
        preferencesRepository.update(UserPreferences(theme = theme.id))

        val useCase = DeleteCustomThemeUseCase(themeRepository, preferencesRepository, readingStateRepository)
        useCase(theme.id)

        assertEquals(ReadingTheme.DEFAULT.id, preferencesRepository.get().theme)
        assertNull(themeRepository.getById(theme.id))
    }

    @Test
    fun supprimer_le_theme_personnalise_actif_efface_aussi_les_surcharges_par_publication() = runTest {
        val themeRepository = FakeThemeRepository()
        val preferencesRepository = FakePreferencesRepository()
        val readingStateRepository = FakeReadingStateRepository()
        val theme = customTheme("mon-theme")
        themeRepository.saveCustom(theme)
        readingStateRepository.save(
            ReadingState(
                publicationId = "pub-1",
                locator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 0),
                lastReadAt = 0L,
                overrides = ReadingOverrides(theme = theme.id, fontSize = 22),
            ),
        )

        val useCase = DeleteCustomThemeUseCase(themeRepository, preferencesRepository, readingStateRepository)
        useCase(theme.id)

        val restored = readingStateRepository.get("pub-1")
        // La surcharge de theme est effacee (repli sur le reglage global) —
        // mais fontSize, une surcharge independante, n'a aucune raison de
        // sauter avec elle.
        assertNull(restored?.overrides?.theme)
        assertEquals(22, restored?.overrides?.fontSize)
    }

    @Test
    fun supprimer_un_theme_personnalise_qui_n_est_ni_actif_ni_reference_ne_touche_a_rien_d_autre() = runTest {
        val themeRepository = FakeThemeRepository()
        val preferencesRepository = FakePreferencesRepository()
        val readingStateRepository = FakeReadingStateRepository()
        themeRepository.saveCustom(customTheme("theme-a"))
        themeRepository.saveCustom(customTheme("theme-b"))
        preferencesRepository.update(UserPreferences(theme = "theme-b"))

        val useCase = DeleteCustomThemeUseCase(themeRepository, preferencesRepository, readingStateRepository)
        useCase("theme-a")

        assertEquals("theme-b", preferencesRepository.get().theme)
        assertNull(themeRepository.getById("theme-a"))
        assertEquals("theme-b", themeRepository.getById("theme-b")?.id)
    }

    @Test
    fun apres_suppression_le_catalogue_ne_contient_plus_que_les_themes_integres_et_les_survivants() = runTest {
        val themeRepository = FakeThemeRepository()
        val preferencesRepository = FakePreferencesRepository()
        val readingStateRepository = FakeReadingStateRepository()
        themeRepository.saveCustom(customTheme("a-supprimer"))
        preferencesRepository.update(UserPreferences(theme = "a-supprimer"))

        val useCase = DeleteCustomThemeUseCase(themeRepository, preferencesRepository, readingStateRepository)
        useCase("a-supprimer")

        val all = themeRepository.observeAll().first()
        assertEquals(ReadingTheme.BUILT_IN.size, all.size)
        assertEquals(true, all.containsAll(ReadingTheme.BUILT_IN))
    }
}
