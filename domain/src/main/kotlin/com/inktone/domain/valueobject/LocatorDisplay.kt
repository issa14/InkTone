package com.inktone.domain.valueobject

private val FIXED_PAGE_HREF = Regex("page-\\d+")

/**
 * Vrai pour la position d'une page de PDF : son `resourceHref` est exactement
 * `page-N`, sans chemin ni extension (voir la KDoc de [Locator]). Un EPUB n'a
 * jamais de ressource nommée ainsi. Permet de libeller une position sans
 * connaître le format de son livre (vue globale des signets, conflit de synchro).
 */
val Locator.isFixedPage: Boolean get() = FIXED_PAGE_HREF.matches(resourceHref)

/** « Page N » pour un PDF, « Chapitre N » sinon : un PDF n'a pas de chapitres (audit UX B3). */
fun Locator.positionLabel(): String =
    if (isFixedPage) "Page ${chapterIndex + 1}" else "Chapitre ${chapterIndex + 1}"
