package com.inktone.infrastructure.media

import com.inktone.domain.service.AudioSegment
import java.io.File
import java.io.RandomAccessFile

/**
 * Écrit un [AudioSegment] (PCM16 brut, Tâche 1.7/3.8) dans un fichier WAV
 * temporaire. Point d'attention explicite du plan de Phase 5 (Tâche 5.4) :
 * `ExoPlayer` attend un flux (fichier/URI), pas un `ByteArray` PCM brut en
 * mémoire — deux options possibles (fichier temporaire WAV vs
 * `MediaSource` custom lisant directement le buffer). **Choix retenu :
 * fichier temporaire WAV** — plus simple, latence disque négligeable
 * pour des segments d'une phrase (quelques centaines de Ko au plus) ;
 * un `MediaSource` custom reste une optimisation possible si les
 * benchmarks (Tâche 5.9) révèlent un coût mesurable, pas avant.
 */
fun AudioSegment.writeToTempWavFile(cacheDir: File): File {
    val file = File.createTempFile("tts-segment-", ".wav", cacheDir)
    RandomAccessFile(file, "rw").use { raf ->
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val dataSize = audioData.size

        raf.writeBytes("RIFF")
        raf.writeIntLE(36 + dataSize)
        raf.writeBytes("WAVE")

        raf.writeBytes("fmt ")
        raf.writeIntLE(16) // taille du sous-chunk fmt
        raf.writeShortLE(1) // PCM non compresse
        raf.writeShortLE(channels)
        raf.writeIntLE(sampleRate)
        raf.writeIntLE(byteRate)
        raf.writeShortLE(blockAlign)
        raf.writeShortLE(bitsPerSample)

        raf.writeBytes("data")
        raf.writeIntLE(dataSize)
        raf.write(audioData)
    }
    return file
}

private fun RandomAccessFile.writeIntLE(value: Int) {
    write(byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    ))
}

private fun RandomAccessFile.writeShortLE(value: Int) {
    write(byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte()))
}
