package com.inktone.data.repository

import com.inktone.domain.model.OpdsCatalog
import com.inktone.domain.repository.OpdsCatalogRepository
import com.inktone.domain.service.OpdsCredentialsStore
import com.inktone.infrastructure.database.dao.CatalogDao
import com.inktone.infrastructure.database.entity.CatalogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implémentation Room de [OpdsCatalogRepository] (Lot 13, tâche 13.1).
 * `hasCredentials` est dérivé de [OpdsCredentialsStore] (chiffré,
 * keyé par id) — jamais stocké en base, jamais lu depuis la base.
 */
class RoomOpdsCatalogRepository @Inject constructor(
    private val dao: CatalogDao,
    private val credentialsStore: OpdsCredentialsStore,
) : OpdsCatalogRepository {

    override fun observeAll(): Flow<List<OpdsCatalog>> =
        dao.observeAll().map { entities ->
            entities.map { it.toDomain(credentialsStore.hasCredentials(it.id)) }
        }

    override suspend fun getById(id: String): OpdsCatalog? =
        dao.getById(id)?.toDomain(credentialsStore.hasCredentials(id))

    override suspend fun add(catalog: OpdsCatalog) {
        // Upsert : on préserve `createdAt` d'un catalogue déjà en base
        // (l'édition ne doit pas réordonner la liste).
        val createdAt = dao.getById(catalog.id)?.createdAt ?: System.currentTimeMillis()
        dao.upsert(catalog.toEntity(createdAt))
    }

    override suspend fun remove(id: String) = dao.delete(id)

    override suspend fun updateSearchTemplate(id: String, template: String?) {
        dao.getById(id)?.let { dao.upsert(it.copy(searchTemplateUrl = template)) }
    }
}

private fun CatalogEntity.toDomain(hasCredentials: Boolean) = OpdsCatalog(
    id = id,
    name = name,
    rootUrl = rootUrl,
    searchTemplateUrl = searchTemplateUrl,
    hasCredentials = hasCredentials,
)

private fun OpdsCatalog.toEntity(createdAt: Long) = CatalogEntity(
    id = id,
    name = name,
    rootUrl = rootUrl,
    searchTemplateUrl = searchTemplateUrl,
    createdAt = createdAt,
)
