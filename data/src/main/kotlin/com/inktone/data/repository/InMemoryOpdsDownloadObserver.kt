package com.inktone.data.repository

import com.inktone.domain.service.OpdsDownloadEvent
import com.inktone.domain.service.OpdsDownloadObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bus en mémoire des fins de téléchargement OPDS (Lot 13, tâche 13.3.4) —
 * le worker (`infrastructure:worker`) et l'UI vivent dans le même
 * processus : un `MutableSharedFlow` suffit, aucun besoin de persister.
 * Un événement perdu (app tuée pendant le téléchargement) ne perd pas le
 * livre lui-même, qui est déjà en bibliothèque — la snackbar n'est qu'un
 * confort.
 */
@Singleton
class InMemoryOpdsDownloadObserver @Inject constructor() : OpdsDownloadObserver {
    private val events = MutableSharedFlow<OpdsDownloadEvent>(extraBufferCapacity = 16)

    override fun observe(): Flow<OpdsDownloadEvent> = events

    override suspend fun publish(event: OpdsDownloadEvent) {
        events.emit(event)
    }
}
