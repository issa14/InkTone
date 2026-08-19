package com.inktone.domain.service

/**
 * Connexion WebDAV (Lot 19) — connecter, déconnecter. Consommé
 * uniquement depuis `data` (`WebDavSyncLinker`), qui le traduit vers
 * `app` sans exposer un type `domain` (Blueprint §12.4). L'implémentation
 * (`infrastructure/sync`) détient le réseau et les identifiants ; le
 * contrat reste pur Kotlin ici.
 *
 * `connect()` matérialise l'exclusivité mutuelle WebDAV/Google Drive :
 * [com.inktone.domain.repository.SyncAccountRepository.save] remplace
 * tout compte existant, et la carte Google Drive est grisée côté UI quand
 * WebDAV est actif (et réciproquement).
 */
interface WebDavSyncService {
    /**
     * Teste la connexion puis, en cas de succès, persiste les identifiants
     * et le compte WebDAV (`accountLabel` = hôte de l'URL). En cas
     * d'échec, ne persiste rien.
     */
    suspend fun connect(url: String, username: String, password: String): SyncOperationResult

    /** Oublie les identifiants et, si le compte actif est WebDAV, le compte lui-même. */
    suspend fun disconnect()
}
