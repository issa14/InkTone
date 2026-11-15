package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(
        entity = PublicationEntity::class, parentColumns = ["id"],
        childColumns = ["publicationId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("publicationId")],
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val publicationId: String,
    val resourceHref: String,
    val chapterIndex: Int,
    val paragraphIndex: Int?,
    val charOffset: Int,
    val title: String?,
    val note: String?,
    val excerpt: String? = null,
    val isPinned: Boolean = false,
    val createdAt: Long,
    // Contexte textuel de la position (ADR-027, MIGRATION_37_38).
    val textBefore: String? = null,
    val textAfter: String? = null,
    // Dernière modification (MIGRATION_38_39) : départage deux versions d'un même signet à la synchronisation.
    val updatedAt: Long = 0,
)
