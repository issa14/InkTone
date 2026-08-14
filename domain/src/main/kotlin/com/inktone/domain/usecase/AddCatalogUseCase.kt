package com.inktone.domain.usecase

import com.inktone.domain.model.OpdsCatalog
import com.inktone.domain.repository.OpdsCatalogRepository
import com.inktone.domain.service.OpdsCredentialsStore
import java.util.UUID

/**
 * Ajoute un catalogue OPDS (Lot 13, tâche 13.1). Les identifiants Basic
 * Auth sont optionnels mais fournis **ensemble** (username + password) :
 * un seul des deux est un état invalide, rejeté plutôt que silencieusement
 * ignoré. Le template OpenSearch est découvert plus tard, en naviguant
 * sur le flux racine (`BrowseOpdsFeedUseCase`) — jamais saisi ici.
 */
class AddCatalogUseCase(
    private val catalogRepository: OpdsCatalogRepository,
    private val credentialsStore: OpdsCredentialsStore,
) {
    suspend operator fun invoke(
        name: String,
        rootUrl: String,
        username: String?,
        password: String?,
    ): String {
        require(name.isNotBlank()) { "name ne peut pas être vide" }
        require(rootUrl.isNotBlank()) { "rootUrl ne peut pas être vide" }

        val hasUsername = !username.isNullOrBlank()
        val hasPassword = !password.isNullOrBlank()
        require(hasUsername == hasPassword) { "username et password doivent être fournis ensemble" }

        val id = UUID.randomUUID().toString()
        catalogRepository.add(
            OpdsCatalog(
                id = id,
                name = name.trim(),
                rootUrl = rootUrl.trim(),
                searchTemplateUrl = null,
                hasCredentials = hasUsername,
            ),
        )
        if (hasUsername && hasPassword) {
            credentialsStore.setCredentials(id, username!!.trim(), password!!)
        }
        return id
    }
}
