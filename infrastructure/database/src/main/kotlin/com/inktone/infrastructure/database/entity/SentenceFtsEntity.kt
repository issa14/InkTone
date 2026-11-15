package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Index FTS4 sur le texte des phrases (Blueprint §6.9, Tâche 7.3). Une
 * ligne par `Sentence` extraite — pas par `Chapter` entier, pour permettre
 * un extrait précis autour du terme trouvé et un `Locator` exact vers le
 * résultat (`chapterIndex` + `charOffset` de la `Sentence`, pas juste
 * « quelque part dans ce chapitre »).
 */
@Fts4
@Entity(tableName = "sentence_fts")
data class SentenceFtsEntity(
    val publicationId: String,
    val chapterIndex: Int,
    val resourceHref: String,
    val charOffset: Int,
    val text: String, // colonne indexee par FTS4
)
