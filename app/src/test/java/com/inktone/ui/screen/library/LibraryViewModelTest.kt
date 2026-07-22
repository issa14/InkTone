package com.inktone.ui.screen.library

import android.content.Context
import android.net.Uri
import androidx.work.WorkManager
import com.inktone.data.settings.SettingsRepository
import com.inktone.domain.model.Book
import com.inktone.data.database.entity.ReadingProgress
import com.inktone.domain.repository.BookRepository
import com.inktone.domain.usecase.ImportBooksUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests unitaires pour [LibraryViewModel], en particulier `applyFilters()` qui
 * ne lisait auparavant jamais `filterMode` (Favoris/Séries/Tags étaient des menus
 * décoratifs sans effet sur la liste affichée).
 *
 * `loadBooks()` s'exécute explicitement sur `Dispatchers.IO` (indépendant du
 * dispatcher de test) : on attend son résultat via `first { !isLoading }` sur un
 * vrai thread (`runBlocking`) plutôt que de compter les émissions avec Turbine,
 * pour ne pas dépendre du minutage exact entre dispatcher virtuel et réel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val bookRepository = mockk<BookRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val importBooksUseCase = mockk<ImportBooksUseCase>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var viewModel: LibraryViewModel

    private val favoriteBook = Book(
        id = "book-favorite",
        title = "Le Favori",
        author = "Auteur A",
        description = null,
        totalChapters = 1,
        language = "fr",
        addedAt = 1L,
        isFavorite = true
    )
    private val seriesBook1 = Book(
        id = "book-series-1",
        title = "Tome 1",
        author = "Auteur B",
        description = null,
        totalChapters = 1,
        language = "fr",
        addedAt = 2L,
        seriesName = "La Saga",
        seriesIndex = 2f
    )
    private val seriesBook2 = Book(
        id = "book-series-2",
        title = "Tome 0",
        author = "Auteur B",
        description = null,
        totalChapters = 1,
        language = "fr",
        addedAt = 3L,
        seriesName = "La Saga",
        seriesIndex = 1f
    )
    private val taggedBook = Book(
        id = "book-tagged",
        title = "Le Tagué",
        author = "Auteur C",
        description = null,
        totalChapters = 1,
        language = "fr",
        addedAt = 4L,
        subjects = listOf("Science-fiction")
    )
    private val plainBook = Book(
        id = "book-plain",
        title = "Le Basique",
        author = "Auteur D",
        description = null,
        totalChapters = 1,
        language = "fr",
        addedAt = 5L
    )
    private val folderBook = Book(
        id = "book-folder",
        title = "Le Rangé",
        author = "Auteur E",
        description = null,
        totalChapters = 1,
        language = "fr",
        addedAt = 6L,
        sourceFolder = "MesLivres"
    )

    private val allBooks = listOf(favoriteBook, seriesBook1, seriesBook2, taggedBook, plainBook, folderBook)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        coEvery { bookRepository.getAllBooks() } returns allBooks
        coEvery { bookRepository.getProgress(any()) } returns ReadingProgress(
            bookId = "any", chapterIndex = 0, sentenceIndex = 0, characterOffset = 0, totalProgressFraction = 0f
        )
        coEvery { bookRepository.getAllTags() } returns listOf("Science-fiction")
        coEvery { settingsRepository.hasImportedFirstBook } returns flowOf(true)
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())

        viewModel = LibraryViewModel(bookRepository, settingsRepository, importBooksUseCase, workManager, context)
        awaitLoaded()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Attend la fin du chargement initial (`loadBooks()` tourne sur `Dispatchers.IO`, hors
     * dispatcher de test). `isLoading` passe à `false` AVANT que `loadNavSubItems()` (appel
     * séparé, sa propre écriture d'état) n'ait fini de peupler `navSubItems` — attendre
     * seulement `!isLoading` peut donc renvoyer un état où `navSubItems` est encore vide,
     * d'où un `NoSuchElementException` intermittent sur `getValue("Tags")` etc. Attendre aussi
     * que `navSubItems` soit peuplé élimine cette fenêtre.
     */
    private fun awaitLoaded(): LibraryUiState = runBlocking {
        withTimeout(5_000) { viewModel.uiState.first { !it.isLoading && it.navSubItems.isNotEmpty() } }
    }

    @Test
    fun `filtre FAVORITES ne garde que les livres favoris`() {
        viewModel.setFilterMode(FilterMode.FAVORITES)

        assertEquals(listOf(favoriteBook.id), viewModel.uiState.value.books.map { it.id })
    }

    @Test
    fun `filtre SERIES ne garde que les livres avec une serie`() {
        viewModel.setFilterMode(FilterMode.SERIES)

        assertEquals(
            setOf(seriesBook1.id, seriesBook2.id),
            viewModel.uiState.value.books.map { it.id }.toSet()
        )
    }

    @Test
    fun `filtre TAGS sans selection garde tous les livres`() {
        viewModel.setFilterMode(FilterMode.TAGS)

        assertEquals(allBooks.size, viewModel.uiState.value.books.size)
    }

    @Test
    fun `filtre TAGS avec des tags selectionnes ne garde que les livres correspondants`() {
        viewModel.setFilterMode(FilterMode.TAGS)
        viewModel.toggleTagFilter("Science-fiction")

        assertEquals(listOf(taggedBook.id), viewModel.uiState.value.books.map { it.id })
    }

    @Test
    fun `booksGroupedBySeries regroupe et trie par tome, ignore les livres sans serie`() {
        viewModel.setFilterMode(FilterMode.SERIES)

        val grouped = viewModel.booksGroupedBySeries()

        assertEquals(setOf("La Saga"), grouped.keys)
        assertEquals(listOf(seriesBook2.id, seriesBook1.id), grouped.getValue("La Saga").map { it.id })
    }

    @Test
    fun `toggleFavorite bascule le statut favori via le repository`() {
        viewModel.toggleFavorite(favoriteBook.id)

        coVerify { bookRepository.setFavorite(favoriteBook.id, false) }
    }

    @Test
    fun `navSubItems Favoris liste les livres favoris sans badge de compte`() {
        val favorites = viewModel.uiState.value.navSubItems.getValue("Favoris")

        assertEquals(listOf(NavSubItem(favoriteBook.title, -1, "book:${favoriteBook.id}")), favorites)
    }

    @Test
    fun `navSubItems Tags compte les livres par tag`() {
        val tags = viewModel.uiState.value.navSubItems.getValue("Tags")

        assertEquals(listOf(NavSubItem("Science-fiction", 1, "tag:Science-fiction")), tags)
    }

    @Test
    fun `navSubItems Dossiers groupe par dossier source et regroupe les origines inconnues`() {
        val folders = viewModel.uiState.value.navSubItems.getValue("Dossiers").associate { it.label to it.count }

        assertEquals(5, folders[UNKNOWN_SOURCE_FOLDER_LABEL])
        assertEquals(1, folders["MesLivres"])
    }

    @Test
    fun `selectNavSubItem sur un tag filtre la bibliotheque sur ce seul tag`() {
        viewModel.setFilterMode(FilterMode.TAGS)
        viewModel.selectNavSubItem("tag:Science-fiction")

        assertEquals(listOf(taggedBook.id), viewModel.uiState.value.books.map { it.id })
    }

    @Test
    fun `selectNavSubItem sur un dossier filtre la bibliotheque sur ce seul dossier`() {
        viewModel.setFilterMode(FilterMode.FOLDER)
        viewModel.selectNavSubItem("folder:MesLivres")

        assertEquals(listOf(folderBook.id), viewModel.uiState.value.books.map { it.id })
    }

    @Test
    fun `filtre FOLDER sans selection garde tous les livres`() {
        viewModel.setFilterMode(FilterMode.FOLDER)

        assertEquals(allBooks.size, viewModel.uiState.value.books.size)
    }

    // ── Import par lot : délégation à WorkManager ───────────

    /**
     * `importBooks()` ne fait plus l'import lui-même (déplacé dans [ImportBooksUseCase],
     * exécuté par `EpubImportWorker` — voir PLAN import EPUB §4) : il persiste les permissions
     * SAF puis enqueue un unique WorkManager work. Le rafraîchissement incrémental et le
     * dédoublonnage par id sont désormais couverts côté `ImportBooksUseCase` (voir
     * `ImportBooksUseCaseTest`) plutôt qu'ici.
     */
    @Test
    fun `importBooks persiste les permissions SAF et enqueue un unique work WorkManager`() {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()

        viewModel.importBooks(listOf(uri1, uri2))

        runBlocking {
            withTimeout(5_000) {
                while (true) {
                    val called = try {
                        coVerify(exactly = 1) {
                            workManager.enqueueUniqueWork(
                                com.inktone.data.work.EpubImportWorker.UNIQUE_WORK_NAME,
                                androidx.work.ExistingWorkPolicy.KEEP,
                                any<androidx.work.OneTimeWorkRequest>()
                            )
                        }
                        true
                    } catch (e: AssertionError) {
                        false
                    }
                    if (called) break
                    kotlinx.coroutines.delay(20)
                }
            }
        }

        io.mockk.verify { importBooksUseCase.takePersistablePermission(uri1) }
        io.mockk.verify { importBooksUseCase.takePersistablePermission(uri2) }
    }

    // ── Récupération des imports orphelins au démarrage ─────

    /**
     * Un livre resté en IMPORTING à la fin d'une session précédente (process tué en cours
     * d'import) doit être signalé à l'utilisateur au lancement suivant — voir PLAN import
     * EPUB §3. `recoverOrphanedImports()` (repository) fait le travail de marquage FAILED en
     * base ; le ViewModel n'a qu'à surfacer le message.
     */
    @Test
    fun `un import orphelin detecte au demarrage est signale a l'utilisateur`() {
        coEvery { bookRepository.recoverOrphanedImports() } returns listOf("Livre Interrompu")

        val vm = LibraryViewModel(bookRepository, settingsRepository, importBooksUseCase, workManager, context)

        runBlocking {
            withTimeout(5_000) { vm.uiState.first { it.error != null } }
        }

        assertTrue(
            vm.uiState.value.error!!.contains("Livre Interrompu"),
            "Le message devrait mentionner le livre récupéré : ${vm.uiState.value.error}"
        )
    }
}
