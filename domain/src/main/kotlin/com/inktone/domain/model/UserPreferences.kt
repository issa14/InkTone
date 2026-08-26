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
    // Lot 20 — moteur par défaut restauré à Sherpa-ONNX (voix neuronale
    // upmc-medium, désormais réellement installable : extraction +
    // modèle CTC câblés). Le repli automatique sur la voix du système
    // (FallbackTtsEngine) garantit que le premier usage fonctionne même
    // sans modèle installé.
    val defaultTtsEngine: TtsEngineId = TtsEngineId.SHERPA_ONNX,
    val crashReportingEnabled: Boolean = false,
    val language: String = "fr",
    val fontFamily: FontFamily = FontFamily.DEFAULT,
    val reduceMotion: Boolean = false,
    // Tache 9bis.1.2 — s'applique uniquement au chrome de l'app
    // (InkToneTheme), jamais aux ReadingTheme de lecture.
    val dynamicColorEnabled: Boolean = false,
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
    // Lot 6 — disposition de la bibliothèque ("LIST", "GRID_COVERS" ou
    // "GRID_DETAILED"), persistée pour que le préréglage d'accessibilité
    // (Tâche 8.4) puisse la piloter. Stockée en String (comme readingMode) :
    // LibraryLayoutMode vit dans feature/library, hors de portée du domaine.
    val libraryLayoutMode: String = "GRID_DETAILED",
    // Lot 10 — indicateur "onboarding vu", pilote le startDestination
    // (Onboarding au premier lancement, Bibliothèque ensuite).
    val hasSeenOnboarding: Boolean = false,
    // Lot 10 — retour Issa (vérification device) : le retrait de l'étape
    // d'onboarding VoiceDownload avait laissé aucun point de proposition
    // proactive au premier usage réel du TTS (la Réglages seule exige une
    // découverte active). true dès la première proposition faite, jamais
    // reproposé ensuite — ReaderViewModel.playCurrentSentence.
    val hasPromptedVoiceDownload: Boolean = false,
    // Lot 11, tâche 11.2 — identité d'appareil stable, générée au premier
    // accès (voir DeviceIdentityRepository) : sert à la fois à la flotte
    // d'appareils (palier C) et à la détection de conflits (palier D).
    val deviceId: String? = null,
    val deviceDisplayName: String? = null,
    // Lot 11, tâche 11.2 — compte de synchronisation unique (exclusivité
    // mutuelle Drive/WebDAV) : `syncProvider == null` matérialise l'état
    // Unconfigured. `syncLastAutoSyncFailed` pilote la bannière persistante
    // du Dashboard (une synchro automatique en échec, palier C).
    val syncProvider: String? = null,
    val syncAccountLabel: String? = null,
    val syncLinkedAt: Long? = null,
    val syncLastSyncAt: Long? = null,
    val syncLastAutoSyncFailed: Boolean = false,
    // Lot 11, tâche 11.8 — synchro automatique en arrière-plan (WorkManager)
    // et sa contrainte réseau. `syncWifiOnly` n'a d'effet que si
    // `syncAutoEnabled` est vrai (même patron que
    // eyeRestReminderEnabled/eyeRestReminderIntervalMinutes) : grisé côté
    // UI, et sans effet côté planification si l'auto-sync est éteinte.
    val syncAutoEnabled: Boolean = false,
    val syncWifiOnly: Boolean = false,
    // P4 (plan polissage Pareto) — confort de lecture visuelle. Le panneau
    // n'exposait que la taille et l'interligne ; ces quatre réglages sont le
    // minimum qu'offre tout lecteur sérieux.
    //
    // Les marges et l'espacement de paragraphe sont des CRANS (0..2) et non
    // des valeurs en dp : l'utilisateur choisit un confort, pas une mesure,
    // et une valeur libre exposerait des combinaisons illisibles (marge de
    // 60 dp sur un écran de 5 pouces). La conversion en dp appartient à la
    // couche de rendu, seule à connaître la densité de l'écran.
    val readerMarginStep: Int = MARGIN_STEP_DEFAULT,
    val paragraphSpacingStep: Int = PARAGRAPH_SPACING_STEP_DEFAULT,
    /**
     * Texte justifié. Emporte la césure avec lui, sans réglage séparé :
     * justifier sans césurer creuse des « rivières » blanches dans un texte
     * français (mots longs, peu de coupures naturelles). Les proposer
     * séparément laisserait choisir la seule combinaison qui dégrade la
     * lecture.
     */
    val textJustified: Boolean = false,
    /** Empêche l'écran de s'éteindre pendant la lecture visuelle. */
    val keepScreenOn: Boolean = false,
    /**
     * Lot 21, tâche 9 — auto-scroll visuel en mode SCROLL. `0` =
     * désactivé, `1..3` = crans de vitesse croissants (le mapping en
     * dp/s appartient au rendu, seul à connaître la densité). Désactivé
     * de fait quand `reduceMotion` est actif : le rendu ne démarre
     * jamais l'auto-scroll dans ce cas.
     */
    val autoScrollSpeed: Int = 0,
) {
    init {
        require(fontSize > 0) { "fontSize doit être strictement positif" }
        require(lineHeightMultiplier > 0f) { "lineHeightMultiplier doit être strictement positif" }
        require(readerMarginStep in MARGIN_STEP_RANGE) {
            "readerMarginStep doit être dans $MARGIN_STEP_RANGE"
        }
        require(paragraphSpacingStep in PARAGRAPH_SPACING_STEP_RANGE) {
            "paragraphSpacingStep doit être dans $PARAGRAPH_SPACING_STEP_RANGE"
        }
        require(autoScrollSpeed in AUTO_SCROLL_SPEED_RANGE) {
            "autoScrollSpeed doit être dans $AUTO_SCROLL_SPEED_RANGE"
        }
        require(readerBrightness == null || readerBrightness in 0.01f..1.0f) {
            "readerBrightness doit être compris entre 0.01 et 1.0, ou null"
        }
        require(eyeRestReminderIntervalMinutes > 0) { "eyeRestReminderIntervalMinutes doit être strictement positif" }
        require((syncProvider == null) == (syncAccountLabel == null && syncLinkedAt == null)) {
            "syncProvider, syncAccountLabel et syncLinkedAt doivent être renseignés ensemble ou tous absents"
        }
    }

    companion object {
        /** Crans de marge latérale : 0 étroite, 1 normale, 2 large. */
        val MARGIN_STEP_RANGE = 0..2
        const val MARGIN_STEP_DEFAULT = 1

        /** Crans d'espacement entre paragraphes : 0 serré, 1 normal, 2 aéré. */
        val PARAGRAPH_SPACING_STEP_RANGE = 0..2
        const val PARAGRAPH_SPACING_STEP_DEFAULT = 1

        /** Crans de vitesse d'auto-scroll : 0 désactivé, 1 lente, 2 moyenne, 3 rapide. */
        val AUTO_SCROLL_SPEED_RANGE = 0..3
    }
}

/**
 * Famille de police persistée en préférence (`UserPreferences.fontFamily`)
 * : une valeur ajoutée ne se retire plus (Lot 21, décision 2 — les thèmes
 * et préréglages existants peuvent s'en servir, une valeur manquante à la
 * lecture d'une vieille préférence planterait la restauration).
 *
 * `SOURCE_SERIF` = Source Serif 4 (OFL), police de lecture française à
 * empattements (Lot 21, tâche 10).
 */
enum class FontFamily { DEFAULT, OPEN_DYSLEXIC, SERIF, SANS_SERIF, SOURCE_SERIF }
