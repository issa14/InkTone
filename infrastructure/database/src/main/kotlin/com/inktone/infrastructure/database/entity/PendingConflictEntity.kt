package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Conflit de position en attente d'arbitrage (Lot 11, tâche 11.10) — au
 * plus un par publication (`publicationId` = clé primaire), doit
 * survivre à un redémarrage du processus jusqu'à la prochaine ouverture
 * de l'app. Deux Locators aplatis (local/distant), même principe que
 * `ReadingStateEntity`/`AnnotationEntity`.
 */
@Entity(tableName = "pending_conflicts")
data class PendingConflictEntity(
    @PrimaryKey val publicationId: String,
    val bookTitle: String,
    val localResourceHref: String,
    val localChapterIndex: Int,
    val localParagraphIndex: Int?,
    val localCharOffset: Int,
    val localDeviceLabel: String,
    val localAt: Long,
    val localChapterCount: Int,
    val remoteResourceHref: String,
    val remoteChapterIndex: Int,
    val remoteParagraphIndex: Int?,
    val remoteCharOffset: Int,
    val remoteDeviceLabel: String,
    val remoteAt: Long,
    val remoteChapterCount: Int,
)
