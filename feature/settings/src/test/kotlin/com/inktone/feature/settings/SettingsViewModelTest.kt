package com.inktone.feature.settings

import androidx.test.core.app.ApplicationProvider
import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakePronunciationRuleRepository
import com.inktone.core.testing.fake.FakeTtsEngine
import com.inktone.core.testing.fake.FakeVoiceModelDownloadService
import com.inktone.core.testing.fake.FakeVoiceProfileRepository
import com.inktone.domain.model.AppTheme
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.PronunciationRule
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioPlayer
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackPosition
import com.inktone.domain.service.PlayerState
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.service.VoiceModelDownloadService
import com.inktone.domain.usecase.ApplyAccessibilityPresetUseCase
import com.inktone.domain.usecase.GetVoiceProfilesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Lot 6, Tâche 6.6 — tests du palier A. Se concentre sur ce que le
 * ViewModel peut garantir seul (écriture des préférences et du profil
 * vocal) ; le calcul de l'état "toggle éteint si un réglage manuel a été
 * changé" vit dans SettingsScreen.kt (dérivé de UserPreferences), pas
 * testé ici séparément — non couvert par un test Compose dans ce palier.
 *
 * Lot 6, Tâche 6.9 (Palier B) : cache réel (Robolectric — `Context.cacheDir`
 * exige un vrai environnement Android, indisponible en JVM pur) et carte
 * Prononciation inline. L'export/import de sauvegarde (`BackupManager`,
 * module `data`) n'est pas testé ici : il vit dans `BackupViewModel`
 * (module `app`), hors de portée de `feature/settings`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(
        preferencesRepository: FakePreferencesRepository = FakePreferencesRepository(),
        voiceProfileRepository: FakeVoiceProfileRepository = FakeVoiceProfileRepository(),
        pronunciationRuleRepository: FakePronunciationRuleRepository = FakePronunciationRuleRepository(),
        voiceModelDownloadService: VoiceModelDownloadService = FakeVoiceModelDownloadService(),
        ttsEngine: TtsEngine = FakeTtsEngine(),
        audioPlayer: AudioPlayer = FakeAudioPlayer(),
    ) = SettingsViewModel(
        preferencesRepository,
        ApplyAccessibilityPresetUseCase(preferencesRepository),
        GetVoiceProfilesUseCase(voiceProfileRepository),
        voiceProfileRepository,
        pronunciationRuleRepository,
        voiceModelDownloadService,
        ApplicationProvider.getApplicationContext(),
        // Même dispatcher que Dispatchers.setMain(dispatcher) ci-dessus : sans
        // ça, withContext(Dispatchers.IO) saute sur un vrai pool de threads
        // qu'advanceUntilIdle() (kotlinx-coroutines-test) ne voit pas, et la
        // coroutine reprend en temps réel — course avec l'assertion (trouvé
        // par un échec intermittent, pas en théorie).
        dispatcher,
        ttsEngine,
        audioPlayer,
    )

    @Test
    fun `le preset Mode sombre applique le theme systeme et le theme de lecture`() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val vm = viewModel(preferencesRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetDarkModePreset(true))
        dispatcher.scheduler.advanceUntilIdle()

        val prefs = preferencesRepository.get()
        assertEquals(AppTheme.DARK, prefs.appTheme)
        assertEquals(ReadingTheme.OBSIDIENNE.id, prefs.theme)
    }

    @Test
    fun `changer de moteur synchronise le VoiceProfile actif - defaut prealable n2`() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val voiceProfileRepository = FakeVoiceProfileRepository()
        val vm = viewModel(preferencesRepository, voiceProfileRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetDefaultTtsEngine(TtsEngineId.EDGE_TTS))
        dispatcher.scheduler.advanceUntilIdle()

        val prefs = preferencesRepository.get()
        assertEquals(TtsEngineId.EDGE_TTS, prefs.defaultTtsEngine)

        // Le profil actif doit exister et porter le moteur Edge — sans cela,
        // SelectiveTtsEngine (qui route sur voiceProfile.engine) ne routerait
        // jamais vers Edge : resolveVoiceProfile retomberait sur le repli.
        val activeProfile = voiceProfileRepository.getById(prefs.activeVoiceProfileId!!)
        assertTrue("un profil actif doit être créé", activeProfile != null)
        assertEquals(TtsEngineId.EDGE_TTS, activeProfile!!.engine)
        assertEquals("fr-FR-VivienneNeural", activeProfile.voice)
    }

    @Test
    fun `choisir une voix met a jour le profil actif - Henri et Vivienne`() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val voiceProfileRepository = FakeVoiceProfileRepository()
        val vm = viewModel(preferencesRepository, voiceProfileRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetDefaultTtsEngine(TtsEngineId.EDGE_TTS))
        dispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(SettingsIntent.SetActiveVoiceProfileVoice("fr-FR-HenriNeural"))
        dispatcher.scheduler.advanceUntilIdle()

        val prefs = preferencesRepository.get()
        val activeProfile = voiceProfileRepository.getById(prefs.activeVoiceProfileId!!)
        assertEquals("fr-FR-HenriNeural", activeProfile!!.voice)
    }

    @Test
    fun `desactiver le preset Mode sombre revient aux valeurs par defaut`() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val vm = viewModel(preferencesRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetDarkModePreset(true))
        dispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(SettingsIntent.SetDarkModePreset(false))
        dispatcher.scheduler.advanceUntilIdle()

        val prefs = preferencesRepository.get()
        assertEquals(AppTheme.SYSTEM, prefs.appTheme)
        assertEquals(ReadingTheme.DEFAULT.id, prefs.theme)
    }

    @Test
    fun `le preset Accessibilite applique tous ses reglages y compris le mode Liste`() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val vm = viewModel(preferencesRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetAccessibilityPreset(true))
        dispatcher.scheduler.advanceUntilIdle()

        val prefs = preferencesRepository.get()
        assertEquals(FontFamily.OPEN_DYSLEXIC, prefs.fontFamily)
        assertEquals(24, prefs.fontSize)
        assertEquals(ReadingTheme.PAPIER_CLAIR.id, prefs.theme)
        assertEquals(true, prefs.reduceMotion)
        assertEquals("LIST", prefs.libraryLayoutMode)
    }

    @Test
    fun `desactiver le preset Accessibilite defait tous les reglages qu il a poses`() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val vm = viewModel(preferencesRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetAccessibilityPreset(true))
        dispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(SettingsIntent.SetAccessibilityPreset(false))
        dispatcher.scheduler.advanceUntilIdle()

        val prefs = preferencesRepository.get()
        assertEquals(FontFamily.DEFAULT, prefs.fontFamily)
        assertEquals(18, prefs.fontSize)
        assertEquals(ReadingTheme.DEFAULT.id, prefs.theme)
        assertEquals(false, prefs.reduceMotion)
        assertEquals("GRID_DETAILED", prefs.libraryLayoutMode)
    }

    @Test
    fun `le theme systeme et le theme de lecture sont deux valeurs independantes`() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val vm = viewModel(preferencesRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetAppTheme(AppTheme.DARK))
        dispatcher.scheduler.advanceUntilIdle()

        val prefs = preferencesRepository.get()
        assertEquals(AppTheme.DARK, prefs.appTheme)
        assertEquals(ReadingTheme.DEFAULT.id, prefs.theme) // inchangé
    }

    @Test
    fun `la vitesse d elocution ecrit dans le profil vocal actif, meme cible que le panneau lecteur`() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val voiceProfileRepository = FakeVoiceProfileRepository()
        val profile = VoiceProfile(id = "vp-1", engine = TtsEngineId.SHERPA_ONNX, voice = "fr-1", language = "fr")
        voiceProfileRepository.save(profile)
        preferencesRepository.update(preferencesRepository.get().copy(activeVoiceProfileId = "vp-1"))
        val vm = viewModel(preferencesRepository, voiceProfileRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetVoiceSpeed(1.5f))
        dispatcher.scheduler.advanceUntilIdle()

        val updated = voiceProfileRepository.getById("vp-1")
        assertEquals(1.5f, updated?.speed)
        // La vitesse reste dans VoiceProfile, pas un second emplacement dans UserPreferences.
        assertNotEquals(null, updated)
    }

    @Test
    fun `l intervalle de repos oculaire reste modifiable independamment du rappel`() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val vm = viewModel(preferencesRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetEyeRestReminderEnabled(false))
        vm.onIntent(SettingsIntent.SetEyeRestReminderIntervalMinutes(30))
        dispatcher.scheduler.advanceUntilIdle()

        val prefs = preferencesRepository.get()
        assertEquals(false, prefs.eyeRestReminderEnabled)
        assertEquals(30, prefs.eyeRestReminderIntervalMinutes)
    }

    @Test
    fun `l objectif quotidien se met a jour`() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val vm = viewModel(preferencesRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetDailyGoalMinutes(45))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(45, preferencesRepository.get().dailyGoalMinutes)
    }

    @Test
    fun `reinitialiser les parametres revient aux valeurs par defaut`() = runTest {
        val preferencesRepository = FakePreferencesRepository()
        val vm = viewModel(preferencesRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.SetDailyGoalMinutes(90))
        vm.onIntent(SettingsIntent.SetAppTheme(AppTheme.DARK))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.ResetPreferences)
        dispatcher.scheduler.advanceUntilIdle()

        val prefs = preferencesRepository.get()
        assertEquals(30, prefs.dailyGoalMinutes)
        assertEquals(AppTheme.SYSTEM, prefs.appTheme)
    }

    @Test
    fun `ajouter une regle de prononciation l ajoute a l etat`() = runTest {
        val ruleRepository = FakePronunciationRuleRepository()
        val vm = viewModel(pronunciationRuleRepository = ruleRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.SavePronunciationRule(id = null, originalText = "Dr.", replacementText = "Docteur", isRegex = false))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.pronunciationRules.size)
        val rule = vm.state.value.pronunciationRules.single()
        assertEquals("Dr.", rule.originalText)
        assertEquals("Docteur", rule.replacementText)
        assertTrue(rule.isEnabled) // valeur par défaut sur création
    }

    @Test
    fun `editer une regle preserve son etat isEnabled existant`() = runTest {
        val ruleRepository = FakePronunciationRuleRepository()
        ruleRepository.save(
            PronunciationRule(id = "r1", originalText = "Dr.", replacementText = "Docteur", isEnabled = false),
        )
        val vm = viewModel(pronunciationRuleRepository = ruleRepository)
        dispatcher.scheduler.advanceUntilIdle()

        // L'édition change le texte, mais ne doit pas réactiver une règle
        // désactivée par l'utilisateur — reconstruire un PronunciationRule
        // par défaut l'aurait fait silencieusement.
        vm.onIntent(SettingsIntent.SavePronunciationRule(id = "r1", originalText = "Dr", replacementText = "Docteur", isRegex = false))
        dispatcher.scheduler.advanceUntilIdle()

        val rule = vm.state.value.pronunciationRules.single { it.id == "r1" }
        assertEquals("Dr", rule.originalText)
        assertEquals(false, rule.isEnabled)
    }

    @Test
    fun `basculer une regle inverse son etat isEnabled`() = runTest {
        val ruleRepository = FakePronunciationRuleRepository()
        val rule = PronunciationRule(id = "r1", originalText = "Dr.", replacementText = "Docteur", isEnabled = true)
        ruleRepository.save(rule)
        val vm = viewModel(pronunciationRuleRepository = ruleRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.TogglePronunciationRule(rule))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, vm.state.value.pronunciationRules.single { it.id == "r1" }.isEnabled)
    }

    @Test
    fun `supprimer une regle la retire de l etat`() = runTest {
        val ruleRepository = FakePronunciationRuleRepository()
        ruleRepository.save(PronunciationRule(id = "r1", originalText = "Dr.", replacementText = "Docteur"))
        val vm = viewModel(pronunciationRuleRepository = ruleRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.DeletePronunciationRule("r1"))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.pronunciationRules.isEmpty())
    }

    @Test
    fun `une regle avec un texte d origine vide n est pas enregistree`() = runTest {
        val ruleRepository = FakePronunciationRuleRepository()
        val vm = viewModel(pronunciationRuleRepository = ruleRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.SavePronunciationRule(id = null, originalText = "   ", replacementText = "x", isRegex = false))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.pronunciationRules.isEmpty())
    }

    @Test
    fun `la taille du cache est calculee au demarrage puis apres vidage`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tempFile = java.io.File(context.cacheDir, "test-cache-file.bin")
        tempFile.writeBytes(ByteArray(2048))

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue("la taille doit refléter le fichier réel", vm.state.value.cacheSizeBytes >= 2048)

        vm.onIntent(SettingsIntent.ClearCache)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0L, vm.state.value.cacheSizeBytes)
        assertEquals(false, tempFile.exists())
    }

    // ───── Lot 10, Tâche 10.3 — point de besoin réel du téléchargement de voix ─────

    @Test
    fun `StartVoiceDownload reflete la progression jusqu a la fin`() = runTest {
        val downloadService = FakeVoiceModelDownloadService(
            listOf(
                com.inktone.domain.service.VoiceDownloadProgress.InProgress(50, 100),
                com.inktone.domain.service.VoiceDownloadProgress.Complete,
            ),
            installed = false,
        )
        val vm = viewModel(voiceModelDownloadService = downloadService)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, vm.state.value.voiceDownloadProgress)

        vm.onIntent(SettingsIntent.StartVoiceDownload)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(com.inktone.domain.service.VoiceDownloadProgress.Complete, vm.state.value.voiceDownloadProgress)
    }

    @Test
    fun `CancelVoiceDownload interrompt le telechargement en cours et efface la progression`() = runTest {
        // Flow qui ne termine jamais seul (suspend indefiniment apres le
        // premier evenement) : seule une annulation explicite peut
        // l'arreter -- prouve que CancelVoiceDownload annule reellement
        // la coroutine, pas seulement qu'elle ignore les evenements
        // suivants d'un flow deja termine.
        val neverEndingDownload = object : com.inktone.domain.service.VoiceModelDownloadService {
            override fun downloadDefaultVoiceModel() = kotlinx.coroutines.flow.flow {
                emit(com.inktone.domain.service.VoiceDownloadProgress.InProgress(50, 100))
                kotlinx.coroutines.awaitCancellation()
            }
            override fun isDefaultVoiceInstalled(): Boolean = false
        }
        val vm = viewModel(voiceModelDownloadService = neverEndingDownload)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.StartVoiceDownload)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            com.inktone.domain.service.VoiceDownloadProgress.InProgress(50, 100),
            vm.state.value.voiceDownloadProgress,
        )

        vm.onIntent(SettingsIntent.CancelVoiceDownload)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, vm.state.value.voiceDownloadProgress)
    }

    // ───── Audit v1.0.0 (B1) — « Écouter un extrait » ré-implémenté ─────

    @Test
    fun `PlayPreview synthetise joue puis s arrete naturellement a la fin du segment`() = runTest {
        val audioPlayer = FakeAudioPlayer()
        val vm = viewModel(audioPlayer = audioPlayer)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.PlayPreview)
        // runCurrent : la synthèse et le play ont eu lieu, le délai de fin
        // n'est pas encore écoulé → l'extrait « joue ».
        dispatcher.scheduler.runCurrent()
        assertEquals(1, audioPlayer.enqueued.size)
        assertEquals(1, audioPlayer.playCount)
        assertEquals(true, vm.state.value.isPreviewing)

        // advanceUntilIdle : la durée du segment (0 ms chez le fake) + marge
        // est écoulée → arrêt propre, retour à l'état initial.
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, audioPlayer.stopCount)
        assertEquals(false, vm.state.value.isPreviewing)
        assertEquals(null, vm.state.value.previewError)
    }

    @Test
    fun `PlayPreview une seconde fois arrete la lecture`() = runTest {
        val audioPlayer = FakeAudioPlayer()
        val vm = viewModel(audioPlayer = audioPlayer)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.PlayPreview)
        dispatcher.scheduler.runCurrent()
        assertEquals(true, vm.state.value.isPreviewing)

        vm.onIntent(SettingsIntent.PlayPreview)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, audioPlayer.stopCount)
        assertEquals(false, vm.state.value.isPreviewing)
    }

    @Test
    fun `PlayPreview en echec de synthese affiche une erreur au lieu d echouer en silence`() = runTest {
        val failingEngine = object : TtsEngine {
            override val id = TtsEngineId.ANDROID_NATIVE
            override val capabilities = com.inktone.domain.service.TtsCapabilities(
                offline = true, wordTimestamps = false, sentenceTimestamps = false,
                languages = listOf("fr"), streamingSynthesis = false,
                speedControl = true, pitchControl = true, modelSizeMb = 0, license = "test",
            )
            override suspend fun synthesize(
                sentence: com.inktone.domain.model.Sentence,
                voiceProfile: VoiceProfile,
            ): AudioSegment = throw IllegalStateException("moteur indisponible")
            override fun observePlaybackEvents(): kotlinx.coroutines.flow.Flow<com.inktone.domain.service.PlaybackEvent> =
                kotlinx.coroutines.flow.emptyFlow()
        }
        val vm = viewModel(ttsEngine = failingEngine)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(SettingsIntent.PlayPreview)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, vm.state.value.isPreviewing)
        assertEquals("moteur indisponible", vm.state.value.previewError)
    }

    /** Faux lecteur gapless — enregistre les appels, aucun matériel audio. */
    private class FakeAudioPlayer : AudioPlayer {
        val enqueued = mutableListOf<AudioSegment>()
        var playCount = 0
        var stopCount = 0
        private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
        private val _playbackPosition = MutableStateFlow(PlaybackPosition(0, 16000, null, false))
        override val state: StateFlow<PlayerState> = _state
        override val playbackPosition: StateFlow<PlaybackPosition> = _playbackPosition
        override var sampleRate: Int = 16000
        override val pendingCount: Int get() = enqueued.size

        override fun enqueue(segment: AudioSegment) { enqueued.add(segment) }
        override fun play() { playCount++; _state.value = PlayerState.Playing }
        override fun pause() { _state.value = PlayerState.Paused }
        override fun resume() { _state.value = PlayerState.Playing }
        override fun stop() { stopCount++; _state.value = PlayerState.Stopped }
        override fun release() {}
        override fun setVolume(volume: Float) {}
    }
}
