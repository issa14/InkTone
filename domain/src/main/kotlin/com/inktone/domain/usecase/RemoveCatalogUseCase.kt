package com.inktone.domain.usecase

import com.inktone.domain.repository.OpdsCatalogRepository
import com.inktone.domain.service.OpdsCredentialsStore

/**
 * Supprime un catalogue OPDS (Lot 13, tâche 13.1) — et purge ses
 * identifiants chiffrés dans la même opération (point 4.3 du plan) :
 * jamais d'identifiants orphelins sans propriétaire.
 */
class RemoveCatalogUseCase(
    private val catalogRepository: OpdsCatalogRepository,
    private val credentialsStore: OpdsCredentialsStore,
) {
    suspend operator fun invoke(id: String) {
        catalogRepository.remove(id)
        credentialsStore.clearCredentials(id)
    }
}
