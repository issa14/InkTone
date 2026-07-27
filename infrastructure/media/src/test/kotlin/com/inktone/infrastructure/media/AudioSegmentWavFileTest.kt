package com.inktone.infrastructure.media

import com.inktone.domain.service.AudioSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class AudioSegmentWavFileTest {

    @Test
    fun ecrit_un_wav_valide_avec_les_bons_en_tetes() {
        val pcm = byteArrayOf(1, 2, 3, 4, 5, 6)
        val segment = AudioSegment(audioData = pcm, durationMs = 100L, wordTimestamps = emptyList(), sampleRate = 22050)
        val tempDir = File.createTempFile("wav-test", "").apply { delete(); mkdirs() }

        val file = segment.writeToTempWavFile(tempDir)

        assertTrue(file.exists())
        RandomAccessFile(file, "r").use { raf ->
            val riff = ByteArray(4).also { raf.readFully(it) }
            assertEquals("RIFF", String(riff))

            raf.seek(8)
            val wave = ByteArray(4).also { raf.readFully(it) }
            assertEquals("WAVE", String(wave))

            raf.seek(12)
            val fmt = ByteArray(4).also { raf.readFully(it) }
            assertEquals("fmt ", String(fmt))

            raf.seek(24)
            val sampleRateBytes = ByteArray(4).also { raf.readFully(it) }
            val sampleRate = (sampleRateBytes[0].toInt() and 0xFF) or
                ((sampleRateBytes[1].toInt() and 0xFF) shl 8) or
                ((sampleRateBytes[2].toInt() and 0xFF) shl 16) or
                ((sampleRateBytes[3].toInt() and 0xFF) shl 24)
            assertEquals(22050, sampleRate)

            raf.seek(36)
            val data = ByteArray(4).also { raf.readFully(it) }
            assertEquals("data", String(data))

            raf.seek(44) // 36 (tag "data") + 4 (taille du sous-chunk) = debut du payload PCM
            val payload = ByteArray(pcm.size).also { raf.readFully(it) }
            assertTrue(payload.contentEquals(pcm))
        }
        file.delete()
        tempDir.delete()
    }
}
