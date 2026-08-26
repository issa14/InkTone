package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ligne unique — id toujours 0. Choix délibéré de rester sur Room plutôt
 * que d'introduire DataStore comme seconde techno de persistance sans ADR. */
@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey val id: Int = 0,
    val theme: String,
    val fontSize: Int,
    val defaultTtsEngine: String,
    val crashReportingEnabled: Boolean,
    val language: String,
    val fontFamily: String = "DEFAULT",
    val reduceMotion: Boolean = false,
    val dynamicColorEnabled: Boolean = false,
    val readingRulerEnabled: Boolean = false,
    val dailyGoalMinutes: Int = 20, // Tache 1.4 (Partie 1)
    val activeVoiceProfileId: String? = null, // A.5
    val readingMode: String = "SCROLL", // B.1
    val audioGain: Float = 1.0f, // D.3
    val useSystemFontScale: Boolean = false, // D.3
    val lineHeightMultiplier: Float = 1.4f, // 3d.2
    val readerBrightness: Float? = null, // 3d.3
    val eyeRestReminderEnabled: Boolean = true, // 3d.5
    val eyeRestReminderIntervalMinutes: Int = 60, // 3d.5
    val appTheme: String = "SYSTEM", // Lot 6 — thème système de l'app
    val libraryLayoutMode: String = "GRID_DETAILED", // Lot 6 — disposition biblio, pilotée par le préréglage d'accessibilité
    val hasSeenOnboarding: Boolean = false, // Lot 10 — pilote le startDestination
    val hasPromptedVoiceDownload: Boolean = false, // Lot 10 — proposition proactive au premier usage TTS
    val deviceId: String? = null, // Lot 11 — identité d'appareil stable
    val deviceDisplayName: String? = null, // Lot 11
    val syncProvider: String? = null, // Lot 11 — null = aucun compte lié (Unconfigured)
    val syncAccountLabel: String? = null, // Lot 11
    val syncLinkedAt: Long? = null, // Lot 11
    val syncLastSyncAt: Long? = null, // Lot 11
    val syncLastAutoSyncFailed: Boolean = false, // Lot 11 — pilote la bannière persistante du Dashboard
    val syncAutoEnabled: Boolean = false, // Lot 11, tâche 11.8
    val syncWifiOnly: Boolean = false, // Lot 11, tâche 11.8
    // P4 (plan polissage Pareto) — confort de lecture visuelle (MIGRATION_26_27)
    val readerMarginStep: Int = 1,
    val paragraphSpacingStep: Int = 1,
    val textJustified: Boolean = false,
    val keepScreenOn: Boolean = false,
    // Lot 21, tâche 9 — auto-scroll visuel (MIGRATION_27_28). 0 = désactivé.
    val autoScrollSpeed: Int = 0,
    // Lot 22, tâche 12 — couleurs de surlignage récentes (MIGRATION_29_30),
    // CSV de couleurs ARGB hex (`#AARRGGBB`, réécrit depuis les noms
    // d'enum hérités par MIGRATION_30_31, Lot 23), la plus récente en premier.
    val recentAnnotationColors: String = "",
)
