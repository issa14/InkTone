package com.inktone.infrastructure.database

import com.inktone.domain.service.ImportResultEntry
import com.inktone.domain.service.ImportResultsStore
import com.inktone.domain.usecase.ImportResult
import com.inktone.infrastructure.database.dao.ImportResultDao
import com.inktone.infrastructure.database.entity.ImportResultEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémente [ImportResultsStore] via Room (Palier A, Lot 5).
 *
 * Les résultats des sessions précédentes sont supprimés au démarrage
 * d'une nouvelle session ([beginSession]) — uniquement les sessions
 * AUTRES que la courante, pour rester sûr même si le worker a déjà
 * commencé à écrire les résultats de la nouvelle session (race-safe).
 * La consultation ([getResults]) survit à la mort du processus.
 */
@Singleton
class RoomImportResultsStore @Inject constructor(
    private val dao: ImportResultDao,
) : ImportResultsStore {

    override suspend fun beginSession(sessionId: String) {
        dao.deleteExcept(sessionId)
    }

    override suspend fun recordResult(sessionId: String, fileName: String, result: ImportResult) {
        val entity = ImportResultEntity(
            sessionId = sessionId,
            fileName = fileName,
            resultType = result.toTypeString(),
            message = when (result) {
                is ImportResult.Corrupted -> result.message
                is ImportResult.DrmProtected -> result.message
                else -> null
            },
            existingPublicationId = (result as? ImportResult.Duplicate)?.existingPublicationId,
        )
        dao.insert(entity)
    }

    override suspend fun getResults(sessionId: String): List<ImportResultEntry> =
        dao.getBySession(sessionId).map { it.toDomain() }

    override suspend fun clearSession(sessionId: String) {
        dao.deleteBySession(sessionId)
    }

    private fun ImportResult.toTypeString(): String = when (this) {
        is ImportResult.Success -> "success"
        is ImportResult.Duplicate -> "duplicate"
        is ImportResult.DrmProtected -> "drm_protected"
        is ImportResult.Corrupted -> "corrupted"
        is ImportResult.UnsupportedFormat -> "unsupported_format"
    }

    private fun ImportResultEntity.toDomain() = ImportResultEntry(
        fileName = fileName,
        resultType = resultType,
        message = message,
        existingPublicationId = existingPublicationId,
    )
}
