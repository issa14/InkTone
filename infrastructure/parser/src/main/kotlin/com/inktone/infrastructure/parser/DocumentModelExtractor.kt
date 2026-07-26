package com.inktone.infrastructure.parser

import com.inktone.domain.model.Chapter
import com.inktone.domain.model.DocumentModel
import com.inktone.domain.model.Paragraph
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.TableOfContentsEntry
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.TextContentTokenizer
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.util.Language
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

        val toc = publication.tableOfContents.mapIndexed { index, link ->
            TableOfContentsEntry(title = link.title ?: "", chapterIndex = index)
        }

        return DocumentModel(chapters = chapters, tableOfContents = toc, resources = emptyList())
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
        val chapterUrl = link.href.resolve()
        val chapterTextElements = allElements
            .filter { it.locator.href == chapterUrl }
            .filterIsInstance<Content.TextElement>()

        var runningOffset = 0
        val sentences = mutableListOf<Sentence>()
        var sentenceIndex = 0

        chapterTextElements.forEach { element ->
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
        }

        val paragraph = Paragraph(index = 0, sentences = sentences)
        return Chapter(index = chapterIndex, href = link.href.toString(), title = null, paragraphs = listOf(paragraph))
    }
}
