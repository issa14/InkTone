package com.inktone.infrastructure.tts

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Resampling PCM générique par sinus cardinal fenêtré (filtre passe-bas
 * anti-repliement, pas une décimation naïve) — Tâche 5.1/5.2 : Kokoro
 * produit du 24 kHz, le modèle d'alignement CTC attend du 16 kHz. Jamais
 * un ratio ni un taux figé en dur : `fromRate`/`toRate` viennent tous les
 * deux de valeurs réelles à l'appel (`AudioSegment.sampleRate`, Tâche
 * 3.8.0, côté source ; taux natif du modèle CTC côté cible) — si un futur
 * moteur de synthèse produit un autre taux, ce code fonctionne sans
 * modification.
 *
 * Algorithme : pour chaque échantillon de sortie, interpolation par noyau
 * sinc normalisé à la fréquence de coupure `min(toRate, fromRate) / 2`
 * (Nyquist de la plus basse des deux fréquences — c'est la contrainte de
 * Shannon qui empêche le repliement lors d'une décimation), fenêtré par
 * une fenêtre de Blackman pour limiter le lobe de troncature. Comparable
 * en principe à `scipy.signal.resample_poly` (même famille d'algorithme,
 * implémentation directe plutôt que polyphase optimisée — le volume
 * audio traité ici, quelques secondes par phrase, ne justifie pas la
 * complexité d'une décomposition polyphase pour la performance).
 *
 * Validation numérique contre `scipy.signal.resample_poly` sur de l'audio
 * Kokoro réel (pas synthétique) : voir
 * `docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md` §9.
 */
object AudioResampler {

    /** Nombre de "zéros" du noyau sinc de chaque côté du centre — largeur du filtre. */
    private const val HALF_TAPS = 16

    fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        require(fromRate > 0 && toRate > 0) { "fromRate/toRate doivent etre positifs" }
        if (fromRate == toRate) return input

        // cutoff normalise a la frequence d'entree : Nyquist(min(from,to)) / Nyquist(from)
        val normalizedCutoff = min(1.0, toRate.toDouble() / fromRate.toDouble())

        val ratio = fromRate.toDouble() / toRate.toDouble() // echantillons d'entree par echantillon de sortie
        val outLen = floor(input.size / ratio).toInt()
        val output = FloatArray(outLen)

        for (m in 0 until outLen) {
            val t = m * ratio
            val centerN = floor(t).toInt()
            var sum = 0.0
            for (k in -HALF_TAPS until HALF_TAPS) {
                val n = centerN + k
                if (n < 0 || n >= input.size) continue
                val x = t - n
                val sincVal = sincKernel(x, normalizedCutoff)
                val w = blackmanWindow(x, HALF_TAPS)
                sum += input[n] * sincVal * w
            }
            output[m] = sum.toFloat()
        }
        return output
    }

    private fun sincKernel(x: Double, cutoff: Double): Double {
        val arg = PI * cutoff * x
        return if (arg == 0.0) cutoff else cutoff * sin(arg) / arg
    }

    /** Fenetre de Blackman continue sur [-halfTaps, +halfTaps], 0 hors support. */
    private fun blackmanWindow(x: Double, halfTaps: Int): Double {
        if (x <= -halfTaps || x >= halfTaps) return 0.0
        val n = (x + halfTaps) / (2.0 * halfTaps) // normalise dans [0,1]
        return 0.42 - 0.5 * cos(2.0 * PI * n) + 0.08 * cos(4.0 * PI * n)
    }
}
