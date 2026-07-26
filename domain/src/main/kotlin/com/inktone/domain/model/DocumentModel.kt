package com.inktone.domain.model

/**
 * Représentation interne unifiée d'un document, indépendante du format
 * source (Blueprint §7.5). Reader, TTS et recherche travaillent tous sur
 * cette structure commune — jamais directement sur un modèle Readium ou
 * un autre parseur (ADR-011 : Readium encapsulé dans
 * infrastructure/parser).
 */
data class DocumentModel(
    val chapters: List<Chapter>,
    val tableOfContents: List<TableOfContentsEntry>,
    val resources: List<Resource>,
)

data class Chapter(
    val index: Int,
    val href: String,
    val title: String?,
    val paragraphs: List<Paragraph>,
)

data class Paragraph(
    val index: Int,
    val sentences: List<Sentence>,
)

/**
 * Unité de synthèse TTS. `startOffset`/`endOffset` sont des offsets de
 * caractère dans la ressource du chapitre — c'est ce qui rend possible la
 * synchronisation mot-à-mot (Blueprint §8.6 : "le découpage en phrases
 * conserve les offsets").
 */
data class Sentence(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    init {
        require(endOffset >= startOffset) { "endOffset doit être >= startOffset" }
        require(startOffset >= 0) { "startOffset doit être positif ou nul" }
    }

    /** Construit le Locator de début de cette phrase. */
    fun startLocator(
        chapterIndex: Int,
        resourceHref: String,
        paragraphIndex: Int? = null,
    ): com.inktone.domain.valueobject.Locator = com.inktone.domain.valueobject.Locator(
        resourceHref = resourceHref,
        chapterIndex = chapterIndex,
        paragraphIndex = paragraphIndex,
        charOffset = startOffset,
    )
}

data class TableOfContentsEntry(
    val title: String,
    val chapterIndex: Int,
    val children: List<TableOfContentsEntry> = emptyList(),
)

data class Resource(
    val href: String,
    val mediaType: String,
)
