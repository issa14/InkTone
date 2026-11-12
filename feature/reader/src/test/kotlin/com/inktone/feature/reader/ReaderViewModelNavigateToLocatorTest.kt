package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeEpubResourceResolver
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakePronunciationRuleRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeThemeRepository
import com.inktone.core.testing.fake.FakeTtsEngine
import com.inktone.core.testing.fake.FakeTtsSegmentCache
import com.inktone.core.testing.fake.FakeVoiceProfileRepository
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.StyledText
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.service.ChapterParser
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.usecase.AddAnnotationUseCase
import com.inktone.domain.usecase.CreateBookmarkUseCase
import com.inktone.domain.usecase.DeleteBookmarkUseCase
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.GetVoiceProfilesUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import com.inktone.domain.valueobject.Locator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Audit UX A14 et D3 — aller à une position (signet, note, résultat de
 * recherche) dans un chapitre pas encore lu. Les chapitres sont ici des
 * coquilles sans phrases ni blocs, comme les produit le parseur paresseux
 * réel : c'est ce cas qu'aucun test ne couvrait (les faux fournissaient des
 * chapitres complets).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelNavigateToLocatorTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private val hrefs = listOf("cover.xhtml", "ch1.xhtml", "ch2.xhtml")

    /** Deux phrases par chapitre, texte propre à chaque chapitre. */
    private fun sentencesOf(href: String) = listOf(
        Sentence(index = 0, text = "Début $href.", startOffset = 0, endOffset = 20),
        Sentence(index = 1, text = "Suite $href.", startOffset = 21, endOffset = 41),
    )

    /** Parseur qui renvoie le contenu réel d'un chapitre, avec un index faux comme le parseur réel. */
    private inner class ContentChapterParser : ChapterParser {
        override fun registerPublication(publicationId: String, fileUri: String) = Unit

        override suspend fun parseChapter(publicationId: String, chapterHref: String, fragment: String?): Chapter {
            val sentences = sentencesOf(chapterHref)
            return Chapter(
                index = 0,
                href = chapterHref,
                title = null,
                content = ChapterContent.Rich(
                    blocks = listOf(
                        BookBlock.ParagraphBlock(
                            richText = StyledText.plain(sentences.joinToString(" ") { it.text }),
                            globalOffsetRange = 0 until 41,
                        ),
                    ),
                ),
                sentences = sentences,
            )
        }

        override fun preload(publicationId: String, chapterHref: String, scope: CoroutineScope): Job =
            Job().apply { complete() }

        override fun invalidate(publicationId: String) = Unit
    }

    private suspend fun openBook(
        readingStateRepository: FakeReadingStateRepository,
        openIntent: ReaderIntent.OpenPublication = ReaderIntent.OpenPublication("pub"),
    ): ReaderViewModel {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = false))
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = "pub", title = "Livre", format = PublicationFormat.EPUB,
                fileUri = "content://livre", fileHash = "hash", fileSize = 10, chapterCount = hrefs.size,
                importDate = 0L,
            ),
        )
        val shells = hrefs.mapIndexed { index, href ->
            Chapter(index = index, href = href, title = null, content = ChapterContent.Rich(blocks = emptyList()))
        }
        val publicationParser = FakePublicationParser(
            result = ParseResult.Success(
                documentModel = DocumentModel(chapters = shells, tableOfContents = emptyList(), resources = emptyList()),
                isDrmProtected = false,
                metadata = PublicationMetadata(title = "Livre"),
            ),
        )
        val annotationRepository = FakeAnnotationRepository()
        val viewModel = ReaderViewModel(
            ttsEngine = FakeTtsEngine(),
            playbackOrchestrator = PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository(), FakeTtsSegmentCache(), FakePronunciationRuleRepository()),
            publicationParser = publicationParser,
            updateReadingState = UpdateReadingStateUseCase(readingStateRepository),
            getReadingState = GetReadingStateUseCase(readingStateRepository),
            publicationRepository = publicationRepository,
            preferencesRepository = preferencesRepository,
            annotationRepository = annotationRepository,
            addAnnotation = AddAnnotationUseCase(annotationRepository),
            bookmarkRepository = FakeBookmarkRepository(),
            createBookmark = CreateBookmarkUseCase(FakeBookmarkRepository()),
            deleteBookmark = DeleteBookmarkUseCase(FakeBookmarkRepository()),
            voiceProfileRepository = FakeVoiceProfileRepository(),
            getVoiceProfiles = GetVoiceProfilesUseCase(FakeVoiceProfileRepository()),
            readingSessionRepository = FakeReadingSessionRepository(),
            themeRepository = FakeThemeRepository(),
            fixedPageRenderer = com.inktone.core.testing.fake.FakeFixedPageRenderer(),
            renderedPageCache = com.inktone.core.testing.fake.FakeRenderedPageCache(),
            chapterParser = ContentChapterParser(),
            epubResourceResolver = FakeEpubResourceResolver(),
            narrationSessionContinuation = NarrationSessionContinuation(
                PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository(), FakeTtsSegmentCache(), FakePronunciationRuleRepository()),
                FakeReadingSessionRepository(),
            ),
        )
        viewModel.onIntent(openIntent)
        dispatcher.scheduler.runCurrent()
        return viewModel
    }

    /**
     * Bug réel corrigé (audit UX A14) : le chapitre cible n'était jamais
     * chargé — page vide au début du chapitre, et position non enregistrée.
     */
    @Test
    fun aller_a_une_position_d_un_chapitre_non_lu_charge_ce_chapitre_et_s_y_place() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val viewModel = openBook(readingStateRepository)
        try {
            assertEquals(0, viewModel.state.value.currentChapterIndex)

            viewModel.onIntent(ReaderIntent.NavigateToLocator(Locator(resourceHref = "ch2.xhtml", chapterIndex = 2, charOffset = 25)))
            dispatcher.scheduler.runCurrent()

            val state = viewModel.state.value
            assertEquals(2, state.currentChapterIndex)
            assertEquals("la phrase visée, pas le début du chapitre", 1, state.currentSentenceIndex)
            val content = state.chapters[2].content as ChapterContent.Rich
            assertTrue("le chapitre cible est chargé", content.blocks.isNotEmpty())
            val saved = readingStateRepository.get("pub")?.locator
            assertEquals("ch2.xhtml", saved?.resourceHref)
            assertEquals(21, saved?.charOffset)
        } finally {
            stopTimers(viewModel)
        }
    }

    /**
     * Bug réel corrigé (audit UX D3) : l'index de recherche est écrit sans le
     * décalage de la couverture ajoutée en tête ; seul le `resourceHref` est
     * exact. Un résultat ouvrait le chapitre précédent.
     */
    @Test
    fun un_index_de_chapitre_decale_est_corrige_par_le_resource_href() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val viewModel = openBook(readingStateRepository)
        try {
            viewModel.onIntent(ReaderIntent.NavigateToLocator(Locator(resourceHref = "ch2.xhtml", chapterIndex = 1, charOffset = 0)))
            dispatcher.scheduler.runCurrent()

            assertEquals(2, viewModel.state.value.currentChapterIndex)
            assertEquals("ch2.xhtml", viewModel.state.value.currentChapter?.href)
        } finally {
            stopTimers(viewModel)
        }
    }

    /**
     * Bug réel corrigé (audit UX A4) : la route relance l'ouverture à chaque
     * retour d'un sous-écran. Le livre ouvert depuis un signet revenait alors
     * à ce signet, même après avoir lu plus loin.
     */
    @Test
    fun relancer_l_ouverture_du_livre_deja_ouvert_ne_ramene_pas_a_la_cible() = runTest {
        val readingStateRepository = FakeReadingStateRepository()
        val openFromBookmark = ReaderIntent.OpenPublication(
            publicationId = "pub", targetResourceHref = "ch1.xhtml", targetChapterIndex = 1, targetCharOffset = 0,
        )
        val viewModel = openBook(readingStateRepository, openFromBookmark)
        try {
            assertEquals(1, viewModel.state.value.currentChapterIndex)
            viewModel.onIntent(ReaderIntent.NavigateToLocator(Locator(resourceHref = "ch2.xhtml", chapterIndex = 2, charOffset = 25)))
            dispatcher.scheduler.runCurrent()

            viewModel.onIntent(openFromBookmark)
            dispatcher.scheduler.runCurrent()

            assertEquals(2, viewModel.state.value.currentChapterIndex)
            assertEquals(1, viewModel.state.value.currentSentenceIndex)
        } finally {
            stopTimers(viewModel)
        }
    }

    /**
     * Le minuteur de session se relance sans fin : non arrêté, il fait tourner
     * runTest indéfiniment à la fermeture. Dans un `finally`, pour qu'une
     * assertion en échec fasse échouer le test au lieu de le bloquer.
     */
    private fun stopTimers(viewModel: ReaderViewModel) {
        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }
}
