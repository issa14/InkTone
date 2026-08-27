package com.inktone.core.testing.fake

import com.inktone.domain.service.AudioSegment
import com.inktone.domain.service.TtsCacheKey
import com.inktone.domain.service.TtsSegmentCache

/**
 * Fake pour [TtsSegmentCache] — stocke les segments en mémoire et expose
 * l'état pour les assertions (`stored`, `pinned`, `deletedPublications`).
 */
class FakeTtsSegmentCache : TtsSegmentCache {

    val stored = mutableMapOf<String, AudioSegment>()
    val pinned = mutableMapOf<String, TtsCacheKey>()
    val deletedPublications = mutableListOf<String>()

    override suspend fun get(publicationId: String, key: TtsCacheKey): AudioSegment? =
        stored[key.value]

    override suspend fun put(publicationId: String, key: TtsCacheKey, segment: AudioSegment) {
        stored[key.value] = segment
    }

    override suspend fun pinResumePoint(publicationId: String, key: TtsCacheKey) {
        pinned[publicationId] = key
    }

    override suspend fun deletePublication(publicationId: String) {
        deletedPublications += publicationId
    }
}
