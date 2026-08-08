package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
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
@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(
        entity = PublicationEntity::class, parentColumns = ["id"],
        childColumns = ["publicationId"], onDelete = ForeignKey.CASCADE,
    )],
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
)
