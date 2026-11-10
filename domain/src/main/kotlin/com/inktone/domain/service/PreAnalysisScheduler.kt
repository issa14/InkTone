package com.inktone.domain.service

/**
 * Planifie la régénération en tâche de fond des pré-analyses absentes ou
 * périmées (`RefreshStalePreAnalysesUseCase`).
 *
 * Idempotent : une demande pendant qu'une régénération est déjà planifiée
 * ou en cours n'en lance pas une seconde. Peut donc être appelé librement —
 * au démarrage de l'application, et dès qu'une ouverture de livre constate
 * une pré-analyse inutilisable.
 */
fun interface PreAnalysisScheduler {
    fun scheduleRefresh()
}
