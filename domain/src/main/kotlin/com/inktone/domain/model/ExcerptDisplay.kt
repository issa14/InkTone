package com.inktone.domain.model

/**
 * Extrait de signet ou d'annotation prêt à afficher. À la création, l'extrait
 * est borné à [maxLength] caractères au milieu d'un mot, sans le signaler :
 * un extrait qui atteint la borne est ramené au dernier mot entier et suivi
 * de « … ». Appliqué à l'affichage, il vaut aussi pour les extraits déjà
 * enregistrés.
 */
fun String.excerptForDisplay(maxLength: Int = Annotation.MAX_EXCERPT_LENGTH): String {
    if (length < maxLength) return this
    val lastSpace = lastIndexOf(' ')
    val cut = if (lastSpace > maxLength / 2) substring(0, lastSpace) else this
    return cut.trimEnd(' ', ',', ';', ':', ' ', ' ') + "…"
}
