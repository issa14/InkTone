package com.inktone.infrastructure.tts.spike

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SPIKE — Tâche 3.1.0 (ADR-021, Palier 1). Vérifie empiriquement si
 * `onRangeStart` se déclenche pendant `synthesizeToFile()` (Plan A) ou
 * seulement pendant une lecture live via `speak()` (Plan B). Exécuté sur
 * device réel (V2206, Google TTS installé) — pas un test d'assertion
 * classique, un instrument de mesure. Le résultat conditionne l'écriture
 * de AndroidNativeTtsEngine (Tâche 3.1.1 ou 3.1.2).
 */
@RunWith(AndroidJUnit4::class)
class OnRangeStartFileSynthesisSpikeTest {

    @Test
    fun mesure_declenchement_onRangeStart_pendant_synthesizeToFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rangeStartFired = AtomicBoolean(false)
        val rangeStartCount = AtomicBoolean(false)
        val doneLatch = CountDownLatch(1)
        var initFailed = false

        lateinit var tts: TextToSpeech
        val initLatch = CountDownLatch(1)
        tts = TextToSpeech(context) { status ->
            initFailed = status != TextToSpeech.SUCCESS
            initLatch.countDown()
        }

        check(initLatch.await(10, TimeUnit.SECONDS)) { "Timeout d'initialisation TextToSpeech" }
        check(!initFailed) { "Echec d'initialisation TextToSpeech" }

        tts.language = Locale.FRENCH

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.i("Spike", "onStart: $utteranceId")
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                rangeStartFired.set(true)
                rangeStartCount.set(true)
                Log.i("Spike", "onRangeStart: [$start,$end) frame=$frame")
            }

            override fun onDone(utteranceId: String?) {
                Log.i("Spike", "onDone: $utteranceId - rangeStartFired=${rangeStartFired.get()}")
                doneLatch.countDown()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("Spike", "onError: $utteranceId")
                doneLatch.countDown()
            }
        })

        val outputFile = File(context.cacheDir, "spike-onrangestart-test.wav")
        val bundle = Bundle()
        val enqueueResult = tts.synthesizeToFile(
            "Bonjour, ceci est un test de synchronisation.",
            bundle,
            outputFile,
            "spike-utterance-1",
        )
        Log.i("Spike", "synthesizeToFile enqueueResult=$enqueueResult (0=SUCCESS,-1=ERROR)")

        check(doneLatch.await(15, TimeUnit.SECONDS)) { "Timeout de synthese" }

        Log.i("Spike", "outputFile exists=${outputFile.exists()} size=${outputFile.length()}")
        Log.i("Spike", "RESULTAT FINAL: onRangeStart declenche pendant synthesizeToFile = ${rangeStartFired.get()}")

        outputFile.delete()
        tts.shutdown()
    }
}
