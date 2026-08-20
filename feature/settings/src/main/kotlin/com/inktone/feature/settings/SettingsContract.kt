package com.inktone.feature.settings

import com.inktone.domain.model.AppTheme
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.PronunciationRule
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.UserPreferences
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.VoiceDownloadProgress

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    // A.5 — profils vocaux disponibles pour le picker
    val voiceProfiles: List<VoiceProfile> = emptyList(),
    // Lot 6, Palier B — carte Prononciation inline (remplace l'écran séparé
    // dans les Réglages ; le lien depuis le panneau Voix du lecteur reste
    // sur PronunciationRulesRoute, préservé séparément).
    val pronunciationRules: List<PronunciationRule> = emptyList(),
    // Lot 6, Palier B — carte Données. Calculée localement (Context.cacheDir),
    // aucun module externe requis.
    val cacheSizeBytes: Long = 0L,
    val isClearingCache: Boolean = false,
    // Lot 10 — point de besoin réel du téléchargement de voix neuronale
    // (Tâche 10.3), retiré de l'onboarding : accessible ici, dans la
    // carte Lecture, à côté du sélecteur de voix.
    val voiceDownloadProgress: VoiceDownloadProgress? = null,
    // Audit v1.0.0 (B1) — lecture d'extrait : vrai pendant la synthèse +
    // lecture de la phrase d'exemple ; `previewError` porte un échec
    // éventuel (jamais un échec silencieux).
    val isPreviewing: Boolean = false,
    val previewError: String? = null,
)

/**
 * Lot 6, Palier B — dossier des modèles TTS. Chemin en lecture seule :
 * `infrastructure/tts` (`SherpaOnnxModelPaths`) fixe ce chemin en dur,
 * aucune capacité de déplacement n'existe — signalé plutôt que masqué
 * par un contrôle qui suggérerait le contraire (voir tâche 6.7).
 */
data class ModelsFolderInfo(
    val path: String,
    val isEditable: Boolean = false,
)

/**
 * Lot 6, Palier B — résultat d'une opération de sauvegarde/restauration,
 * à afficher explicitement (jamais jeté — c'est le défaut corrigé au lot 5
 * pour l'import de livres). `BackupManager` (module `data`) n'est pas
 * visible depuis `feature/settings` (Blueprint §12.4) : le module `app`
 * traduit `ImportBackupResult` vers ce type avant de le faire redescendre.
 */
sealed interface DataOperationResult {
    data object ExportSuccess : DataOperationResult
    data class ExportFailed(val message: String) : DataOperationResult
    data class ImportSuccess(val restored: Int, val skippedOrphans: Int) : DataOperationResult
    data class ImportFailed(val message: String) : DataOperationResult
}

sealed interface SettingsIntent {
    // Lot 9 — id d'un ReadingTheme (intégré ou personnalisé), plus un enum fermé.
    data class SetTheme(val themeId: String) : SettingsIntent
    data class SetFontSize(val fontSize: Int) : SettingsIntent
    data class SetFontFamily(val fontFamily: FontFamily) : SettingsIntent
    data class SetDefaultTtsEngine(val engine: TtsEngineId) : SettingsIntent
    data class SetActiveVoiceProfileVoice(val voice: String) : SettingsIntent
    data class SetLanguage(val language: String) : SettingsIntent
    data class SetCrashReportingEnabled(val enabled: Boolean) : SettingsIntent
    data class SetReduceMotion(val enabled: Boolean) : SettingsIntent
    data class SetDynamicColorEnabled(val enabled: Boolean) : SettingsIntent
    data class SetReadingRulerEnabled(val enabled: Boolean) : SettingsIntent
    // A.5 — selection du profil vocal actif
    data class SetActiveVoiceProfile(val profileId: String?) : SettingsIntent
    // D.3 — gain audio (1.0× à 4.0×)
    data class SetAudioGain(val gain: Float) : SettingsIntent
    // D.3 — respecter le fontScale système
    data class SetUseSystemFontScale(val enabled: Boolean) : SettingsIntent
    // Lot 6 — vitesse d'élocution — écrit dans VoiceProfile.speed (même cible que le panneau lecteur)
    data class SetVoiceSpeed(val speed: Float) : SettingsIntent
    // Lot 6 — intonation/pitch — écrit dans VoiceProfile.pitch
    data class SetVoicePitch(val pitch: Float) : SettingsIntent
    // Lot 6 — thème système de l'app (distinct du thème de lecture ReadingTheme)
    data class SetAppTheme(val appTheme: AppTheme) : SettingsIntent
    // Lot 6 — objectif quotidien (10–120 min)
    data class SetDailyGoalMinutes(val minutes: Int) : SettingsIntent
    // Lot 6 — rappel repos oculaire
    data class SetEyeRestReminderEnabled(val enabled: Boolean) : SettingsIntent
    // Lot 6 — intervalle repos oculaire (par pas de 15 min)
    data class SetEyeRestReminderIntervalMinutes(val minutes: Int) : SettingsIntent
    // Lot 6 — présets rapides (toggle réversible, désapplication vers les valeurs par défaut)
    data class SetDarkModePreset(val enabled: Boolean) : SettingsIntent
    data class SetAccessibilityPreset(val enabled: Boolean) : SettingsIntent
    // Lot 6 — écouter un extrait. Audit v1.0.0 (AUDIT_CONSOLIDATION_V1.md,
    // B1) : RÉ-IMPLÉMENTÉ — le bouton avait été retiré parce que l'intent
    // était un no-op ; il est désormais câblé sur une vraie synthèse +
    // lecture d'une phrase d'exemple (SettingsViewModel.togglePreview).
    object PlayPreview : SettingsIntent
    // Lot 10 — téléchargement de la voix neuronale par défaut, point de
    // besoin réel après le retrait de l'étape d'onboarding (Tâche 10.3).
    object StartVoiceDownload : SettingsIntent
    // Retour Issa (vérification device) : le téléchargement doit pouvoir
    // être annulé en cours de route, pas seulement lancé.
    object CancelVoiceDownload : SettingsIntent
    // Conservé pour compatibilité — remplacé fonctionnellement par SetAccessibilityPreset
    object ApplyAccessibilityPreset : SettingsIntent

    // Lot 6, Palier B — carte Données
    /** Recalcule la taille réelle du cache (Context.cacheDir) — appelé à l'ouverture de la carte. */
    object RefreshCacheSize : SettingsIntent
    /** Vide effectivement le cache — n'est envoyé qu'après confirmation explicite côté UI. */
    object ClearCache : SettingsIntent
    /** Réinitialise UserPreferences aux valeurs par défaut — n'est envoyé qu'après confirmation explicite côté UI. */
    object ResetPreferences : SettingsIntent

    // Lot 6, Palier B — carte Prononciation inline
    /** `id` null = nouvelle règle, non-null = édition d'une règle existante. */
    data class SavePronunciationRule(
        val id: String?,
        val originalText: String,
        val replacementText: String,
        val isRegex: Boolean,
    ) : SettingsIntent
    data class TogglePronunciationRule(val rule: PronunciationRule) : SettingsIntent
    data class DeletePronunciationRule(val id: String) : SettingsIntent
}
