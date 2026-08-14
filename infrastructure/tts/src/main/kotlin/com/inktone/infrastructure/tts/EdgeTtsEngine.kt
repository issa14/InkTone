package com.inktone.infrastructure.tts

import android.content.Context
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AppliedText
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.PronunciationRuleApplier
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.service.WordTimestamp
import com.inktone.domain.service.remapToOriginal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adaptateur Edge TTS (Lot 14, ADR-024) — moteur cloud optionnel. Implémente
 * le contrat [TtsEngine] avec des capacités HONNÊTES : `wordTimestamps = true`
 * est prouvé sur device (spike `EdgeTtsWebSocketSpikeTest` : frontières de mot
 * `Path:audio.metadata`, `Offset`/`Duration` en ticks 100 ns), jamais supposé.
 *
 * `synthesize` : règles de prononciation → [EdgeTtsClient] (WebSocket) →
 * [Mp3Decoder] (MP3 → PCM16) → mapping des frontières vers des
 * [WordTimestamp] alignés sur le texte AFFICHÉ (jamais le texte substitué —
 * même règle que `SherpaOnnxTtsEngine`, §8.9).
 */
@Singleton
class EdgeTtsEngine @Inject constructor(
    private val edgeTtsClient: EdgeTtsClient,
    private val mp3Decoder: Mp3Decoder,
    private val pronunciationRuleApplier: PronunciationRuleApplier,
    @ApplicationContext private val context: Context,
) : TtsEngine {

    override val id = TtsEngineId.EDGE_TTS

    override val capabilities = TtsCapabilities(
        offline = false,
        wordTimestamps = true, // prouvé sur device (spike, Path:audio.metadata)
        sentenceTimestamps = true,
        languages = listOf("fr"),
        streamingSynthesis = false, // synthèse phrase par phrase, pas de flux
        speedControl = true,        // taux SSML
        pitchControl = false,       // le client v1 fige pitch="+0Hz" (legacy)
        modelSizeMb = 0,            // aucun modèle local
        license = "Microsoft (API non officielle, edge-tts)",
    )

    override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
        val appliedText = pronunciationRuleApplier.apply(sentence.text)
        val result = edgeTtsClient.synthesize(
            text = appliedText.substitutedText,
            voiceName = voiceProfile.voice,
            speed = voiceProfile.speed,
        )
        val decoded = mp3Decoder.decode(result.mp3Bytes, context.cacheDir)
        val wordTimestamps = mapEdgeWordBoundaries(result.wordBoundaries, appliedText)
        return AudioSegment(
            audioData = decoded.audioData,
            durationMs = (decoded.audioData.size.toLong() * 1000L) / (2L * decoded.sampleRate),
            wordTimestamps = wordTimestamps,
            sampleRate = decoded.sampleRate,
        )
    }

    override fun observePlaybackEvents(): Flow<PlaybackEvent> = emptyFlow()
}

/**
 * Mappe les frontières de mot Edge (ticks 100 ns + texte synthétisé) vers des
 * [WordTimestamp] alignés sur le texte AFFICHÉ, jamais le texte substitué
 * (§8.9). `internal` pour test JVM pur (fonction déterministe, sans Android).
 *
 * Chaque frontière porte le mot TEL QUE SYNTHÉTISÉ (`boundary.text`), pas son
 * offset : on le localise séquentiellement dans `substitutedText` (curseur
 * `searchFrom`, pour gérer les mots répétés), puis on remappe vers le texte
 * original via [remapToOriginal] — même mécanique que `SherpaOnnxTtsEngine`.
 */
internal fun mapEdgeWordBoundaries(
    boundaries: List<EdgeWordBoundary>,
    appliedText: AppliedText,
): List<WordTimestamp> {
    val substituted = appliedText.substitutedText
    var searchFrom = 0
    return boundaries.mapNotNull { boundary ->
        val idx = findWordIndex(substituted, boundary.text, searchFrom)
            ?: return@mapNotNull null
        searchFrom = idx + boundary.text.length
        WordTimestamp(
            word = boundary.text,
            startMs = boundary.offsetTicks / 10_000L,
            endMs = (boundary.offsetTicks + boundary.durationTicks) / 10_000L,
            charOffset = idx,
        ).remapToOriginal(appliedText)
    }
}

private fun findWordIndex(text: String, word: String, fromIndex: Int): Int? {
    val exact = text.indexOf(word, fromIndex)
    if (exact >= 0) return exact
    val insensitive = text.indexOf(word, fromIndex, ignoreCase = true)
    return insensitive.takeIf { it >= 0 }
}
