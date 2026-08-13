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
class JsoupChapterParser {

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

        val blocks = extractBlocks(body, fragment, chapterHref)
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
    private fun extractBlocks(body: Element, fragment: String?, chapterHref: String): List<BookBlock> {
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
            for (block in extractBlocksFromNode(child, runningOffset, chapterHref)) {
                blocks.add(block)
                if (block is BookBlock.ParagraphBlock || block is BookBlock.HeadingBlock) {
                    // +1 pour la fin exclusive de la plage, +BLOCK_SEPARATOR.length
                    // pour réserver le caractère séparateur inséré entre blocs
                    // par tokenizeSentences — sans ce décalage, le texte de deux
                    // paragraphes consécutifs seraient fusionnés sans espace.
                    runningOffset = block.globalOffsetRange!!.last + 1 + BLOCK_SEPARATOR.length
                }
            }
        }

        return blocks
    }

    /**
     * Résout un href relatif (attribut `src`/`href` d'un élément du
     * chapitre) contre le href du chapitre lui-même, pour obtenir un href
     * relatif à la racine de la publication — le référentiel utilisé par
     * `Publication.readingOrder`/`resources` (K6, CLAUDE.md).
     *
     * Résolution par segments de chemin (pas d'URI absolue : les hrefs
     * EPUB sont toujours des chemins relatifs POSIX, jamais un schéma
     * `http://`/`file://`). Le percent-encoding est normalisé plus tard,
     * à la résolution effective de la ressource (`resourceWithHref`).
     */
    private fun resolveHref(chapterHref: String, relativeHref: String): String {
        if (relativeHref.contains("://") || relativeHref.startsWith("data:")) return relativeHref
        val baseDir = chapterHref.substringBeforeLast('/', "")
        val combined = if (baseDir.isEmpty()) relativeHref else "$baseDir/$relativeHref"

        val resolved = mutableListOf<String>()
        for (segment in combined.split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
                else -> resolved.add(segment)
            }
        }
        return resolved.joinToString("/")
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
     * Extrait les [BookBlock] depuis un nœud DOM.
     *
     * @param node Nœud DOM à traiter.
     * @param runningOffset Offset global courant (début du premier bloc).
     * @return Les blocs extraits (vide si le nœud ne produit aucun bloc :
     *   commentaire, script, etc.).
     */
    private fun extractBlocksFromNode(node: Node, runningOffset: Int, chapterHref: String): List<BookBlock> {
        return when {
            node is Element -> extractBlocksFromElement(node, runningOffset, chapterHref)
            node is TextNode -> {
                val text = node.wholeText.trim()
                if (text.isBlank()) emptyList()
                else listOf(
                    // Texte hors balise de bloc : on le traite comme un
                    // paragraphe implicite.
                    BookBlock.ParagraphBlock(
                        richText = StyledText.plain(text),
                        globalOffsetRange = runningOffset until (runningOffset + text.length),
                    ),
                )
            }
            else -> emptyList() // Commentaires, etc.
        }
    }

    /**
     * Extrait les [BookBlock] depuis un élément DOM.
     *
     * Un élément de paragraphe contenant des images inline (`<img>`/`<svg>`)
     * est scindé en segments texte/image/texte par [extractInlineBlocks] —
     * voir cette méthode pour la continuité des [BookBlock.globalOffsetRange].
     */
    private fun extractBlocksFromElement(element: Element, runningOffset: Int, chapterHref: String): List<BookBlock> {
        val tagName = element.normalName()

        return when (tagName) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = tagName[1].digitToInt()
                val richText = extractRichText(element)
                if (richText.plainText.isBlank()) emptyList()
                else listOf(
                    BookBlock.HeadingBlock(
                        level = level,
                        richText = richText,
                        globalOffsetRange = runningOffset until (runningOffset + richText.plainText.length),
                    ),
                )
            }
            "p", "blockquote", "li", "td", "th" -> {
                if (element.selectFirst("img, svg") != null) {
                    // Image inline (ou conteneur d'image) : scinder le flux
                    // en segments texte/image/texte.
                    extractInlineBlocks(element, runningOffset, chapterHref)
                } else {
                    val richText = extractRichText(element)
                    if (richText.plainText.isBlank()) emptyList()
                    else listOf(
                        BookBlock.ParagraphBlock(
                            richText = richText,
                            globalOffsetRange = runningOffset until (runningOffset + richText.plainText.length),
                            isBlockquote = tagName == "blockquote",
                        ),
                    )
                }
            }
            // Conteneurs structurels : descendre dans leurs enfants de
            // niveau bloc pour préserver la granularité des paragraphes.
            // Sans cette récursion, un chapitre entièrement enveloppé dans
            // un <div> (motif courant des EPUB du commerce) était aplati en
            // UN SEUL ParagraphBlock — gelant le compteur de page du mode
            // SCROLL (qui dérive la position du premier bloc visible).
            "div", "section", "article", "main" -> {
                if (hasBlockLevelChild(element)) {
                    extractContainerBlocks(element, runningOffset, chapterHref)
                } else if (element.selectFirst("img, svg") != null) {
                    extractInlineBlocks(element, runningOffset, chapterHref)
                } else {
                    val richText = extractRichText(element)
                    if (richText.plainText.isBlank()) emptyList()
                    else listOf(
                        BookBlock.ParagraphBlock(
                            richText = richText,
                            globalOffsetRange = runningOffset until (runningOffset + richText.plainText.length),
                        ),
                    )
                }
            }
            "img" -> {
                val src = element.attr("src").ifBlank { null }
                if (src == null) emptyList()
                else listOf(
                    BookBlock.ImageBlock(
                        // K6, CLAUDE.md : `src` est relatif au chapitre (ex.
                        // "../Images/x.jpg"), pas à la racine de la publication
                        // — résolu ici pour matcher les hrefs de
                        // Publication.resourceWithHref (ReadiumResourceResolver),
                        // qui sont eux relatifs à la racine.
                        href = resolveHref(chapterHref, src),
                        alt = element.attr("alt").takeIf { it.isNotBlank() },
                        intrinsicWidth = element.attr("width").toIntOrNull(),
                        intrinsicHeight = element.attr("height").toIntOrNull(),
                    ),
                )
            }
            "svg" -> listOfNotNull(extractSvgImageBlock(element, chapterHref))
            "hr" -> listOf(BookBlock.SeparatorBlock)
            // Éléments ignorés : on extrait le texte des enfants comme
            // paragraphes (ex: <pre>, <code>, <figcaption>, etc.)
            else -> {
                // Vérifier si l'élément contient du texte significatif
                if (element.ownText().isBlank() && element.children().isEmpty()) emptyList()
                else if (element.selectFirst("img, svg") != null) {
                    // <figure><img/></figure>, <pre> avec image, etc.
                    extractInlineBlocks(element, runningOffset, chapterHref)
                } else {
                    val richText = extractRichText(element)
                    if (richText.plainText.isBlank()) emptyList()
                    else listOf(
                        BookBlock.ParagraphBlock(
                            richText = richText,
                            globalOffsetRange = runningOffset until (runningOffset + richText.plainText.length),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Vrai si [element] contient au moins un enfant de niveau bloc — auquel
     * cas il est traité comme conteneur structurel (ses enfants deviennent
     * des [BookBlock] distincts) plutôt que comme paragraphe aplati.
     */
    private fun hasBlockLevelChild(element: Element): Boolean =
        element.children().any { it.normalName() in BLOCK_LEVEL_TAGS }

    /**
     * Extrait les blocs d'un conteneur structurel (`<div>`, `<section>`,
     * `<article>`, `<main>`) en descendant récursivement dans ses enfants,
     * en ordre de document, avec des [BookBlock.globalOffsetRange]
     * consécutifs — même convention de séparateur ([BLOCK_SEPARATOR]) que
     * [extractBlocks] et [tokenizeSentences].
     */
    private fun extractContainerBlocks(container: Element, startOffset: Int, chapterHref: String): List<BookBlock> {
        val blocks = mutableListOf<BookBlock>()
        var runningOffset = startOffset
        for (child in container.childNodes()) {
            for (block in extractBlocksFromNode(child, runningOffset, chapterHref)) {
                blocks.add(block)
                if (block is BookBlock.ParagraphBlock || block is BookBlock.HeadingBlock) {
                    runningOffset = block.globalOffsetRange!!.last + 1 + BLOCK_SEPARATOR.length
                }
            }
        }
        return blocks
    }

    /**
     * Extrait un [BookBlock.ImageBlock] depuis un `<svg><image xlink:href="…"/></svg>`
     * — motif standard EPUB3 pour les pages de couverture/illustrations
     * pleine page (généré par Calibre, Sigil, etc.), utilisé à la place
     * d'un simple `<img>` pour préserver le ratio d'aspect via `viewBox`.
     * Jsoup (parseur HTML, pas XML) traite `xlink:href` comme un nom
     * d'attribut littéral — pas de résolution d'espace de noms nécessaire.
     *
     * @return null si [container] (ou lui-même s'il est déjà un `<svg>`)
     *   ne contient aucun `<image>` avec un href exploitable.
     */
    private fun extractSvgImageBlock(container: Element, chapterHref: String): BookBlock.ImageBlock? {
        val svg = if (container.normalName() == "svg") container else container.selectFirst("svg")
        val imageEl = svg?.selectFirst("image") ?: return null
        val src = imageEl.attr("xlink:href").ifBlank { imageEl.attr("href") }.ifBlank { null } ?: return null
        return BookBlock.ImageBlock(
            href = resolveHref(chapterHref, src),
            alt = imageEl.attr("aria-label").takeIf { it.isNotBlank() }
                ?: svg.attr("aria-label").takeIf { it.isNotBlank() },
            intrinsicWidth = imageEl.attr("width").toIntOrNull() ?: svg.attr("width").toIntOrNull(),
            intrinsicHeight = imageEl.attr("height").toIntOrNull() ?: svg.attr("height").toIntOrNull(),
        )
    }

    /**
     * Extrait un [BookBlock.ImageBlock] depuis un conteneur qui n'a produit
     * aucun texte exploitable. Gère deux motifs d'image embarquée :
     *
     * 1. `<svg><image xlink:href="…"/></svg>` (couverture EPUB3 Calibre/Sigil) ;
     * 2. un `<img>` descendant — cas des cartes/illustrations enveloppées
     *    dans un `<div>`, `<p>` ou `<figure>`, auparavant silencieusement
     *    abandonnées (bug réel « L'arcane des épées »).
     *
     * @return null si [container] ne contient aucune image exploitable.
     */
    private fun extractImageBlock(container: Element, chapterHref: String): BookBlock.ImageBlock? {
        extractSvgImageBlock(container, chapterHref)?.let { return it }

        val img = container.selectFirst("img") ?: return null
        val src = img.attr("src").ifBlank { null } ?: return null
        return BookBlock.ImageBlock(
            href = resolveHref(chapterHref, src),
            alt = img.attr("alt").takeIf { it.isNotBlank() },
            intrinsicWidth = img.attr("width").toIntOrNull(),
            intrinsicHeight = img.attr("height").toIntOrNull(),
        )
    }

    /**
     * Extrait les blocs d'un élément de paragraphe contenant des images
     * inline. Parcourt les enfants en ordre de document et scinde le flux
     * en [BookBlock.ParagraphBlock] autour des [BookBlock.ImageBlock], avec
     * des [BookBlock.globalOffsetRange] consécutifs — même convention de
     * séparateur ([BLOCK_SEPARATOR]) que [tokenizeSentences] et
     * [extractBlocks], pour ne pas désynchroniser les offsets TTS/sélection.
     */
    private fun extractInlineBlocks(
        element: Element,
        startOffset: Int,
        chapterHref: String,
    ): List<BookBlock> {
        val blocks = mutableListOf<BookBlock>()
        val textBuilder = StringBuilder()
        val rawSpans = mutableListOf<RawSpan>()
        var runningOffset = startOffset

        fun flushText() {
            val plainText = textBuilder.toString()
            if (plainText.isNotBlank()) {
                blocks.add(
                    BookBlock.ParagraphBlock(
                        richText = StyledText(plainText, normalizeSpans(plainText, rawSpans)),
                        globalOffsetRange = runningOffset until (runningOffset + plainText.length),
                        isBlockquote = element.normalName() == "blockquote",
                    ),
                )
                runningOffset = runningOffset + plainText.length + BLOCK_SEPARATOR.length
            }
            textBuilder.setLength(0)
            rawSpans.clear()
        }

        collectInlineContent(element, SpanStyles.NONE, null, textBuilder, rawSpans) { imageEl ->
            flushText()
            extractImageBlock(imageEl, chapterHref)?.let { blocks.add(it) }
        }
        flushText()

        return blocks
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
     * Parcourt récursivement le DOM en ordre de document et accumule le
     * texte + les spans bruts, en signalant chaque image inline via
     * [onImage] (sans descendre dans celle-ci). Base commune de
     * [collectTextAndSpans] (callback no-op) et de [extractInlineBlocks]
     * (callback qui émet un [BookBlock.ImageBlock]).
     */
    private fun collectInlineContent(
        node: Node,
        inheritedStyles: SpanStyles,
        inheritedHref: String?,
        textBuilder: StringBuilder,
        rawSpans: MutableList<RawSpan>,
        onImage: (Element) -> Unit,
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
                when (tagName) {
                    // Image inline : signaler et ne pas descendre dedans
                    // (aucun texte à en extraire).
                    "img", "svg" -> {
                        onImage(node)
                        return
                    }
                    // Éléments à ignorer entièrement
                    "script", "style", "head", "meta", "link" -> return
                }
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
                    else -> inheritedStyles to inheritedHref
                }

                for (child in node.childNodes()) {
                    collectInlineContent(child, newStyles, newHref, textBuilder, rawSpans, onImage)
                }
            }
        }
    }

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
        // Comportement historique : les images sont ignorées (callback no-op).
        collectInlineContent(node, inheritedStyles, inheritedHref, textBuilder, rawSpans) {}
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
     * Concatène le [StyledText.plainText] de tous les blocs de texte (séparés
     * par [BLOCK_SEPARATOR], comme [extractBlocks] réserve l'espace
     * correspondant dans les `globalOffsetRange`), applique le
     * [FrenchSentenceSplitter], puis assigne à chaque [Sentence] son
     * [Sentence.blockIndex] par recherche dichotomique sur les
     * [BookBlock.globalOffsetRange].
     *
     * [Sentence.blockIndex] doit être l'index du bloc dans la liste
     * COMPLÈTE [blocks] (celle que `ReaderScreen` affiche telle quelle dans
     * son `LazyColumn`, images/séparateurs compris) — jamais l'index dans
     * la sous-liste filtrée des blocs de texte, qui ne correspond à rien
     * côté rendu dès qu'un chapitre contient une image ou un `<hr>`.
     */
    private fun tokenizeSentences(blocks: List<BookBlock>): List<Sentence> {
        val textBlocks = blocks.withIndex().filter { it.value.globalOffsetRange != null }
        if (textBlocks.isEmpty()) return emptyList()

        val fullText = buildString {
            textBlocks.forEachIndexed { position, (_, block) ->
                if (position > 0) append(BLOCK_SEPARATOR)
                when (block) {
                    is BookBlock.ParagraphBlock -> append(block.richText.plainText)
                    is BookBlock.HeadingBlock -> append(block.richText.plainText)
                    else -> {}
                }
            }
        }

        if (fullText.isBlank()) return emptyList()

        val rawSentences = FrenchSentenceSplitter.split(fullText)
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
     * @return L'index ORIGINAL du bloc dans la liste complète des blocs du
     *   chapitre (pas dans [textBlocks]), ou -1 si non trouvé.
     */
    private fun findBlockIndex(textBlocks: List<IndexedValue<BookBlock>>, charOffset: Int): Int {
        var low = 0
        var high = textBlocks.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            val range = textBlocks[mid].value.globalOffsetRange!! // filtré en amont : jamais null ici
            when {
                charOffset < range.first -> high = mid - 1
                charOffset > range.last -> low = mid + 1
                else -> return textBlocks[mid].index // charOffset est dans ce bloc
            }
        }
        return -1
    }

    private companion object {
        /** Séparateur inséré entre deux blocs de texte consécutifs (offsets ET tokenisation). */
        const val BLOCK_SEPARATOR = "\n"

        /**
         * Éléments de niveau bloc déclenchant la récursion d'un conteneur
         * (`<div>`, `<section>`, …). `<img>`/`<svg>` en sont exclus
         * volontairement : un conteneur qui ne contient que du texte et des
         * images inline est traité par [extractInlineBlocks], pas découpé en
         * blocs (cela préserverait mal les spans d'un flux inline).
         */
        val BLOCK_LEVEL_TAGS = setOf(
            "p", "h1", "h2", "h3", "h4", "h5", "h6",
            "div", "section", "article", "main", "header", "footer", "nav", "aside",
            "blockquote", "ul", "ol", "li", "table", "figure", "figcaption", "hr",
            "pre", "address", "details", "summary", "dl", "dt", "dd", "fieldset",
            "form", "center",
        )
    }
}
