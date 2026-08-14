package com.inktone.domain.service

/**
 * Causes d'échec OPDS distinguées explicitement (Lot 13, tâche 13.2.3) —
 * même discipline que [SyncFailureReason] (Lot 11) : pas de `else`
 * fourre-tout, chaque implémentation réseau/parseur mappe ses erreurs
 * concrètes (HTTP 401, 404, XML malformé, format non supporté, lien
 * d'acquisition non téléchargeable) vers l'une de ces causes.
 */
enum class OpdsFailureReason {
    UNAUTHORIZED,
    NOT_FOUND,
    MALFORMED_FEED,
    UNSUPPORTED_FORMAT,
    NON_DOWNLOADABLE_ACQUISITION,
    NETWORK,
    UNKNOWN,
}
