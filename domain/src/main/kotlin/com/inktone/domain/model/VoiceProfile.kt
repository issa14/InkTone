package com.inktone.domain.model

enum class TtsEngineId { SHERPA_ONNX, PIPER, EDGE_TTS, ANDROID_NATIVE }

/**
 * Configuration vocale réutilisable (Blueprint §3.3). Le champ `style`
 * fait partie du modèle ici ET dans le Data Model (§6.2) — l'alignement
 * entre les deux était une contradiction identifiée en revue (B6),
 * désormais résolue par un seul chapitre de référence pour les deux.
 */
data class VoiceProfile(
    val id: String,
    val engine: TtsEngineId,
    val voice: String,
    val language: String,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volume: Float = 1.0f,
    val style: String? = null,
) {
    init {
        require(voice.isNotBlank()) { "voice ne peut pas être vide" }
        require(speed > 0f) { "speed doit être strictement positif" }
        require(volume in 0f..1f) { "volume doit être compris entre 0 et 1" }
        require(pitch > 0f) { "pitch doit être strictement positif" }
    }

    companion object {
        /**
         * Bornes proposées dans les deux écrans qui règlent la voix (audit UX
         * B9 : 0,5–2,0 aux Réglages, 0,5–3,0 au Lecteur, alors qu'Edge plafonne
         * à 2,0). Pas un invariant : un profil enregistré hors bornes reste
         * lisible, l'interface le ramène dans l'intervalle.
         */
        val SPEED_RANGE = 0.5f..2.0f
        val PITCH_RANGE = 0.5f..1.5f
    }
}
