package com.inktone.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Source de vérité unique pour la progression de lecture (fusion des anciennes tables
 * `progress`/`reading_progress` — voir architecture.md §11).
 *
 * Stockée de manière atomique à chaque transition de phrase (lecture TTS) ou à chaque
 * scroll/tap manuel débouncé, pour permettre une reprise exacte après arrêt, crash ou
 * passage en arrière-plan, ainsi que le calcul du badge `%` affiché dans la bibliothèque.
 */
@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class ReadingProgress(
    @PrimaryKey
    val bookId: String,
    val chapterIndex: Int,
    val sentenceIndex: Int,
    val characterOffset: Int,
    val totalProgressFraction: Float,
    val updatedAt: Long = System.currentTimeMillis(),
    /** "TTS" | "MANUAL_SCROLL" — informatif (debug/télémétrie), n'arbitre pas les écritures : la règle reste last-write-wins par [updatedAt]. */
    val source: String = "TTS"
)
