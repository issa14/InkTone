package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeAnnotationRepository
import com.inktone.core.testing.fake.FakeBookmarkRepository
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakePublicationParser
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeTtsEngine
import com.inktone.core.testing.fake.FakeVoiceProfileRepository
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.StyledText
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
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
import org.junit.Before
import org.junit.Test
import com.inktone.core.testing.fake.FakeChapterParser
import com.inktone.core.testing.fake.FakeEpubResourceResolver

/**
 * Lot 4, tâche 4.7/4.8 — flash différé du passage visé depuis « Marque-
 * pages et notes ». Le déclencheur réel (fin de mise en page asynchrone,
 * `ChapterPaginationState`) vit dans `ReaderScreen` (Compose) et n'est
 * pas testable ici ; ce test couvre la partie ViewModel : armement,
 * consommation unique, et sortie de secours si `ChapterLayoutCompleted`
 * n'arrive jamais.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelPendingHighlightTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun twoSentenceChapter() = Chapter(
        index = 0,
        href = "OEBPS/chapter1.xhtml",
        title = null,
        content = ChapterContent.Rich(
            blocks = listOf(
                BookBlock.ParagraphBlock(
                    richText = StyledText.plain("Premiere phrase. Deuxieme phrase plus longue."),
                    globalOffsetRange = 0 until 46,
                ),
            ),
        ),
        sentences = listOf(
            Sentence(index = 0, text = "Premiere phrase.", startOffset = 0, endOffset = 17),
            Sentence(index = 1, text = "Deuxieme phrase plus longue.", startOffset = 18, endOffset = 46),
        ),
    )

    private suspend fun buildViewModel(
        publicationRepository: FakePublicationRepository,
        readingStateRepository: FakeReadingStateRepository = FakeReadingStateRepository(),
    ): ReaderViewModel {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = false))
        val parser = FakePublicationParser(
            result = ParseResult.Success(
                documentModel = DocumentModel(
                    chapters = listOf(twoSentenceChapter()),
                    tableOfContents = emptyList(),
                    resources = emptyList(),
                ),
                isDrmProtected = false,
                metadata = PublicationMetadata(title = "Titre de test"),
            ),
        )
        val bookmarkRepository = FakeBookmarkRepository()
        val annotationRepository = FakeAnnotationRepository()
        return ReaderViewModel(
            ttsEngine = FakeTtsEngine(),
            audioSegmentPlayer = AudioSegmentPlayer(),
            publicationParser = parser,
            updateReadingState = UpdateReadingStateUseCase(readingStateRepository),
            getReadingState = GetReadingStateUseCase(readingStateRepository),
            publicationRepository = publicationRepository,
            preferencesRepository = preferencesRepository,
            annotationRepository = annotationRepository,
            addAnnotation = AddAnnotationUseCase(annotationRepository),
            bookmarkRepository = bookmarkRepository,
            createBookmark = CreateBookmarkUseCase(bookmarkRepository),
            deleteBookmark = DeleteBookmarkUseCase(bookmarkRepository),
            voiceProfileRepository = FakeVoiceProfileRepository(),
            getVoiceProfiles = GetVoiceProfilesUseCase(FakeVoiceProfileRepository()),
            readingSessionRepository = FakeReadingSessionRepository(),
            themeRepository = com.inktone.core.testing.fake.FakeThemeRepository(),
            fixedPageRenderer = com.inktone.core.testing.fake.FakeFixedPageRenderer(),
            chapterParser = FakeChapterParser(),
            epubResourceResolver = FakeEpubResourceResolver(),
        )
    }

    @Test
    fun ouverture_avec_flashOnArrival_arme_une_cible_en_attente_sur_la_bonne_phrase() = runTest {
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1,
                importDate = 0L,
            ),
        )
        val viewModel = buildViewModel(publicationRepository)

        viewModel.onIntent(
            ReaderIntent.OpenPublication(
                publicationId = "pub-1",
                targetResourceHref = "OEBPS/chapter1.xhtml",
                targetChapterIndex = 0,
                targetCharOffset = 20, // tombe dans la deuxième phrase
                flashOnArrival = true,
            ),
        )
        dispatcher.scheduler.runCurrent()

        val pending = viewModel.state.value.pendingHighlightTarget
        assertNotNull(pending)
        assertEquals(0, pending!!.chapterIndex)
        assertEquals(1, pending.sentenceIndex)
        assertNull("pas de flash tant que la mise en page n'est pas confirmée", viewModel.state.value.highlightedWordRange)

        // Casse le timer de checkpoint de session (Lot Sessions), démarré
        // inconditionnellement à l'ouverture, auto-récurrent — sinon le
        // drain implicite de fin de runTest boucle indéfiniment.
        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun chapterLayoutCompleted_consomme_la_cible_une_seule_fois() = runTest {
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1,
                importDate = 0L,
            ),
        )
        val viewModel = buildViewModel(publicationRepository)
        viewModel.onIntent(
            ReaderIntent.OpenPublication(
                publicationId = "pub-1",
                targetResourceHref = "OEBPS/chapter1.xhtml",
                targetChapterIndex = 0,
                targetCharOffset = 20,
                flashOnArrival = true,
            ),
        )
        dispatcher.scheduler.runCurrent()

        viewModel.onIntent(ReaderIntent.ChapterLayoutCompleted(0))
        dispatcher.scheduler.runCurrent()

        assertNull("la cible est consommée", viewModel.state.value.pendingHighlightTarget)
        assertEquals(0 until "Deuxieme phrase plus longue.".length, viewModel.state.value.highlightedWordRange)

        // Un second signal pour le même chapitre (ex. changement de taille de
        // police qui remesure) ne rejoue pas le flash : rien à consommer.
        viewModel.onIntent(ReaderIntent.ChapterLayoutCompleted(0))
        dispatcher.scheduler.runCurrent()
        assertEquals(0 until "Deuxieme phrase plus longue.".length, viewModel.state.value.highlightedWordRange)

        // Le flash s'efface tout seul après son délai.
        dispatcher.scheduler.advanceTimeBy(10_000)
        dispatcher.scheduler.runCurrent()
        assertNull(viewModel.state.value.highlightedWordRange)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun la_cible_en_attente_est_abandonnee_si_la_mise_en_page_n_aboutit_jamais() = runTest {
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 1,
                importDate = 0L,
            ),
        )
        val viewModel = buildViewModel(publicationRepository)
        viewModel.onIntent(
            ReaderIntent.OpenPublication(
                publicationId = "pub-1",
                targetResourceHref = "OEBPS/chapter1.xhtml",
                targetChapterIndex = 0,
                targetCharOffset = 20,
                flashOnArrival = true,
            ),
        )
        dispatcher.scheduler.runCurrent()
        assertNotNull(viewModel.state.value.pendingHighlightTarget)

        // ChapterLayoutCompleted n'arrive jamais — sortie de secours après le délai.
        dispatcher.scheduler.advanceTimeBy(10_000)
        dispatcher.scheduler.runCurrent()

        assertNull(viewModel.state.value.pendingHighlightTarget)
        assertNull(viewModel.state.value.highlightedWordRange)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }
}
