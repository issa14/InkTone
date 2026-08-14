package com.inktone.core.testing.fake

import com.inktone.domain.service.OpdsDownloadEvent
import com.inktone.domain.service.OpdsDownloadObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Fake de l'observateur de téléchargement OPDS — bus en mémoire testable, avec historique des événements publiés. */
class FakeOpdsDownloadObserver : OpdsDownloadObserver {
    private val events = MutableSharedFlow<OpdsDownloadEvent>(extraBufferCapacity = 16)
    val published = mutableListOf<OpdsDownloadEvent>()

    override fun observe(): Flow<OpdsDownloadEvent> = events

    override suspend fun publish(event: OpdsDownloadEvent) {
        published += event
        events.emit(event)
    }
}
