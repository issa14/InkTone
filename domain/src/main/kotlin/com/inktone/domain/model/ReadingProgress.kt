package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator
import kotlin.math.roundToInt

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
    // La dernière page atteinte, c'est le livre lu : 9/10 affichait 90 %
    // (audit UX A19).
    if (pageIndex >= pageCount - 1) return 1f
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

/**
 * Temps de lecture restant estimé pour un livre ([progression] 0..1). `null`
 * quand rien ne permet une estimation honnête — jamais un chiffre inventé.
 *
 * Passé [EXTRAPOLATION_MIN_PROGRESSION], le rythme réel de CE livre (temps
 * cumulé rapporté à la progression) est le meilleur indicateur. En deçà, cette
 * extrapolation diverge (26 min à 0,3 % → 146 h) : on estime alors le texte
 * restant ([totalChars], en caractères) à la vitesse mesurée [averageWpm].
 */
fun estimatedRemainingReadingMs(
    timeSpentMs: Long,
    progression: Float?,
    totalChars: Int? = null,
    averageWpm: Int = 0,
): Long? {
    if (progression == null) return null
    val remaining = when {
        progression >= EXTRAPOLATION_MIN_PROGRESSION && timeSpentMs > 0L ->
            (timeSpentMs / progression).toLong() - timeSpentMs
        totalChars != null && totalChars > 0 && averageWpm > 0 -> {
            val remainingWords = totalChars * (1.0 - progression.coerceIn(0f, 1f)) / CHARS_PER_WORD
            (remainingWords / averageWpm * 60_000.0).toLong()
        }
        else -> return null
    }
    return remaining.takeIf { it > 0 }
}

/** Progression à partir de laquelle le rythme propre au livre est fiable. */
const val EXTRAPOLATION_MIN_PROGRESSION = 0.10f

/** Longueur moyenne d'un mot français, espace comprise (à calibrer sur un EPUB réel). */
const val CHARS_PER_WORD = 6.0

/**
 * Progression à partir de laquelle un livre reflowable est « terminé » : la
 * progression se mesure au DÉBUT de la phrase courante, un livre lu jusqu'au
 * bout n'atteint donc jamais tout à fait 1.
 */
const val FINISHED_PROGRESSION = 0.98f

/**
 * Seule définition de « livre terminé » (filtres « Terminé » / « En cours »,
 * « Livres finis » des statistiques). Audit UX B2, D31 : ces filtres
 * comparaient l'index de chapitre au dernier chapitre — un livre à un seul
 * chapitre était terminé dès son ouverture, un livre entamé dans un long
 * dernier chapitre aussi. PDF : la dernière page atteinte. Progression
 * inconnue : jamais terminé.
 */
fun Publication.isFinishedAt(locator: Locator): Boolean = when (format) {
    PublicationFormat.PDF -> chapterCount > 0 && locator.chapterIndex >= chapterCount - 1
    else -> (progressionAt(locator) ?: 0f) >= FINISHED_PROGRESSION
}

/**
 * Pourcentage affiché d'une progression, le même partout : Lecteur,
 * Bibliothèque, Récents, Statistiques (audit UX A19 — trois arrondis
 * différents affichaient 12,6 %, 13 % et 12 % pour la même position). Arrondi
 * à l'unité ; un livre terminé ([FINISHED_PROGRESSION]) affiche 100.
 */
fun progressionPercent(progression: Float): Int =
    if (progression >= FINISHED_PROGRESSION) 100 else (progression * 100).roundToInt().coerceIn(0, 100)
