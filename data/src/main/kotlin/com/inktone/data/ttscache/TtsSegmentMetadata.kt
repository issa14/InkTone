package com.inktone.data.ttscache

import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.WordTimestamp
import kotlinx.serialization.Serializable

/**
 * Métadonnées d'un segment TTS caché (Lot 22, Palier B) — sérialisées en
 * en-tête JSON, suivies du PCM16 brut. `formatVersion` versionne le format
 * d'écriture (décision 2) : basculer plus tard vers un format compressé
 * (AAC/Opus) ne demande qu'à l'incrémenter, pas à changer la conception.
 */
@Serializable
internal data class TtsSegmentMetadata(
    val formatVersion: Int,
    val sampleRate: Int,
    val durationMs: Long,
    val wordTimestamps: List<WordTimestampDto>,
)

@Serializable
internal data class WordTimestampDto(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val charOffset: Int,
)

internal fun AudioSegment.toMetadata(): TtsSegmentMetadata = TtsSegmentMetadata(
    formatVersion = TtsSegmentCacheImpl.FORMAT_VERSION,
    sampleRate = sampleRate,
    durationMs = durationMs,
    wordTimestamps = wordTimestamps.map { WordTimestampDto(it.word, it.startMs, it.endMs, it.charOffset) },
)

internal fun TtsSegmentMetadata.toSegment(pcm: ByteArray): AudioSegment = AudioSegment(
    audioData = pcm,
    durationMs = durationMs,
    wordTimestamps = wordTimestamps.map { WordTimestamp(it.word, it.startMs, it.endMs, it.charOffset) },
    sampleRate = sampleRate,
)
