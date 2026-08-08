package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator

enum class ReadingMode { VISUAL, AUDIO }

enum class ReadingTheme { LIGHT, DARK, SEPIA, SYSTEM }

/**
 * État de reprise d'une publication — SOURCE DE VÉRITÉ UNIQUE de la
 * position de lecture, quel que soit le mode (Blueprint §3.3, §7.7,
 * acquis K3). Au plus une instance par publication — ne pas confondre
 * avec [ReadingSession] (revue B10).
 */
data class ReadingState(
    val publicationId: String,
    val locator: Locator,
    val lastReadAt: Long,
    val voiceProfileId: String? = null,
    val overrides: ReadingOverrides? = null,
) {
    init {
        require(publicationId.isNotBlank()) { "publicationId ne peut pas être vide" }
    }
}

/**
 * Surcharges de réglages propres à une publication. Priment toujours sur
 * [UserPreferences] — voir la règle de précédence du Blueprint §3.3 et
 * [EffectiveReadingSettings.resolve].
 */
data class ReadingOverrides(
    val theme: ReadingTheme? = null,
    val fontSize: Int? = null,
)

/**
 * Enregistrement HISTORIQUE d'une période de lecture, à des fins
 * statistiques uniquement (Blueprint §3.3). Plusieurs instances par
 * publication.
 *
 * Lot Statistiques Palier 1 : [visualDurationMs] et [ttsDurationMs]
 * séparent les deux modes de lecture pour les graphiques.
 * [durationMs] reste disponible comme propriété calculée pour
 * compatibilité ascendante (total = visuel + TTS).
 */
data class ReadingSession(
    val id: String,
    val publicationId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val mode: ReadingMode,
    val sentencesRead: Int = 0,
    val wordsRead: Int = 0,
    // Lot Statistiques Palier 1 — métriques séparées (avant durationMs,
    // qui en est une propriété calculée et doit les voir déclarées).
    val visualDurationMs: Long = 0,
    val ttsDurationMs: Long = 0,
) {
    /** Propriété calculée — total = visuel + TTS (compatibilité ascendante). */
    val durationMs: Long get() = visualDurationMs + ttsDurationMs

    init {
        require(publicationId.isNotBlank()) { "publicationId ne peut pas être vide" }
        require(sentencesRead >= 0) { "sentencesRead doit être positif ou nul" }
        require(durationMs >= 0) { "durationMs doit être positif ou nul" }
        require(visualDurationMs >= 0) { "visualDurationMs doit être positif ou nul" }
        require(ttsDurationMs >= 0) { "ttsDurationMs doit être positif ou nul" }
        endedAt?.let { end ->
            require(end >= startedAt) { "endedAt doit être postérieur ou égal à startedAt" }
        }
    }
}

/**
 * Réglages effectifs après application de la règle de précédence
 * (Blueprint §3.3) : surcharge de publication > préférences globales.
 * Résultat calculé, jamais persisté tel quel.
 */
data class EffectiveReadingSettings(
    val theme: ReadingTheme,
    val fontSize: Int,
) {
    companion object {
        fun resolve(overrides: ReadingOverrides?, global: UserPreferences): EffectiveReadingSettings =
            EffectiveReadingSettings(
                theme = overrides?.theme ?: global.theme,
                fontSize = overrides?.fontSize ?: global.fontSize,
            )
    }
}
