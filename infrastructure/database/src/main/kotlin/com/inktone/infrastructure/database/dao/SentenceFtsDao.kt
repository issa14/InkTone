package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.inktone.infrastructure.database.entity.SentenceFtsEntity

@Dao
interface SentenceFtsDao {
    @Insert
    suspend fun insertAll(entities: List<SentenceFtsEntity>)

    /**
     * `text MATCH` et non `sentence_fts MATCH` : une correspondance sur la
     * TABLE porte sur toutes ses colonnes, y compris l'identifiant du livre,
     * le chemin interne du chapitre et l'offset. Chercher « html », « text »
     * ou un nombre remontait alors toutes les phrases des livres dont un
     * chemin de fichier contient ce mot.
     *
     * Les phrases d'un livre retiré de la bibliothèque restent dans l'index
     * (aucune purge à la suppression) : la jointure les écarte, sans quoi
     * elles menaient à « Publication introuvable ».
     *
     * Borné et ordonné : sans borne, un mot courant chargeait en mémoire
     * toutes les phrases correspondantes de toute la bibliothèque, à chaque
     * frappe. Ordre de lecture (livre, chapitre, position), seul ordre qui
     * garde un sens une fois la liste tronquée. Les colonnes d'une table
     * FTS4 n'ont pas d'affinité de type : les nombres sont convertis avant
     * de trier, faute de quoi « 10 » précéderait « 2 ».
     */
    @Query(
        """
        SELECT * FROM sentence_fts
        WHERE text MATCH :query
            AND publicationId IN (SELECT id FROM publications)
        ORDER BY publicationId, CAST(chapterIndex AS INTEGER), CAST(charOffset AS INTEGER)
        LIMIT :limit
        """,
    )
    suspend fun searchAll(query: String, limit: Int): List<SentenceFtsEntity>

    /** Même requête que [searchAll], restreinte à un livre. */
    @Query(
        """
        SELECT * FROM sentence_fts
        WHERE text MATCH :query
            AND publicationId = :publicationId
            AND publicationId IN (SELECT id FROM publications)
        ORDER BY CAST(chapterIndex AS INTEGER), CAST(charOffset AS INTEGER)
        LIMIT :limit
        """,
    )
    suspend fun searchInPublication(query: String, publicationId: String, limit: Int): List<SentenceFtsEntity>
}
