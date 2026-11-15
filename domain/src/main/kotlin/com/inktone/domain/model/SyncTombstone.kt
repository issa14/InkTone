package com.inktone.domain.model

/** Nature de l'élément supprimé que désigne une [SyncTombstone]. */
enum class TombstoneKind { BOOKMARK, ANNOTATION }

/**
 * Trace de la suppression d'un signet ou d'une annotation, transportée par la synchronisation pour
 * que les autres appareils la répliquent et que l'élément ne revienne pas (audit UX D61).
 */
data class SyncTombstone(
    val id: String,
    val kind: TombstoneKind,
    val deletedAt: Long,
) {
    init {
        require(id.isNotBlank()) { "id ne peut pas être vide" }
    }
}
