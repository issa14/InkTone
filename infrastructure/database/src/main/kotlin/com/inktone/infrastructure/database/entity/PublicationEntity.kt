package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "publications",
    indices = [
        Index("title"), Index("lastOpened"), Index("seriesName"),
        Index("fileHash", unique = true),
    ],
)
data class PublicationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String?,
    val authors: List<String>,
    val publisher: String?,
    val language: String?,
    val description: String?,
    val coverUri: String?,
    val format: String,
    val fileUri: String,
    val fileHash: String,
    val fileSize: Long,
    val chapterCount: Int,
    val pageCount: Int? = null,
    // Longueur de chaque chapitre, CSV d'entiers dans l'ordre de lecture
    // (MIGRATION_32_33). NULL = pas encore calculée, ou PDF.
    val chapterLengths: String? = null,
    val seriesName: String?,
    val seriesIndex: Float?,
    val isFavorite: Boolean,
    val isPinned: Boolean = false,
    val subjects: List<String>,
    val isDrmProtected: Boolean,
    val importDate: Long,
    val lastOpened: Long?,
    // Version du parseur pour laquelle les positions de ce livre ont été ancrées
    // (ADR-027, MIGRATION_37_38) ; 0 = pas encore.
    val locatorAnchorVersion: Int = 0,
    // Version de l'index plein texte complet du livre (MIGRATION_39_40) ; 0 = incomplet ou à refaire.
    val searchIndexVersion: Int = 0,
)
