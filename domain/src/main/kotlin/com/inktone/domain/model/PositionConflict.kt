package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator

/**
 * Une position de lecture pour un livre, telle que connue par un
 * appareil (tâche 11.10). `chapterIndex`/`chapterCount` viennent avec
 * le conflit plutôt que d'être recalculés côté UI — l'arbitrage doit
 * pouvoir afficher « Chapitre 12, 34,7 % » sans que
 * `SyncConflictBottomSheet` ait besoin de connaître `Publication`.
 */
data class ReadingPositionSnapshot(
    val locator: Locator,
    val deviceLabel: String,
    val at: Long,
    val chapterIndex: Int,
    val chapterCount: Int,
) {
    val progressFraction: Float get() = if (chapterCount > 0) (chapterIndex.toFloat() / chapterCount).coerceIn(0f, 1f) else 0f
}

/**
 * Conflit de position de lecture (tâche 11.10) — **uniquement** la
 * position, jamais les annotations ni les marque-pages (fusion
 * silencieuse, aucun conflit possible pour ces catégories). La synchro
 * en arrière-plan ne tranche jamais elle-même : elle détecte, met en
 * file via [com.inktone.domain.repository.ConflictQueueRepository] ;
 * la présentation à l'utilisateur (bottom sheet, à la prochaine
 * ouverture de l'app) vit côté `feature`.
 */
data class PositionConflict(
    val publicationId: String,
    val bookTitle: String,
    val local: ReadingPositionSnapshot,
    val remote: ReadingPositionSnapshot,
)
