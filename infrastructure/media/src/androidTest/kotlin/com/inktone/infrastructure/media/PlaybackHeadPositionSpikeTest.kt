package com.inktone.infrastructure.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.concurrent.thread

/**
 * SPIKE — Lot 16, Tâche 1.1. Instrument de mesure, pas un test d'assertion :
 * il mesure sur device la fiabilité de la position `AudioTrack` en MODE_STREAM
 * pour piloter un surlignage mot « position réelle ». Le verdict remplit
 * `docs/execution/SPIKE_SURLIGNAGE_POSITION.md` §7-8.
 *
 * Mesuré : `getPlaybackHeadPosition()` (frames écrits attendus, donc en avance)
 * vs `getTimestamp()` (frame présenté + horodatage, corrigé de la latence). PCM
 * de test connu : 5 tons de 1 s (440/550/660/770/880 Hz), horodatage théorique
 * connu. Échantillonnage toutes les 100 ms, sur 22 050 Hz puis 24 000 Hz.
 *
 * Lire les résultats : `adb logcat -d -s HeadPosSpike`.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackHeadPositionSpikeTest {

    @Test
    fun mesureLaPositionSurLesDeuxSampleRates() {
        measure(22_050)
        measure(24_000)
    }

    private fun measure(sampleRate: Int) {
        val totalMs = 5_000L
        val segmentMs = 1_000L
        val bufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(sampleRate * 2) // au moins ~1 s de buffer

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val pcm = generateTones(sampleRate, totalMs, segmentMs)

        track.play()
        val startNanos = System.nanoTime()

        // Écriture bloquante dans un thread séparé : elle se cale sur la vitesse
        // de lecture (le buffer fini impose le rythme), comme le pipeline gapless.
        val writer = thread(name = "spike-writer") {
            track.write(pcm, 0, pcm.size)
        }

        var sampleCount = 0
        var tsValidCount = 0
        var maxHeadErrMs = 0L
        var maxTsErrMs = 0L
        var firstTsErrMs = Long.MAX_VALUE
        var lastTsErrMs = Long.MAX_VALUE
        val samples = mutableListOf<String>()

        // Mesure UNIQUEMENT pendant la lecture (les échantillons post-lecture
        // figeraient la position et fausseraient la mesure de dérive).
        val endNanos = startNanos + totalMs * 1_000_000L
        while (System.nanoTime() < endNanos) {
            val nowNanos = System.nanoTime()
            val elapsedMs = (nowNanos - startNanos) / 1_000_000L
            val expectedFrames = elapsedMs * sampleRate / 1_000L

            val headFrames = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            val headErrMs = (headFrames - expectedFrames) * 1_000L / sampleRate

            val ts = AudioTimestamp()
            val hasTs = track.getTimestamp(ts)
            var tsErrMs = Long.MAX_VALUE
            if (hasTs) {
                tsValidCount++
                // Position corrigée de la latence : le frame `framePosition` a
                // été présenté à `nanoTime` ; depuis, la lecture avance à 1×.
                val playedFrames = ts.framePosition + (nowNanos - ts.nanoTime) * sampleRate / 1_000_000_000L
                tsErrMs = (playedFrames - expectedFrames) * 1_000L / sampleRate
                if (firstTsErrMs == Long.MAX_VALUE) firstTsErrMs = tsErrMs
                lastTsErrMs = tsErrMs
            }

            maxHeadErrMs = maxOf(maxHeadErrMs, abs(headErrMs))
            if (tsErrMs != Long.MAX_VALUE) maxTsErrMs = maxOf(maxTsErrMs, abs(tsErrMs))
            samples += "t=${elapsedMs}ms head=${headFrames} headErr=${headErrMs}ms ts=${if (hasTs) ts.framePosition else -1} tsErr=${if (hasTs) "${tsErrMs}ms" else "n/a"}"
            sampleCount++
            Thread.sleep(100)
        }
        writer.join()
        track.stop()
        track.release()

        samples.forEach { Log.i(TAG, "[sr=$sampleRate] $it") }
        val driftMs = if (lastTsErrMs != Long.MAX_VALUE) lastTsErrMs - firstTsErrMs else Long.MAX_VALUE
        Log.i(
            TAG,
            "VERDICT sr=$sampleRate tsValid=$tsValidCount/$sampleCount maxHeadErr=${maxHeadErrMs}ms " +
                "maxTsErr=${maxTsErrMs}ms drift=${driftMs}ms",
        )
    }

    /** 5 tons de 1 s (440/550/660/770/880 Hz), PCM16 mono. */
    private fun generateTones(sampleRate: Int, totalMs: Long, segmentMs: Long): ByteArray {
        val frames = (sampleRate * totalMs / 1_000L).toInt()
        val segFrames = (sampleRate * segmentMs / 1_000L).toInt().coerceAtLeast(1)
        val freqs = intArrayOf(440, 550, 660, 770, 880)
        val bytes = ByteArray(frames * 2)
        for (i in 0 until frames) {
            val freq = freqs[(i / segFrames).coerceAtMost(freqs.lastIndex)]
            val sample = (sin(2 * PI * freq * i / sampleRate) * AMPLITUDE).toInt().toShort()
            bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private companion object {
        const val TAG = "HeadPosSpike"
        const val AMPLITUDE = 8_192
    }
}
