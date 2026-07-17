package com.readflow.service.audio

import com.readflow.domain.model.SynthesisResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AudioCacheManagerTest {

    private lateinit var cache: AudioCacheManager

    @BeforeEach
    fun setUp() {
        cache = AudioCacheManager()
    }

    private fun makeResult(text: String, sampleCount: Int): SynthesisResult {
        return SynthesisResult(
            samples = FloatArray(sampleCount) { 0.5f },
            sampleRate = 22050,
            text = text,
            voiceLabel = "Jessica",
            synthesisTimeMs = 100,
            audioDurationMs = 500
        )
    }

    @Test
    fun `put and get — cache hit`() {
        val result = makeResult("Bonjour", 1000)
        cache.put("key1", result)
        val cached = cache.get("key1")
        assertNotNull(cached)
        assertEquals("Bonjour", cached?.text)
        assertEquals(1, cache.hitCount)
        assertEquals(0, cache.missCount)
    }

    @Test
    fun `get missing key — cache miss`() {
        val cached = cache.get("nonexistent")
        assertNull(cached)
        assertEquals(1, cache.missCount)
        assertEquals(0, cache.hitCount)
    }

    @Test
    fun `put replaces existing key`() {
        val r1 = makeResult("Bonjour", 1000)
        val r2 = makeResult("Salut", 2000)
        cache.put("key1", r1)
        cache.put("key1", r2) // replace
        val cached = cache.get("key1")
        assertEquals("Salut", cached?.text)
        assertEquals(1, cache.size())
    }

    @Test
    fun `clear empties cache`() {
        cache.put("k1", makeResult("a", 1000))
        cache.put("k2", makeResult("b", 1000))
        assertEquals(2, cache.size())
        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun `LRU eviction when cache is full`() {
        // Remplir le cache avec des données > 20 Mo par entrées de ~2 Mo
        val sampleCount = 500_000
        var i = 0
        while (i < 20) {
            cache.put("key$i", makeResult("text$i", sampleCount))
            i++
        }
        // Le cache a 20 Mo de capacité, chaque entrée fait ~2 Mo → max 10 entrées
        val count = cache.size()
        assertTrue(count > 0, "Le cache devrait contenir des entrées (trouvé: $count)")
        assertTrue(count <= 10, "Le cache devrait avoir évincé des entrées (trouvé: $count)")
    }

    @Test
    fun `hitRatio computation`() {
        cache.get("miss1")
        cache.get("miss2")
        cache.put("hit1", makeResult("a", 1000))
        cache.get("hit1")
        cache.get("hit1")
        // 2 hits, 2 misses = 0.5
        assertEquals(0.5f, cache.hitRatio, 0.01f)
    }

    @Test
    fun `sizeOf computes correct byte size`() {
        val result = makeResult("test", 1000)
        val size = AudioCacheManager.sizeOf(result)
        // FloatArray: 1000*4 + 24 overhead = 4024
        // String "test": 4*2 + 38 overhead = 46
        // SynthesisResult object: 32
        // Entry wrapper: 24
        assertEquals(4024L + 46L + 32L + 24L, size)
    }

    @Test
    fun `entry too large is rejected`() {
        // Créer une entrée > 20 Mo (MAX_SIZE_BYTES = 20 Mo)
        val hugeCount = (21L * 1024 * 1024 / 4).toInt()
        val result = makeResult("huge", hugeCount)
        cache.put("huge", result)
        assertEquals(0, cache.size())
    }
}
