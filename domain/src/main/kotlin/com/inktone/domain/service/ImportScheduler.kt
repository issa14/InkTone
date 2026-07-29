package com.inktone.domain.service

/**
 * Déclenche un import en tâche de fond (Tâche 6.2bis) — abstraction du
 * domaine sur WorkManager/`ImportWorker` (infrastructure/worker), pour
 * que `feature/import` ne dépende jamais directement de WorkManager ni
 * d'`ImportWorker` (Blueprint §12.4 : `feature` n'a le droit de dépendre
 * que de `domain`/`core`, jamais d'`infrastructure`).
 */
interface ImportScheduler {
    fun enqueue(fileUris: List<String>)
}
