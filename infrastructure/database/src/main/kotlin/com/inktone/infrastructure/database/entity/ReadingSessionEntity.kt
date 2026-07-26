package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(
        entity = PublicationEntity::class, parentColumns = ["id"],
        childColumns = ["publicationId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("publicationId")],
)
data class ReadingSessionEntity(
    @PrimaryKey val id: String,
    val publicationId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val mode: String,
    val sentencesRead: Int,
    val durationMs: Long,
)
