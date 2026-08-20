package com.inktone.infrastructure.tts

import android.util.Log
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import com.inktone.infrastructure.tts.di.EdgeEngine
import com.inktone.infrastructure.tts.di.OfflineTtsEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Façade de routage TTS (Lot 14, ADR-024) — additive, SANS refonte du tronc
 * offline. Route selon `VoiceProfile.engine` :
 *
 * - `EDGE_TTS` → adaptateur Edge (cloud) ; sur erreur **réseau** transitoire,
 *   repli immédiat vers la chaîne offline pour la phrase en cours et les
 *   suivantes de la même instance (flag sticky, même sémantique que
 *   `FallbackTtsEngine`).
 * - tout autre moteur (`SHERPA_ONNX`, `ANDROID_NATIVE`) → chaîne offline
 *   (`FallbackTtsEngine`, inchangée).
 *
 * Edge n'est **jamais** sélectionné implicitement (ADR-024, règle 1) ; le
 * repli ne va **jamais** de l'offline vers le cloud. Une erreur permanente
 * (non réseau) est remontée, jamais avalée en repli silencieux (décision 5).
 *
 * `id`/`capabilities` reflètent le moteur réellement actif lors du dernier
 * `synthesize()` (jamais figés — même discipline que `FallbackTtsEngine`).
 */
@Singleton
class SelectiveTtsEngine @Inject constructor(
    @EdgeEngine private val edgeEngine: TtsEngine,
    @OfflineTtsEngine private val offlineEngine: TtsEngine,
) : TtsEngine {

    @Volatile
    private var edgeFailed = false

    @Volatile
    private var edgeActive = false

    override val id: TtsEngineId
        get() = if (edgeActive) TtsEngineId.EDGE_TTS else offlineEngine.id

    override val capabilities: TtsCapabilities
        get() = if (edgeActive) edgeEngine.capabilities else offlineEngine.capabilities

    override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
        val useEdge = voiceProfile.engine == TtsEngineId.EDGE_TTS && !edgeFailed
        if (!useEdge) {
            val segment = offlineEngine.synthesize(sentence, voiceProfile)
            edgeActive = false
            return segment
        }
        return try {
            val segment = edgeEngine.synthesize(sentence, voiceProfile)
            edgeActive = true
            segment
        } catch (e: Exception) {
            if (EdgeTtsClient.isNetworkError(e)) {
                Log.w(TAG, "Edge TTS indisponible (réseau) — repli offline pour la session")
                edgeFailed = true
                val segment = offlineEngine.synthesize(sentence, voiceProfile)
                edgeActive = false
                segment
            } else {
                throw e
            }
        }
    }

    override fun observePlaybackEvents(): Flow<PlaybackEvent> =
        if (edgeActive) edgeEngine.observePlaybackEvents() else offlineEngine.observePlaybackEvents()

    /** Lot 20 — préchauffe la chaîne offline (Sherpa-ONNX charge ses modèles). */
    override fun warmUp() {
        offlineEngine.warmUp()
    }

    private companion object {
        const val TAG = "SelectiveTtsEngine"
    }
}
