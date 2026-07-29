package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.inktone.infrastructure.database.entity.PublicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PublicationDao {
    @Query("SELECT * FROM publications ORDER BY lastOpened DESC")
    fun observeAll(): Flow<List<PublicationEntity>>

    /**
     * Filtre structurel (Tâche 6.5) — `mode` reçoit le nom de [FilterMode]
     * (`FAVORITES`, `SERIES`, `UNREAD`, `IN_PROGRESS`, `READ`) ; `TAG` et
     * `BY_AUTHOR` ne passent pas par cette requête (listes sérialisées en
     * colonne, filtrées côté Kotlin par `RoomPublicationRepository` sur le
     * résultat d'`observeAll()` — une seule requête au total, pas de N+1,
     * K8). Jointure `reading_states` unique (§6.5.3) : pas une requête par
     * publication pour connaître son état de lecture.
     *
     * `READ` = dernier chapitre atteint (`rs.chapterIndex >= p.chapterCount
     * - 1`) — définition documentée et volontairement limitée, voir
     * [com.inktone.domain.model.FilterMode].
     */
    @Query(
        """
        SELECT p.* FROM publications p
        LEFT JOIN reading_states rs ON p.id = rs.publicationId
        WHERE
            (:mode != 'FAVORITES' OR p.isFavorite = 1)
            AND (:mode != 'SERIES' OR p.seriesName = :value)
            AND (:mode != 'UNREAD' OR rs.publicationId IS NULL)
            AND (:mode != 'IN_PROGRESS' OR (rs.publicationId IS NOT NULL AND rs.chapterIndex < p.chapterCount - 1))
            AND (:mode != 'READ' OR (rs.publicationId IS NOT NULL AND rs.chapterIndex >= p.chapterCount - 1))
        ORDER BY p.lastOpened DESC
        """,
    )
    fun observeFiltered(mode: String, value: String?): Flow<List<PublicationEntity>>

    @Query("SELECT * FROM publications WHERE id = :id")
    suspend fun getById(id: String): PublicationEntity?

    @Query("SELECT * FROM publications WHERE fileHash = :hash LIMIT 1")
    suspend fun getByFileHash(hash: String): PublicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PublicationEntity)

    @Update
    suspend fun update(entity: PublicationEntity)

    @Query("DELETE FROM publications WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE publications SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)
}
