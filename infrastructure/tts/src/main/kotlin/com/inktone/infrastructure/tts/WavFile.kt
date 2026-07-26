package com.inktone.infrastructure.tts

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** PCM samples (sans en-tête WAV) et sample rate extraits d'un fichier WAV produit par `synthesizeToFile`. */
data class WavPcmData(val pcm: ByteArray, val sampleRate: Int)

/**
 * Lecteur WAV minimal : parcourt les sous-chunks RIFF pour trouver `fmt `
 * (sample rate) et `data` (échantillons PCM), plutôt que de supposer un
 * en-tête fixe de 44 octets — certains encodeurs insèrent des chunks
 * additionnels (`LIST`, etc.) avant `data`.
 */
fun readWavPcmAndSampleRate(file: File): WavPcmData {
    RandomAccessFile(file, "r").use { raf ->
        val header = ByteArray(12)
        raf.readFully(header)
        require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF") { "Fichier non-RIFF : ${file.name}" }
        require(String(header, 8, 4, Charsets.US_ASCII) == "WAVE") { "Fichier non-WAVE : ${file.name}" }

        var sampleRate = -1
        var pcm: ByteArray? = null

        while (raf.filePointer < raf.length()) {
            val chunkHeader = ByteArray(8)
            if (raf.read(chunkHeader) < 8) break
            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

            when (chunkId) {
                "fmt " -> {
                    val fmt = ByteArray(chunkSize)
                    raf.readFully(fmt)
                    sampleRate = ByteBuffer.wrap(fmt, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                }
                "data" -> {
                    val data = ByteArray(chunkSize)
                    raf.readFully(data)
                    pcm = data
                }
                else -> raf.seek(raf.filePointer + chunkSize)
            }
            // Les chunks RIFF sont alignés sur 2 octets.
            if (chunkSize % 2 != 0 && raf.filePointer < raf.length()) raf.seek(raf.filePointer + 1)
        }

        checkNotNull(pcm) { "Chunk 'data' introuvable dans ${file.name}" }
        check(sampleRate > 0) { "Chunk 'fmt ' introuvable ou sampleRate invalide dans ${file.name}" }
        return WavPcmData(pcm = pcm, sampleRate = sampleRate)
    }
}
