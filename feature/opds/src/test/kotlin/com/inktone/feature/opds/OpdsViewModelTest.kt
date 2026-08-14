package com.inktone.feature.opds

import com.inktone.core.testing.fake.FakeOpdsCatalogRepository
import com.inktone.core.testing.fake.FakeOpdsCredentialsStore
import com.inktone.core.testing.fake.FakeOpdsDownloadObserver
import com.inktone.core.testing.fake.FakeOpdsDownloadScheduler
import com.inktone.core.testing.fake.FakeOpdsFeedParser
import com.inktone.core.testing.fake.FakeOpdsHttpClient
import com.inktone.domain.model.OpdsCatalog
import com.inktone.domain.usecase.AddCatalogUseCase
import com.inktone.domain.usecase.BrowseOpdsFeedUseCase
import com.inktone.domain.usecase.DownloadOpdsBookUseCase
import com.inktone.domain.usecase.GetCatalogsUseCase
import com.inktone.domain.usecase.RemoveCatalogUseCase
import com.inktone.domain.usecase.SearchOpdsFeedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Lot 13 — tâche 13.8 : transition Dashboard↔Feed, pile de retour, fermeture d'écran quand la pile est vide (données mockées, pas de réseau). */
@OptIn(ExperimentalCoroutinesApi::class)
class OpdsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun catalog(id: String) = OpdsCatalog(
        id = id, name = "Catalogue $id", rootUrl = "https://example.com/$id.opds",
        searchTemplateUrl = null, hasCredentials = false,
    )

    private fun viewModel(repo: FakeOpdsCatalogRepository) = OpdsViewModel(
        getCatalogsUseCase = GetCatalogsUseCase(repo),
        addCatalog = AddCatalogUseCase(repo, FakeOpdsCredentialsStore()),
        removeCatalog = RemoveCatalogUseCase(repo, FakeOpdsCredentialsStore()),
        browse = BrowseOpdsFeedUseCase(FakeOpdsHttpClient(), FakeOpdsFeedParser(), repo),
        search = SearchOpdsFeedUseCase(BrowseOpdsFeedUseCase(FakeOpdsHttpClient(), FakeOpdsFeedParser(), repo)),
        downloadBook = DownloadOpdsBookUseCase(FakeOpdsDownloadScheduler()),
        downloadObserver = FakeOpdsDownloadObserver(),
        httpClient = FakeOpdsHttpClient(),
    )

    /** Souscrit à l'état pour démarrer le `stateIn(WhileSubscribed)` — sinon `state.value` reste l'état initial. */
    private fun kotlinx.coroutines.test.TestScope.collectState(vm: OpdsViewModel): kotlinx.coroutines.Job =
        launch(dispatcher) { vm.state.collect {} }

    @Test
    fun le_tableau_de_bord_affiche_les_catalogues_du_repository() = runTest {
        val repo = FakeOpdsCatalogRepository()
        repo.add(catalog("cat-1"))
        repo.add(catalog("cat-2"))

        val vm = viewModel(repo)
        val job = collectState(vm)
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value as OpdsUiState.Dashboard
        assertEquals(listOf("cat-1", "cat-2"), state.catalogs.map { it.id })

        job.cancel()
    }

    @Test
    fun ouvrir_un_catalogue_passe_en_feed_puis_retour_revient_au_dashboard() = runTest {
        val repo = FakeOpdsCatalogRepository()
        repo.add(catalog("cat-1"))
        val vm = viewModel(repo)
        val job = collectState(vm)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(OpdsIntent.OpenCatalog(catalog("cat-1")))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value is OpdsUiState.Feed)

        vm.onIntent(OpdsIntent.GoBack)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value is OpdsUiState.Dashboard)

        job.cancel()
    }

    @Test
    fun retour_sur_dashboard_ferme_l_ecran_quand_la_pile_est_vide() = runTest {
        val repo = FakeOpdsCatalogRepository()
        val vm = viewModel(repo)
        val job = collectState(vm)
        dispatcher.scheduler.advanceUntilIdle()

        var effect: OpdsEffect? = null
        val effectJob = launch(dispatcher) { vm.effects.collect { effect = it } }
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(OpdsIntent.GoBack)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(OpdsEffect.CloseScreen, effect)
        effectJob.cancel()
        job.cancel()
    }
}
