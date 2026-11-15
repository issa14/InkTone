package com.inktone.domain.repository

import com.inktone.domain.model.SyncTombstone

/** Tombes de suppression locales (audit UX D61). Écrites par les suppressions de signets et d'annotations. */
interface SyncTombstoneRepository {
    suspend fun getAll(): List<SyncTombstone>

    /** Enregistre (ou remplace) la tombe reçue d'un autre appareil, pour la relayer à son tour. */
    suspend fun record(tombstone: SyncTombstone)

    /** Oublie la tombe d'un élément, par exemple recréé ou modifié après sa suppression. */
    suspend fun remove(id: String)

    /** Purge les tombes plus anciennes que [before] (epoch ms) : elles ont eu le temps de se propager. */
    suspend fun purgeBefore(before: Long)
}
