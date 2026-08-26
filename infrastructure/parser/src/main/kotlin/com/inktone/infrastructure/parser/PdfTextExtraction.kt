package com.inktone.infrastructure.parser

import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.Sentence
import com.inktone.domain.model.StyledText
import io.legere.pdfiumandroid.PdfPage

/**
 * Primitive de bas niveau partagée pour le TEXTE d'une page PDF — pendant
 * exact de [renderToBitmap] pour le rendu (Lot 12, décision actée 13 : un
 * seul point d'appel par API PDFium).
 *
 * Deux appelants, deux moments :
 * - [PdfPublicationParser] à l'import, qui a besoin de toutes les pages
 *   pour alimenter l'index de recherche ;
 * - [PdfChapterParser] à la lecture, qui n'en charge qu'une à la fois.
 *
 * Découpage en phrases : même regex naïve que `TxtPublicationParser`
 * (Tâche 4.2) — le découpage linguistique réel est hors périmètre d'un
 * parseur (Blueprint §8.6).
 */
internal val PDF_SENTENCE_BOUNDARY = Regex("""(?<=[.!?])\s+""")

/**
 * Texte complet et phrases d'une page.
 *
 * @return Pair(texte complet trimé, phrases avec offsets). Texte vide si
 *   la page est une image scannée sans texte.
 */
internal fun PdfPage.extractPageContent(): Pair<String, List<Sentence>> = openTextPage().use { textPage ->
    val charCount = textPage.textPageCountChars()
    if (charCount <= 0) return@use "" to emptyList()
    val text = textPage.textPageGetText(0, charCount)?.trim()
    if (text.isNullOrBlank()) return@use "" to emptyList()

    var offset = 0
    val sentences = PDF_SENTENCE_BOUNDARY.split(text).mapIndexed { index, raw ->
        val trimmed = raw.trim()
        // blockIndex = 0 : la page produit toujours exactement un
        // BookBlock.ParagraphBlock unique (voir [toChapter]) quand du texte
        // existe — jamais le défaut -1, sinon l'auto-scroll TTS
        // (ReaderScreen) ne trouve jamais son bloc pour un PDF.
        val sentence = Sentence(
            index = index,
            text = trimmed,
            startOffset = offset,
            endOffset = offset + trimmed.length,
            blockIndex = 0,
        )
        offset += trimmed.length + 1
        sentence
    }.filter { it.text.isNotBlank() }
    text to sentences
}

/** Page → [Chapter] (page = chapitre, décision actée 4 du Lot 12). */
internal fun PdfPage.toChapter(pageIndex: Int): Chapter {
    val (fullText, sentences) = extractPageContent()
    val blocks = if (fullText.isNotBlank()) {
        listOf(
            BookBlock.ParagraphBlock(
                richText = StyledText.plain(fullText),
                globalOffsetRange = 0 until fullText.length,
            ),
        )
    } else {
        emptyList()
    }
    return Chapter(
        index = pageIndex,
        href = pageHref(pageIndex),
        title = null,
        content = ChapterContent.Rich(blocks = blocks),
        sentences = sentences,
    )
}

/**
 * Coquille d'une page non encore extraite — `blocks` et `sentences` vides,
 * exactement comme une coquille de chapitre EPUB issue de
 * `ReadiumPublicationParser.parseLazy`. C'est ce vide qui déclenche le
 * chargement paresseux côté lecteur (`loadChapterContentIfNeeded`).
 */
internal fun pageShell(pageIndex: Int): Chapter = Chapter(
    index = pageIndex,
    href = pageHref(pageIndex),
    title = null,
    content = ChapterContent.Rich(blocks = emptyList()),
    sentences = emptyList(),
)

/**
 * Seule fabrique du href d'une page — `page-N`. Un PDF n'a pas de ressource
 * d'archive à adresser : ce href est un identifiant de position, jamais un
 * chemin. [pageIndexOf] en est l'inverse exact.
 */
internal fun pageHref(pageIndex: Int): String = "page-$pageIndex"

/** Inverse de [pageHref] ; `null` si [href] n'est pas un href de page PDF. */
internal fun pageIndexOf(href: String): Int? = href.removePrefix("page-").toIntOrNull()
