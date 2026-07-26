package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Locator aplati en 4 colonnes (resourceHref/chapterIndex/paragraphIndex/
 * charOffset) — voir le mapper dédié en Tâche 2.5, réutilisé pour
 * Bookmark et Annotation, jamais réimplémenté ad hoc.
 * overrideTheme/overrideFontSize nullables : ReadingOverrides est
 * optionnel dans le domaine (Blueprint §3.3) ; nul sur les deux colonnes
 * = aucune surcharge, pas une table séparée pour un seul objet optionnel.
 */
@Entity(
    tableName = "reading_states",
    foreignKeys = [ForeignKey(
        entity = PublicationEntity::class, parentColumns = ["id"],
        childColumns = ["publicationId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("publicationId", unique = true)],
)
data class ReadingStateEntity(
    @PrimaryKey val publicationId: String,
    val resourceHref: String,
    val chapterIndex: Int,
    val paragraphIndex: Int?,
    val charOffset: Int,
    val lastReadAt: Long,
    val voiceProfileId: String?,
    val overrideTheme: String?,
    val overrideFontSize: Int?,
)
