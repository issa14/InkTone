package com.inktone.infrastructure.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.PlaybackEvent
import com.inktone.domain.service.TtsCapabilities
import com.inktone.domain.service.TtsEngine
import com.inktone.domain.service.WordTimestamp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Adaptateur Palier 1 (ADR-021) : `android.speech.tts.TextToSpeech` natif.
 *
 * Vérification empirique (Tâche 3.1.0, device V2206/Android 14, voix
 * embarquée `fr-fr-x-frb-seanet-embedded`) : `onRangeStart` se déclenche
 * bien pendant `synthesizeToFile` (Plan A confirmé) — MAIS ses paramètres
 * ne respectent pas la sémantique documentée par Android sur ce device.
 * La doc officielle indique `onRangeStart(utteranceId, start, end, frame)`
 * avec `start`/`end` = offsets caractère et `frame` = position audio.
 * Empiriquement observé ici : `start` porte une valeur croissante de
 * type position audio (échantillons), tandis que `end` et `frame`
 * portent respectivement le début et la fin caractère du mot — décodage
 * vérifié sur 7 mots consécutifs d'une phrase de test, correspondance
 * exacte. Le code ci-dessous n'suppose donc AUCUNE des deux sémantiques :
 * il teste l'interprétation documentée d'abord, puis se rabat sur
 * l'interprétation empirique si la première produit des offsets hors
 * bornes — cohérent avec la nécessité de détection au runtime déjà
 * actée par ADR-021 pour les moteurs constructeur.
 */
@Singleton
class AndroidNativeTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : TtsEngine {

    override val id = TtsEngineId.ANDROID_NATIVE

    override val capabilities = TtsCapabilities(
        offline = true,
        wordTimestamps = true,
        sentenceTimestamps = true,
        languages = listOf("fr"),
        streamingSynthesis = false,
        speedControl = true,
        pitchControl = true,
        modelSizeMb = 0,
        license = "Système Android (moteur OS installé)",
    )

    private var tts: TextToSpeech? = null

    private suspend fun ensureInitialized(): TextToSpeech {
        tts?.let { return it }
        return suspendCancellableCoroutine { cont ->
            lateinit var instance: TextToSpeech
            instance = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts = instance
                    cont.resume(instance)
                } else {
                    cont.resumeWithException(IllegalStateException("Echec d'initialisation TextToSpeech: $status"))
                }
            }
        }
    }

    override suspend fun synthesize(sentence: Sentence, voiceProfile: VoiceProfile): AudioSegment {
        val engine = ensureInitialized()
        engine.language = Locale.FRENCH
        val utteranceId = UUID.randomUUID().toString()
        val outputFile = File(context.cacheDir, "tts-$utteranceId.wav")
        val boundaries = mutableListOf<WordBoundary>()

        return suspendCancellableCoroutine { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                    if (id != utteranceId) return
                    val resolved = resolveWordBoundary(start, end, frame, sentence.text.length)
                    if (resolved == null) {
                        Log.w(
                            "AndroidNativeTtsEngine",
                            "onRangeStart ignore, offsets hors bornes: start=$start end=$end frame=$frame " +
                                "textLength=${sentence.text.length}",
                        )
                        return
                    }
                    boundaries += resolved
                }

                override fun onDone(id: String?) {
                    if (id != utteranceId) return
                    try {
                        val (pcm, sampleRate) = readWavPcmAndSampleRate(outputFile)
                        val durationMs = (pcm.size.toLong() * 1000L) / (sampleRate * 2L) // PCM16 mono
                        val wordTimestamps = boundaries.mapIndexed { index, boundary ->
                            val startMs = (boundary.audioFrame.toLong() * 1000L) / sampleRate
                            val endMs = if (index < boundaries.lastIndex) {
                                (boundaries[index + 1].audioFrame.toLong() * 1000L) / sampleRate
                            } else {
                                durationMs
                            }
                            WordTimestamp(
                                word = sentence.text.substring(boundary.charStart, boundary.charEnd),
                                startMs = startMs,
                                endMs = endMs,
                                charOffset = boundary.charStart,
                            )
                        }
                        cont.resume(AudioSegment(audioData = pcm, durationMs = durationMs, wordTimestamps = wordTimestamps))
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    } finally {
                        outputFile.delete()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id != utteranceId) return
                    outputFile.delete()
                    cont.resumeWithException(IllegalStateException("Echec de synthese TTS pour l'utterance $utteranceId"))
                }
            })

            val bundle = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, voiceProfile.volume)
            }
            engine.setSpeechRate(voiceProfile.speed)
            engine.synthesizeToFile(sentence.text, bundle, outputFile, utteranceId)
        }
    }

    override fun observePlaybackEvents(): Flow<PlaybackEvent> = callbackFlow {
        // Ce moteur produit ses évènements de mot PENDANT synthesize(),
        // pas pendant la lecture (qui passe par notre AudioPlaybackService
        // une fois l'AudioSegment obtenu, Phase 5) — contrairement à un
        // moteur neuronal où lecture et timing sont découplés de la
        // synthèse. Ce flow reste donc vide pour ce moteur : les
        // WordTimestamp sont déjà dans l'AudioSegment retourné.
        awaitClose { }
    }
}

internal data class WordBoundary(val charStart: Int, val charEnd: Int, val audioFrame: Int)

/**
 * Résout les 3 entiers bruts d'`onRangeStart` en (charStart, charEnd,
 * audioFrame), sans supposer laquelle des deux sémantiques (documentée
 * ou empirique, voir KDoc de [AndroidNativeTtsEngine]) est active sur le
 * device courant. Retourne `null` si aucune des deux interprétations ne
 * produit un intervalle caractère valide — l'appelant ignore alors cet
 * évènement plutôt que de lever une exception qui interromprait toute
 * la synthèse pour un seul mot mal rapporté.
 */
internal fun resolveWordBoundary(start: Int, end: Int, frame: Int, textLength: Int): WordBoundary? {
    fun isValidRange(a: Int, b: Int) = a in 0..textLength && b in 0..textLength && a <= b

    return when {
        // Sémantique documentée par Android : start/end = offsets caractère.
        isValidRange(start, end) -> WordBoundary(charStart = start, charEnd = end, audioFrame = frame)
        // Sémantique observée empiriquement (Tâche 3.1.0) sur ce device :
        // end/frame = offsets caractère, start = position audio.
        isValidRange(end, frame) -> WordBoundary(charStart = end, charEnd = frame, audioFrame = start)
        else -> null
    }
}
