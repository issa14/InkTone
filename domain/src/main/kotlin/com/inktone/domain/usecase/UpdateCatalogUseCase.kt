package com.inktone.domain.usecase

import com.inktone.domain.repository.OpdsCatalogRepository
import com.inktone.domain.service.OpdsCredentialsStore

/**
 * Modifie un catalogue OPDS existant (Lot 13, polissage) — `add` fait
 * déjà l'upsert (même id remplace l'existant). Les identifiants Basic
 * Auth sont optionnels et fournis **ensemble** : champs vides = on
 * conserve les identifiants existants, jamais on ne les efface
 * silencieusement. Un changement de `rootUrl` invalide le template
 * OpenSearch découvert (remis à null).
 */
class UpdateCatalogUseCase(
    private val catalogRepository: OpdsCatalogRepository,
    private val credentialsStore: OpdsCredentialsStore,
) {
    suspend operator fun invoke(
        id: String,
        name: String,
        rootUrl: String,
        username: String?,
        password: String?,
    ) {
        require(name.isNotBlank()) { "name ne peut pas être vide" }
        require(rootUrl.isNotBlank()) { "rootUrl ne peut pas être vide" }

        val hasUsername = !username.isNullOrBlank()
        val hasPassword = !password.isNullOrBlank()
        require(hasUsername == hasPassword) { "username et password doivent être fournis ensemble" }

        val existing = catalogRepository.getById(id) ?: return
        val newRoot = rootUrl.trim()
        catalogRepository.add(
            existing.copy(
                name = name.trim(),
                rootUrl = newRoot,
                searchTemplateUrl = if (existing.rootUrl == newRoot) existing.searchTemplateUrl else null,
            ),
        )

        if (hasUsername && hasPassword) {
            credentialsStore.setCredentials(id, username!!.trim(), password!!)
        }
        // Champs vides : identifiants existants conservés (rien à faire).
    }
}
