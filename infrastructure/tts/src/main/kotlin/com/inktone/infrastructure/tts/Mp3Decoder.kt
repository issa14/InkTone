package com.inktone.infrastructure.tts

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Décodeur MP3 → PCM16 via `MediaCodec`/`MediaExtractor` Android, sans
 * dépendance externe (Lot 14). Sort directement du `ByteArray` PCM16 signé
 * little-endian — le format attendu par `AudioSegment.audioData` (écart
 * délibéré du legacy, qui normalisait en `FloatArray` [-1,1] ; voir
 * `LOT_14_EDGE_TTS.md` §1).
 *
 * Le flux MP3 est écrit dans un fichier temporaire (`cacheDir`) car
 * `MediaExtractor` exige une source fichier ; supprimé en `finally`. Acceptable
 * pour des phrases de quelques centaines de Ko — à documenter, pas à
 * optimiser prématurément.
 */
@Singleton
class Mp3Decoder @Inject constructor() {

    companion object {
        private const val TAG = "Mp3Decoder"
        private const val TIMEOUT_US = 10_000L
    }

    data class DecodedAudio(
        val audioData: ByteArray,
        val sampleRate: Int,
    )

    suspend fun decode(mp3Bytes: ByteArray, cacheDir: File): DecodedAudio =
        withContext(Dispatchers.IO) {
            check(mp3Bytes.isNotEmpty()) { "Flux MP3 vide — rien à décoder" }

            val tempFile = File.createTempFile("edge_tts_", ".mp3", cacheDir)
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                FileOutputStream(tempFile).use { it.write(mp3Bytes) }
                extractor.setDataSource(tempFile.absolutePath)

                val trackIndex = findAudioTrack(extractor)
                    ?: throw IllegalStateException("Aucune piste audio dans le flux MP3")
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)
                    ?: throw IllegalStateException("MIME introuvable dans le format audio")
                val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)

                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()
                extractor.selectTrack(trackIndex)

                val shorts = decodeToShortArray(extractor, codec)
                Log.d(TAG, "Décodage MP3 : ${mp3Bytes.size} octets → ${shorts.size} shorts PCM @ $sampleRate Hz")
                DecodedAudio(audioData = shortsToPcm16LittleEndian(shorts), sampleRate = sampleRate)
            } finally {
                codec?.let { runCatching { it.stop() } }
                codec?.let { runCatching { it.release() } }
                runCatching { extractor.release() }
                tempFile.delete()
            }
        }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    /** Boucle de décodage sur `outputDone` (jamais une boucle infinie sur buffers). */
    private fun decodeToShortArray(extractor: MediaExtractor, codec: MediaCodec): ShortArray {
        val bufferInfo = MediaCodec.BufferInfo()
        val output = mutableListOf<Short>()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        val shortCount = bufferInfo.size / 2
                        val shortBuf = ShortArray(shortCount)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.asShortBuffer().get(shortBuf)
                        output.addAll(shortBuf.toList())
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
            }
        }
        return output.toShortArray()
    }

    /** ShortArray PCM16 → ByteArray little-endian (le contrat `AudioSegment`). */
    private fun shortsToPcm16LittleEndian(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
        return bytes
    }
}
