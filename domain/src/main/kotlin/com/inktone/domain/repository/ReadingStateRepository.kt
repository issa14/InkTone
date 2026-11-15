package com.inktone.domain.repository

import com.inktone.domain.model.ReadingState
import kotlinx.coroutines.flow.Flow

interface ReadingStateRepository {
    suspend fun get(publicationId: String): ReadingState?
    fun observe(publicationId: String): Flow<ReadingState?>

    /**
     * Toutes les positions de reprise, en flux.
     *
     * C'est ce qui rend la progression de la Bibliothèque réactive : elle en
     * dérivait auparavant d'un [getAll] ponctuel, rejoué à chaque retour sur
     * l'écran, au prix d'un rechargement visible de la grille entière.
     */
    fun observeAll(): Flow<List<ReadingState>>

    /** Necessaire pour BackupManager (Tache 8.5). */
    suspend fun getAll(): List<ReadingState>

    /** Persiste l'état de reprise. Appelée par les deux chemins K3 (TTS / manuel) — jamais simultanément. */
    suspend fun save(state: ReadingState)

    /**
     * Applique [transform] à l'état actuel de [publicationId] (`null` s'il n'existe
     * pas encore), lecture et écriture comprises, sans qu'une autre mise à jour
     * passée par cette méthode ne s'intercale. La narration (position, à chaque
     * phrase) et le Lecteur (surcharges, position manuelle) lisaient puis
     * réécrivaient la même ligne : un changement de thème pendant la narration
     * pouvait être écrasé par l'écriture de position qui avait lu l'ancienne
     * valeur (audit UX D20).
     */
    suspend fun update(publicationId: String, transform: (ReadingState?) -> ReadingState) {
        save(transform(get(publicationId)))
    }

    suspend fun delete(publicationId: String)
}
