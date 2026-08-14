package com.inktone.domain.usecase

import com.inktone.domain.model.OpdsCatalog
import com.inktone.domain.repository.OpdsCatalogRepository
import kotlinx.coroutines.flow.Flow

/** Liste des catalogues OPDS (Lot 13, tâche 13.1) — flux Room direct. */
class GetCatalogsUseCase(
    private val catalogRepository: OpdsCatalogRepository,
) {
    operator fun invoke(): Flow<List<OpdsCatalog>> = catalogRepository.observeAll()
}
