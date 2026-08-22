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

/**
 * Contenu d'un chapitre — modèle Rich unifié (Plan v3, Palier 5).
 * L'ancien modèle Legacy (Paragraph, ParagraphStyle, StructuralBlock) a
 * été supprimé.
 */
sealed class ChapterContent {
    data class Rich(
        val blocks: List<BookBlock>,
    ) : ChapterContent()
}

data class Chapter(
    val index: Int,
    val href: String,
    val title: String?,
    val content: ChapterContent,
    val sentences: List<Sentence> = emptyList(),
)

/**
 * Unité de synthèse TTS. `startOffset`/`endOffset` sont des offsets de
 * caractère dans la ressource du chapitre — c'est ce qui rend possible la
 * synchronisation mot-à-mot (Blueprint §8.6 : "le découpage en phrases
 * conserve les offsets").
 *
 * @property blockIndex Index du [BookBlock] parent dans
 *   [ChapterContent.Rich.blocks] (PDF/TXT : toujours `0`, le chapitre/page
 *   ne contient qu'un unique [BookBlock.ParagraphBlock]). `-1` uniquement
 *   quand aucun bloc ne contient cette phrase. Utilisé pour le pont TTS↔UI
 *   (recherche dichotomique O(log n)).
 */
data class Sentence(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val blockIndex: Int = -1, // NOUVEAU (Plan v3) : défaut -1 = pas de bloc parent
) {
    init {
        require(endOffset >= startOffset) { "endOffset doit être >= startOffset" }
        require(startOffset >= 0) { "startOffset doit être positif ou nul" }
        require(blockIndex >= -1) { "blockIndex doit être >= -1" }
    }

    /**
     * Nombre de mots de la phrase — définition UNIQUE, partagée par les deux
     * chemins qui alimentent `ReadingSession.wordsRead` (narration et
     * défilement manuel). Deux découpages divergents rendraient la vitesse de
     * lecture dépendante du mode, ce qui la viderait de son sens.
     *
     * Découpage sur les blancs : suffisant pour une métrique de vitesse, et
     * volontairement indépendant de la segmentation TTS, dont tous les moteurs
     * ne fournissent pas les limites de mots (K9).
     */
    val wordCount: Int get() = text.split(WHITESPACE).count { it.isNotBlank() }

    private companion object {
        val WHITESPACE = Regex("\\s+")
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
