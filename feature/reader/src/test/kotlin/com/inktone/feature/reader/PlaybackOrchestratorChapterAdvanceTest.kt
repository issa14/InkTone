package com.inktone.feature.reader

import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.ChapterParser
import com.inktone.domain.usecase.GetReadingStateUseCase
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Garde-fou du palier P2-b : l'auto-avance de chapitre appartient à
 * l'ordonnanceur, **pas** à l'écran Lecteur.
 *
 * Ces cas ne peuvent pas se prouver depuis le ViewModel : tout leur intérêt
 * est justement de fonctionner quand celui-ci n'existe plus. Ils vérifient
 * donc directement que l'ordonnanceur, muni d'un programme de narration,
 * obtient lui-même les phrases du chapitre suivant et enchaîne — sans aucun
 * collaborateur de présentation.
 */
class PlaybackOrchestratorChapterAdvanceTest {

    /** Parseur pilotable : associe un href aux phrases à retourner. */
    private class ProgrammedChapterParser(
        private val chapters: Map<String, List<Sentence>>,
    ) : ChapterParser {
        var parsedHrefs = mutableListOf<String>()

        override fun registerPublication(publicationId: String, fileUri: String) = Unit

        override suspend fun parseChapter(
            publicationId: String,
            chapterHref: String,
            fragment: String?,
        ): Chapter {
            parsedHrefs += chapterHref
            return Chapter(
                // Index volontairement FAUX (toujours 0), comme le parseur réel
                // sur les livres à couverture prépendue : l'ordonnanceur doit
                // se fier à la position dans le programme, jamais à ce champ.
                index = 0,
                href = chapterHref,
                title = null,
                content = ChapterContent.Rich(blocks = emptyList()),
                sentences = chapters[chapterHref].orEmpty(),
            )
        }

        override fun preload(publicationId: String, chapterHref: String, scope: CoroutineScope): Job =
            Job().apply { complete() }

        override fun invalidate(publicationId: String) = Unit
    }

    private fun sentence(index: Int, text: String, offset: Int) = Sentence(
        index = index,
        text = text,
        startOffset = offset,
        endOffset = offset + text.length,
    )

    private val profile = VoiceProfile(
        id = "vp-test",
        engine = TtsEngineId.ANDROID_NATIVE,
        voice = "fr-fr",
        language = "fr-FR",
    )

    /** Moteur minimal : un segment court par phrase, sans timestamps. */
    private class ShortTtsEngine : com.inktone.domain.service.TtsEngine {
        override val id = TtsEngineId.ANDROID_NATIVE
        override val capabilities = com.inktone.domain.service.TtsCapabilities(
            offline = true,
            wordTimestamps = false,
            sentenceTimestamps = false,
            languages = listOf("fr-FR"),
            streamingSynthesis = false,
            speedControl = false,
            pitchControl = false,
            modelSizeMb = 0,
            license = "test",
        )

        override suspend fun synthesize(
            sentence: Sentence,
            voiceProfile: VoiceProfile,
        ): com.inktone.domain.service.AudioSegment = com.inktone.domain.service.AudioSegment(
            audioData = ByteArray(4),
            durationMs = 20,
            wordTimestamps = emptyList(),
            sampleRate = 22_050,
        )

        override fun observePlaybackEvents(): kotlinx.coroutines.flow.Flow<com.inktone.domain.service.PlaybackEvent> =
            kotlinx.coroutines.flow.emptyFlow()
    }

    private fun orchestrator(parser: ChapterParser) = PlaybackOrchestrator(
        ttsEngine = ShortTtsEngine(),
        audioPlayer = FakeAudioPlayer(),
        updateReadingState = UpdateReadingStateUseCase(FakeReadingStateRepository()),
            getReadingState = GetReadingStateUseCase(FakeReadingStateRepository()),
        chapterParser = parser,
    )

