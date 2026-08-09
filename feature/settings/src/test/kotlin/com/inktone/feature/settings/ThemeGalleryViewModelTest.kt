package com.inktone.feature.settings

import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeThemeRepository
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.usecase.DeleteCustomThemeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Lot 9, Tâche 9.6 — Galerie de thèmes. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeGalleryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun customTheme(id: String, name: String = "Perso $id") = ReadingTheme(
        id = id, displayName = name, isBuiltIn = false,
        backgroundColorHex = "#112233", textColorHex = "#FFFFFF",
        accentColorHex = "#AABBCC", highlightColorHex = "#FEDCBA",
        fontFamily = com.inktone.domain.model.FontFamily.DEFAULT,
    )

    private fun viewModel(themeRepository: FakeThemeRepository, preferencesRepository: FakePreferencesRepository) =
        ThemeGalleryViewModel(
            themeRepository, preferencesRepository,
            DeleteCustomThemeUseCase(themeRepository, preferencesRepository, FakeReadingStateRepository()),
        )

    @Test
    fun le_badge_actif_suit_le_theme_reellement_applique() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(theme = ReadingTheme.OBSIDIENNE.id))
        val vm = viewModel(FakeThemeRepository(), preferencesRepository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReadingTheme.OBSIDIENNE.id, vm.state.value.activeThemeId)
        assertEquals(ReadingTheme.OBSIDIENNE.id, vm.state.value.displayedActiveId)
    }

    @Test
    fun selectTheme_applique_reellement_le_theme_choisi() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val vm = viewModel(FakeThemeRepository(), preferencesRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(ThemeGalleryIntent.SelectTheme(ReadingTheme.SEPIA_VINTAGE.id))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReadingTheme.SEPIA_VINTAGE.id, preferencesRepository.get().theme)
        assertEquals(ReadingTheme.SEPIA_VINTAGE.id, vm.state.value.activeThemeId)
    }

    @Test
    fun l_appui_long_previsualise_sans_valider_et_relacher_revient_au_theme_courant() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(theme = ReadingTheme.PAPIER_CLAIR.id))
        val vm = viewModel(FakeThemeRepository(), preferencesRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(ThemeGalleryIntent.StartPreview(ReadingTheme.OBSIDIENNE.id))
        assertEquals(ReadingTheme.OBSIDIENNE.id, vm.state.value.displayedActiveId)
        // La preference persistee ne bouge JAMAIS pendant l'apercu.
        assertEquals(ReadingTheme.PAPIER_CLAIR.id, preferencesRepository.get().theme)

        vm.onIntent(ThemeGalleryIntent.EndPreview)
        assertEquals(ReadingTheme.PAPIER_CLAIR.id, vm.state.value.displayedActiveId)
        assertEquals(ReadingTheme.PAPIER_CLAIR.id, preferencesRepository.get().theme)
    }

    @Test
    fun seuls_les_themes_personnalises_apparaissent_dans_customThemes() = runTest {
        val themeRepository = FakeThemeRepository()
        themeRepository.saveCustom(customTheme("perso-1"))
        val vm = viewModel(themeRepository, FakePreferencesRepository())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("perso-1"), vm.state.value.customThemes.map { it.id })
        assertEquals(true, vm.state.value.customThemes.none { it.isBuiltIn })
    }

    @Test
    fun supprimer_un_theme_personnalise_actif_le_retire_et_replie_la_preference() = runTest {
        val themeRepository = FakeThemeRepository()
        val preferencesRepository = FakePreferencesRepository()
        themeRepository.saveCustom(customTheme("perso-1"))
        preferencesRepository.update(UserPreferences(theme = "perso-1"))
        val vm = viewModel(themeRepository, preferencesRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(ThemeGalleryIntent.DeleteCustomTheme("perso-1"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<String>(), vm.state.value.customThemes.map { it.id })
        assertEquals(ReadingTheme.DEFAULT.id, preferencesRepository.get().theme)
        assertNull(themeRepository.getById("perso-1"))
    }

    @Test
    fun ouvrir_le_studio_envoie_l_effet_de_navigation_avec_le_bon_id() = runTest {
        val vm = viewModel(FakeThemeRepository(), FakePreferencesRepository())
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(ThemeGalleryIntent.OpenStudio("perso-1"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ThemeGalleryEffect.NavigateToStudio("perso-1"), vm.effects.first())
    }
}
