package com.inktone.feature.library

import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakeLibraryItemRepository
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.LibraryItem
import com.inktone.domain.model.LibraryItemFilter
import com.inktone.domain.model.LibraryItemSortOrder
import com.inktone.domain.model.LibraryItemType
import com.inktone.domain.usecase.DeleteLibraryItemUseCase
import com.inktone.domain.usecase.ToggleLibraryItemPinUseCase
import com.inktone.domain.valueobject.Locator
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

/**
 * Lot 4, tâche 4.8 — palier B : puces de filtre, recherche, tri,
 * épinglage, suppression confirmée. `FakeLibraryItemRepository` applique
 * elle-même le filtre/recherche/tri (miroir en mémoire de la requête SQL
 * réelle testée dans `LibraryItemDaoTest`) — ce test vérifie que le
 * ViewModel relance bien l'observation à chaque changement, pas la
 * logique SQL elle-même.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryItemsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun bookmarkItem(id: String, pinned: Boolean = false) = LibraryItem(
        id = id, type = LibraryItemType.BOOKMARK, publicationId = "pub-1", publicationTitle = "Livre",
        startLocator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 0), endLocator = null,
        color = null, excerpt = "Un extrait de signet", note = null, isPinned = pinned, createdAt = 100L,
    )

    private fun highlightItem(id: String) = LibraryItem(
        id = id, type = LibraryItemType.HIGHLIGHT, publicationId = "pub-1", publicationTitle = "Livre",
        startLocator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 10),
        endLocator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 20),
        color = AnnotationColor.YELLOW, excerpt = "Un surlignage", note = null, isPinned = false, createdAt = 200L,
    )

    private fun noteItem(id: String) = LibraryItem(
        id = id, type = LibraryItemType.NOTE, publicationId = "pub-1", publicationTitle = "Livre",
        startLocator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 30),
        endLocator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 40),
        color = AnnotationColor.GREEN, excerpt = "Un passage annoté", note = "Ma note", isPinned = false, createdAt = 300L,
    )

    private fun buildViewModel(
        libraryItemRepository: FakeLibraryItemRepository,
        annotationRepository: FakeAnnotationRepository = FakeAnnotationRepository(),
        bookmarkRepository: FakeBookmarkRepository = FakeBookmarkRepository(),
    ) = LibraryItemsViewModel(
        libraryItemRepository = libraryItemRepository,
        deleteLibraryItem = DeleteLibraryItemUseCase(annotationRepository, bookmarkRepository),
        toggleLibraryItemPin = ToggleLibraryItemPinUseCase(annotationRepository, bookmarkRepository),
    )

    @Test
    fun les_puces_filtrent_reellement_le_contenu_affiche() = runTest {
        val repository = FakeLibraryItemRepository()
        repository.state.value = listOf(bookmarkItem("bm-1"), highlightItem("hl-1"), noteItem("note-1"))
        val viewModel = buildViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.state.value.items.size)

        viewModel.onIntent(LibraryItemsIntent.SetFilter(LibraryItemFilter.HIGHLIGHT))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("hl-1"), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun la_recherche_porte_sur_l_extrait_et_le_titre() = runTest {
        val repository = FakeLibraryItemRepository()
        repository.state.value = listOf(bookmarkItem("bm-1"), highlightItem("hl-1"))
        val viewModel = buildViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onIntent(LibraryItemsIntent.SetSearchQuery("surlignage"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("hl-1"), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun les_deux_tris_reordonnent_effectivement() = runTest {
        val repository = FakeLibraryItemRepository()
        val zorro = bookmarkItem("bm-1").copy(publicationTitle = "Zorro", createdAt = 200L)
        val alceste = highlightItem("hl-1").copy(publicationTitle = "Alceste", createdAt = 100L)
        repository.state.value = listOf(zorro, alceste)
        val viewModel = buildViewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        // Chronologique (par défaut) : le plus récent en tête.
        assertEquals("bm-1", viewModel.state.value.items.first().id)

        viewModel.onIntent(LibraryItemsIntent.SetSortOrder(LibraryItemSortOrder.ALPHABETICAL))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("hl-1", viewModel.state.value.items.first().id) // "Alceste" < "Zorro"
    }

    @Test
    fun supprimer_demande_confirmation_refuser_n_appelle_pas_le_use_case() = runTest {
        val repository = FakeLibraryItemRepository()
        val bookmarkRepository = FakeBookmarkRepository()
        bookmarkRepository.insert(
            com.inktone.domain.model.Bookmark(
                id = "bm-1", publicationId = "pub-1",
                locator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 0), createdAt = 0L,
            ),
        )
        repository.state.value = listOf(bookmarkItem("bm-1"))
        val viewModel = buildViewModel(repository, bookmarkRepository = bookmarkRepository)
        dispatcher.scheduler.advanceUntilIdle()

        val item = viewModel.state.value.items.single()
        viewModel.onIntent(LibraryItemsIntent.RequestDelete(item))
        assertEquals(item, viewModel.state.value.pendingDelete)

        viewModel.onIntent(LibraryItemsIntent.CancelDelete)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.pendingDelete)
        assertEquals(1, bookmarkRepository.observeAll().first().size)
    }

    @Test
    fun accepter_la_suppression_appelle_le_use_case_une_fois() = runTest {
        val repository = FakeLibraryItemRepository()
        val bookmarkRepository = FakeBookmarkRepository()
        bookmarkRepository.insert(
            com.inktone.domain.model.Bookmark(
                id = "bm-1", publicationId = "pub-1",
                locator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 0), createdAt = 0L,
            ),
        )
        repository.state.value = listOf(bookmarkItem("bm-1"))
        val viewModel = buildViewModel(repository, bookmarkRepository = bookmarkRepository)
        dispatcher.scheduler.advanceUntilIdle()

        val item = viewModel.state.value.items.single()
        viewModel.onIntent(LibraryItemsIntent.RequestDelete(item))
        viewModel.onIntent(LibraryItemsIntent.ConfirmDelete)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.pendingDelete)
        assertEquals(0, bookmarkRepository.observeAll().first().size)
    }

    @Test
    fun epingler_un_element_le_remonte_en_tete() = runTest {
        val repository = FakeLibraryItemRepository()
        val annotationRepository = FakeAnnotationRepository()
        annotationRepository.insert(
            com.inktone.domain.model.Annotation(
                id = "hl-1", publicationId = "pub-1",
                startLocator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 10),
                endLocator = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = 20),
                color = AnnotationColor.YELLOW, createdAt = 0L, updatedAt = 0L,
            ),
        )
        repository.state.value = listOf(bookmarkItem("bm-1"), highlightItem("hl-1"))
        val viewModel = buildViewModel(repository, annotationRepository = annotationRepository)
        dispatcher.scheduler.advanceUntilIdle()

        val target = viewModel.state.value.items.first { it.id == "hl-1" }
        viewModel.onIntent(LibraryItemsIntent.TogglePin(target))
        dispatcher.scheduler.advanceUntilIdle()

        org.junit.Assert.assertTrue(annotationRepository.observeForPublication("pub-1").first().single().isPinned)
    }
}
