package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.inktone.infrastructure.database.entity.LibraryItemView
import kotlinx.coroutines.flow.Flow

/**
 * Lecture de la vue globale marque-pages + annotations (Lot 4, tâche
 * 4.4). Filtre, recherche et tri s'exécutent tous ici, jamais en
 * mémoire côté ViewModel — voir [com.inktone.infrastructure.database.entity.LibraryItemView].
 */
@Dao
interface LibraryItemDao {
    @Query(
        """
        SELECT * FROM library_items
        WHERE
            (:typeFilter IS NULL
                OR (:typeFilter = 'BOOKMARK' AND type = 'bookmark')
                OR (:typeFilter = 'HIGHLIGHT' AND type = 'annotation' AND note IS NULL)
                OR (:typeFilter = 'NOTE' AND type = 'annotation' AND note IS NOT NULL))
            AND (:searchQuery = ''
                OR excerpt LIKE '%' || :searchQuery || '%'
                OR note LIKE '%' || :searchQuery || '%'
                OR publicationTitle LIKE '%' || :searchQuery || '%')
        ORDER BY
            isPinned DESC,
            CASE WHEN :alphabetical THEN publicationTitle END ASC,
            CASE WHEN :alphabetical THEN NULL ELSE createdAt END DESC
        """,
    )
    fun observe(
        typeFilter: String?,
        searchQuery: String,
        alphabetical: Boolean,
    ): Flow<List<LibraryItemView>>
}
