package com.inktone.data.ttscache

import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.TtsCacheKey
import com.inktone.domain.service.TtsSegmentCache
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation fichier de [TtsSegmentCache] (Lot 22, Palier B).
 *
 * Un fichier par segment : en-tête JSON (métadonnées + timestamps) suivi
 * du PCM16 brut — audio et timestamps écrits et lus ensemble (correction 1,
 * ADR-021). Structure :
 * - `tts/<publicationId>/<key>.seg` : segments (le `publicationId` explicite
 *   rend la purge par livre triviale — la clé hashée n'est pas inversable) ;
 * - `tts/<publicationId>.pin` : clé épinglée du point de reprise.
 *
 * Plafond ~200 Mo global, éviction LRU (les segments épinglés survivent),
 * purge à la suppression du livre ([deletePublication]).
 */
@Singleton
class TtsSegmentCacheImpl : TtsSegmentCache {

    private val json = Json { ignoreUnknownKeys = true }
    private val baseDir: File
    private val maxTotalBytes: Long

    @Inject
    constructor(cacheDir: File) : this(cacheDir, MAX_TOTAL_BYTES)

    /** Constructeur de test : plafond ajustable pour exercer l'éviction LRU sans écrire 200 Mo. */
    internal constructor(cacheDir: File, maxTotalBytes: Long) {
        this.baseDir = File(cacheDir, "tts")
        this.maxTotalBytes = maxTotalBytes
    }

    override suspend fun get(publicationId: String, key: TtsCacheKey): AudioSegment? {
        val file = segmentFile(publicationId, key)
        if (!file.exists()) return null
        return runCatching { readSegment(file) }.getOrNull()?.also {
            // Touche la date d'accès pour l'ordre LRU.
            file.setLastModified(System.currentTimeMillis())
        }
    }

    override suspend fun put(publicationId: String, key: TtsCacheKey, segment: AudioSegment) {
        val dir = publicationDir(publicationId)
        dir.mkdirs()
        val file = segmentFile(publicationId, key)
        val tmp = File(dir, "${key.value}.seg.tmp")
        try {
            writeSegment(tmp, segment)
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        evictToCap()
    }

    override suspend fun pinResumePoint(publicationId: String, key: TtsCacheKey) {
        val pin = pinFile(publicationId)
        pin.parentFile?.mkdirs()
        pin.writeText(key.value)
    }

    override suspend fun deletePublication(publicationId: String) {
        publicationDir(publicationId).deleteRecursively()
        pinFile(publicationId).delete()
    }

    // ---- Fichiers ----

    private fun publicationDir(publicationId: String): File = File(baseDir, publicationId)

    private fun segmentFile(publicationId: String, key: TtsCacheKey): File =
        File(publicationDir(publicationId), "${key.value}.seg")

    private fun pinFile(publicationId: String): File = File(baseDir, "$publicationId.pin")

    // ---- Lecture / écriture ----

    private fun writeSegment(file: File, segment: AudioSegment) {
        val metadataJson = json.encodeToString(segment.toMetadata())
        file.outputStream().use { out ->
            out.write(metadataJson.toByteArray(Charsets.UTF_8))
            out.write(NEWLINE.toInt())
            out.write(segment.audioData)
        }
    }

    private fun readSegment(file: File): AudioSegment {
        val bytes = file.readBytes()
        val newlineIndex = bytes.indexOfFirst { it == NEWLINE }
        if (newlineIndex < 0) error("Segment corrompu : en-tête JSON absent")
        val metadataJson = bytes.copyOfRange(0, newlineIndex).decodeToString()
        val pcm = bytes.copyOfRange(newlineIndex + 1, bytes.size)
        val metadata = json.decodeFromString(TtsSegmentMetadata.serializer(), metadataJson)
        if (metadata.formatVersion != FORMAT_VERSION) error("Format de segment périmé")
        return metadata.toSegment(pcm)
    }

    // ---- Éviction LRU ----

    private fun evictToCap() {
        val pinned = pinnedKeys()
        var total = 0L
        val segments = mutableListOf<File>()
        baseDir.listFiles()?.forEach { pubDir ->
            if (!pubDir.isDirectory) return@forEach
            pubDir.listFiles { f -> f.name.endsWith(SEGMENT_SUFFIX) }?.forEach { seg ->
                total += seg.length()
                segments += seg
            }
        }
        if (total <= maxTotalBytes) return

        // Évince du plus ancien au plus récent, en sautant les épinglés.
        segments.sortedBy { it.lastModified() }.forEach { seg ->
            if (total <= maxTotalBytes) return@forEach
            if (seg.name.removeSuffix(SEGMENT_SUFFIX) in pinned) return@forEach
            total -= seg.length()
            seg.delete()
        }
    }

    private fun pinnedKeys(): Set<String> {
        val keys = mutableSetOf<String>()
        baseDir.listFiles { f -> f.name.endsWith(PIN_SUFFIX) }?.forEach { pin ->
            runCatching { pin.readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { keys += it }
        }
        return keys
    }

    companion object {
        /** Version du format d'écriture (décision 2) — incrémenter si le format change. */
        const val FORMAT_VERSION = 1

        /** Plafond global ~200 Mo (décision 3). */
        const val MAX_TOTAL_BYTES = 200L * 1024 * 1024

        private const val SEGMENT_SUFFIX = ".seg"
        private const val PIN_SUFFIX = ".pin"
        private val NEWLINE = '\n'.code.toByte()
    }
}
