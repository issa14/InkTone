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
 * Contenu d'un chapitre — sealed pour transition compiler-checked entre
 * l'ancien modèle (Legacy) et le nouveau (Rich, basé sur [BookBlock]).
 *
 * Au Palier 5, [Legacy] est supprimé et [Rich] devient le seul variant.
 */
sealed class ChapterContent {
    /** Ancien modèle : paragraphes + blocs structurels ancrés. */
    data class Legacy(
        val paragraphs: List<Paragraph>,
        val structuralBlocks: List<StructuralBlock> = emptyList(),
    ) : ChapterContent()

    /** Nouveau modèle : blocs de rendu atomiques avec styles inline. */
    data class Rich(
        val blocks: List<BookBlock>,
    ) : ChapterContent()
}

/**
 * Style de rendu d'un paragraphe — n'affecte JAMAIS le texte lu par le
 * TTS, l'alignement CTC ou l'indexation FTS, uniquement l'affichage.
 * Extension non cassante de [Paragraph] (Tâche 1.3.1, Partie 1) —
 * valeur par défaut NORMAL, tout code existant continue de fonctionner
 * sans modification.
 *
 * @deprecated Remplacé par [BookBlock] (HeadingBlock, ParagraphBlock).
 *   Conservé uniquement pour [ChapterContent.Legacy] — sera supprimé au Palier 5.
 */
@Deprecated("Remplacé par BookBlock (HeadingBlock, ParagraphBlock)")
enum class ParagraphStyle { NORMAL, HEADING, BLOCK_QUOTE, POEM_LINE }

/**
 * Blocs purement structurels, SANS texte participant au flux de
 * phrases — jamais vus par TTS/CTC/FTS, uniquement intercalés au rendu
 * (même principe que le legacy `computeStructuralBlockAnchors`).
 *
 * @deprecated Remplacé par [BookBlock] (ImageBlock, SeparatorBlock).
 *   Conservé uniquement pour [ChapterContent.Legacy] — sera supprimé au Palier 5.
 */
@Deprecated("Remplacé par BookBlock (ImageBlock, SeparatorBlock)")
sealed interface StructuralBlock {
    /** Index du paragraphe APRÈS lequel ce bloc doit être intercalé. */
    val anchorAfterParagraphIndex: Int

    /** Image intercalée (EPUB `<img>`, etc.). */
    data class EpubImage(
        override val anchorAfterParagraphIndex: Int,
        val href: String,
        val altText: String?,
    ) : StructuralBlock

    /** Séparateur de section (ligne blanche, `* * *`, etc.). */
    data class SectionBreak(
        override val anchorAfterParagraphIndex: Int,
    ) : StructuralBlock
}

data class Chapter(
    val index: Int,
    val href: String,
    val title: String?,
    val content: ChapterContent, // NOUVEAU (Plan v3) : remplace paragraphs + structuralBlocks
    val sentences: List<Sentence> = emptyList(), // NOUVEAU (Plan v3) : conservé pour TTS, avec blockIndex vers BookBlock
) {
    /** @deprecated Utiliser [content] (Legacy.paragraphs). */
    @Deprecated("Utiliser content (Legacy.paragraphs)")
    val paragraphs: List<Paragraph>
        get() = (content as? ChapterContent.Legacy)?.paragraphs ?: emptyList()

    /** @deprecated Utiliser [content] (Legacy.structuralBlocks). */
    @Deprecated("Utiliser content (Legacy.structuralBlocks)")
    val structuralBlocks: List<StructuralBlock>
        get() = (content as? ChapterContent.Legacy)?.structuralBlocks ?: emptyList()
}

data class Paragraph(
    val index: Int,
    val sentences: List<Sentence>,
    @Deprecated("Remplacé par BookBlock (HeadingBlock, ParagraphBlock)")
    val style: ParagraphStyle = ParagraphStyle.NORMAL, // NOUVEAU, défaut non cassant
)

/**
 * Unité de synthèse TTS. `startOffset`/`endOffset` sont des offsets de
 * caractère dans la ressource du chapitre — c'est ce qui rend possible la
 * synchronisation mot-à-mot (Blueprint §8.6 : "le découpage en phrases
 * conserve les offsets").
 *
 * @property blockIndex Index du [BookBlock] parent dans
 *   [ChapterContent.Rich.blocks] quand le contenu est [ChapterContent.Rich].
 *   Vaut -1 pour [ChapterContent.Legacy] (PDF/TXT) où il n'y a pas de
 *   [BookBlock]. Utilisé pour le pont TTS↔UI (recherche dichotomique O(log n)).
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
