package com.inktone.feature.importer

import androidx.lifecycle.ViewModel
import com.inktone.domain.service.ImportScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Déclenche un import (Tâche 6.2bis) — dépend uniquement de
 * [ImportScheduler] (domain), jamais de WorkManager ni d'`ImportWorker`
 * directement (Blueprint §12.4 : `feature` -> `domain`/`core`
 * uniquement, jamais `infrastructure`).
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importScheduler: ImportScheduler,
) : ViewModel() {

    /**
     * Appelée avec les URI déjà choisies par le sélecteur SAF (déclenché
     * depuis le Composable, pas depuis le ViewModel — `ActivityResult`
     * est lié au cycle de vie Activity/Compose, pas testable proprement
     * en dehors de cette couche).
     */
    fun enqueueImport(uris: List<String>) {
        importScheduler.enqueue(uris)
    }
}
