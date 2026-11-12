package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator

/**
 * Longueur de chaque chapitre d'une publication reflowable (EPUB/TXT), dans
 * l'espace des offsets de [Sentence] — le même que `Locator.charOffset`.
 * Indexée par POSITION dans l'ordre de lecture (couverture ajoutée en tête
 * comprise), jamais par `Chapter.index`, qui peut diverger de cette position
 * (voir `ReaderViewModel.loadChapterContentIfNeeded`).
 *
 * Calculée une fois, sur le livre ENTIER, à l'import (ou par
 * `BackfillChapterLengthsUseCase` pour un livre importé avant son
 * existence). Cause racine qu'elle corrige : la progression était divisée
 * par la somme des chapitres DÉJÀ CHARGÉS en mémoire (chargement paresseux),
 * si bien qu'ouvrir un livre au milieu du chapitre 10 affichait ≈ 50 % ; et
 * la Bibliothèque comptait, elle, des numéros de chapitre.
 */
data class ChapterLengths(val values: List<Int>) {

    init {
        require(values.all { it >= 0 }) { "une longueur de chapitre doit être positive ou nulle" }
    }

    /** prefix[i] = longueur cumulée des chapitres [0, i) ; prefix.last() = total. */
    private val prefix: IntArray = IntArray(values.size + 1).also { acc ->
        values.forEachIndexed { i, length -> acc[i + 1] = acc[i] + length }
    }

    val total: Int get() = prefix.last()

    /**
     * Progression 0..1 de [locator] dans le livre entier, ou `null` si elle
     * n'est pas calculable honnêtement (livre sans texte, chapitre hors
     * bornes). Jamais une valeur inventée : l'appelant n'affiche rien.
     */
    fun progressionAt(locator: Locator): Float? {
        if (total <= 0) return null
        val index = locator.chapterIndex
        if (index !in values.indices) return null
        // Un offset au-delà de la fin du chapitre (fin de phrase, blanc final)
        // ne doit jamais déborder sur le chapitre suivant.
        val withinChapter = locator.charOffset.coerceAtMost(values[index])
        return Locator.computeProgression(
            locator = locator.copy(charOffset = withinChapter),
            totalCharsBeforeChapter = prefix[index],
            totalCharsInPublication = total,
        )
    }

    companion object {
        /**
         * Longueurs de [chapters], dans leur ordre : fin de la dernière
         * phrase de chacun (`0` pour un chapitre sans texte, ex. couverture).
         */
        fun of(chapters: List<Chapter>): ChapterLengths =
            ChapterLengths(chapters.map { chapter -> chapter.sentences.maxOfOrNull { it.endOffset } ?: 0 })
    }
}

/**
 * Progression 0..1 d'un format à pages fixes (PDF : page = chapitre),
 * [pageOffsetY] étant le défilement vertical dans la page.
 */
fun fixedPageProgression(pageIndex: Int, pageOffsetY: Float, pageCount: Int): Float? {
    if (pageCount <= 0) return null
    return ((pageIndex + pageOffsetY.coerceIn(0f, 1f)) / pageCount).coerceIn(0f, 1f)
}

/**
 * Progression 0..1 de [locator] dans cette publication — calcul UNIQUE,
 * partagé par le Lecteur, la Bibliothèque, les Récents et les Statistiques.
 * `null` = inconnue (longueurs pas encore calculées) : l'appelant n'affiche
 * aucun pourcentage plutôt qu'un chiffre faux.
 */
fun Publication.progressionAt(locator: Locator): Float? = when (format) {
    PublicationFormat.PDF -> fixedPageProgression(locator.chapterIndex, locator.pageOffsetY ?: 0f, chapterCount)
    PublicationFormat.EPUB, PublicationFormat.TXT -> chapterLengths?.progressionAt(locator)
}
