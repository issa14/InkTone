package com.inktone.feature.reader

import com.inktone.domain.model.Chapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Cache minimal a une entree : le chapitre courant est deja en memoire
 * (fait partie de state.chapters, tout le DocumentModel est charge a
 * l'ouverture pour la Phase 4 — pas de chargement paresseux par chapitre
 * pour l'instant). "Precharger" ici signifie donc : preparer le rendu
 * (mesures de mise en page Compose) du chapitre suivant en arriere-plan,
 * pas charger des donnees qui sont deja toutes en memoire.
 *
 * Cette simplification est assumee pour la Phase 4 — un chargement
 * veritablement paresseux (chapitre par chapitre depuis le fichier EPUB,
 * plutot que tout le DocumentModel d'un coup) est un sujet de
 * performance a mesurer en Tache 4.9 avant de decider s'il est
 * necessaire pour de gros EPUB (budget memoire, §11.2).
 */
class ChapterPreloader(private val scope: CoroutineScope) {

    private var preloadJob: Job? = null

    fun preload(chapter: Chapter?, onReady: (Chapter) -> Unit) {
        preloadJob?.cancel()
        if (chapter == null) return
        preloadJob = scope.launch {
            // Placeholder pour un travail de preparation reel (ex.
            // pre-tokenisation d'affichage) — vide pour l'instant car le
            // DocumentModel est deja entierement en memoire (voir note
            // ci-dessus). A completer si les benchmarks de la Tache 4.9
            // montrent un cout de recomposition non negligeable au
            // changement de chapitre.
            onReady(chapter)
        }
    }
}
