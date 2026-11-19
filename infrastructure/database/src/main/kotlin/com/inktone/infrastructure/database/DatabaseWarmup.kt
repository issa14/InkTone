package com.inktone.infrastructure.database

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ouvre la base dès le démarrage du processus, pendant le splash : sans lui,
 * la première requête de la Bibliothèque payait seule l'ouverture (≈ 0,5 s
 * mesurées sur Snapdragon 680) après que l'écran est devenu visible.
 */
@Singleton
class DatabaseWarmup @Inject constructor(private val database: InkToneDatabase) {
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { database.openHelper.writableDatabase }
    }
}
