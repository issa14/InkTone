package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Session de lecture persistée (Lot Statistiques Palier 1).
 *
 * [visualDurationMs] et [ttsDurationMs] remplacent l'ancien champ
 * global `durationMs` pour les requêtes SQL-first. `durationMs`
 * est conservé pour compatibilité ascendante (migration 16→17)
 * mais n'est plus alimenté par le nouveau code — les projections
 * DAO calculent le total à la volée (`visualDurationMs + ttsDurationMs`).
 */
// Aucune clé étrangère vers `publications` (MIGRATION_33_34, audit UX D27) :
// supprimer un livre effaçait en cascade tout son temps de lecture, et
// totaux, historique et série reculaient rétroactivement. L'historique de
// lecture survit au retrait du livre.
@Entity(
    tableName = "reading_sessions",
    indices = [Index("publicationId"), Index("startedAt")],
)
data class ReadingSessionEntity(
    @PrimaryKey val id: String,
    val publicationId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val mode: String,
    val sentencesRead: Int,
    val durationMs: Long,
    val wordsRead: Int = 0,
    // Lot Statistiques Palier 1 — métriques séparées
    val visualDurationMs: Long = 0,
    val ttsDurationMs: Long = 0,
    // Mots lus à l'écran, hors narration (MIGRATION_40_41) : base de la vitesse de lecture.
    val visualWordsRead: Int = 0,
)
