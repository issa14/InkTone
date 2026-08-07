package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inktone.infrastructure.database.entity.ImportResultEntity

@Dao
interface ImportResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ImportResultEntity)

    @Query("SELECT * FROM import_results WHERE session_id = :sessionId ORDER BY CASE WHEN result_type IN ('corrupted', 'drm_protected', 'unsupported_format') THEN 0 WHEN result_type = 'duplicate' THEN 1 ELSE 2 END, file_name ASC")
    suspend fun getBySession(sessionId: String): List<ImportResultEntity>

    /**
     * Purge les résultats de TOUTES les sessions SAUF [sessionId].
     *
     * Race-safe : si le worker a déjà commencé à écrire les résultats
     * de la nouvelle session (beginSession peut être appelé
     * asynchronement, en parallèle du démarrage de WorkManager), la
     * session courante n'est jamais effacée.
     */
    @Query("DELETE FROM import_results WHERE session_id != :sessionId")
    suspend fun deleteExcept(sessionId: String)

    @Query("DELETE FROM import_results WHERE session_id = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