    @Test
    fun enchaineLeChapitreSuivantSansAucunCollaborateurDeLecran() = runBlocking {
        val parser = ProgrammedChapterParser(
            mapOf("ch1.xhtml" to listOf(sentence(0, "Chapitre deux.", 0))),
        )
        val orchestrator = orchestrator(parser)
        orchestrator.setNarrationProgram("pub1", listOf("ch0.xhtml", "ch1.xhtml"))

        orchestrator.play(
            sentences = listOf(sentence(0, "Fin du premier.", 0)),
            voiceProfile = profile,
            startFrom = 0,
            publicationId = "pub1",
            chapterIndex = 0,
            resourceHref = "ch0.xhtml",
        )

        withTimeout(5_000) { orchestrator.currentChapterIndex.first { it == 1 } }

        assertEquals(listOf("ch1.xhtml"), parser.parsedHrefs)
        assertEquals(1, orchestrator.currentChapterIndex.value)
    }

    @Test
    fun sansProgrammeLaNarrationSarreteEnFinDeChapitre() = runBlocking {
        val parser = ProgrammedChapterParser(
            mapOf("ch1.xhtml" to listOf(sentence(0, "Jamais lu.", 0))),
        )
        val orchestrator = orchestrator(parser)
        // Aucun setNarrationProgram : comportement d'avant le palier.

        orchestrator.play(
            sentences = listOf(sentence(0, "Fin du premier.", 0)),
            voiceProfile = profile,
            startFrom = 0,
            publicationId = "pub1",
            chapterIndex = 0,
            resourceHref = "ch0.xhtml",
        )

        withTimeout(5_000) { orchestrator.chapterCompleted.first() }

        assertEquals(emptyList<String>(), parser.parsedHrefs)
        assertEquals(0, orchestrator.currentChapterIndex.value)
    }

    @Test
    fun dernierChapitreDuLivreNeParseRien() = runBlocking {
        val parser = ProgrammedChapterParser(emptyMap())
        val orchestrator = orchestrator(parser)
        orchestrator.setNarrationProgram("pub1", listOf("ch0.xhtml"))

        orchestrator.play(
            sentences = listOf(sentence(0, "Fin du livre.", 0)),
            voiceProfile = profile,
            startFrom = 0,
            publicationId = "pub1",
            chapterIndex = 0,
            resourceHref = "ch0.xhtml",
        )

        withTimeout(5_000) { orchestrator.chapterCompleted.first() }

        assertEquals(emptyList<String>(), parser.parsedHrefs)
        assertEquals(0, orchestrator.currentChapterIndex.value)
    }

    @Test
    fun chapitreSuivantSansPhraseTermineLaNarrationProprement() = runBlocking {
        // Chapitre 1 présent au programme mais vide (page de séparation, image
        // seule) : la narration s'arrête au lieu de sauter au chapitre 2, qui
        // ferait perdre du texte à l'auditeur sans qu'il le sache.
        val parser = ProgrammedChapterParser(
            mapOf("ch2.xhtml" to listOf(sentence(0, "Chapitre trois.", 0))),
        )
        val orchestrator = orchestrator(parser)
        orchestrator.setNarrationProgram("pub1", listOf("ch0.xhtml", "ch1.xhtml", "ch2.xhtml"))

        orchestrator.play(
            sentences = listOf(sentence(0, "Fin du premier.", 0)),
            voiceProfile = profile,
            startFrom = 0,
            publicationId = "pub1",
            chapterIndex = 0,
            resourceHref = "ch0.xhtml",
        )

        withTimeout(5_000) { orchestrator.chapterCompleted.first() }
        withTimeout(5_000) {
            orchestrator.state.first { it is PlaybackOrchestrator.PlaybackStatus.Idle && parser.parsedHrefs.isNotEmpty() }
        }

        assertEquals(listOf("ch1.xhtml"), parser.parsedHrefs)
        assertEquals(0, orchestrator.currentChapterIndex.value)
    }
}
