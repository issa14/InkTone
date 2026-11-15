package com.inktone.data.mapper

import com.inktone.domain.valueobject.Locator

/**
 * Intermédiaire forçant la réutilisation du même aplatissement partout.
 * [pageOffsetY] n'a de colonne que dans `reading_states` : les tables qui ne
 * le stockent pas le laissent nul.
 */
data class LocatorColumns(
    val resourceHref: String,
    val chapterIndex: Int,
    val paragraphIndex: Int?,
    val charOffset: Int,
    val pageOffsetY: Float? = null,
)

fun Locator.toColumns(): LocatorColumns = LocatorColumns(
    resourceHref = resourceHref, chapterIndex = chapterIndex,
    paragraphIndex = paragraphIndex, charOffset = charOffset, pageOffsetY = pageOffsetY,
)

fun LocatorColumns.toLocator(): Locator = Locator(
    resourceHref = resourceHref, chapterIndex = chapterIndex,
    paragraphIndex = paragraphIndex, charOffset = charOffset, pageOffsetY = pageOffsetY,
)
