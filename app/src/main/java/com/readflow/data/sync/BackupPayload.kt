package com.readflow.data.sync

import com.readflow.data.database.entity.BookmarkEntity
import com.readflow.data.database.entity.PronunciationRule
import com.readflow.data.database.entity.ProgressEntity
import com.readflow.data.database.entity.ReadingProgress
import com.readflow.data.database.entity.ReadingSessionEntity

/**
 * Schéma JSON unifié exporté/chiffré pour la synchronisation cloud.
 *
 * Regroupe toutes les données utilisateur nécessaires à une restauration
 * complète de l'état de lecture ReadFlow.
 */
data class BackupPayload(
    val version: Int = 1,
    val appVersion: String = "0.1.0",
    val createdAt: Long = System.currentTimeMillis(),
    val averageWpm: Int = 0,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val pronunciationRules: List<PronunciationRule> = emptyList(),
    val progressEntries: List<ProgressEntity> = emptyList(),
    val readingProgressList: List<ReadingProgress> = emptyList(),
    val readingSessions: List<ReadingSessionEntity> = emptyList()
)
