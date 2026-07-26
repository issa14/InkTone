package com.inktone.domain.service

/**
 * Déclaration des capacités d'un moteur TTS (Blueprint §8.4, ADR-004).
 * `wordTimestamps` est la capacité de premier rang : elle seule autorise
 * le surlignage mot-à-mot réel (§8.9, ADR-013) — jamais simulé par
 * interpolation de caractères si elle est fausse.
 */
data class TtsCapabilities(
    val offline: Boolean,
    val wordTimestamps: Boolean,
    val sentenceTimestamps: Boolean,
    val languages: List<String>,
    val streamingSynthesis: Boolean,
    val speedControl: Boolean,
    val pitchControl: Boolean,
    val modelSizeMb: Int,
    val license: String,
)
