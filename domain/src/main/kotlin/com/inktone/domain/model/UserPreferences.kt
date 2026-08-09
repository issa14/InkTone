package com.inktone.domain.model

/**
 * Préférences globales — une seule instance par application. Toute
 * surcharge par publication vit dans [ReadingOverrides] et prime sur ces
 * valeurs (Blueprint §3.3).
 */
data class UserPreferences(
    // Lot 9 — id d'un ReadingTheme (intégré ou personnalisé), plus un enum
    // fermé. Défaut : Papier Clair (voir ReadingTheme.DEFAULT).
    val theme: String = ReadingTheme.DEFAULT.id,
    val fontSize: Int = 18,
    val defaultTtsEngine: TtsEngineId = TtsEngineId.SHERPA_ONNX,
    val crashReportingEnabled: Boolean = false,
    val language: String = "fr",
    val fontFamily: FontFamily = FontFamily.DEFAULT,
    val reduceMotion: Boolean = false,
    // Tache 9bis.1.2 — s'applique uniquement au chrome de l'app
    // (InkToneTheme), jamais aux ReadingTheme de lecture.
    val dynamicColorEnabled: Boolean = true,
    // Lot 6 — thème système de l'app (Système/Clair/Sombre), distinct du thème de lecture.
    val appTheme: AppTheme = AppTheme.SYSTEM,
    // Tache 9bis.3.6 — reglage seul pour l'instant, ReaderScreen ne
    // consomme pas encore ce champ (voir TODO sur ReadingRuler.kt).
    val readingRulerEnabled: Boolean = false,
    // Tache 1.4 (Partie 1) — objectif de lecture quotidien, valeur par
    // defaut alignee sur la cible UX (Lot 7, tache 7.1), modifiable dans
    // les reglages (Partie 4).
    val dailyGoalMinutes: Int = 30,
    // A.5 — profil vocal actif. null = utiliser la voix par defaut
    // correspondant au moteur TTS selectionne.
    val activeVoiceProfileId: String? = null,
    // B.1 — mode de lecture (SCROLL ou PAGED), persisté pour reprise
    val readingMode: String = "SCROLL",
    // D.3 — gain audio (1.0× = normal, jusqu'à 4.0×)
    val audioGain: Float = 1.0f,
    // D.3 — respecter le fontScale système Android au lieu du fontSize interne
    val useSystemFontScale: Boolean = false,
    // 3d.2 — multiplicateur d'interligne (1.0 = pas d'espacement supplémentaire),
    // combiné à fontSize pour rester en sp cohérent avec PaginationStyleKey.
    val lineHeightMultiplier: Float = 1.4f,
    // 3d.3 — luminosité de l'écran de lecture seulement (WindowManager.LayoutParams.screenBrightness).
    // null = valeur système, sinon 0.01..1.0.
    val readerBrightness: Float? = null,
    // 3d.5 — rappel de repos oculaire, indépendant du minuteur de sommeil TTS.
    val eyeRestReminderEnabled: Boolean = true,
    val eyeRestReminderIntervalMinutes: Int = 60,
    // Lot 6 — disposition de la bibliothèque ("LIST" ou "GRID_COVERS"), persistée pour
    // que le préréglage d'accessibilité (Tâche 8.4) puisse la piloter. Stockée en String
    // (comme readingMode) : LibraryLayoutMode vit dans feature/library, hors de portée du domaine.
    val libraryLayoutMode: String = "GRID_COVERS",
    // Lot 10 — indicateur "onboarding vu", pilote le startDestination
    // (Onboarding au premier lancement, Bibliothèque ensuite).
    val hasSeenOnboarding: Boolean = false,
) {
    init {
        require(fontSize > 0) { "fontSize doit être strictement positif" }
        require(lineHeightMultiplier > 0f) { "lineHeightMultiplier doit être strictement positif" }
        require(readerBrightness == null || readerBrightness in 0.01f..1.0f) {
            "readerBrightness doit être compris entre 0.01 et 1.0, ou null"
        }
        require(eyeRestReminderIntervalMinutes > 0) { "eyeRestReminderIntervalMinutes doit être strictement positif" }
    }
}

enum class FontFamily { DEFAULT, OPEN_DYSLEXIC, SERIF, SANS_SERIF }
