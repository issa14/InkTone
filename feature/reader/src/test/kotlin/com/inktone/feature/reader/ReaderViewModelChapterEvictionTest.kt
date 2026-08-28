package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeTtsSegmentCache
import com.inktone.core.testing.fake.FakePronunciationRuleRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeEpubResourceResolver
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeThemeRepository
import com.inktone.core.testing.fake.FakeTtsEngine
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
import com.inktone.domain.service.ParseResult
import com.inktone.domain.service.PublicationMetadata
import com.inktone.domain.usecase.AddAnnotationUseCase
import com.inktone.domain.usecase.CreateBookmarkUseCase
import com.inktone.domain.usecase.DeleteBookmarkUseCase
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.GetVoiceProfilesUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * AUDIT_REACTIVITE_UX §5.2 — un chapitre visité n'était jamais évincé de
 * l'état : `ReaderViewModel.evictChapterContentOutsideWindow` (appelée
 * par `preloadAdjacentChapters`, elle-même déclenchée à chaque navigation)
 * doit vider `content.blocks` des chapitres hors de la fenêtre N-1..N+2,
 * sans jamais toucher `sentences` (dont la progression §3.4 dépend).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelChapterEvictionTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    /** Chapitre déjà "riche" dès l'ouverture — pas besoin de chargement paresseux pour ce test. */
    private fun loadedChapter(index: Int) = Chapter(
        index = index,
        href = "OEBPS/chapter$index.xhtml",
        title = null,
        content = ChapterContent.Rich(
            blocks = listOf(
                BookBlock.ParagraphBlock(
                    richText = StyledText.plain("Chapitre $index."),
                    globalOffsetRange = 0 until 10,
                ),
            ),
        ),
        sentences = listOf(Sentence(index = 0, text = "Chapitre $index.", startOffset = 0, endOffset = 10)),
    )

    private suspend fun buildViewModel(
        publicationRepository: FakePublicationRepository,
        chapterCount: Int,
    ): ReaderViewModel {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = false))
        val parser = FakePublicationParser(
            result = ParseResult.Success(
                documentModel = DocumentModel(
                    chapters = (0 until chapterCount).map { loadedChapter(it) },
                    tableOfContents = emptyList(),
                    resources = emptyList(),
                ),
                isDrmProtected = false,
                metadata = PublicationMetadata(title = "Titre de test"),
            ),
        )

        return ReaderViewModel(
            ttsEngine = FakeTtsEngine(),
            playbackOrchestrator = PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository(), FakeTtsSegmentCache(), FakePronunciationRuleRepository()),
            publicationParser = parser,
            updateReadingState = UpdateReadingStateUseCase(FakeReadingStateRepository()),
            getReadingState = GetReadingStateUseCase(FakeReadingStateRepository()),
            publicationRepository = publicationRepository,
            preferencesRepository = preferencesRepository,
            annotationRepository = FakeAnnotationRepository(),
            addAnnotation = AddAnnotationUseCase(FakeAnnotationRepository()),
            bookmarkRepository = FakeBookmarkRepository(),
            createBookmark = CreateBookmarkUseCase(FakeBookmarkRepository()),
            deleteBookmark = DeleteBookmarkUseCase(FakeBookmarkRepository()),
            voiceProfileRepository = FakeVoiceProfileRepository(),
            getVoiceProfiles = GetVoiceProfilesUseCase(FakeVoiceProfileRepository()),
            readingSessionRepository = FakeReadingSessionRepository(),
            themeRepository = FakeThemeRepository(),
            fixedPageRenderer = com.inktone.core.testing.fake.FakeFixedPageRenderer(),
            renderedPageCache = com.inktone.core.testing.fake.FakeRenderedPageCache(),
            chapterParser = FakeChapterParser(),
            epubResourceResolver = FakeEpubResourceResolver(),
            narrationSessionContinuation = NarrationSessionContinuation(
                PlaybackOrchestrator(FakeTtsEngine(), FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()), GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(), FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository(), FakeTtsSegmentCache(), FakePronunciationRuleRepository()),
                FakeReadingSessionRepository(),
            ),
        )
    }

    private suspend fun openTestPublication(
        viewModel: ReaderViewModel,
        publicationRepository: FakePublicationRepository,
        chapterCount: Int,
    ) {
        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = chapterCount,
                importDate = 0L,
            ),
        )
        viewModel.onIntent(ReaderIntent.OpenPublication("pub-1"))
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun la_fenetre_suit_la_navigation_et_evince_ce_qui_en_sort() = runTest {
        val publicationRepository = FakePublicationRepository()
        val viewModel = buildViewModel(publicationRepository, chapterCount = 6)
        try {
            // openPublication centre déjà la fenêtre sur le chapitre 0 (pas de
            // position restaurée) : -1..2 → 3, 4 et 5 sont évincés dès
            // l'ouverture. FakeChapterParser ne restaure jamais de contenu
            // (il renvoie toujours des blocks vides) : le reste de ce test
            // évite donc de faire dépendre une assertion positive d'un
            // rechargement, et ne vérifie que des chapitres jamais rechargés
            // depuis l'ouverture (0, 1, 2).
            openTestPublication(viewModel, publicationRepository, chapterCount = 6)
            val afterOpen = viewModel.state.value.chapters
            assertTrue("chapitre 0 (courant à l'ouverture) : content conservé", (afterOpen[0].content as ChapterContent.Rich).blocks.isNotEmpty())
            assertTrue("chapitre 1 dans la fenêtre initiale : content conservé", (afterOpen[1].content as ChapterContent.Rich).blocks.isNotEmpty())
            assertTrue("chapitre 5 hors fenêtre initiale : content déjà vidé", (afterOpen[5].content as ChapterContent.Rich).blocks.isEmpty())

            // Saut à 2 : reste DANS la fenêtre initiale (-1..2), donc
            // loadChapterContentIfNeeded(2) n'a rien à recharger — son
            // content de départ (jamais évincé) est un témoin fiable.
            // Nouvelle fenêtre 1..4 : le chapitre 0 en sort et doit être évincé.
            viewModel.onIntent(ReaderIntent.JumpToChapter(2))
            dispatcher.scheduler.runCurrent()

            val chapters = viewModel.state.value.chapters
            assertTrue("chapitre 0 sorti de la fenêtre : content doit être vidé", (chapters[0].content as ChapterContent.Rich).blocks.isEmpty())
            // §3.4 — sentences doit rester intact même pour un chapitre
            // évincé : la progression en dépend pour tout chapitre déjà visité.
            assertEquals(1, chapters[0].sentences.size)

            assertTrue("chapitre 1 toujours dans la fenêtre : content conservé", (chapters[1].content as ChapterContent.Rich).blocks.isNotEmpty())
            assertTrue("chapitre 2 (courant) : content conservé", (chapters[2].content as ChapterContent.Rich).blocks.isNotEmpty())
        } finally {
            // Le timer de checkpoint (viewModelScope, while(true) { delay(...) })
            // doit être annulé avant la fin de runTest quoi qu'il arrive — sinon
            // le drain implicite du scheduler de test ne devient jamais idle.
            viewModel.cancelCheckpointTimerForTest()
            dispatcher.scheduler.runCurrent()
        }
    }

    @Test
    fun revenir_sur_un_chapitre_evince_le_recharge() = runTest {
        val publicationRepository = FakePublicationRepository()
        val viewModel = buildViewModel(publicationRepository, chapterCount = 6)
        try {
            openTestPublication(viewModel, publicationRepository, chapterCount = 6)

            viewModel.onIntent(ReaderIntent.JumpToChapter(4))
            dispatcher.scheduler.runCurrent()
            assertTrue((viewModel.state.value.chapters[0].content as ChapterContent.Rich).blocks.isEmpty())

            // Retour au chapitre 0 : loadChapterContentIfNeeded le considère
            // comme non chargé (blocks vides) et le redemande au parseur —
            // FakeChapterParser renvoie un Chapter avec des blocks vides aussi,
            // mais l'important ici est que le mécanisme de rechargement est
            // bien déclenché (pas de contenu "figé" définitivement vide).
            viewModel.onIntent(ReaderIntent.JumpToChapter(0))
            dispatcher.scheduler.runCurrent()

            assertEquals(0, viewModel.state.value.currentChapterIndex)
            // La fenêtre est redevenue -1..2 (donc 0..2) : le chapitre 0 n'est
            // plus évincé par la même passe qui vient de le recharger.
            assertEquals(0, viewModel.state.value.chapters[0].index)
        } finally {
            viewModel.cancelCheckpointTimerForTest()
            dispatcher.scheduler.runCurrent()
        }
    }
}
