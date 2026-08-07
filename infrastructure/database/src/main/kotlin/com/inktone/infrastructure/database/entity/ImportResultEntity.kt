package com.inktone.infrastructure.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Résultat d'import d'un fichier (Palier A, Lot 5).
 *
 * Chaque ligne correspond à un fichier traité dans une session
 * d'import. La table est purgée au démarrage d'une nouvelle session
 * ([ImportResultsStore.beginSession]) — les sessions précédentes
 * sont supprimées, seule la session en cours persiste.
 *
 * [sessionId] + [fileName] forme une clé unique implicite (index
 * composite) : un même fichier ne peut apparaître qu'une fois par
 * session.
 */
@Entity(
    tableName = "import_results",
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["session_id", "file_name"], unique = true),
    ],
)
data class ImportResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "result_type") val resultType: String,
    @ColumnInfo(name = "message") val message: String?,
    @ColumnInfo(name = "existing_publication_id") val existingPublicationId: String?,
)
