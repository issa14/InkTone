package com.inktone.data.ttscache

import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.TtsCacheKey
import com.inktone.domain.service.WordTimestamp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Lot 22, Palier B — audio et timestamps sont indivisibles dans le cache
 * (correction 1) : un round-trip doit restituer le MÊME audio et les MÊMES
 * timestamps, jamais ceux d'une autre synthèse. Vérifie aussi la purge par
 * livre, l'éviction LRU et la survie des segments épinglés.
 */
class TtsSegmentCacheImplTest {

    private fun segment(text: ByteArray, durationMs: Long = 100): AudioSegment = AudioSegment(
        audioData = text,
        durationMs = durationMs,
        wordTimestamps = listOf(
            WordTimestamp(word = "mot", startMs = 0, endMs = 50, charOffset = 0),
        ),
        sampleRate = 22_050,
    )

    private fun tempStore(maxTotalBytes: Long = TtsSegmentCacheImpl.MAX_TOTAL_BYTES): TtsSegmentCacheImpl {
        val dir = File.createTempFile("tts", "test").apply { delete() }
        dir.mkdirs()
        return TtsSegmentCacheImpl(dir, maxTotalBytes)
    }

    @Test
    fun `un round-trip restitue l'audio et ses timestamps`() = runTest {
        val store = tempStore()
        val key = TtsCacheKey("key-1")
        store.put("pub-1", key, segment("audio".toByteArray()))

        val loaded = store.get("pub-1", key)

        assertNotNull(loaded)
        assertArrayEquals("audio".toByteArray(), loaded!!.audioData)
        assertEquals(1, loaded.wordTimestamps.size)
        assertEquals("mot", loaded.wordTimestamps[0].word)
        assertEquals(50, loaded.wordTimestamps[0].endMs)
        assertEquals(22_050, loaded.sampleRate)
    }

    @Test
    fun `un segment absent retourne null`() = runTest {
        val store = tempStore()
        assertNull(store.get("pub-1", TtsCacheKey("absent")))
    }

    @Test
    fun `la purge par livre supprime ses segments`() = runTest {
        val store = tempStore()
        store.put("pub-1", TtsCacheKey("k1"), segment("a".toByteArray()))
        store.put("pub-1", TtsCacheKey("k2"), segment("b".toByteArray()))

        store.deletePublication("pub-1")

        assertNull(store.get("pub-1", TtsCacheKey("k1")))
        assertNull(store.get("pub-1", TtsCacheKey("k2")))
    }

    @Test
    fun `l'eviction LRU epingle le segment de reprise`() = runTest {
        // Plafond minuscule (300 o) pour forcer l'éviction sans écrire 200 Mo :
        // un segment y tient, deux non.
        val store = tempStore(maxTotalBytes = 300)
        val pinned = TtsCacheKey("pinned")
        val evicted = TtsCacheKey("evicted")

        // Le segment épinglé est posé PUIS épinglé, avant d'ajouter le second
        // segment dont l'ajout déclenche l'éviction.
        store.put("pub-1", pinned, segment(ByteArray(10)))
        store.pinResumePoint("pub-1", pinned)
        store.put("pub-1", evicted, segment(ByteArray(500)))

        // Le segment épinglé survit à l'éviction, l'autre est évincé.
        assertNotNull("le segment épinglé doit survivre", store.get("pub-1", pinned))
        assertNull("le segment non épinglé doit être évincé", store.get("pub-1", evicted))
    }
}
