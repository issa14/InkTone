package com.inktone.data.mapper

import com.inktone.domain.valueobject.Locator
import com.inktone.domain.valueobject.LocatorText

/**
 * Intermédiaire forçant la réutilisation du même aplatissement partout.
 * [pageOffsetY] n'a de colonne que dans `reading_states` : les tables qui ne
 * le stockent pas le laissent nul. [textBefore] et [textAfter] portent le
 * contexte textuel (ADR-027) ; nuls tant qu'il n'est pas capturé.
 */
data class LocatorColumns(
    val resourceHref: String,
    val chapterIndex: Int,
    val paragraphIndex: Int?,
    val charOffset: Int,
    val pageOffsetY: Float? = null,
    val textBefore: String? = null,
    val textAfter: String? = null,
)

fun Locator.toColumns(): LocatorColumns = LocatorColumns(
    resourceHref = resourceHref, chapterIndex = chapterIndex,
    paragraphIndex = paragraphIndex, charOffset = charOffset, pageOffsetY = pageOffsetY,
    textBefore = text?.before, textAfter = text?.after,
)

fun LocatorColumns.toLocator(): Locator = Locator(
    resourceHref = resourceHref, chapterIndex = chapterIndex,
    paragraphIndex = paragraphIndex, charOffset = charOffset, pageOffsetY = pageOffsetY,
    // Un contexte à moitié lisible vaut aucun contexte.
    text = if (textBefore != null && textAfter != null) LocatorText(textBefore, textAfter) else null,
)
