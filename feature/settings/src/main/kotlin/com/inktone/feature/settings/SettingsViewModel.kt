package com.inktone.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.AppTheme
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.PronunciationRule
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.repository.PreferencesRepository
import com.inktone.domain.repository.PronunciationRuleRepository
import com.inktone.domain.repository.VoiceProfileRepository
import com.inktone.domain.service.VoiceModelDownloadService
import com.inktone.domain.usecase.ApplyAccessibilityPresetUseCase
import com.inktone.domain.usecase.GetVoiceProfilesUseCase
import com.inktone.feature.settings.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * MVI standard (Tache 8.1) — chaque intent appelle
 * `preferencesRepository.update(current.copy(...))`. Rien de nouveau
 * cote domaine ici, uniquement le branchement UI (Phase 2, Tache 1.6/2.6
 * deja fonctionnel).
 *
 * Lot 6 — etendu avec les intents : presets reversibles, vitesse/pitch
 * de voix (meme cible VoiceProfile que le panneau lecteur), theme systeme,
 * objectif quotidien, rappel oculaire.
 *
 * Lot 6, Palier B — carte Données (cache, réinitialisation) et carte
 * Prononciation inline. `Context` (cache uniquement, `cacheDir` est une API
 * plateforme, pas un type `domain`/`data`) et `PronunciationRuleRepository`
 * (domaine, déjà accessible depuis `feature/settings`) sont injectés
 * directement ici — l'export/import de sauvegarde reste hors de ce
 * ViewModel : `BackupManager` vit dans `:data`, invisible depuis
 * `feature/settings` (Blueprint §12.4), câblé depuis le module `app`.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val applyAccessibilityPreset: ApplyAccessibilityPresetUseCase,
    private val getVoiceProfiles: GetVoiceProfilesUseCase,
    private val voiceProfileRepository: VoiceProfileRepository,
    private val pronunciationRuleRepository: PronunciationRuleRepository,
    private val voiceModelDownloadService: VoiceModelDownloadService,
    @ApplicationContext private val appContext: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.observe().collect { preferences ->
                _state.value = _state.value.copy(preferences = preferences)
            }
        }
        // A.5 — charge les profils vocaux une fois au démarrage
        viewModelScope.launch {
            val profiles = getVoiceProfiles()
            _state.value = _state.value.copy(voiceProfiles = profiles)
        }
        // Lot 6, Palier B — carte Prononciation inline
        viewModelScope.launch {
            pronunciationRuleRepository.observeAll().collect { rules ->
                _state.value = _state.value.copy(pronunciationRules = rules)
            }
        }
        // Lot 6, Palier B — taille réelle du cache à l'ouverture de l'écran
        viewModelScope.launch { refreshCacheSize() }
    }

    fun onIntent(intent: SettingsIntent) {
        viewModelScope.launch {
            // Lu ici, pas capture de _state.value.preferences avant le launch :
            // deux intents lances en rafale (ex. toggle + intervalle) verraient
            // sinon le second ecraser le premier avec un instantane perime, le
            // temps que preferencesRepository.observe() ne fasse la ronde
            // jusqu'a _state.value (Tache 6.6, regression trouvee par test).
            val current = preferencesRepository.get()
            when (intent) {
                is SettingsIntent.SetTheme -> preferencesRepository.update(current.copy(theme = intent.themeId))
                is SettingsIntent.SetFontSize -> preferencesRepository.update(current.copy(fontSize = intent.fontSize))
                is SettingsIntent.SetFontFamily -> preferencesRepository.update(current.copy(fontFamily = intent.fontFamily))
                is SettingsIntent.SetDefaultTtsEngine ->
                    preferencesRepository.update(current.copy(defaultTtsEngine = intent.engine))
                is SettingsIntent.SetLanguage -> preferencesRepository.update(current.copy(language = intent.language))
                is SettingsIntent.SetCrashReportingEnabled ->
                    preferencesRepository.update(current.copy(crashReportingEnabled = intent.enabled))
                is SettingsIntent.SetReduceMotion -> preferencesRepository.update(current.copy(reduceMotion = intent.enabled))
                is SettingsIntent.SetDynamicColorEnabled ->
                    preferencesRepository.update(current.copy(dynamicColorEnabled = intent.enabled))
                is SettingsIntent.SetReadingRulerEnabled ->
                    preferencesRepository.update(current.copy(readingRulerEnabled = intent.enabled))
                is SettingsIntent.SetActiveVoiceProfile ->
                    preferencesRepository.update(current.copy(activeVoiceProfileId = intent.profileId))
                is SettingsIntent.SetAudioGain ->
                    preferencesRepository.update(current.copy(audioGain = intent.gain))
                is SettingsIntent.SetUseSystemFontScale ->
                    preferencesRepository.update(current.copy(useSystemFontScale = intent.enabled))
                // Lot 6 — thème système (ne touche pas ReadingTheme)
                is SettingsIntent.SetAppTheme ->
                    preferencesRepository.update(current.copy(appTheme = intent.appTheme))
                // Lot 6 — objectif quotidien et bien-être
                is SettingsIntent.SetDailyGoalMinutes ->
                    preferencesRepository.update(current.copy(dailyGoalMinutes = intent.minutes))
                is SettingsIntent.SetEyeRestReminderEnabled ->
                    preferencesRepository.update(current.copy(eyeRestReminderEnabled = intent.enabled))
                is SettingsIntent.SetEyeRestReminderIntervalMinutes ->
                    preferencesRepository.update(current.copy(eyeRestReminderIntervalMinutes = intent.minutes))
                // Lot 6 — vitesse et pitch : même cible VoiceProfile que setTtsSpeed() dans ReaderViewModel
                is SettingsIntent.SetVoiceSpeed -> updateActiveVoiceProfile(current) { it.copy(speed = intent.speed) }
                is SettingsIntent.SetVoicePitch -> updateActiveVoiceProfile(current) { it.copy(pitch = intent.pitch) }
                // Lot 6 — présets réversibles (désapplication vers les valeurs par défaut)
                is SettingsIntent.SetDarkModePreset -> applyDarkModePreset(intent.enabled, current)
                is SettingsIntent.SetAccessibilityPreset -> applyAccessibilityPresetToggle(intent.enabled, current)
                // Compat — remplacé par SetAccessibilityPreset mais conservé pour les tests existants
                is SettingsIntent.ApplyAccessibilityPreset -> applyAccessibilityPreset()
                // Lot 6 — écouter un extrait : signalé non branché (voir commentaire)
                // PlayPreview nécessite une UseCase dédiée hors scope SettingsViewModel.
                is SettingsIntent.PlayPreview -> { /* Signalé : non branché au TTS — UseCase dédiée à créer */ }
                // Lot 10 — point de besoin réel du téléchargement de voix,
                // retiré de l'onboarding (Tâche 10.3). Même mécanisme que
                // l'ancien OnboardingViewModel.startVoiceDownload.
                is SettingsIntent.StartVoiceDownload -> startVoiceDownload()

                // Lot 6, Palier B — carte Données
                is SettingsIntent.RefreshCacheSize -> refreshCacheSize()
                is SettingsIntent.ClearCache -> clearCache()
                is SettingsIntent.ResetPreferences -> preferencesRepository.update(UserPreferences())

                // Lot 6, Palier B — carte Prononciation inline
                is SettingsIntent.SavePronunciationRule -> savePronunciationRule(intent)
                is SettingsIntent.TogglePronunciationRule ->
                    pronunciationRuleRepository.save(intent.rule.copy(isEnabled = !intent.rule.isEnabled))
                is SettingsIntent.DeletePronunciationRule -> pronunciationRuleRepository.delete(intent.id)
            }
        }
    }

    private suspend fun startVoiceDownload() {
        voiceModelDownloadService.downloadDefaultVoiceModel().collect { progress ->
            _state.value = _state.value.copy(voiceDownloadProgress = progress)
        }
    }

    /** Lot 6, Palier B — taille réelle occupée par `Context.cacheDir`, pas une estimation. */
    private suspend fun refreshCacheSize() {
        val size = withContext(ioDispatcher) {
            appContext.cacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        }
        _state.value = _state.value.copy(cacheSizeBytes = size)
    }

    /** Lot 6, Palier B — n'est appelé qu'après confirmation côté UI (aucune action destructive sans elle). */
    private suspend fun clearCache() {
        _state.value = _state.value.copy(isClearingCache = true)
        withContext(ioDispatcher) {
            appContext.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        }
        refreshCacheSize()
        _state.value = _state.value.copy(isClearingCache = false)
    }

    /**
     * Lot 6, Palier B — ajout ou édition d'une règle. Sur édition
     * (`id` non-null), l'état `isEnabled` de la règle existante est
     * préservé — reconstruire un `PronunciationRule` par défaut aurait
     * silencieusement réactivé une règle désactivée par l'utilisateur.
     */
    private suspend fun savePronunciationRule(intent: SettingsIntent.SavePronunciationRule) {
        if (intent.originalText.isBlank()) return
        val existing = intent.id?.let { id -> _state.value.pronunciationRules.find { it.id == id } }
        pronunciationRuleRepository.save(
            PronunciationRule(
                id = intent.id ?: UUID.randomUUID().toString(),
                originalText = intent.originalText,
                replacementText = intent.replacementText,
                isRegex = intent.isRegex,
                isEnabled = existing?.isEnabled ?: true,
            ),
        )
    }

    /**
     * Lot 6 — met à jour VoiceProfile.speed ou .pitch pour le profil actif.
     * Si aucun profil n'est actif, crée un profil de base "vp-settings-default"
     * et l'active. Même cible que setTtsSpeed() dans ReaderViewModel : un seul
     * champ dans la base, pas un second emplacement.
     */
    private suspend fun updateActiveVoiceProfile(
        prefs: UserPreferences,
        update: (VoiceProfile) -> VoiceProfile,
    ) {
        val existingProfile = prefs.activeVoiceProfileId?.let { voiceProfileRepository.getById(it) }
        val baseProfile = existingProfile ?: VoiceProfile(
            id = "vp-settings-default",
            engine = prefs.defaultTtsEngine,
            voice = "default",
            language = prefs.language,
        )
        val updated = update(baseProfile)
        voiceProfileRepository.save(updated)
        if (prefs.activeVoiceProfileId != updated.id) {
            preferencesRepository.update(prefs.copy(activeVoiceProfileId = updated.id))
        }
        // Recharger les profils pour mettre à jour l'UI
        _state.value = _state.value.copy(voiceProfiles = getVoiceProfiles())
    }

    /**
     * Lot 6/9 — Préset Mode sombre.
     * ON : ReadingTheme.OBSIDIENNE (Lot 9 — id du thème intégré remplaçant
     * l'ancien `ReadingTheme.DARK`) + AppTheme.DARK.
     * OFF : retour aux valeurs par défaut (approche simple et prévisible plutôt
     * que mémoriser l'état antérieur, qui surprend si l'utilisateur a modifié
     * des réglages entre-temps).
     */
    private suspend fun applyDarkModePreset(enabled: Boolean, current: UserPreferences) {
        if (enabled) {
            preferencesRepository.update(current.copy(theme = ReadingTheme.OBSIDIENNE.id, appTheme = AppTheme.DARK))
        } else {
            val defaults = UserPreferences()
            preferencesRepository.update(current.copy(theme = defaults.theme, appTheme = AppTheme.SYSTEM))
        }
    }

    /**
     * Lot 6/9 — Préset Accessibilité (toggle réversible).
     * ON : OpenDyslexic + 24sp + ReadingTheme.PAPIER_CLAIR (Lot 9 — id du
     * thème intégré remplaçant l'ancien `ReadingTheme.LIGHT`) + reduceMotion
     * + readingRuler + bascule de la bibliothèque en mode Liste (décision
     * actée au lot 2b — cohérent avec l'esprit du préréglage :
     * reconnaissance d'un livre par le texte plutôt que par la seule
     * couverture).
     * OFF : retour aux valeurs par défaut — plus simple et prévisible.
     */
    private suspend fun applyAccessibilityPresetToggle(enabled: Boolean, current: UserPreferences) {
        if (enabled) {
            preferencesRepository.update(
                current.copy(
                    fontSize = 24,
                    theme = ReadingTheme.PAPIER_CLAIR.id,
                    fontFamily = FontFamily.OPEN_DYSLEXIC,
                    reduceMotion = true,
                    readingRulerEnabled = true,
                    libraryLayoutMode = "LIST",
                ),
            )
        } else {
            val defaults = UserPreferences()
            preferencesRepository.update(
                current.copy(
                    fontSize = defaults.fontSize,
                    theme = defaults.theme,
                    fontFamily = defaults.fontFamily,
                    reduceMotion = defaults.reduceMotion,
                    readingRulerEnabled = defaults.readingRulerEnabled,
                    libraryLayoutMode = defaults.libraryLayoutMode,
                ),
            )
        }
    }
}
