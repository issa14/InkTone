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
        /** Catalogue courant — résout l'auth Basic Auth des couvertures, jamais affiché. */
        val catalogId: String? = null,
        /** État du téléchargement de chaque livre, par lien d'acquisition (audit UX C10). */
        val downloads: Map<String, OpdsBookDownload> = emptyMap(),
    ) : OpdsUiState
}

/** Erreur affichable d'un flux OPDS — cause typée + message humain. */
data class OpdsErrorUi(
    val reason: OpdsFailureReason,
    val message: String,
)

/**
 * Message montré pour un échec OPDS : dérivé du motif, jamais du texte
 * technique du client HTTP ou du parseur (audit UI P1-13).
 */
internal fun opdsFailureMessage(reason: OpdsFailureReason): String = when (reason) {
    OpdsFailureReason.UNAUTHORIZED -> "Ce catalogue demande un nom d'utilisateur et un mot de passe valides."
    OpdsFailureReason.NOT_FOUND -> "Cette page du catalogue est introuvable."
    OpdsFailureReason.MALFORMED_FEED -> "Le catalogue a renvoyé une page illisible."
    OpdsFailureReason.UNSUPPORTED_FORMAT -> "Ce format de livre n'est pas pris en charge."
    OpdsFailureReason.NON_DOWNLOADABLE_ACQUISITION -> "Ce livre ne peut pas être téléchargé directement."
    OpdsFailureReason.NETWORK -> "Le catalogue est injoignable. Vérifiez la connexion."
    OpdsFailureReason.UNKNOWN -> "Le catalogue n'a pas pu être chargé."
}

sealed interface OpdsIntent {
    data class OpenCatalog(val catalog: OpdsCatalog) : OpdsIntent
    data class OpenNavigation(val item: OpdsItem.Navigation) : OpdsIntent
    data object GoBack : OpdsIntent
    data class AddCatalog(
        val name: String,
        val rootUrl: String,
        val username: String?,
        val password: String?,
    ) : OpdsIntent
    data class RemoveCatalog(val id: String) : OpdsIntent
    data class UpdateCatalog(
        val id: String,
        val name: String,
        val rootUrl: String,
        val username: String?,
        val password: String?,
    ) : OpdsIntent
    data class LoadNextPage(val nextPageUrl: String) : OpdsIntent
    /** Recharge le flux courant apres un echec — sans cet intent, une erreur reseau est un cul-de-sac. */
    data object RetryFeed : OpdsIntent
    data class Search(val query: String) : OpdsIntent
    data class DownloadBook(val item: OpdsItem.Book) : OpdsIntent
}

/** Effets ponctuels (canal dédié MVI) — jamais dérivés de l'état. */
sealed interface OpdsEffect {
    data object CloseScreen : OpdsEffect
    data class ShowMessage(val message: String) : OpdsEffect
    data class DownloadComplete(val bookTitle: String, val publicationId: String) : OpdsEffect
}

/** Avancement du téléchargement d'un livre de catalogue. */
enum class OpdsBookDownload { IN_PROGRESS, DONE }
