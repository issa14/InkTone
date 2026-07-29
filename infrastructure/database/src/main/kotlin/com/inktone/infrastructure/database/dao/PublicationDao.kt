package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
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

    /**
     * `@Insert` sans `onConflict` = `OnConflictStrategy.ABORT` (défaut
     * Room) — délibéré, pas un oubli. `REPLACE` faisait un DELETE+INSERT
     * réel sur conflit d'id, qui cascadait silencieusement vers
     * `ReadingState`/`Bookmark`/`Annotation` (`ON DELETE CASCADE`) —
     * découvert Tâche 7.1 via `bootstrapAndOpenFixture` (scaffolding qui
     * réinsère le même id à chaque lancement). `ImportPublicationUseCase`
     * (Tâche 6.1) vérifie déjà les doublons par hash AVANT d'appeler cette
     * méthode : `REPLACE` n'était donc jamais censé se déclencher en usage
     * réel — juste un filet qui masquait un vrai bug de perte de données
     * au lieu de le faire échouer bruyamment. Un futur besoin de
     * « réimporter en gardant le même id » sera une méthode dédiée
     * (ex. `upsertPreservingChildren()`), jamais un `insert()` ambigu.
     */
    @Insert
    suspend fun insert(entity: PublicationEntity)

    @Update
    suspend fun update(entity: PublicationEntity)

    @Query("DELETE FROM publications WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE publications SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)
}
