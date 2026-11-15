package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Deux Locators aplatis (start/end), même principe que ReadingState. */
@Entity(
    tableName = "annotations",
    foreignKeys = [ForeignKey(
        entity = PublicationEntity::class, parentColumns = ["id"],
        childColumns = ["publicationId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("publicationId")],
)
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val publicationId: String,
    val startResourceHref: String,
    val startChapterIndex: Int,
    val startParagraphIndex: Int?,
    val startCharOffset: Int,
    val endResourceHref: String,
    val endChapterIndex: Int,
    val endParagraphIndex: Int?,
    val endCharOffset: Int,
    val color: String,
    val kind: String = "HIGHLIGHT",
    val content: String?,
    val excerpt: String? = null,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    // Contexte textuel de chaque borne (ADR-027, MIGRATION_37_38).
    val startTextBefore: String? = null,
    val startTextAfter: String? = null,
    val endTextBefore: String? = null,
    val endTextAfter: String? = null,
)
