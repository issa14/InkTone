package com.inktone.infrastructure.parser

import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.Span
import com.inktone.domain.model.SpanStyles
import com.inktone.domain.model.StyledText
import com.inktone.domain.service.FrenchSentenceSplitter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parseur HTML→[BookBlock] basé sur Jsoup.
 *
 * ## Responsabilités
 *
 * 1. **Parsing HTML** : Jsoup parse le XHTML (y compris malformé, courant
 *    dans les EPUB). Le DOM complet est chargé en mémoire — coût I/O O(n)
 *    sur la taille du fichier, pas d'optimisation de streaming.
 *
 * 2. **Normalisation des spans** : Les balises HTML imbriquées
 *    (`<b><i>texte</i></b>`) sont converties en [Span] non-chevauchants
 *    avec [SpanStyles] bitmask. Algorithme : collecter les spans bruts →
 *    trier les points de transition → découper en segments atomiques →
 *    assigner le masque de styles actif.
 *
 * 3. **Extraction par fragment** : Quand un fragment (`#prologue`) est
 *    spécifié, le DOM complet est chargé (coût I/O inchangé) mais seuls
 *    les nœuds à partir du fragment sont convertis en [BookBlock].
 *
 * 4. **Construction des offsets globaux** : Un `runningOffset` global est
 *    maintenu pendant l'extraction. Chaque [BookBlock.ParagraphBlock] et
 *    [BookBlock.HeadingBlock] reçoit son [BookBlock.globalOffsetRange].
 *    Les [Sentence] reçoivent leur [Sentence.blockIndex] par recherche
 *    dichotomique sur les `globalOffsetRange`.
 *
 * ## Éléments HTML traités
 *
 * | Élément          | BookBlock produit        |
 * |------------------|--------------------------|
 * | `<p>`, `<div>`   | ParagraphBlock           |
 * | `<h1>`–`<h6>`    | HeadingBlock(level)      |
 * | `<blockquote>`   | ParagraphBlock           |
 * | `<img>`          | ImageBlock               |
 * | `<hr>`           | SeparatorBlock           |
 * | `<b>`, `<strong>`| SpanStyles.STRONG        |
 * | `<i>`, `<em>`    | SpanStyles.EMPHASIS      |
 * | `<u>`, `<ins>`   | SpanStyles.INSERTED      |
 * | `<s>`, `<del>`   | SpanStyles.DELETED       |
 * | `<sup>`          | SpanStyles.SUPERSCRIPT   |
 * | `<sub>`          | SpanStyles.SUBSCRIPT     |
 * | `<a href>`       | SpanStyles.REFERENCE     |
 *
 * ## Éléments ignorés (v1)
 *
 * `<table>`, `<figure>`, `<math>`, `<svg>`, `<script>`, `<style>`,
 * `<head>`, `<br>` (fusionné comme espace).
 */
