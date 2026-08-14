package com.inktone.domain.repository

import com.inktone.domain.model.OpdsCatalog
import kotlinx.coroutines.flow.Flow

/**
 * Persistance des catalogues OPDS (Lot 13, tâche 13.1). Les identifiants
 * ne transitent jamais par ce repository — ils relèvent de
 * [com.inktone.domain.service.OpdsCredentialsStore].
 */
interface OpdsCatalogRepository {
    fun observeAll(): Flow<List<OpdsCatalog>>
    suspend fun getById(id: String): OpdsCatalog?

    /** [catalog].id est la clé ; un même id remplace l'existant. */
    suspend fun add(catalog: OpdsCatalog)
    suspend fun remove(id: String)

    /** Persiste le template OpenSearch annoncé par le flux racine du catalogue. */
    suspend fun updateSearchTemplate(id: String, template: String?)
}
