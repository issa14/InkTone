package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Trace d'une suppression de signet ou d'annotation, pour que la synchronisation la propage
 * au lieu de laisser l'élément revenir depuis l'instantané d'un autre appareil (audit UX D61).
 * [kind] vaut `BOOKMARK` ou `ANNOTATION`.
 */
@Entity(tableName = "sync_tombstones")
data class SyncTombstoneEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val deletedAt: Long,
)
