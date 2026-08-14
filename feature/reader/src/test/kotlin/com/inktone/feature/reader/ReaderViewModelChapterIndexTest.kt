package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeAnnotationRepository
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
import com.inktone.domain.model.AnnotationColor
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
import org.junit.Before
import org.junit.Test

/**
 * Bug réel trouvé à l'audit (livres à couverture prépendue, ex. "L'Arcane
 * des Épées") : [ChapterParser][com.inktone.domain.service.ChapterParser]
 * (implémentations réelles comme `EpubChapterParser`) peut renvoyer un
 * [Chapter.index] différent de la position réelle du chapitre dans
 * `ReaderUiState.chapters` — [FakeChapterParser] reproduit ce défaut en
 * renvoyant toujours `index = 0`. `ReaderViewModel.loadChapterContentIfNeeded`
 * doit forcer `chapter.index` à sa position array avant de le stocker :
 * sinon `confirmAnnotation` sauvegarde un `Locator.chapterIndex` qui ne
 * correspond jamais à `state.currentChapterIndex` — le filtre de rendu du
 * surlignage (`BookBlockItem`/`PagedChapterContent`) échoue alors
 * systématiquement, sans erreur visible (la couleur ne s'affiche juste
 * jamais).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelChapterIndexTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun firstChapterWithContent() = Chapter(
        index = 0,
        href = "OEBPS/chapter0.xhtml",
        title = null,
        content = ChapterContent.Rich(
            blocks = listOf(
                BookBlock.ParagraphBlock(
                    richText = StyledText.plain("Premier chapitre."),
                    globalOffsetRange = 0 until 17,
                ),
            ),
        ),
        sentences = listOf(Sentence(index = 0, text = "Premier chapitre.", startOffset = 0, endOffset = 17)),
    )

    /**
     * Coquille vide — déclenche `loadChapterContentIfNeeded` à la
     * navigation, comme `ReadiumPublicationParser.parseLazy`. `title`
     * porte un sentinel distinct de ce que [FakeChapterParser] renvoie
     * (toujours `title = null`) : sert à distinguer, dans les assertions,
     * un chargement paresseux qui a bien eu lieu d'un test vide de sens.
     */
    private fun secondChapterPlaceholder() = Chapter(
        index = 1,
        href = "OEBPS/chapter1.xhtml",
        title = "PLACEHOLDER_NON_CHARGE",
        content = ChapterContent.Rich(blocks = emptyList()),
        sentences = emptyList(),
    )

    private suspend fun buildViewModel(
        publicationRepository: FakePublicationRepository,
        annotationRepository: FakeAnnotationRepository,
    ): ReaderViewModel {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = false))
        val parser = FakePublicationParser(
            result = ParseResult.Success(
                documentModel = DocumentModel(
                    chapters = listOf(firstChapterWithContent(), secondChapterPlaceholder()),
                    tableOfContents = emptyList(),
                    resources = emptyList(),
                ),
                isDrmProtected = false,
                metadata = PublicationMetadata(title = "Titre de test"),
            ),
        )

        return ReaderViewModel(
            ttsEngine = FakeTtsEngine(),
            audioSegmentPlayer = AudioSegmentPlayer(),
            publicationParser = parser,
            updateReadingState = UpdateReadingStateUseCase(FakeReadingStateRepository()),
            getReadingState = GetReadingStateUseCase(FakeReadingStateRepository()),
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
            // Reproduit le défaut réel : toujours index = 0, quel que soit
            // le chapitre demandé (voir KDoc de cette classe de test).
            chapterParser = FakeChapterParser(),
            epubResourceResolver = FakeEpubResourceResolver(),
        )
    }

    private suspend fun openTestPublication(
        viewModel: ReaderViewModel,
        publicationRepository: FakePublicationRepository,
    ) {
        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Test", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "hash", fileSize = 10, chapterCount = 2,
                importDate = 0L,
            ),
        )
        viewModel.onIntent(ReaderIntent.OpenPublication("pub-1"))
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun loadChapterContentIfNeeded_force_index_a_la_position_array_du_chapitre() = runTest {
        val publicationRepository = FakePublicationRepository()
        val viewModel = buildViewModel(publicationRepository, FakeAnnotationRepository())
        openTestPublication(viewModel, publicationRepository)

        viewModel.onIntent(ReaderIntent.JumpToChapter(1))
        dispatcher.scheduler.runCurrent()

        // Confirme que le chargement paresseux a bien remplacé la coquille
        // (title null vient de FakeChapterParser, pas du placeholder) —
        // sinon l'assertion suivante serait vide de sens.
        assertEquals(null, viewModel.state.value.chapters[1].title)
        assertEquals(1, viewModel.state.value.chapters[1].index)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun confirmAnnotation_apres_chargement_paresseux_utilise_le_chapterIndex_de_position_pas_celui_du_parser() = runTest {
        val publicationRepository = FakePublicationRepository()
        val annotationRepository = FakeAnnotationRepository()
        val viewModel = buildViewModel(publicationRepository, annotationRepository)
        openTestPublication(viewModel, publicationRepository)

        viewModel.onIntent(ReaderIntent.JumpToChapter(1))
        dispatcher.scheduler.runCurrent()

        // Le chapitre 1 est chargé paresseusement (blocks vides au départ) —
        // FakeChapterParser renvoie un Chapter avec index = 0 (défaut
        // reproduit), mais confirmAnnotation doit sauvegarder chapterIndex
        // = 1 (la position réelle), sans quoi le rendu du surlignage
        // (filtré par state.currentChapterIndex = 1) ne matchera jamais.
        viewModel.onIntent(ReaderIntent.SetFreeSelection(anchorOffset = 0, focusOffset = 0))
        viewModel.onIntent(ReaderIntent.ConfirmAnnotation(AnnotationColor.YELLOW))
        dispatcher.scheduler.runCurrent()

        val annotation = viewModel.state.value.annotations.singleOrNull()
        assertEquals(1, annotation?.startLocator?.chapterIndex)

        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }
}
