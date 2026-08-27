package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakeFixedPageRenderer
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeTtsEngine
import com.inktone.core.testing.fake.FakeVoiceProfileRepository
import com.inktone.domain.model.Bookmark
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.StyledText
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.usecase.AddAnnotationUseCase
import com.inktone.domain.usecase.CreateBookmarkUseCase
import com.inktone.domain.usecase.DeleteBookmarkUseCase
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.GetVoiceProfilesUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import com.inktone.domain.valueobject.Locator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeEpubResourceResolver

/**
 * Lot 12, tâche 12.13 — tests du Palier 2 (comportement PDF dans le
 * ViewModel). Vérifie que les fonctionnalités hors périmètre pour le
 * format PDF (décision actée 16 du plan) sont bien neutralisées, que
 * la granularité des signets est adaptée (page, pas phrase), et que la
 * progression est calculée correctement pour un format paginé.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelPdfTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun pdfChapters(pageCount: Int): List<Chapter> =
        (0 until pageCount).map { pageIndex ->
            Chapter(
                index = pageIndex,
                href = "page-$pageIndex",
                title = null,
                content = ChapterContent.Rich(
                    blocks = if (pageIndex % 2 == 0) {
                        // Pages paires : vectorielles avec texte
                        listOf(
                            BookBlock.ParagraphBlock(
                                richText = StyledText.plain("Page $pageIndex contenu texte."),
                                globalOffsetRange = 0 until 25,
                            ),
                        )
                    } else {
                        // Pages impaires : scannées sans texte (image pure)
                        emptyList()
                    },
                ),
                sentences = if (pageIndex % 2 == 0) {
                    listOf(
                        Sentence(
                            index = 0,
                            text = "Page $pageIndex contenu texte.",
                            startOffset = 0,
                            endOffset = 25,
                        ),
                    )
                } else {
                    emptyList()
                },
            )
        }

    /** PDF entierement scanne : chaque page est une image, sans texte. */
    private fun pagesSansTexte(pageCount: Int): List<Chapter> =
        (0 until pageCount).map { pageIndex ->
            Chapter(
                index = pageIndex,
                href = "page-$pageIndex",
                title = null,
                content = ChapterContent.Rich(blocks = emptyList()),
                sentences = emptyList(),
            )
        }

    private suspend fun buildPdfViewModel(
        readingStateRepository: FakeReadingStateRepository = FakeReadingStateRepository(),
        publicationRepository: FakePublicationRepository = FakePublicationRepository(),
        bookmarkRepository: FakeBookmarkRepository = FakeBookmarkRepository(),
        chapters: List<Chapter> = pdfChapters(3),
    ): ReaderViewModel {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = false))
        val parser = FakePublicationParser(
            result = ParseResult.Success(
                documentModel = DocumentModel(
                    chapters = chapters,
                    tableOfContents = emptyList(),
                    resources = emptyList(),
                ),
                isDrmProtected = false,
                metadata = PublicationMetadata(title = "PDF de test"),
            ),
        )

        return ReaderViewModel(
            ttsEngine = FakeTtsEngine(),
            playbackOrchestrator = PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(readingStateRepository), GetReadingStateUseCase(readingStateRepository), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository()),
            publicationParser = parser,
            updateReadingState = UpdateReadingStateUseCase(readingStateRepository),
            getReadingState = GetReadingStateUseCase(readingStateRepository),
            publicationRepository = publicationRepository,
            preferencesRepository = preferencesRepository,
            annotationRepository = FakeAnnotationRepository(),
            addAnnotation = AddAnnotationUseCase(FakeAnnotationRepository()),
            bookmarkRepository = bookmarkRepository,
            createBookmark = CreateBookmarkUseCase(bookmarkRepository),
            deleteBookmark = DeleteBookmarkUseCase(bookmarkRepository),
            voiceProfileRepository = FakeVoiceProfileRepository(),
            getVoiceProfiles = GetVoiceProfilesUseCase(FakeVoiceProfileRepository()),
            readingSessionRepository = FakeReadingSessionRepository(),
            themeRepository = com.inktone.core.testing.fake.FakeThemeRepository(),
            // FakeFixedPageRenderer() par defaut renvoie Failed("non
            // configure par le test") — ces tests exercent le comportement
            // PDF normal, jamais le chemin d'echec d'ouverture, donc un
            // Success exploitable ici (sinon Log.w non mocke en JUnit pur
            // fait tout planter, cf. openPublication ligne ~451).
            fixedPageRenderer = FakeFixedPageRenderer(
                result = com.inktone.domain.service.FixedPageOpenResult.Success(
                    com.inktone.core.testing.fake.FakeFixedPageDocument(pageCount = 3),
                ),
            ),
            chapterParser = FakeChapterParser(),
            epubResourceResolver = FakeEpubResourceResolver(),
            narrationSessionContinuation = NarrationSessionContinuation(
                PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository()),
                FakeReadingSessionRepository(),
            ),
        )
    }

    // ──────────────────────────────────────────────────────────────
    // 12.13.1 — TTS sur PDF (ADR-017 volet 2a : narration a la phrase,
    // remplace la neutralisation totale de la decision actee 16)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun aucune_narration_ne_demarre_sur_un_pdf_entierement_scanne() = runTest {
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = "pdf-1", title = "PDF Test", format = PublicationFormat.PDF,
                fileUri = "content://pdf", fileHash = "hash", fileSize = 100,
                chapterCount = 3, pageCount = 3, importDate = 0L,
            ),
        )
        // Toutes les pages scannees : aucune ne porte de texte.
        val viewModel = buildPdfViewModel(
            publicationRepository = publicationRepository,
            chapters = pagesSansTexte(3),
        )
        viewModel.onIntent(ReaderIntent.OpenPublication("pdf-1"))
        dispatcher.scheduler.runCurrent()

        assertEquals(PublicationFormat.PDF, viewModel.state.value.publicationFormat)
        assertEquals(
            "aucune page ne portant de texte, les commandes TTS sont masquees",
            false,
            viewModel.state.value.supportsTts,
        )

        // `supportsTts` masque le bouton, mais un declencheur externe
        // (MediaSession, ecran verrouille) peut encore appeler ce chemin :
        // il doit renoncer, pas narrer du vide ni planter.
        viewModel.onIntent(ReaderIntent.PlayCurrentSentence)
        dispatcher.scheduler.runCurrent()

        assertEquals(
            "aucune page narrable en aval : la narration ne demarre pas",
            false,
            viewModel.state.value.isPlaying,
        )
        assertEquals(
            "et la position ne bouge pas — l'utilisateur reste ou il etait",
            0,
            viewModel.state.value.currentChapterIndex,
        )

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    // ──────────────────────────────────────────────────────────────
    // 12.13.2 — Bascule SCROLL/PAGED neutralisée pour le format PDF
    // ──────────────────────────────────────────────────────────────

    @Test
    fun toggleReadingMode_est_sans_effet_pour_un_pdf() = runTest {
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = "pdf-1", title = "PDF Test", format = PublicationFormat.PDF,
                fileUri = "content://pdf", fileHash = "hash", fileSize = 100,
                chapterCount = 3, pageCount = 3, importDate = 0L,
            ),
        )
        val viewModel = buildPdfViewModel(publicationRepository = publicationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication("pdf-1"))
        dispatcher.scheduler.runCurrent()

        val modeAvant = viewModel.state.value.readingMode
        viewModel.onIntent(ReaderIntent.ToggleReadingMode)
        dispatcher.scheduler.runCurrent()

        assertEquals(
            "ToggleReadingMode ne doit pas changer le mode pour un PDF (décision actée 16)",
            modeAvant,
            viewModel.state.value.readingMode,
        )

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    // ──────────────────────────────────────────────────────────────
    // 12.13.3 — Signets : granularité page pour PDF
    // ──────────────────────────────────────────────────────────────

    @Test
    fun signet_pdf_utilise_la_granularite_page_pas_phrase() = runTest {
        val publicationRepository = FakePublicationRepository()
        val bookmarkRepository = FakeBookmarkRepository()
        publicationRepository.insert(
            Publication(
                id = "pdf-1", title = "PDF Test", format = PublicationFormat.PDF,
                fileUri = "content://pdf", fileHash = "hash", fileSize = 100,
                chapterCount = 3, pageCount = 3, importDate = 0L,
            ),
        )
        val viewModel = buildPdfViewModel(
            publicationRepository = publicationRepository,
            bookmarkRepository = bookmarkRepository,
        )
        viewModel.onIntent(ReaderIntent.OpenPublication("pdf-1"))
        dispatcher.scheduler.runCurrent()

        // Position sur la page 1 (index 1, page impaire scannée sans
        // phrase — un toggle basé sur currentSentenceIndex échouerait
        // ici avec le code EPUB, preuve que la branche PDF est active).
        viewModel.onIntent(ReaderIntent.JumpToChapter(1))
        dispatcher.scheduler.runCurrent()
        assertEquals(1, viewModel.state.value.currentChapterIndex)

        viewModel.onIntent(ReaderIntent.ToggleBookmarkAtCurrentPosition)
        dispatcher.scheduler.runCurrent()

        assertEquals("un signet doit être créé sur la page 1", 1, viewModel.state.value.bookmarks.size)
        val bookmark = viewModel.state.value.bookmarks.first()
        assertEquals("le signet doit pointer sur le chapitre 1 (page 1)", 1, bookmark.locator.chapterIndex)
        assertEquals("resourceHref doit être page-1", "page-1", bookmark.locator.resourceHref)
        assertEquals("charOffset = 0 pour une page scannée sans texte", 0, bookmark.locator.charOffset)

        // Un second toggle retire le signet, n'en crée pas un deuxième
        viewModel.onIntent(ReaderIntent.ToggleBookmarkAtCurrentPosition)
        dispatcher.scheduler.runCurrent()
        assertEquals("le signet doit être retiré au second toggle", 0, viewModel.state.value.bookmarks.size)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun isCurrentPageBookmarked_pdf_ignore_charOffset() = runTest {
        val publicationRepository = FakePublicationRepository()
        val bookmarkRepository = FakeBookmarkRepository()
        publicationRepository.insert(
            Publication(
                id = "pdf-1", title = "PDF Test", format = PublicationFormat.PDF,
                fileUri = "content://pdf", fileHash = "hash", fileSize = 100,
                chapterCount = 3, pageCount = 3, importDate = 0L,
            ),
        )
        val viewModel = buildPdfViewModel(
            publicationRepository = publicationRepository,
            bookmarkRepository = bookmarkRepository,
        )
        viewModel.onIntent(ReaderIntent.OpenPublication("pdf-1"))
        dispatcher.scheduler.runCurrent()

        // Créer un signet sur la page 0
        viewModel.onIntent(ReaderIntent.ToggleBookmarkAtCurrentPosition)
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.state.value.isCurrentPageBookmarked)

        // Changer le pageOffsetY (défilement intra-page) : le signet
        // doit toujours être détecté car la comparaison ignore
        // charOffset/pageOffsetY pour le format PDF (décision actée 21).
        viewModel.onIntent(ReaderIntent.UpdatePageOffset(0.5f))
        dispatcher.scheduler.runCurrent()
        assertTrue(
            "le signet doit rester détecté après défilement intra-page (décision actée 21)",
            viewModel.state.value.isCurrentPageBookmarked,
        )

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    // ──────────────────────────────────────────────────────────────
    // 12.13.4 — Progression PDF
    // ──────────────────────────────────────────────────────────────

    @Test
    fun bookProgression_pdf_calcule_ratio_page_sur_total() {
        val state = ReaderUiState(
            publicationFormat = PublicationFormat.PDF,
            chapters = pdfChapters(10),
            currentChapterIndex = 4,
            pageOffsetY = 0.5f, // moitié de la page 5
        )

        val expectedProgression = (4f + 0.5f) / 10f
        assertEquals(expectedProgression, state.bookProgression, 0.001f)
    }

    @Test
    fun bookProgression_pdf_vide_retourne_zero() {
        val state = ReaderUiState(
            publicationFormat = PublicationFormat.PDF,
            chapters = emptyList(),
        )
        assertEquals(0f, state.bookProgression, 0.001f)
    }

    // ──────────────────────────────────────────────────────────────
    // 12.13.5 — Reprise de lecture : pageOffsetY persistant
    // ──────────────────────────────────────────────────────────────

    @Test
    fun updatePageOffset_persiste_la_nouvelle_valeur_dans_l_etat() = runTest {
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = "pdf-1", title = "PDF Test", format = PublicationFormat.PDF,
                fileUri = "content://pdf", fileHash = "hash", fileSize = 100,
                chapterCount = 3, pageCount = 3, importDate = 0L,
            ),
        )
        val readingStateRepository = FakeReadingStateRepository()
        val viewModel = buildPdfViewModel(
            publicationRepository = publicationRepository,
            readingStateRepository = readingStateRepository,
        )
        viewModel.onIntent(ReaderIntent.OpenPublication("pdf-1"))
        dispatcher.scheduler.runCurrent()

        assertEquals(0f, viewModel.state.value.pageOffsetY)

        viewModel.onIntent(ReaderIntent.UpdatePageOffset(0.75f))
        dispatcher.scheduler.runCurrent()

        assertEquals(0.75f, viewModel.state.value.pageOffsetY)

        // Casse le timer de checkpoint (auto-récurrent) comme le ferait
        // onCleared() sur un vrai ViewModel détruit — sinon le drain
        // implicite de fin de runTest boucle indéfiniment.
        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    // ──────────────────────────────────────────────────────────────
    // 12.13.6 — Invariants du domaine
    // ──────────────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `pageCount nul leve IllegalArgumentException`() {
        Publication(
            id = "pdf-1", title = "Test", format = PublicationFormat.PDF,
            fileUri = "content://x", fileHash = "hash", fileSize = 10,
            chapterCount = 1, pageCount = 0, importDate = 0L,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pageCount negatif leve IllegalArgumentException`() {
        Publication(
            id = "pdf-1", title = "Test", format = PublicationFormat.PDF,
            fileUri = "content://x", fileHash = "hash", fileSize = 10,
            chapterCount = 1, pageCount = -5, importDate = 0L,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pageOffsetY hors bornes superieures leve IllegalArgumentException`() {
        Locator(resourceHref = "page-0", chapterIndex = 0, charOffset = 0, pageOffsetY = 1.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pageOffsetY hors bornes inferieures leve IllegalArgumentException`() {
        Locator(resourceHref = "page-0", chapterIndex = 0, charOffset = 0, pageOffsetY = -0.1f)
    }

    // ──────────────────────────────────────────────────────────────
    // 12.13.7 — Nettoyage des ressources natives à la fermeture
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `fermeture du viewModel libere les ressources PDF`() = runTest {
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = "pdf-1", title = "PDF Test", format = PublicationFormat.PDF,
                fileUri = "content://pdf", fileHash = "hash", fileSize = 100,
                chapterCount = 3, pageCount = 3, importDate = 0L,
            ),
        )
        val viewModel = buildPdfViewModel(publicationRepository = publicationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication("pdf-1"))
        dispatcher.scheduler.runCurrent()

        // L'état doit refléter le format PDF
        assertEquals(PublicationFormat.PDF, viewModel.state.value.publicationFormat)
        assertEquals(3, viewModel.state.value.chapters.size)

        // onCleared() est protected — on vérifie que la fermeture d'une
        // publication PDF puis l'ouverture d'une autre n'accumule pas
        // d'erreur (le handle natif de la première est fermé dans
        // openPublication avant d'ouvrir le second, décision actée 14).
        publicationRepository.insert(
            Publication(
                id = "pdf-2", title = "PDF Test 2", format = PublicationFormat.PDF,
                fileUri = "content://pdf2", fileHash = "hash2", fileSize = 200,
                chapterCount = 5, pageCount = 5, importDate = 0L,
            ),
        )
        // Ouvre une seconde publication : openPublication ferme le
        // FixedPageDocument de la première avant d'ouvrir la seconde.
        viewModel.onIntent(ReaderIntent.OpenPublication("pdf-2"))
        dispatcher.scheduler.runCurrent()
        assertEquals("pdf-2", viewModel.currentPublicationId)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    // ──────────────────────────────────────────────────────────────
    // 12.13.8 — Force inversion sur pages scannées (tâche 12.11)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `forcePdfInversion est desactive par defaut`() = runTest {
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = "pdf-1", title = "PDF Test", format = PublicationFormat.PDF,
                fileUri = "content://pdf", fileHash = "hash", fileSize = 100,
                chapterCount = 3, pageCount = 3, importDate = 0L,
            ),
        )
        val viewModel = buildPdfViewModel(publicationRepository = publicationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication("pdf-1"))
        dispatcher.scheduler.runCurrent()

        assertEquals(false, viewModel.state.value.forcePdfInversion)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `ToggleForcePdfInversion bascule l etat`() = runTest {
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = "pdf-1", title = "PDF Test", format = PublicationFormat.PDF,
                fileUri = "content://pdf", fileHash = "hash", fileSize = 100,
                chapterCount = 3, pageCount = 3, importDate = 0L,
            ),
        )
        val viewModel = buildPdfViewModel(publicationRepository = publicationRepository)
        viewModel.onIntent(ReaderIntent.OpenPublication("pdf-1"))
        dispatcher.scheduler.runCurrent()

        viewModel.onIntent(ReaderIntent.ToggleForcePdfInversion)
        dispatcher.scheduler.runCurrent()
        assertEquals(true, viewModel.state.value.forcePdfInversion)

        viewModel.onIntent(ReaderIntent.ToggleForcePdfInversion)
        dispatcher.scheduler.runCurrent()
        assertEquals(false, viewModel.state.value.forcePdfInversion)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }
}
