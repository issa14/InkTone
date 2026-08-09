package com.inktone.feature.settings

import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeThemeRepository
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.ReadingTheme
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Lot 9, Tâche 9.6 — Studio de thème personnalisé. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeStudioViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(themeRepository: FakeThemeRepository = FakeThemeRepository()) = ThemeStudioViewModel(
        themeRepository,
        DeleteCustomThemeUseCase(themeRepository, FakePreferencesRepository(), FakeReadingStateRepository()),
    )

    @Test
    fun charger_sans_id_reinitialise_sur_les_valeurs_de_creation() = runTest {
        val vm = viewModel()
        vm.onIntent(ThemeStudioIntent.SetName("Reste pas"))
        vm.onIntent(ThemeStudioIntent.Load(null))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("", vm.state.value.name)
        assertNull(vm.state.value.editingThemeId)
    }

    @Test
    fun charger_un_theme_existant_peuple_tous_les_champs() = runTest {
        val themeRepository = FakeThemeRepository()
        val theme = ReadingTheme(
            id = "perso-1", displayName = "Mon thème", isBuiltIn = false,
            backgroundColorHex = "#111111", textColorHex = "#222222",
            accentColorHex = "#333333", highlightColorHex = "#444444",
            fontFamily = FontFamily.SANS_SERIF,
        )
        themeRepository.saveCustom(theme)
        val vm = viewModel(themeRepository)

        vm.onIntent(ThemeStudioIntent.Load("perso-1"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("perso-1", vm.state.value.editingThemeId)
        assertEquals("Mon thème", vm.state.value.name)
        assertEquals("#111111", vm.state.value.backgroundColorHex)
        assertEquals("#222222", vm.state.value.textColorHex)
        assertEquals("#333333", vm.state.value.accentColorHex)
        assertEquals("#444444", vm.state.value.highlightColorHex)
    }

    // ───── Tâche 9.6, point 5 — les quatre sélecteurs modifient quatre propriétés distinctes ─────

    @Test
    fun chaque_selecteur_de_couleur_ne_modifie_que_sa_propre_propriete() = runTest {
        val vm = viewModel()
        val initial = vm.state.value

        vm.onIntent(ThemeStudioIntent.SetBackgroundColor("#ABCDEF"))
        assertEquals("#ABCDEF", vm.state.value.backgroundColorHex)
        assertEquals(initial.textColorHex, vm.state.value.textColorHex)
        assertEquals(initial.accentColorHex, vm.state.value.accentColorHex)
        assertEquals(initial.highlightColorHex, vm.state.value.highlightColorHex)

        vm.onIntent(ThemeStudioIntent.SetTextColor("#123456"))
        assertEquals("#123456", vm.state.value.textColorHex)
        assertEquals("#ABCDEF", vm.state.value.backgroundColorHex) // inchange par l'appel precedent

        vm.onIntent(ThemeStudioIntent.SetAccentColor("#654321"))
        assertEquals("#654321", vm.state.value.accentColorHex)

        vm.onIntent(ThemeStudioIntent.SetHighlightColor("#FEDCBA"))
        assertEquals("#FEDCBA", vm.state.value.highlightColorHex)
        // Les trois autres proprietes n'ont pas bouge depuis leurs derniers reglages.
        assertEquals("#ABCDEF", vm.state.value.backgroundColorHex)
        assertEquals("#123456", vm.state.value.textColorHex)
        assertEquals("#654321", vm.state.value.accentColorHex)
    }

    @Test
    fun une_palette_de_depart_applique_bien_quatre_couleurs_distinctes() = runTest {
        val vm = viewModel()
        vm.onIntent(ThemeStudioIntent.ApplyStarterPalette(StarterPalette.NEON))
        val s = vm.state.value

        val colors = setOf(s.backgroundColorHex, s.textColorHex, s.accentColorHex, s.highlightColorHex)
        assertEquals(4, colors.size)
    }

    // ───── Tâche 9.6, point 4 — badge WCAG informatif, jamais bloquant ─────

    @Test
    fun le_ratio_de_contraste_reflete_les_couleurs_choisies() = runTest {
        val vm = viewModel()
        vm.onIntent(ThemeStudioIntent.SetBackgroundColor("#FFFFFF"))
        vm.onIntent(ThemeStudioIntent.SetTextColor("#000000"))
        assertEquals(WcagLevel.AAA, vm.state.value.wcagLevel)

        vm.onIntent(ThemeStudioIntent.SetTextColor("#FEFEFE")) // quasi identique au fond
        assertEquals(WcagLevel.BELOW_THRESHOLD, vm.state.value.wcagLevel)
    }

    @Test
    fun sauvegarder_fonctionne_meme_sous_le_seuil_wcag() = runTest {
        val themeRepository = FakeThemeRepository()
        val vm = viewModel(themeRepository)
        vm.onIntent(ThemeStudioIntent.SetName("Contraste faible"))
        vm.onIntent(ThemeStudioIntent.SetBackgroundColor("#FFFFFF"))
        vm.onIntent(ThemeStudioIntent.SetTextColor("#FEFEFE"))
        assertEquals(WcagLevel.BELOW_THRESHOLD, vm.state.value.wcagLevel)

        vm.onIntent(ThemeStudioIntent.Save)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeStudioEffect.SavedAndClose, vm.effects.first())
        assertEquals(1, themeRepository.observeAll().first().count { !it.isBuiltIn })
    }

    // ───── Tâche 9.6, point 7 — pas d'entrée anonyme ─────

    @Test
    fun un_theme_sans_nom_recoit_un_nom_par_defaut_a_la_sauvegarde() = runTest {
        val themeRepository = FakeThemeRepository()
        val vm = viewModel(themeRepository)
        // Nom jamais renseigne (reste "").

        vm.onIntent(ThemeStudioIntent.Save)
        dispatcher.scheduler.advanceUntilIdle()

        val saved = themeRepository.observeAll().first().first { !it.isBuiltIn }
        assertTrue(saved.displayName.isNotBlank())
    }

    // ───── Tâche 9.6, point 6 — suppression du thème actif ─────

    @Test
    fun confirmer_la_suppression_declenche_l_effet_de_fermeture() = runTest {
        val themeRepository = FakeThemeRepository()
        val theme = ReadingTheme(
            id = "perso-1", displayName = "A supprimer", isBuiltIn = false,
            backgroundColorHex = "#111111", textColorHex = "#FFFFFF",
            accentColorHex = "#222222", highlightColorHex = "#333333",
            fontFamily = FontFamily.DEFAULT,
        )
        themeRepository.saveCustom(theme)
        val vm = viewModel(themeRepository)
        vm.onIntent(ThemeStudioIntent.Load("perso-1"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(ThemeStudioIntent.ConfirmDelete)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeStudioEffect.DeletedAndClose, vm.effects.first())
        assertNull(themeRepository.getById("perso-1"))
    }

    @Test
    fun sauvegarder_une_edition_reutilise_le_meme_id_pas_un_nouveau() = runTest {
        val themeRepository = FakeThemeRepository()
        val theme = ReadingTheme(
            id = "perso-1", displayName = "Original", isBuiltIn = false,
            backgroundColorHex = "#111111", textColorHex = "#FFFFFF",
            accentColorHex = "#222222", highlightColorHex = "#333333",
            fontFamily = FontFamily.DEFAULT,
        )
        themeRepository.saveCustom(theme)
        val vm = viewModel(themeRepository)
        vm.onIntent(ThemeStudioIntent.Load("perso-1"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(ThemeStudioIntent.SetName("Renomme"))
        vm.onIntent(ThemeStudioIntent.Save)
        dispatcher.scheduler.advanceUntilIdle()

        val all = themeRepository.observeAll().first().filterNot { it.isBuiltIn }
        assertEquals(1, all.size)
        assertEquals("perso-1", all.first().id)
        assertEquals("Renomme", all.first().displayName)
    }

    @Test
    fun la_famille_de_police_inferee_distingue_fond_clair_et_fond_sombre() {
        assertNotEquals(
            ThemeStudioViewModel.inferFontFamily("#FFFFFF"),
            ThemeStudioViewModel.inferFontFamily("#000000"),
        )
    }
}
