package com.inktone.feature.reader

import com.inktone.domain.model.TableOfContentsEntry

/** Section du sommaire en cours de lecture, et ses bornes dans le chapitre (audit UX, ligne de statut). */
internal data class TocSection(val title: String, val startOffset: Int, val endOffset: Int?)

/** Sommaire aplati dans l'ordre du livre, sous-entrées comprises. */
internal fun flattenTableOfContents(entries: List<TableOfContentsEntry>): List<TableOfContentsEntry> =
    entries.flatMap { listOf(it) + flattenTableOfContents(it.children) }

/**
 * Section du sommaire où se trouve [currentOffset] dans le chapitre [chapterIndex], ou null si le
 * sommaire n'a aucune entrée titrée pour ce chapitre. Même règle que l'entrée « en cours » du
 * sommaire ([currentTocEntryIndex]). La fin est l'ancre de la section suivante du même chapitre,
 * ou null jusqu'à la fin du chapitre.
 */
internal fun currentTocSection(
    flatEntries: List<TableOfContentsEntry>,
    chapterIndex: Int,
    currentOffset: Int,
    anchors: Map<String, Int>,
): TocSection? {
    val index = currentTocEntryIndex(flatEntries, chapterIndex, currentOffset, anchors) ?: return null
    val entry = flatEntries[index]
    val title = entry.title.trim().ifEmpty { return null }
    val start = entry.fragment?.let { anchors[it] } ?: 0
    val end = flatEntries
        .filter { it.chapterIndex == chapterIndex }
        .map { other -> other.fragment?.let { anchors[it] } ?: 0 }
        .filter { it > start }
        .minOrNull()
    return TocSection(title, start, end)
}

/**
 * Position dans la section : (page courante, nombre de pages), à partir des pages de ses bornes.
 * [pageIndexOfStart] et [pageIndexOfLastChar] sont les pages (base 0) du début de la section et de
 * son dernier caractère ; [pageIndexOfLastChar] vaut la dernière page du chapitre pour la dernière
 * section.
 */
internal fun pageInSection(currentPageIndex: Int, pageIndexOfStart: Int, pageIndexOfLastChar: Int): Pair<Int, Int> {
    val count = (pageIndexOfLastChar - pageIndexOfStart + 1).coerceAtLeast(1)
    val page = (currentPageIndex - pageIndexOfStart + 1).coerceIn(1, count)
    return page to count
}
