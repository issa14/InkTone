package com.inktone.infrastructure.parser

import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.Chapter
import com.inktone.domain.model.ChapterContent
import com.inktone.domain.model.Span
import com.inktone.domain.model.SpanStyles
import com.inktone.domain.model.StyledText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Tests JVM purs du [JsoupChapterParser] — aucun Android, aucun Readium.
 *
 * Jsoup n'a pas besoin d'Android, donc ces tests sont exécutables sur
 * n'importe quelle JVM. Critère de conception : chaque test vérifie une
 * transformation HTML → BookBlock spécifique, avec une assertion précise
 * sur la structure produite.
 */
class JsoupChapterParserTest {

    private lateinit var parser: JsoupChapterParser

    @Before
    fun setUp() {
        parser = JsoupChapterParser()
    }

    // ---- Tests de normalisation des spans ----

    @Test
    fun `texte simple sans balise produit un ParagraphBlock sans spans`() {
        val html = "<html><body><p>Bonjour le monde.</p></body></html>"
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(1, blocks.size)
        val block = blocks[0] as BookBlock.ParagraphBlock
        assertEquals("Bonjour le monde.", block.richText.plainText)
        assertTrue(block.richText.spans.isEmpty())
    }

    @Test
    fun `balise b produit un Span STRONG`() {
        val html = "<html><body><p>Le <b>Petit</b> Prince.</p></body></html>"
        val chapter = parse(html)

        val block = richBlocks(chapter)[0] as BookBlock.ParagraphBlock
        assertEquals("Le Petit Prince.", block.richText.plainText)
        assertEquals(1, block.richText.spans.size)

        val span = block.richText.spans[0]
        assertEquals(SpanStyles.STRONG, span.styles)
        assertEquals(3, span.start) // "Le " = 3 chars
        assertEquals(8, span.end)   // "Petit" = 5 chars
    }

    @Test
    fun `balise i produit un Span EMPHASIS`() {
        val html = "<html><body><p>Un mot <i>important</i> ici.</p></body></html>"
        val chapter = parse(html)

        val block = richBlocks(chapter)[0] as BookBlock.ParagraphBlock
        assertEquals("Un mot important ici.", block.richText.plainText)

        val span = block.richText.spans[0]
        assertEquals(SpanStyles.EMPHASIS, span.styles)
    }

    @Test
    fun `balises imbriquees b et i produisent un span STRONG pipe EMPHASIS`() {
        // Test CRITIQUE de normalisation — <b>bold <i>bold-italic</i></b>
        val html = "<html><body><p><b>bold <i>bold-italic</i></b></p></body></html>"
        val chapter = parse(html)

        val block = richBlocks(chapter)[0] as BookBlock.ParagraphBlock
        assertEquals("bold bold-italic", block.richText.plainText)
        assertEquals(2, block.richText.spans.size)

        // Premier span : "bold " → STRONG uniquement
        val span1 = block.richText.spans[0]
        assertEquals(SpanStyles.STRONG, span1.styles)
        assertEquals(0, span1.start)
        assertEquals(5, span1.end) // "bold "

        // Deuxième span : "bold-italic" → STRONG | EMPHASIS
        val span2 = block.richText.spans[1]
        assertTrue(SpanStyles.STRONG in span2.styles)
        assertTrue(SpanStyles.EMPHASIS in span2.styles)
        assertEquals(5, span2.start)
        assertEquals(16, span2.end)
    }

    @Test
    fun `balises adjacentes b, i, u produisent 3 spans sans chevauchement`() {
        val html = "<html><body><p><b>A</b><i>B</i><u>C</u></p></body></html>"
        val chapter = parse(html)

        val block = richBlocks(chapter)[0] as BookBlock.ParagraphBlock
        assertEquals("ABC", block.richText.plainText)
        assertEquals(3, block.richText.spans.size)

        assertEquals(SpanStyles.STRONG, block.richText.spans[0].styles)
        assertEquals(SpanStyles.EMPHASIS, block.richText.spans[1].styles)
        assertEquals(SpanStyles.INSERTED, block.richText.spans[2].styles)

        // Vérifier l'adjacence sans chevauchement
        assertEquals(block.richText.spans[0].end, block.richText.spans[1].start)
        assertEquals(block.richText.spans[1].end, block.richText.spans[2].start)
    }

