package com.inktone.infrastructure.tts

import android.util.Log
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import com.inktone.infrastructure.tts.di.Palier1
import com.inktone.infrastructure.tts.di.Palier2
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repli automatique Palier 2 -> Palier 1 (Tâche 5.8, ADR-021) : si
 * `SherpaOnnxTtsEngine` échoue (modèle vocal absent/corrompu, échec JNI
 * natif), bascule vers `AndroidNativeTtsEngine` plutôt que de crasher ou
 * d'interrompre la lecture (Blueprint §8.12, jamais d'interruption
 * brutale). Une fois un échec observé, reste sur le Palier 1 pour les
 * phrases suivantes de la même instance — cohérent avec la « détection
 * au runtime » déjà actée par ADR-021 : inutile de retenter le Palier 2
 * phrase par phrase si le device/modèle ne le supporte pas.
 *
 * `capabilities`/`id` reflètent TOUJOURS le moteur réellement actif,
 * jamais figés à la construction — §8.9 ("un moteur ne fait jamais
 * semblant") s'applique aussi à ce wrapper : après un repli,
 * `wordTimestamps` redevient vrai (le Palier 1 les fournit réellement),
 * pas figé sur la valeur du Palier 2.
 */
@Singleton
class FallbackTtsEngine @Inject constructor(
    @Palier2 private val primary: TtsEngine,
    @Palier1 private val fallback: TtsEngine,
) : TtsEngine {

    @Volatile
    private var fallenBack = false

    override val id: TtsEngineId
        get() = if (fallenBack) fallback.id else primary.id

    override val capabilities: TtsCapabilities
        get() = if (fallenBack) fallback.capabilities else primary.capabilities

    override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
        if (fallenBack) return fallback.synthesize(sentence, voiceProfile)
        return try {
            primary.synthesize(sentence, voiceProfile)
        } catch (e: Exception) {
            Log.w(TAG, "Palier 2 (Sherpa-ONNX) a echoue, repli vers le Palier 1 (Android natif): ${e.message}", e)
            fallenBack = true
            fallback.synthesize(sentence, voiceProfile)
        }
    }

    override fun observePlaybackEvents(): Flow<PlaybackEvent> =
        if (fallenBack) fallback.observePlaybackEvents() else primary.observePlaybackEvents()

    private companion object {
        const val TAG = "FallbackTtsEngine"
    }
}
