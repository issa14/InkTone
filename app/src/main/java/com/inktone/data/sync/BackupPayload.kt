package com.inktone.data.sync

import com.inktone.data.database.entity.BookmarkEntity
import com.inktone.data.database.entity.PronunciationRule
import com.inktone.data.database.entity.ReadingProgress
import com.inktone.data.database.entity.ReadingSessionEntity

/**
 * Schéma JSON unifié exporté/chiffré pour la synchronisation cloud.
 *
 * Regroupe toutes les données utilisateur nécessaires à une restauration
 * complète de l'état de lecture InkTone.
 *
 * `version` = 2 depuis la fusion des tables `progress`/`reading_progress` (architecture.md
 * §11.5) — un ancien export `version 1` reste importable : son champ `progressEntries` (déjà
 * absent du schéma cible) est simplement ignoré par Gson, désérialisation structurelle.
 */
data class BackupPayload(
    val version: Int = 2,
    val appVersion: String = "0.1.0",
    val createdAt: Long = System.currentTimeMillis(),
    val averageWpm: Int = 0,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val pronunciationRules: List<PronunciationRule> = emptyList(),
    val readingProgressList: List<ReadingProgress> = emptyList(),
    val readingSessions: List<ReadingSessionEntity> = emptyList()
)