    // ---- Tests des blocs ----

    @Test
    fun `h1 produit un HeadingBlock de niveau 1`() {
        val html = "<html><body><h1>Titre principal</h1><p>Texte.</p></body></html>"
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(2, blocks.size)

        val heading = blocks[0] as BookBlock.HeadingBlock
        assertEquals(1, heading.level)
        assertEquals("Titre principal", heading.richText.plainText)
        assertNotNull(heading.globalOffsetRange)

        val paragraph = blocks[1] as BookBlock.ParagraphBlock
        assertEquals("Texte.", paragraph.richText.plainText)
    }

    @Test
    fun `h2 h3 produisent les bons niveaux`() {
        val html = "<html><body><h2>Sous-titre</h2><h3>Sous-sous-titre</h3></body></html>"
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(2, blocks.size)
        assertEquals(2, (blocks[0] as BookBlock.HeadingBlock).level)
        assertEquals(3, (blocks[1] as BookBlock.HeadingBlock).level)
    }

    @Test
    fun `img avec attributs width height produit ImageBlock`() {
        val html = """<html><body><img src="cover.png" alt="Couverture" width="200" height="100"/></body></html>"""
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(1, blocks.size)

        val img = blocks[0] as BookBlock.ImageBlock
        assertEquals("cover.png", img.href)
        assertEquals("Couverture", img.alt)
        assertEquals(200, img.intrinsicWidth)
        assertEquals(100, img.intrinsicHeight)
    }

    @Test
    fun `img sans alt ni dimensions est accepte`() {
        val html = """<html><body><img src="figure.png"/></body></html>"""
        val chapter = parse(html)

        val img = richBlocks(chapter)[0] as BookBlock.ImageBlock
        assertEquals("figure.png", img.href)
        assertNull(img.alt)
        assertNull(img.intrinsicWidth)
        assertNull(img.intrinsicHeight)
    }

    @Test
    fun `svg image xlink href produit ImageBlock (couverture Calibre Sigil)`() {
        // Bug réel trouvé sur appareil : motif standard EPUB3 pour les
        // pages de couverture, entièrement ignoré avant ce correctif (le
        // <div> sans texte était abandonné, aucun ImageBlock produit).
        val html = """
            <html><body>
                <div>
                    <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 479 706">
                        <image width="479" height="706" xlink:href="cover.jpeg"/>
                    </svg>
                </div>
            </body></html>
        """.trimIndent()
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(1, blocks.size)
        val img = blocks[0] as BookBlock.ImageBlock
        assertEquals("cover.jpeg", img.href)
        assertEquals(479, img.intrinsicWidth)
        assertEquals(706, img.intrinsicHeight)
    }

    @Test
    fun `svg direct enfant du body produit aussi ImageBlock`() {
        val html = """
            <html><body>
                <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
                    <image xlink:href="illustration.png"/>
                </svg>
            </body></html>
        """.trimIndent()
        val chapter = parse(html)

        val img = richBlocks(chapter)[0] as BookBlock.ImageBlock
        assertEquals("illustration.png", img.href)
    }

    @Test
    fun `img imbrique dans un div sans texte produit ImageBlock`() {
        // Bug réel « L'arcane des épées » : une carte enveloppée dans un
        // <div> (sans texte) était silencieusement abandonnée — le repli
        // ne cherchait que <svg><image>, jamais un <img> descendant.
        val html = """<html><body><div class="illustration"><img src="../Images/carte.jpg" alt="Carte"/></div></body></html>"""
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(1, blocks.size)
        val img = blocks[0] as BookBlock.ImageBlock
        assertEquals("Images/carte.jpg", img.href)
        assertEquals("Carte", img.alt)
    }

