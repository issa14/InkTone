package com.inktone.feature.reader

import com.inktone.domain.model.Sentence
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Précharge la synthèse de la phrase suivante pendant la lecture de la
 * phrase courante (Blueprint §8.7). Un seul slot d'avance — pas de file
 * profonde, la lecture est séquentielle par nature (une phrase à la
 * fois), inutile de précharger plus loin qu'une phrase.
 */
class SentenceAudioBuffer(private val scope: CoroutineScope, private val ttsEngine: TtsEngine) {

    private var preloadedNext: Deferred<AudioSegment>? = null
    private var preloadedForSentenceIndex: Int? = null

    fun preloadNext(sentence: Sentence, voiceProfile: VoiceProfile) {
        if (preloadedForSentenceIndex == sentence.index) return // deja en cours ou fait
        preloadedNext = scope.async { ttsEngine.synthesize(sentence, voiceProfile) }
        preloadedForSentenceIndex = sentence.index
    }

    /** Consomme le segment preload s'il correspond, sinon synthetise a la volee (repli, ex. saut manuel). */
    suspend fun get(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
        if (preloadedForSentenceIndex == sentence.index) {
            val segment = preloadedNext!!.await()
            preloadedNext = null
            preloadedForSentenceIndex = null
            return segment
        }
        return ttsEngine.synthesize(sentence, voiceProfile)
    }
}
