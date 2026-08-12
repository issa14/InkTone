package com.inktone.infrastructure.parser

import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Paragraph
import com.inktone.domain.model.ParagraphStyle
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.StructuralBlock
import com.inktone.domain.model.TableOfContentsEntry
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.TextContentTokenizer
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.tokenizer.TextUnit

/**
 * Construit notre DocumentModel à partir de publication.content(), en
 * comptant nous-mêmes les offsets caractère (Tâche 3.3 : jamais dérivés
 * de la progression Readium). Convention posée ici, à ne jamais casser
 * silencieusement : le "texte du chapitre" est la concaténation, dans
 * l'ordre, du texte de chaque segment tokenisé de ce chapitre — les
 * offsets de Sentence sont comptés contre CETTE chaîne concaténée.
 *
 * Prérequis vérifié contre les sources 3.0.0 (absent du plan d'origine) :
 * `publication.content()` renvoie null tant qu'un ContentService n'est
 * PAS explicitement enregistré — DefaultPublicationParser ne le fait pas
 * automatiquement. Voir ReadiumPublicationParser.publicationOpener pour
 * l'enregistrement de DefaultContentService avec
 * HtmlResourceContentIterator.Factory (EPUB/XHTML).
 */
@OptIn(ExperimentalReadiumApi::class)
class DocumentModelExtractor {

    private val tokenizer = TextContentTokenizer(language = Language("fr"), unit = TextUnit.Sentence)

    suspend fun extract(publication: Publication): DocumentModel {
        val content = publication.content()
            ?: return DocumentModel(chapters = emptyList(), tableOfContents = emptyList(), resources = emptyList())

        val allElements = content.elements()

        val chapters = publication.readingOrder.mapIndexed { chapterIndex, link ->
            extractChapter(chapterIndex, link, allElements)
        }

        // Bug reel trouve en testant contre un vrai EPUB (Tache 4.11,
        // Les Miserables Tome I) : publication.tableOfContents a ses
        // propres entrees (souvent des ancres #fragment DANS une meme
        // ressource de spine - ce livre a 6 ressources de spine mais 153
        // navPoints) - son cardinal n'a AUCUN rapport avec le nombre de
        // chapitres (readingOrder/spine). Utiliser l'index de la TOC
        // elle-meme comme chapterIndex (comme le faisait le code
        // d'origine) produit un chapterIndex hors bornes pour la quasi
        // totalite des entrees au-dela du nombre de ressources de spine,
        // et ReaderViewModel.navigateToChapter (Tache 4.5) les ignore
        // alors silencieusement (bornes verifiees par design, K3) - la
        // TOC semblait fonctionner (aucun crash) mais ne naviguait nulle
        // part pour la plupart des entrees. Resolu par correspondance de
        // href (sans fragment) contre readingOrder, comme extractChapter
        // le fait deja pour le filtrage des elements de contenu.
        val readingOrderUrls = publication.readingOrder.map { it.href.resolve().removeFragment() }
        val toc = publication.tableOfContents.map { link -> toTocEntry(link, readingOrderUrls) }

        return DocumentModel(chapters = chapters, tableOfContents = toc, resources = emptyList())
    }

    /**
     * Bug reel trouve en verifiant contre un vrai EPUB hierarchique
     * (Gutenberg #17489, Les Miserables Tome I — la premiere verification
     * en Tache 4.11 avait conclu a tort que le toc.ncx de ce livre etait
     * plat ; il ne l'est pas, `TableOfContentsChildrenTest` le prouve) :
     * `link.children` (Readium `Link`, Blueprint §7.5) n'etait JAMAIS lu,
     * `TableOfContentsEntry.children` restait donc toujours vide quelle
     * que soit la hierarchie NCX source. Recursion necessaire ici, pas
     * juste sur le premier niveau — un NCX peut imbriquer sur plusieurs
     * niveaux (Tome > Livre > Chapitre), pas seulement Chapitre > titre
     * comme dans ce fixture precis.
     */
    private fun toTocEntry(link: Link, readingOrderUrls: List<Url>): TableOfContentsEntry {
        val chapterIndex = readingOrderUrls.indexOf(link.href.resolve().removeFragment())
        return TableOfContentsEntry(
            title = link.title ?: "",
            chapterIndex = chapterIndex.coerceAtLeast(0),
            children = link.children.map { child -> toTocEntry(child, readingOrderUrls) },
        )
    }

