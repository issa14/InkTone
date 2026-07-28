package com.inktone.infrastructure.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Tests déterministes (signal synthétique, pas de fixture externe) pour
 * `AudioResampler`. La validation numérique contre
 * `scipy.signal.resample_poly` sur de l'audio Kokoro réel (24kHz -> 16kHz)
 * est documentée dans `docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md` §9 —
 * pas reproductible ici sans committer un fixture volumineux, même
 * principe déjà appliqué aux autres gros fichiers de ce projet.
 */
class AudioResamplerTest {

    @Test
    fun memes_taux_retourne_le_signal_inchange() {
        val input = FloatArray(100) { it / 100f }
        val output = AudioResampler.resample(input, 16000, 16000)
        assertEquals("aucune copie/transformation quand fromRate == toRate", input, output)
    }

    @Test
    fun longueur_de_sortie_coherente_avec_le_ratio() {
        val input = FloatArray(24000) { 0f } // 1 seconde a 24kHz
        val output = AudioResampler.resample(input, 24000, 16000)
        // 1s a 24kHz resample vers 16kHz doit produire ~1s de 16000 echantillons
        assertTrue(
            "longueur attendue proche de 16000, obtenu ${output.size}",
            abs(output.size - 16000) <= 2,
        )
    }

    @Test
    fun preserve_la_frequence_d_un_signal_sinusoidal() {
        // Sinusoide a 440 Hz echantillonnee a 24kHz, bien en-dessous du
        // Nyquist de la cible (8000 Hz a 16kHz) : le contenu frequentiel
        // doit survivre au resampling sans distorsion majeure.
        val sourceRate = 24000
        val targetRate = 16000
        val freq = 440.0
        val durationS = 0.1
        val input = FloatArray((sourceRate * durationS).toInt()) { i ->
            sin(2.0 * PI * freq * i / sourceRate).toFloat()
        }

        val output = AudioResampler.resample(input, sourceRate, targetRate)

        // Compte les passages par zero (montants) pour estimer la frequence
        // dominante du signal resample - methode simple et robuste, pas
        // besoin de FFT pour cette assertion.
        var zeroCrossings = 0
        for (i in 1 until output.size) {
            if (output[i - 1] < 0f && output[i] >= 0f) zeroCrossings++
        }
        val estimatedFreq = zeroCrossings / durationS
        assertTrue(
            "frequence estimee ($estimatedFreq Hz) doit rester proche de $freq Hz apres resampling",
            abs(estimatedFreq - freq) < 20.0,
        )
    }

    @Test
    fun amplitude_globalement_preservee_pour_un_signal_dans_la_bande_passante() {
        val sourceRate = 24000
        val targetRate = 16000
        val freq = 300.0 // bien en dessous de la coupure (8000 Hz)
        val input = FloatArray(sourceRate) { i -> sin(2.0 * PI * freq * i / sourceRate).toFloat() }

        val output = AudioResampler.resample(input, sourceRate, targetRate)

        // Ignore les bords (transitoires du filtre) pour comparer l'amplitude
        // en regime etabli.
        val inputPeak = input.slice(sourceRate / 4 until sourceRate * 3 / 4).maxOf { abs(it) }
        val outputPeak = output.slice(targetRate / 4 until targetRate * 3 / 4).maxOf { abs(it) }

        assertTrue(
            "amplitude crete apres resampling ($outputPeak) doit rester proche de l'entree ($inputPeak)",
            abs(outputPeak - inputPeak) < 0.15f,
        )
    }
}
