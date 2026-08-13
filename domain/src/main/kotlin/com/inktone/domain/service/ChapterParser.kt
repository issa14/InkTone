package com.inktone.domain.service

import com.inktone.domain.model.Chapter

/**
 * Parseur de contenu de chapitre — contrat de domaine.
 *
 * Sépare le « quoi » (extraire le contenu d'un chapitre EPUB) du « comment »
 * (Jsoup, Readium, cache LRU, threading). Le domaine ne connaît ni Jsoup ni
 * Readium — seule cette interface est visible depuis les use cases et les
 * ViewModels.
 *
 * ## Contrat d'annulation des préchargements
 *
 * [preload] retourne un [kotlinx.coroutines.Job] que l'appelant DOIT annuler
 * ([kotlinx.coroutines.Job.cancel]) si le chapitre n'est plus pertinent
 * (changement de chapitre, fermeture du lecteur). L'implémentation garantit
 * que l'annulation libère le [kotlinx.coroutines.sync.Semaphore] interne et
 * les threads du dispatcher dédié — pas de starvation.
 *
 * ## Cache
 *
 * L'implémentation est libre de cacher les résultats en mémoire (LRU, etc.).
 * [invalidate] vide le cache pour une publication donnée (appelé à la
 * fermeture du lecteur).
 */
interface ChapterParser {

    /**
     * Enregistre le mapping [publicationId] → [fileUri].
     * Doit être appelé après l'import, avant [parseChapter].
     */
    fun registerPublication(publicationId: String, fileUri: String)

    /**
     * Parse un chapitre et retourne son contenu.
     *
     * @param publicationId Identifiant unique de la publication (utilisé
     *   pour le cache et la résolution des ressources).
     * @param chapterHref Href du chapitre dans le spine EPUB.
     * @param fragment Fragment optionnel (ex: "#prologue") pour n'extraire
     *   qu'une partie du chapitre.
     * @return Le chapitre parsé avec son [ChapterContent].
     */
    suspend fun parseChapter(
        publicationId: String,
        chapterHref: String,
        fragment: String? = null,
    ): Chapter

    /**
     * Lance le préchargement asynchrone d'un chapitre.
     *
     * @return Un [kotlinx.coroutines.Job] que l'appelant DOIT annuler si le
     *   chapitre n'est plus pertinent. L'annulation libère les ressources
     *   (threads, semaphore) associées au parsing.
     */
    fun preload(
        publicationId: String,
        chapterHref: String,
        scope: kotlinx.coroutines.CoroutineScope,
    ): kotlinx.coroutines.Job

    /**
     * Vide le cache pour une publication donnée.
     * Appelé à la fermeture du lecteur pour libérer la mémoire.
     */
    fun invalidate(publicationId: String)
}
