package com.inktone.feature.opds

import com.inktone.domain.model.OpdsCatalog
import com.inktone.domain.model.OpdsItem
import com.inktone.domain.service.OpdsFailureReason

/**
 * État unique de l'écran OPDS (Lot 13, ADR-023, ADR-012) — type scellé
 * unique porté par un seul `StateFlow`, jamais deux flux qui pourraient
 * diverger. [Dashboard] (tableau de bord des catalogues) et [Feed]
 * (navigation dans un flux) sont les deux faces du même écran : la
 * navigation interne au catalogue est un état de présentation pur, pas
 * une route de navigation.
 */
sealed interface OpdsUiState {
    data class Dashboard(
        val catalogs: List<OpdsCatalog> = emptyList(),
        val isLoading: Boolean = true,
    ) : OpdsUiState

    data class Feed(
        val title: String,
        val breadcrumb: List<String> = emptyList(),
        val items: List<OpdsItem> = emptyList(),
        val nextPageUrl: String? = null,
        val searchTemplateUrl: String? = null,
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val error: OpdsErrorUi? = null,
    ) : OpdsUiState
}

/** Erreur affichable d'un flux OPDS — cause typée + message humain. */
data class OpdsErrorUi(
    val reason: OpdsFailureReason,
    val message: String,
)

sealed interface OpdsIntent {
    data class OpenCatalog(val catalog: OpdsCatalog) : OpdsIntent
    data object GoBack : OpdsIntent
    data class AddCatalog(
        val name: String,
        val rootUrl: String,
        val username: String?,
        val password: String?,
    ) : OpdsIntent
    data class RemoveCatalog(val id: String) : OpdsIntent
}

/** Effets ponctuels (canal dédié MVI) — jamais dérivés de l'état. */
sealed interface OpdsEffect {
    data object CloseScreen : OpdsEffect
}