    private fun extractChapter(chapterIndex: Int, link: Link, allElements: List<Content.Element>): Chapter {
        // Filtrage par ressource (point ouvert du plan d'origine, Tache 3.4,
        // point 1), verifie empiriquement : link.href est de type Href
        // (org.readium.r2.shared.publication.Href), jamais egal par lui-meme
        // a un Url — il faut resoudre explicitement via .resolve() pour
        // obtenir l'Url comparable a element.locator.href (RelativeUrl).
        // Sans cette resolution, le filtre ne matche jamais silencieusement
        // (confirme par un test qui echouait avec 0 phrase extraite alors
        // que content() renvoyait bien 2 elements).
        //
        // K6 (percent-encoding, Tache 4.3) : Href.resolve() normalise deja
        // le percent-encoding mixte entre le href du spine et le nom de
        // fichier physique dans le conteneur (HrefEncodingTest, fixture a
        // href "chapitre%20un.xhtml" vs fichier "chapitre un.xhtml") -
        // verifie empiriquement, pas suppose. Aucune normalisation
        // supplementaire a ecrire ici : ce serait redondant avec ce que
        // Readium fait deja.
        val chapterUrl = link.href.resolve()

        // Tache 1.3.3 — on itere TOUS les elements (pas seulement
        // TextElement) pour collecter a la fois les paragraphes et les
        // blocs structurels (images). L'ordre de Content.elements()
        // est preserve — c'est l'ordre du document source, sur lequel
        // on s'aligne pour intercaler correctement les blocs au rendu.
        val chapterElements = allElements.filter { it.locator.href == chapterUrl }

        val paragraphs = mutableListOf<Paragraph>()
        val structuralBlocks = mutableListOf<StructuralBlock>()
        var runningOffset = 0
        var paragraphIndex = 0
        var sentenceIndex = 0

        chapterElements.forEach { element ->
            when (element) {
                is Content.TextElement -> {
                    val sentences = mutableListOf<Sentence>()
                    tokenizer.tokenize(element)
                        .filterIsInstance<Content.TextElement>()
                        .forEach { tokenizedElement ->
                            tokenizedElement.segments.forEach { segment ->
                                val text = segment.text
                                if (text.isBlank()) return@forEach
                                sentences += Sentence(
                                    index = sentenceIndex++,
                                    text = text,
                                    startOffset = runningOffset,
                                    endOffset = runningOffset + text.length,
                                )
                                runningOffset += text.length + 1 // +1 : separateur entre segments
                            }
                        }
                    if (sentences.isNotEmpty()) {
                        val style = when (element.role) {
                            is Content.TextElement.Role.Heading -> ParagraphStyle.HEADING
                            is Content.TextElement.Role.Quote -> ParagraphStyle.BLOCK_QUOTE
                            // Role.Body, Role.Footnote -> NORMAL par defaut
                            else -> ParagraphStyle.NORMAL
                        }
                        paragraphs += Paragraph(index = paragraphIndex++, sentences = sentences, style = style)
                    }
                }

                is Content.ImageElement -> {
                    // Tache 1.3.1 — les images sont ancrees APRES le
                    // dernier paragraphe extrait (meme logique que le
                    // legacy computeStructuralBlockAnchors).
                    structuralBlocks += StructuralBlock.EpubImage(
                        anchorAfterParagraphIndex = (paragraphIndex - 1).coerceAtLeast(0),
                        href = element.embeddedLink.href.toString(),
                        altText = element.accessibilityLabel,
                    )
                }

                // AudioElement, VideoElement : ignores pour l'instant
                // (hors perimetre v1, Blueprint §7.5).
                else -> { /* non traite */ }
            }
        }

        return Chapter(
            index = chapterIndex,
            href = link.href.toString(),
            title = null,
            content = ChapterContent.Legacy(
                paragraphs = paragraphs,
                structuralBlocks = structuralBlocks,
            ),
        )
    }
}
