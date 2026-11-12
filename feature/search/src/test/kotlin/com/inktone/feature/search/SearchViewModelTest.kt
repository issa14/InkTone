package com.inktone.feature.search

import androidx.lifecycle.SavedStateHandle
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeSearchService
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.SearchResult
import com.inktone.domain.usecase.SearchPublicationUseCase
import com.inktone.domain.valueobject.Locator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Audit UX A3 — la recherche lancée depuis un livre ne cherche que dans ce
 * livre ; la recherche globale dit de quel livre vient chaque résultat.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private val searchService = FakeSearchService()
    private val publicationRepository = FakePublicationRepository()

    private fun viewModel(publicationId: String?) = SearchViewModel(
        SearchPublicationUseCase(searchService),
        publicationRepository,
        SavedStateHandle(if (publicationId != null) mapOf(PUBLICATION_ID_ARG to publicationId) else emptyMap()),
    )

    @Test
    fun la_recherche_lancee_depuis_un_livre_ne_cherche_que_dans_ce_livre() = runTest {
        val vm = viewModel(publicationId = "livre-a")

        vm.onIntent(SearchIntent.QueryChanged("baleine"))
        advanceUntilIdle()

        assertEquals(listOf<String?>("livre-a"), searchService.searchedPublicationIds)
        assertTrue(vm.state.value.isBookScoped)
    }

    @Test
    fun la_recherche_globale_donne_le_titre_du_livre_de_chaque_resultat() = runTest {
        publicationRepository.insert(
            Publication(
                id = "livre-a", title = "Moby Dick", format = PublicationFormat.EPUB,
                fileUri = "content://a", fileHash = "h", fileSize = 1, chapterCount = 1, importDate = 0L,
            ),
        )
        searchService.nextSearchResult = listOf(
            SearchResult(publicationId = "livre-a", locator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 1, charOffset = 0), snippet = "La baleine blanche."),
        )
        val vm = viewModel(publicationId = null)

        vm.onIntent(SearchIntent.QueryChanged("baleine"))
        advanceUntilIdle()

        assertEquals(listOf<String?>(null), searchService.searchedPublicationIds)
        assertEquals(mapOf("livre-a" to "Moby Dick"), vm.state.value.bookTitles)
    }
}
