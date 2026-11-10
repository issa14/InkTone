package com.inktone.domain.service

import com.inktone.domain.model.Chapter

/**
 * Produit la pré-analyse complète d'une publication HORS de toute session
 * de lecture — pour la régénérer en tâche de fond
 * (`RefreshStalePreAnalysesUseCase`).
 *
 * Distinct de [ChapterParser], qui sert la session de lecture : son
 * ouverture de l'archive est partagée avec le chargement des chapitres et
 * des images, et `ChapterParser.invalidate` la referme. Régénérer par ce
 * chemin pendant qu'un livre est ouvert fermerait donc l'archive sous le
 * Lecteur. Une implémentation de ce contrat ouvre sa propre instance,
 * refermée à la fin, et ne touche ni au cache ni à une session en cours.
 */
interface PreAnalysisBuilder {

    /**
     * Analyse (blocs + phrases) chacun des [chapterHrefs] de la source
     * [fileUri], dans l'ordre donné. Même résultat que `ChapterParser
     * .parseChapter` appelé chapitre par chapitre à l'import.
     *
     * @throws Exception si la source est illisible — l'appelant décide de
     *   la suite (la régénération passe au livre suivant).
     */
    suspend fun analyzeAll(fileUri: String, chapterHrefs: List<String>): List<Chapter>

    /**
     * Signale que la pré-analyse de [publicationId] vient d'être réécrite :
     * toute copie gardée en mémoire (y compris le constat « absente ou
     * périmée ») doit être relue au prochain accès.
     */
    fun onPreAnalysisRewritten(publicationId: String)
}
