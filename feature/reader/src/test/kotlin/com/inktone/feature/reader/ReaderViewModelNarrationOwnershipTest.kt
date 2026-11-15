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
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.StyledText
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Audit UX D35 et D36 — le Lecteur face à une narration déjà engagée.
 * La synthèse ne se termine jamais ([HangingTtsEngine]) : la session reste en
 * `Buffering`, donc engagée, pendant tout le test, sans dépendre du temps réel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelNarrationOwnershipTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private class HangingTtsEngine : TtsEngine {
        /** Vitesse de chaque synthèse demandée, dans l'ordre. */
        val requestedSpeeds: MutableList<Float> = java.util.Collections.synchronizedList(mutableListOf())

        override val id = TtsEngineId.ANDROID_NATIVE
        override val capabilities = TtsCapabilities(
            offline = true, wordTimestamps = false, sentenceTimestamps = false, languages = listOf("fr"),
            streamingSynthesis = false, speedControl = true, pitchControl = false, modelSizeMb = 0, license = "test",
        )

        override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
            requestedSpeeds += voiceProfile.speed
            awaitCancellation()
        }

        override fun observePlaybackEvents(): Flow<PlaybackEvent> = emptyFlow()
    }

    private val hrefs = listOf("cover.xhtml", "ch1.xhtml", "ch2.xhtml")
    private val profile = VoiceProfile(id = "vp", engine = TtsEngineId.ANDROID_NATIVE, voice = "v", language = "fr-FR")

    private fun sentencesOf(href: String) = listOf(
        Sentence(index = 0, text = "Début $href.", startOffset = 0, endOffset = 20),
        Sentence(index = 1, text = "Suite $href.", startOffset = 21, endOffset = 41),
    )

    private inner class ContentChapterParser : ChapterParser {
        override fun registerPublication(publicationId: String, fileUri: String) = Unit
        override suspend fun parseChapter(publicationId: String, chapterHref: String, fragment: String?): Chapter {
            val sentences = sentencesOf(chapterHref)
            return Chapter(
                index = 0, href = chapterHref, title = null,
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

    private val ttsEngine = HangingTtsEngine()

    private val orchestrator = PlaybackOrchestrator(
        ttsEngine, FakeAudioPlayer(), UpdateReadingStateUseCase(FakeReadingStateRepository()),
        GetReadingStateUseCase(FakeReadingStateRepository()), FakeChapterParser(), FakePublicationRepository(),
        FakePublicationParser(), FakePreferencesRepository(), FakeVoiceProfileRepository(), FakeTtsSegmentCache(),
        FakePronunciationRuleRepository(),
    )

    private suspend fun openBook(id: String, savedPosition: Locator? = null): ReaderViewModel {
        val preferencesRepository = FakePreferencesRepository()
        preferencesRepository.update(UserPreferences(eyeRestReminderEnabled = false))
        val publicationRepository = FakePublicationRepository()
        publicationRepository.insert(
            Publication(
                id = id, title = "Livre $id", format = PublicationFormat.EPUB,
                fileUri = "content://$id", fileHash = "hash-$id", fileSize = 10, chapterCount = hrefs.size,
                importDate = 0L,
            ),
        )
        val shells = hrefs.mapIndexed { index, href ->
            Chapter(index = index, href = href, title = null, content = ChapterContent.Rich(blocks = emptyList()))
        }
        val readingStateRepository = FakeReadingStateRepository()
        savedPosition?.let { readingStateRepository.save(ReadingState(publicationId = id, locator = it, lastReadAt = 0L)) }
        val annotationRepository = FakeAnnotationRepository()
        val viewModel = ReaderViewModel(
            ttsEngine = FakeTtsEngine(),
            playbackOrchestrator = orchestrator,
            publicationParser = FakePublicationParser(
                result = ParseResult.Success(
                    documentModel = DocumentModel(chapters = shells, tableOfContents = emptyList(), resources = emptyList()),
                    isDrmProtected = false,
                    metadata = PublicationMetadata(title = "Livre $id"),
                ),
            ),
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
            readingSessionRepository = FakeReadingSessionRepository(),
            themeRepository = FakeThemeRepository(),
            fixedPageRenderer = com.inktone.core.testing.fake.FakeFixedPageRenderer(),
            renderedPageCache = com.inktone.core.testing.fake.FakeRenderedPageCache(),
            chapterParser = ContentChapterParser(),
            epubResourceResolver = FakeEpubResourceResolver(),
            narrationSessionContinuation = NarrationSessionContinuation(orchestrator, FakeReadingSessionRepository()),
        )
        viewModel.onIntent(ReaderIntent.OpenPublication(id))
        dispatcher.scheduler.runCurrent()
        return viewModel
    }

    private fun narrate(publicationId: String, chapterIndex: Int, startFrom: Int) {
        orchestrator.play(
            sentences = sentencesOf(hrefs[chapterIndex]),
            voiceProfile = profile,
            startFrom = startFrom,
            publicationId = publicationId,
            chapterIndex = chapterIndex,
            resourceHref = hrefs[chapterIndex],
        )
    }

    private fun cleanUp(viewModel: ReaderViewModel) {
        orchestrator.stop()
        viewModel.cancelCheckpointTimerForTest()
        dispatcher.scheduler.runCurrent()
    }

    /**
     * Bug réel corrigé (audit UX D35) : ouvrir le Lecteur sur le livre en
     * cours de narration (mini-lecteur, carte « Reprendre ») arrêtait la
     * narration en rejoignant la position enregistrée.
     */
    @Test
    fun ouvrir_le_livre_narre_rejoint_la_narration_sans_l_interrompre() = runTest {
        narrate("pub", chapterIndex = 2, startFrom = 1)

        val viewModel = openBook("pub")
        try {
            assertTrue("la narration continue", orchestrator.isSessionEngaged())
            val state = viewModel.state.value
            assertEquals(2, state.currentChapterIndex)
            assertEquals(1, state.currentSentenceIndex)
            assertTrue(state.isPlaying)
        } finally {
            cleanUp(viewModel)
        }
    }

    /**
     * Bug réel corrigé (audit UX D36) : ouvrir un livre B pendant la narration
     * de A remplaçait les métadonnées de la notification par celles de B, et le
     * Lecteur de B se disait « en lecture ».
     */
    @Test
    fun ouvrir_un_autre_livre_ne_s_approprie_pas_la_narration_en_cours() = runTest {
        narrate("livre-a", chapterIndex = 1, startFrom = 0)
        orchestrator.setMetadata("livre-a", "Livre A", null)

        val viewModel = openBook("livre-b")
        try {
            assertTrue("la narration de A continue", orchestrator.isSessionEngaged())
            assertEquals("livre-a", orchestrator.metadata.value.publicationId)
            assertFalse(viewModel.state.value.isPlaying)
            assertEquals("B reste à sa propre position", 0, viewModel.state.value.currentChapterIndex)
        } finally {
            cleanUp(viewModel)
        }
    }

    /**
     * Audit UX D36, vu sur appareil : B se place sur sa position enregistrée,
     * et cette navigation arrêtait la narration de A.
     */
    @Test
    fun ouvrir_un_autre_livre_a_sa_position_enregistree_n_arrete_pas_la_narration() = runTest {
        narrate("livre-a", chapterIndex = 1, startFrom = 0)

        val viewModel = openBook(
            "livre-b",
            savedPosition = Locator(resourceHref = "ch2.xhtml", chapterIndex = 2, charOffset = 25),
        )
        try {
            assertTrue("la narration de A continue", orchestrator.isSessionEngaged())
            assertEquals("livre-a", orchestrator.narratedPublicationId)
            assertEquals("B rejoint sa position enregistrée", 2, viewModel.state.value.currentChapterIndex)
            assertEquals(1, viewModel.state.value.currentSentenceIndex)
            assertFalse(viewModel.state.value.isPlaying)
        } finally {
            cleanUp(viewModel)
        }
    }

    /**
     * Audit UX D36, vu sur appareil : lancer B pendant la narration de A fait
     * passer l'ordonnanceur par `Idle` (arrêt de A, publié une fois la piste
     * libérée) puis `Buffering`. Le Lecteur de B se disait arrêté pendant toute
     * la synthèse, et chaque nouvel appui sur Play relançait la préparation.
     * La séquence est rejouée ici état par état, telle que l'appareil la livre.
     */
    @Test
    fun la_synthese_de_la_narration_de_ce_livre_compte_comme_lecture() = runTest {
        narrate("livre-a", chapterIndex = 1, startFrom = 0)
        val viewModel = openBook("livre-b")
        try {
            orchestrator.stop()
            dispatcher.scheduler.runCurrent()
            narrate("livre-b", chapterIndex = 0, startFrom = 0)
            dispatcher.scheduler.runCurrent()

            assertTrue("B est en lecture pendant sa synthèse", viewModel.state.value.isPlaying)
        } finally {
            cleanUp(viewModel)
        }
    }

    /**
     * Bug réel corrigé (audit UX A6) : la vitesse changée pendant la narration
     * était enregistrée mais pas appliquée — l'ordonnanceur gardait le profil
     * du lancement jusqu'à une pause et une reprise.
     */
    @Test
    fun changer_la_vitesse_pendant_la_narration_l_applique_aussitot() = runTest {
        val viewModel = openBook("pub")
        try {
            viewModel.onIntent(ReaderIntent.PlayCurrentSentence)
            dispatcher.scheduler.runCurrent()
            awaitRealTime { ttsEngine.requestedSpeeds.isNotEmpty() }

            viewModel.onIntent(ReaderIntent.SetTtsSpeed(2.0f))
            dispatcher.scheduler.runCurrent()

            awaitRealTime { 2.0f in ttsEngine.requestedSpeeds }
        } finally {
            cleanUp(viewModel)
        }
    }

    /** La synthèse tourne sur un vrai dispatcher : attente en temps réel, bornée. */
    private fun awaitRealTime(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition non atteinte en 5 s" }
            dispatcher.scheduler.runCurrent()
            Thread.sleep(20)
        }
    }
}
