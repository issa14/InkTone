package com.inktone.data.mapper

import com.inktone.domain.valueobject.Locator

/** Intermédiaire forçant la réutilisation du même aplatissement partout. */
data class LocatorColumns(
    val resourceHref: String,
    val chapterIndex: Int,
    val paragraphIndex: Int?,
    val charOffset: Int,
)

fun Locator.toColumns(): LocatorColumns = LocatorColumns(
    resourceHref = resourceHref, chapterIndex = chapterIndex,
    paragraphIndex = paragraphIndex, charOffset = charOffset,
)

fun LocatorColumns.toLocator(): Locator = Locator(
    resourceHref = resourceHref, chapterIndex = chapterIndex,
    paragraphIndex = paragraphIndex, charOffset = charOffset,
)