@Singleton
class JsoupChapterParser @Inject constructor(
    private val sentenceSplitter: FrenchSentenceSplitter,
) {

    /**
     * Parse un flux XHTML en [Chapter] avec contenu riche.
     *
     * @param inputStream Flux du fichier XHTML dans l'archive EPUB.
     * @param baseUrl URL de base pour la résolution des ressources (href
     *   du chapitre dans le spine).
     * @param chapterIndex Index du chapitre dans le spine.
     * @param chapterHref Href du chapitre.
     * @param fragment Fragment optionnel (ex: "#prologue").
     */
    fun parse(
        inputStream: InputStream,
        baseUrl: String,
        chapterIndex: Int,
        chapterHref: String,
        fragment: String? = null,
    ): Chapter {
        val document = Jsoup.parse(inputStream, "UTF-8", baseUrl)
        val body = document.body()

        val blocks = extractBlocks(body, fragment)
        val sentences = tokenizeSentences(blocks)

        return Chapter(
            index = chapterIndex,
            href = chapterHref,
            title = document.title().takeIf { it.isNotBlank() },
            content = ChapterContent.Rich(blocks = blocks),
            sentences = sentences,
        )
    }

    /**
     * Extrait la liste des [BookBlock] depuis le corps du document.
     *
     * @param body Élément `<body>` du DOM Jsoup.
     * @param fragment Fragment optionnel : si présent, seuls les nœuds à
     *   partir de l'élément ciblé sont extraits.
     */
    private fun extractBlocks(body: Element, fragment: String?): List<BookBlock> {
        val blocks = mutableListOf<BookBlock>()
        var runningOffset = 0

        // Déterminer le point de départ dans le DOM
        val startNode: Node = if (fragment != null) {
            findFragmentAnchor(body, fragment) ?: body
        } else {
            body
        }

        // Parcourir les enfants du nœud de départ (ou body entier si pas
        // de fragment). On ne parcourt QUE les enfants directs — pas de
        // récursion profonde qui dupliquerait le contenu.
        val childrenToProcess = if (fragment != null && startNode !== body) {
            // Le fragment cible un élément spécifique : on extrait à
            // partir de cet élément et on inclut ses frères suivants.
            val parent = startNode.parent() ?: body
            val startIndex = parent.childNodes().indexOf(startNode)
            if (startIndex >= 0) {
                parent.childNodes().subList(startIndex, parent.childNodes().size)
            } else {
                listOf(startNode)
            }
        } else {
            body.childNodes()
        }

        for (child in childrenToProcess) {
            val extracted = extractBlockFromNode(child, runningOffset)
            if (extracted != null) {
                blocks.add(extracted)
                if (extracted is BookBlock.ParagraphBlock || extracted is BookBlock.HeadingBlock) {
                    runningOffset = extracted.globalOffsetRange!!.last + 1
                }
            }
        }

        return blocks
    }

    /**
     * Trouve l'élément cible d'un fragment (ancre `id` ou `a[name]`).
     */
    private fun findFragmentAnchor(body: Element, fragment: String): Element? {
        val targetId = fragment.removePrefix("#")
        // Chercher par id
        body.getElementById(targetId)?.let { return it }
        // Chercher par <a name="...">
        body.selectFirst("a[name=$targetId]")?.let { return it }
        return null
    }

    /**
     * Extrait un [BookBlock] depuis un nœud DOM.
     *
     * @param node Nœud DOM à traiter.
     * @param runningOffset Offset global courant (début du bloc).
     * @return Le bloc extrait, ou null si le nœud ne produit pas de bloc
     *   (commentaire, script, etc.).
     */
    private fun extractBlockFromNode(node: Node, runningOffset: Int): BookBlock? {
        return when {
            node is Element -> extractBlockFromElement(node, runningOffset)
            node is TextNode -> {
                val text = node.wholeText.trim()
                if (text.isBlank()) return null
                // Texte hors balise de bloc : on le traite comme un
                // paragraphe implicite.
                BookBlock.ParagraphBlock(
                    richText = StyledText.plain(text),
                    globalOffsetRange = runningOffset until (runningOffset + text.length),
                )
            }
            else -> null // Commentaires, etc.
        }
    }

    /**
     * Extrait un [BookBlock] depuis un élément DOM.
     */
    private fun extractBlockFromElement(element: Element, runningOffset: Int): BookBlock? {
        val tagName = element.normalName()

        return when (tagName) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = tagName[1].digitToInt()
                val richText = extractRichText(element)
                if (richText.plainText.isBlank()) return null
                BookBlock.HeadingBlock(
                    level = level,
                    richText = richText,
                    globalOffsetRange = runningOffset until (runningOffset + richText.plainText.length),
                )
            }
            "p", "div", "blockquote", "section", "article", "li", "td", "th" -> {
                val richText = extractRichText(element)
                if (richText.plainText.isBlank()) return null
                BookBlock.ParagraphBlock(
                    richText = richText,
                    globalOffsetRange = runningOffset until (runningOffset + richText.plainText.length),
                )
            }
            "img" -> {
                val src = element.attr("src").ifBlank { null } ?: return null
                BookBlock.ImageBlock(
                    href = src,
                    alt = element.attr("alt").takeIf { it.isNotBlank() },
                    intrinsicWidth = element.attr("width").toIntOrNull(),
                    intrinsicHeight = element.attr("height").toIntOrNull(),
                )
            }
            "hr" -> BookBlock.SeparatorBlock
            // Éléments ignorés : on extrait le texte des enfants comme
            // paragraphes (ex: <pre>, <code>, <figcaption>, etc.)
            else -> {
                // Vérifier si l'élément contient du texte significatif
                if (element.ownText().isBlank() && element.children().isEmpty()) return null
                // Tenter d'extraire comme paragraphe
                val richText = extractRichText(element)
                if (richText.plainText.isBlank()) return null
                BookBlock.ParagraphBlock(
                    richText = richText,
                    globalOffsetRange = runningOffset until (runningOffset + richText.plainText.length),
                )
            }
        }
    }

    /**
     * Extrait le [StyledText] d'un sous-arbre DOM.
     *
     * Parcourt récursivement les nœuds enfants, accumule le texte brut et
     * les spans bruts, puis normalise (split aux frontières → bitmask).
     */
    fun extractRichText(node: Node): StyledText {
        val rawSpans = mutableListOf<RawSpan>()
        val textBuilder = StringBuilder()

        collectTextAndSpans(node, SpanStyles.NONE, null, textBuilder, rawSpans)

        val plainText = textBuilder.toString()
        if (plainText.isEmpty()) return StyledText.plain("")

        // Normalisation : split aux frontières → segments atomiques
        val normalized = normalizeSpans(plainText, rawSpans)
        return StyledText(plainText, normalized)
    }

    /**
     * Span brut collecté pendant le parcours DOM, avant normalisation.
     */
    private data class RawSpan(
        val styles: SpanStyles,
        val start: Int,
        val end: Int,
        val href: String? = null,
    )

    /**
     * Parcourt récursivement le DOM et accumule le texte + les spans bruts.
     */
    private fun collectTextAndSpans(
        node: Node,
        inheritedStyles: SpanStyles,
        inheritedHref: String?,
        textBuilder: StringBuilder,
        rawSpans: MutableList<RawSpan>,
    ) {
        when (node) {
            is TextNode -> {
                val text = node.wholeText
                if (text.isNotEmpty()) {
                    val start = textBuilder.length
                    textBuilder.append(text)
                    if (!inheritedStyles.isEmpty() || inheritedHref != null) {
                        rawSpans.add(
                            RawSpan(
                                styles = inheritedStyles,
                                start = start,
                                end = start + text.length,
                                href = inheritedHref,
                            ),
                        )
                    }
                }
            }
            is Element -> {
                val tagName = node.normalName()
                val (newStyles, newHref) = when (tagName) {
                    "b", "strong" -> inheritedStyles + SpanStyles.STRONG to inheritedHref
                    "i", "em" -> inheritedStyles + SpanStyles.EMPHASIS to inheritedHref
                    "u", "ins" -> inheritedStyles + SpanStyles.INSERTED to inheritedHref
                    "s", "del", "strike" -> inheritedStyles + SpanStyles.DELETED to inheritedHref
                    "sup" -> inheritedStyles + SpanStyles.SUPERSCRIPT to inheritedHref
                    "sub" -> inheritedStyles + SpanStyles.SUBSCRIPT to inheritedHref
                    "a" -> {
                        val href = node.attr("href").takeIf { it.isNotBlank() }
                        inheritedStyles + SpanStyles.REFERENCE to (href ?: inheritedHref)
                    }
                    // <br> → espace
                    "br" -> {
                        textBuilder.append(' ')
                        return
                    }
                    // Éléments à ignorer entièrement
                    "script", "style", "head", "meta", "link" -> return
                    else -> inheritedStyles to inheritedHref
                }

                for (child in node.childNodes()) {
                    collectTextAndSpans(child, newStyles, newHref, textBuilder, rawSpans)
                }
            }
        }
    }

    /**
     * Normalise les spans bruts en spans non-chevauchants avec bitmask.
     *
     * ## Algorithme (split aux frontières) :
     *
     * 1. Collecter tous les points de transition (start/end de chaque span brut)
     * 2. Trier les points de transition
     * 3. Pour chaque segment [transition[i], transition[i+1]) :
     *    - Calculer le masque = OR de tous les RawSpan couvrant ce segment
     *    - Si masque != NONE, émettre Span(masque, segment)
     *
     * ## Exemple :
     * `<b>bold <i>bold-italic</i></b>`
     * → RawSpan(STRONG, 0, 5), RawSpan(STRONG|EMPHASIS, 5, 15)
     * → Span(STRONG, 0, 5), Span(STRONG|EMPHASIS, 5, 15)
     */
    private fun normalizeSpans(plainText: String, rawSpans: List<RawSpan>): List<Span> {
        if (rawSpans.isEmpty()) return emptyList()

        // 1. Collecter tous les points de transition
        val transitions = sortedSetOf<Int>()
        for (span in rawSpans) {
            transitions.add(span.start)
            transitions.add(span.end)
        }

        if (transitions.size < 2) return emptyList()

        // 2. Pour chaque segment, calculer le masque de styles actif
        val transitionList = transitions.toList()
        val result = mutableListOf<Span>()

        for (i in 0 until transitionList.size - 1) {
            val segStart = transitionList[i]
            val segEnd = transitionList[i + 1]

            // OR de tous les RawSpan qui couvrent ce segment
            var mask = SpanStyles.NONE
            var href: String? = null
            for (raw in rawSpans) {
                if (raw.start <= segStart && raw.end >= segEnd) {
                    mask = mask + raw.styles
                    if (raw.href != null) href = raw.href
                }
            }

            if (!mask.isEmpty()) {
                result.add(
                    Span(
                        styles = mask,
                        start = segStart,
                        end = segEnd,
                        href = href,
                    ),
                )
            }
        }

        // 3. Fusionner les spans adjacents de même style (optionnel mais
        //    réduit le nombre de spans pour le rendu)
        return mergeAdjacentSpans(result)
    }

    /**
     * Fusionne les spans adjacents qui ont exactement le même style.
     */
    private fun mergeAdjacentSpans(spans: List<Span>): List<Span> {
        if (spans.isEmpty()) return spans

        val merged = mutableListOf<Span>()
        var current = spans[0]

        for (i in 1 until spans.size) {
            val next = spans[i]
            if (current.styles == next.styles &&
                current.href == next.href &&
                current.end == next.start
            ) {
                // Fusionner
                current = current.copy(end = next.end)
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    /**
     * Tokenise les phrases à partir des blocs extraits.
     *
     * Concatène le [StyledText.plainText] de tous les blocs de texte,
     * applique le [FrenchSentenceSplitter], puis assigne à chaque
     * [Sentence] son [Sentence.blockIndex] par recherche dichotomique
     * sur les [BookBlock.globalOffsetRange].
     */
    private fun tokenizeSentences(blocks: List<BookBlock>): List<Sentence> {
        // Concaténer le texte de tous les blocs de texte
        val textBlocks = blocks.filter { it.globalOffsetRange != null }
        if (textBlocks.isEmpty()) return emptyList()

        val fullText = buildString {
            for (block in textBlocks) {
                when (block) {
                    is BookBlock.ParagraphBlock -> append(block.richText.plainText)
                    is BookBlock.HeadingBlock -> append(block.richText.plainText)
                    else -> {}
                }
            }
        }

        if (fullText.isBlank()) return emptyList()

        val rawSentences = sentenceSplitter.split(fullText)
        return rawSentences.mapIndexed { index, (text, startOffset, endOffset) ->
            // Trouver le bloc contenant cet offset par recherche dichotomique
            val blockIdx = findBlockIndex(textBlocks, startOffset)
            Sentence(
                index = index,
                text = text,
                startOffset = startOffset,
                endOffset = endOffset,
                blockIndex = blockIdx,
            )
        }
    }

    /**
     * Trouve l'index du bloc contenant [charOffset] par recherche
     * dichotomique sur les [BookBlock.globalOffsetRange].
     *
     * @return L'index du bloc dans [textBlocks], ou -1 si non trouvé.
     */
    private fun findBlockIndex(textBlocks: List<BookBlock>, charOffset: Int): Int {
        var low = 0
        var high = textBlocks.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            val range = textBlocks[mid].globalOffsetRange
            if (range == null) {
                low = mid + 1
            } else when {
                charOffset < range.first -> high = mid - 1
                charOffset > range.last -> low = mid + 1
                else -> return mid // charOffset est dans ce bloc
            }
        }
        return -1
    }
}
