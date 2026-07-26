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
    val content: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
