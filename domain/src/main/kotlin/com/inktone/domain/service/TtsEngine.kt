package com.inktone.domain.service

import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import kotlinx.coroutines.flow.Flow

/**
 * Contrat commun à tout moteur TTS (Blueprint §8.3, ADR-004). Chaque
 * adaptateur (Sherpa-ONNX, Piper, Edge TTS) implémente cette interface
 * dans infrastructure/tts et déclare ses capacités RÉELLES via
 * [capabilities] — jamais de plus petit dénominateur commun (§2.6).
 */
interface TtsEngine {
    val id: TtsEngineId
    val capabilities: TtsCapabilities

    suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment

    /** Événements de progression pendant la lecture d'un segment (§8.9). */
    fun observePlaybackEvents(): Flow<PlaybackEvent>

    /**
     * Lot 20 — préchauffage optionnel (chargement des modèles) hors du
     * premier appel à [synthesize]. No-op par défaut : seuls les moteurs
     * qui chargent des modèles lourds à la première synthèse (ex.
     * Sherpa-ONNX) le surchargent. À déclencher avant le premier usage
     * (ouverture du Reader, fin de téléchargement) pour ne pas payer
     * l'init froide au premier tap (budget §11.2).
     */
    fun warmUp() {}
}

/**
 * Segment audio synthétisé. `wordTimestamps` est vide si
 * [TtsCapabilities.wordTimestamps] est faux pour ce moteur — jamais
 * simulé par interpolation de caractères (§8.9, ADR-013).
 *
 * Classe ordinaire (pas `data class`) : `audioData` est un ByteArray, et
 * l'égalité par défaut d'une data class sur un ByteArray compare des
 * références, pas du contenu — piège classique. `equals`/`hashCode` sont
 * donc écrits à la main avec `contentEquals`/`contentHashCode`.
 *
 * `sampleRate` est nécessaire pour toute lecture réelle du PCM brut —
 * ajout Tâche 3.8, absent depuis la Phase 1 car aucun code ne jouait
 * encore l'audio à ce moment-là. Hypothèse posée ici, à documenter si un
 * moteur futur la viole : PCM16 signé, mono. Si un moteur produit un
 * format différent (stéréo, flottant), cette classe devra être étendue
 * explicitement — jamais une supposition silencieuse côté lecteur.
 */
class AudioSegment(
    val audioData: ByteArray,
    val durationMs: Long,
    val wordTimestamps: List<WordTimestamp>,
    val sampleRate: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioSegment) return false
        return audioData.contentEquals(other.audioData) &&
            durationMs == other.durationMs &&
            wordTimestamps == other.wordTimestamps &&
            sampleRate == other.sampleRate
    }

    override fun hashCode(): Int {
        var result = audioData.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + wordTimestamps.hashCode()
        result = 31 * result + sampleRate
        return result
    }
}

data class WordTimestamp(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val charOffset: Int,
)

sealed interface PlaybackEvent {
    data class SentenceStarted(val sentenceIndex: Int) : PlaybackEvent
    data class WordReached(val wordTimestamp: WordTimestamp) : PlaybackEvent
    data class SentenceCompleted(val sentenceIndex: Int) : PlaybackEvent
    data class Error(val message: String) : PlaybackEvent
}
