package com.inktone.domain.model

/**
 * Lot 11, tâche 11.2 — un seul fournisseur actif à la fois (exclusivité
 * mutuelle Drive/WebDAV, décision actée du plan). `WEBDAV` existe déjà
 * dans l'énumération bien qu'aucune implémentation n'existe avant le
 * palier B (tâche 11.6) — la carte WebDAV de l'écran Configuration doit
 * pouvoir référencer ce cas sans un second type ad hoc.
 */
enum class SyncProviderId { GOOGLE_DRIVE, WEBDAV }

/**
 * Compte de synchronisation lié — **unique** (Blueprint : `SyncUiState
 * .Configured` ne porte jamais une liste). `accountLabel` est l'adresse
 * du compte Google ou, plus tard, l'hôte WebDAV — jamais un jeton ni un
 * mot de passe.
 */
data class SyncAccount(
    val provider: SyncProviderId,
    val accountLabel: String,
    val linkedAt: Long,
    val lastSyncAt: Long? = null,
    val lastAutoSyncFailed: Boolean = false,
) {
    init {
        require(accountLabel.isNotBlank()) { "accountLabel ne peut pas être vide" }
    }
}

/**
 * Identifiant stable d'appareil (tâche 11.2), généré une seule fois au
 * premier accès et persisté — sert à la fois à la flotte d'appareils
 * (palier C) et à la détection de conflits (palier D), posé ici pour ne
 * pas le rétrofitter deux fois.
 */
data class DeviceIdentity(
    val id: String,
    val displayName: String,
) {
    init {
        require(id.isNotBlank()) { "id ne peut pas être vide" }
        require(displayName.isNotBlank()) { "displayName ne peut pas être vide" }
    }
}

/**
 * État de l'écran de synchronisation (tâche 11.2). Étendu par rapport à
 * la cible UX (`UX_FLOW_DESIGN.md`) : [Syncing] et le drapeau d'échec
 * persistant de [Configured] n'y figurent pas mais sont indispensables
 * — sans [Syncing], « Synchroniser maintenant » accepterait des clics
 * répétés et lancerait des transferts concurrents ; sans
 * `lastAutoSyncFailed`, une synchro automatique en échec pendant que
 * l'utilisateur est ailleurs n'aurait aucun moyen de le signaler (une
 * snackbar est déjà retombée).
 */
sealed interface SyncUiState {
    data object Unconfigured : SyncUiState
    data object Authenticating : SyncUiState
    data class Configured(val account: SyncAccount) : SyncUiState

    /** Édition en cours d'un fournisseur (WebDAV, à partir de la tâche 11.6 suivante). */
    data class Editing(val provider: SyncProviderId) : SyncUiState

    /** Transfert en cours — le bouton « Synchroniser maintenant » doit refuser tout nouveau clic tant que cet état est actif. */
    data class Syncing(val account: SyncAccount) : SyncUiState
}