    @Test
    fun `img dans un figure sans legende produit ImageBlock`() {
        val html = """<html><body><figure><img src="carte.png" alt="Carte"/></figure></body></html>"""
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is BookBlock.ImageBlock)
        assertEquals("Carte", (blocks[0] as BookBlock.ImageBlock).alt)
    }

    @Test
    fun `img inline dans un paragraphe scinde en texte image texte`() {
        val html = """<html><body><p>Voici la carte <img src="../Images/carte.jpg" alt="Carte"/> ci-dessous.</p><p>Suite.</p></body></html>"""
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(4, blocks.size)

        val avant = blocks[0] as BookBlock.ParagraphBlock
        assertEquals("Voici la carte ", avant.richText.plainText)

        val img = blocks[1] as BookBlock.ImageBlock
        assertEquals("Images/carte.jpg", img.href)
        assertEquals("Carte", img.alt)

        val apres = blocks[2] as BookBlock.ParagraphBlock
        assertEquals(" ci-dessous.", apres.richText.plainText)

        // Continuité des offsets (pont TTS) : le séparateur '\n' (1 char)
        // est réservé entre le texte avant et le texte après.
        assertEquals(avant.globalOffsetRange!!.last + 2, apres.globalOffsetRange!!.first)

        // Le paragraphe suivant continue l'espace d'offsets.
        val suite = blocks[3] as BookBlock.ParagraphBlock
        assertEquals("Suite.", suite.richText.plainText)
        assertEquals(apres.globalOffsetRange!!.last + 2, suite.globalOffsetRange!!.first)
    }

    @Test
    fun `figure avec image et legende scinde en texte et image`() {
        val html = """<html><body><figure><img src="carte.png" alt="Carte"/><figcaption>Carte du monde.</figcaption></figure></body></html>"""
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is BookBlock.ImageBlock)
        val legende = blocks[1] as BookBlock.ParagraphBlock
        assertEquals("Carte du monde.", legende.richText.plainText)
    }

    @Test
    fun `section couverture avec img sans texte produit ImageBlock`() {
        // Motif réel de « L'arcane des épées » : les cartes sont des
        // <section class="couverture"><img alt="" src="images/T1carteN.jpg"/></section>
        // sans aucun texte — silencieusement abandonnées avant le correctif.
        val html = """<html><body><section class="couverture"><img alt="" class="img" src="images/T1carte1.jpg"/></section></body></html>"""
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(1, blocks.size)
        val img = blocks[0] as BookBlock.ImageBlock
        assertEquals("images/T1carte1.jpg", img.href)
        assertNull(img.alt) // alt="" → null, pas de description d'accessibilité
        assertNull(img.intrinsicWidth)
    }

    @Test
    fun `hr produit un SeparatorBlock`() {
        val html = "<html><body><p>Avant.</p><hr/><p>Après.</p></body></html>"
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is BookBlock.ParagraphBlock)
        assertTrue(blocks[1] is BookBlock.SeparatorBlock)
        assertTrue(blocks[2] is BookBlock.ParagraphBlock)
    }

    @Test
    fun `blockquote produit un ParagraphBlock`() {
        val html = "<html><body><blockquote>Citation célèbre.</blockquote></body></html>"
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(1, blocks.size)
        val block = blocks[0] as BookBlock.ParagraphBlock
        assertEquals("Citation célèbre.", block.richText.plainText)
        assertNotNull(block.globalOffsetRange)
    }

    @Test
    fun `div contenant plusieurs p produit un bloc par paragraphe`() {
        // Motif courant des EPUB du commerce : tout le chapitre est enveloppé
        // dans un <div>, les paragraphes étant ses enfants directs. Avant la
        // récursion des conteneurs, ce chapitre était aplati en UN SEUL
        // ParagraphBlock — gelant le compteur de page du mode SCROLL.
        val html = """
            <html><body>
                <div class="div-chap">
                    <p>Premier paragraphe.</p>
                    <p>Deuxième paragraphe.</p>
                    <p>Troisième paragraphe.</p>
                </div>
            </body></html>
        """.trimIndent()
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(3, blocks.size)
        assertEquals("Premier paragraphe.", (blocks[0] as BookBlock.ParagraphBlock).richText.plainText)
        assertEquals("Deuxième paragraphe.", (blocks[1] as BookBlock.ParagraphBlock).richText.plainText)
        assertEquals("Troisième paragraphe.", (blocks[2] as BookBlock.ParagraphBlock).richText.plainText)

        // Les offsets restent consécutifs (1 caractère de séparateur entre blocs).
        assertEquals(blocks[0].globalOffsetRange!!.last + 2, blocks[1].globalOffsetRange!!.first)
        assertEquals(blocks[1].globalOffsetRange!!.last + 2, blocks[2].globalOffsetRange!!.first)

        // Chaque phrase pointe vers le bloc qui la contient.
        val sentences = chapter.sentences
        assertEquals(3, sentences.size)
        assertEquals(0, sentences[0].blockIndex)
        assertEquals(1, sentences[1].blockIndex)
        assertEquals(2, sentences[2].blockIndex)
    }

    @Test
    fun `div imbrique produit des blocs a tous les niveaux`() {
        // Structure profonde : body > div > div > p (comme les EPUB
        // « Le Trône de Fer » : <div class="div-chap"><div class="div-dev"><p>).
        val html = """
            <html><body>
                <div class="div-chap">
                    <h1>Chapitre</h1>
                    <div class="div-dev">
                        <p>Paragraphe un.</p>
                        <p>Paragraphe deux.</p>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is BookBlock.HeadingBlock)
        assertEquals("Paragraphe un.", (blocks[1] as BookBlock.ParagraphBlock).richText.plainText)
        assertEquals("Paragraphe deux.", (blocks[2] as BookBlock.ParagraphBlock).richText.plainText)
    }

    @Test
    fun `div de texte inline sans bloc enfant reste un seul paragraphe`() {
        // Un <div> qui n'enveloppe que du contenu inline (aucun <p>/<h1>/…)
        // ne doit PAS être découpé : il reste un paragraphe aplati.
        val html = """<html><body><div class="note">Une simple note <b>en gras</b>.</div></body></html>"""
        val chapter = parse(html)

        val blocks = richBlocks(chapter)
        assertEquals(1, blocks.size)
        val block = blocks[0] as BookBlock.ParagraphBlock
        assertEquals("Une simple note en gras.", block.richText.plainText)
        assertTrue(block.richText.spans.any { SpanStyles.STRONG in it.styles })
    }

    // ---- Tests de fragment ----

    @Test
    fun `fragment prologue extrait a partir de l ancre et exclut le header`() {
        // Scénario exact du plan : <div id="header"> AVANT l'ancre #prologue.
        // Le header ne doit PAS apparaître dans les BookBlock extraits.
        val html = """
            <html><body>
                <div id="header">En-tête du document.</div>
                <div id="prologue">
                    <h2>Prologue</h2>
                    <p>Ceci est le prologue.</p>
                </div>
                <div id="chapter1">
                    <h2>Chapitre 1</h2>
                    <p>Ceci est le chapitre 1.</p>
                </div>
            </body></html>
        """.trimIndent()

        val chapter = parse(html, fragment = "#prologue")

        val blocks = richBlocks(chapter)
        val allText = blocks.filterIsInstance<BookBlock.ParagraphBlock>()
            .joinToString(" ") { it.richText.plainText } +
            blocks.filterIsInstance<BookBlock.HeadingBlock>()
                .joinToString(" ") { it.richText.plainText }

        // Le prologue doit être présent
        assertTrue("Le prologue doit être présent", allText.contains("Prologue"))
        assertTrue("Le texte du prologue doit être présent", allText.contains("Ceci est le prologue"))
        // L'en-tête AVANT l'ancre ne doit PAS apparaître
        org.junit.Assert.assertFalse(
            "Le <div id='header'> avant l'ancre ne doit pas apparaître dans les BookBlock extraits",
            allText.contains("En-tête du document"),
        )
        // Le contenu APRÈS le fragment (chapter1) doit être présent
        // (le fragment dit « commencer ici », on lit jusqu'à la fin du fichier)
        assertTrue("Le contenu après le fragment doit être présent", allText.contains("Chapitre 1"))
    }

    // ---- Tests de globalOffsetRange ----

    @Test
    fun `les globalOffsetRange sont separes d'un caractere entre blocs`() {
        val html = "<html><body><h1>Titre</h1><p>Premier paragraphe.</p><p>Second paragraphe.</p></body></html>"
        val chapter = parse(html)

        val blocks = richBlocks(chapter).filter { it.globalOffsetRange != null }
        assertTrue("Doit avoir au moins 2 blocs de texte", blocks.size >= 2)

        // Premier bloc commence à 0
        assertEquals(0, blocks[0].globalOffsetRange!!.first)

        // Les blocs suivants doivent réserver exactement 1 caractère entre
        // la fin du bloc N-1 et le début du bloc N (le séparateur inséré
        // par tokenizeSentences — sans ce décalage, deux paragraphes
        // consécutifs fusionneraient sans espace dans le texte concaténé).
        for (i in 1 until blocks.size) {
            val prevRange = blocks[i - 1].globalOffsetRange!!
            val currRange = blocks[i].globalOffsetRange!!
            assertEquals(
                "L'offset de début du bloc $i doit laisser 1 caractère de séparateur après la fin du bloc ${i - 1}",
                prevRange.last + 2,
                currRange.first,
            )
        }

        // Vérifier que tout le texte est couvert
        val allText = blocks.joinToString("") { block ->
            when (block) {
                is BookBlock.ParagraphBlock -> block.richText.plainText
                is BookBlock.HeadingBlock -> block.richText.plainText
                else -> ""
            }
        }
        assertTrue(allText.contains("Titre"))
        assertTrue(allText.contains("Premier"))
        assertTrue(allText.contains("Second"))
    }

    // ---- Tests de tokenisation des phrases ----

    @Test
    fun `chapitre multi-blocs produit des sentences avec blockIndex pointant vers le bon bloc`() {
        // 3 blocs avec séparateurs naturels (sauts de ligne) pour garantir
        // des phrases distinctes dans chaque bloc.
        val html = """
            <html><body>
                <h1>Titre.</h1>
                <p>Première phrase.</p>
                <p>Deuxième phrase.</p>
            </body></html>
        """.trimIndent()

        val chapter = parse(html)
        val blocks = richBlocks(chapter)
        val sentences = chapter.sentences

        // Doit avoir des sentences
        assertTrue("Les sentences ne doivent pas être vides", sentences.isNotEmpty())
        assertTrue("Doit avoir au moins 3 blocs", blocks.size >= 3)

        // Vérifier que chaque sentence a un blockIndex valide (>= 0)
        sentences.forEach { sentence ->
            assertTrue(
                "blockIndex doit être >= 0, reçu: ${sentence.blockIndex}",
                sentence.blockIndex >= 0,
            )
            assertTrue(
                "blockIndex doit être < ${blocks.size}, reçu: ${sentence.blockIndex}",
                sentence.blockIndex < blocks.size,
            )
        }

        // Vérifier que les globalOffsetRange ne se chevauchent pas
        val textBlocks = blocks.filter { it.globalOffsetRange != null }
        for (i in 0 until textBlocks.size - 1) {
            val curr = textBlocks[i].globalOffsetRange!!
            val next = textBlocks[i + 1].globalOffsetRange!!
            assertTrue(
                "Bloc $i [${curr.first},${curr.last}] chevauche bloc ${i + 1} [${next.first},${next.last}]",
                curr.last < next.first,
            )
        }
    }

    // ---- Tests de liens ----

    @Test
    fun `balise a avec href produit Span REFERENCE`() {
        val html = """<html><body><p>Visitez <a href="http://example.com">notre site</a>.</p></body></html>"""
        val chapter = parse(html)

        val block = richBlocks(chapter)[0] as BookBlock.ParagraphBlock
        val refSpans = block.richText.spans.filter { SpanStyles.REFERENCE in it.styles }
        assertEquals(1, refSpans.size)
        assertEquals("http://example.com", refSpans[0].href)
    }

    // ---- Tests sup/sub ----

    @Test
    fun `sup produit Span SUPERSCRIPT et sub produit Span SUBSCRIPT`() {
        val html = "<html><body><p>E=mc<sup>2</sup> et H<sub>2</sub>O</p></body></html>"
        val chapter = parse(html)

        val block = richBlocks(chapter)[0] as BookBlock.ParagraphBlock
        val supSpans = block.richText.spans.filter { SpanStyles.SUPERSCRIPT in it.styles }
        val subSpans = block.richText.spans.filter { SpanStyles.SUBSCRIPT in it.styles }
        assertEquals(1, supSpans.size)
        assertEquals(1, subSpans.size)
    }

    // ---- Helpers ----

    private fun parse(html: String, fragment: String? = null): Chapter {
        val stream = ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
        return parser.parse(stream, "http://example.com/chapter.xhtml", 0, "chapter.xhtml", fragment)
    }

    private fun richBlocks(chapter: Chapter): List<BookBlock> {
        return (chapter.content as ChapterContent.Rich).blocks
    }
}
