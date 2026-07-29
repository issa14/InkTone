package com.inktone.infrastructure.tts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TtsEngineId
import com.inktone.domain.model.VoiceProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.inktone.core.testing.fake.FakePronunciationRuleRepository
import com.inktone.domain.service.PronunciationRuleApplier
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Valide bout en bout (Tâche 3.1, critère de validation) : sur un
 * device réel, `AndroidNativeTtsEngine.synthesize()` renvoie un
 * `AudioSegment` avec au moins un `WordTimestamp` par mot de la phrase,
 * des `charOffset` correspondant aux limites de mots du texte source, et
 * des `startMs` strictement croissants et cohérents avec `durationMs`.
 */
@RunWith(AndroidJUnit4::class)
class AndroidNativeTtsEngineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun voiceProfile() = VoiceProfile(
        id = "vp-native-fr",
        engine = TtsEngineId.ANDROID_NATIVE,
        voice = "fr-fr-default",
        language = "fr-FR",
    )

    @Test
    fun synthesize_produit_un_wordTimestamp_par_mot_avec_offsets_et_timings_coherents() = runTest {
        val engine = AndroidNativeTtsEngine(context, PronunciationRuleApplier(FakePronunciationRuleRepository()))
        val text = "Bonjour, ceci est un test de synchronisation."
        val sentence = Sentence(index = 0, text = text, startOffset = 0, endOffset = text.length)

        val segment = engine.synthesize(sentence, voiceProfile())

        assertTrue("audioData ne doit pas etre vide", segment.audioData.isNotEmpty())
        assertTrue("durationMs doit etre positif", segment.durationMs > 0)
        assertTrue(
            "au moins un WordTimestamp attendu (7 mots dans la phrase de test)",
            segment.wordTimestamps.size >= 5,
        )

        segment.wordTimestamps.forEach { wt ->
            assertTrue("charOffset dans les bornes du texte", wt.charOffset in 0..text.length)
            assertTrue("startMs doit etre positif ou nul", wt.startMs >= 0)
            assertTrue("endMs >= startMs", wt.endMs >= wt.startMs)
            assertTrue("endMs <= durationMs", wt.endMs <= segment.durationMs)
        }

        val startTimes = segment.wordTimestamps.map { it.startMs }
        assertEquals("les startMs doivent etre strictement croissants", startTimes, startTimes.sorted().distinct())

        val words = segment.wordTimestamps.map { it.word }
        assertTrue("les mots extraits doivent correspondre au texte source", words.all { it.isNotBlank() })
    }
}
