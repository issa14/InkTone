package com.inktone.feature.reader

import com.inktone.domain.model.Sentence
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Précharge les phrases suivantes pendant la lecture de la phrase courante
 * (Blueprint §8.7). Correctif Lot 14 : passe de 1 à [LOOKAHEAD] slots — un
 * seul slot ne suffisait pas face à la latence de synthèse (Edge ~1 s) et
 * la lecture retombait en synthèse à la volée (trou audible). [clear] est
 * appelé au changement de chapitre (les indices de phrase sont locaux).
 */
class SentenceAudioBuffer(private val scope: CoroutineScope, private val ttsEngine: TtsEngine) {

    private val preloaded = mutableMapOf<Int, Deferred<AudioSegment>>()

    fun preload(sentence: Sentence, voiceProfile: VoiceProfile) {
        if (preloaded.containsKey(sentence.index)) return
        preloaded[sentence.index] = scope.async { ttsEngine.synthesize(sentence, voiceProfile) }
    }

    /** Précharge jusqu'à [LOOKAHEAD] phrases après [fromIndex]. */
    fun preloadAhead(sentences: List<Sentence>, fromIndex: Int, voiceProfile: VoiceProfile) {
        for (i in 1..LOOKAHEAD) {
            sentences.getOrNull(fromIndex + i)?.let { preload(it, voiceProfile) }
        }
    }

    /** Consomme le segment préchargé s'il correspond, sinon synthétise à la volée (repli, ex. saut manuel). */
    suspend fun get(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
        val deferred = preloaded.remove(sentence.index)
        return if (deferred != null) deferred.await() else ttsEngine.synthesize(sentence, voiceProfile)
    }

    /** Annule et vide les préchargements (changement de chapitre). */
    fun clear() {
        preloaded.values.forEach { it.cancel() }
        preloaded.clear()
    }

    private companion object {
        const val LOOKAHEAD = 3
    }
}
