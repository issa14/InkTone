package com.inktone.domain.service

import com.inktone.domain.model.RenderedPage

/**
 * Contrat de rendu bitmap pour un format à pagination fixe (PDF, Lot 12,
 * Palier 2) — implémenté par `infrastructure/parser`, jamais exposé
 * au-delà (même discipline que [PublicationParser]/`TtsEngine`).
 * `feature/reader` ne référence jamais le binding PDFium directement :
 * la règle de dépendance l'interdit (Blueprint §4.7,
 * `checkArchitectureRules`).
 *
 * Cycle de vie distinct de [PublicationParser] : [open] retourne un
 * document qui reste vivant pour toute la session de lecture (navigation
 * entre pages), jamais rouvert à chaque page — fermé explicitement via
 * [FixedPageDocument.close], jamais laissé au ramasse-miettes pour
 * libérer les ressources natives.
 */
interface FixedPageRenderer {
    suspend fun open(fileUri: String): FixedPageOpenResult
}

/**
 * Distingue succès et raison d'échec (Blueprint §7.11) — jamais un
 * `null` silencieux comme une première version de ce contrat l'avait
 * fait (corrigé en relecture) : un fichier déplacé, une permission SAF
 * révoquée ou un échec natif à l'ouverture n'ont pas la même cause, et
 * l'appelant doit pouvoir l'afficher.
 */
sealed interface FixedPageOpenResult {
    data class Success(val document: FixedPageDocument) : FixedPageOpenResult
    data class Failed(val reason: String) : FixedPageOpenResult
}

interface FixedPageDocument : AutoCloseable {
    val pageCount: Int

    /**
     * Rendu de la page [pageIndex] à la largeur cible [targetWidthPx]
     * (hauteur déduite du ratio réel de la page). `null` sur un échec de
     * rendu ponctuel (page hors bornes, erreur native transitoire
     * interceptée) — jamais une exception qui remonte au hasard : l'appelant
     * (`FixedPageContent`, `feature/reader`) doit pouvoir afficher un état
     * d'erreur par page plutôt que crasher.
     */
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): RenderedPage?

    override fun close()
}
