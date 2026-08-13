package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bug réel trouvé sur appareil (éditions fantasy type "La Première Loi",
 * "L'Arcane des Épées") : écran noir, bloqué sur "Chapitre 1 (1/1)", 0,0%
 * — la couverture est exclue de `readingOrder` (`linear="no"`) et
 * identifiable uniquement via `<guide><reference type="cover">` (EPUB2),
 * que Readium 3.0.0 n'analyse pas du tout (vérifié par décompilation).
 *
 * Directive de correction 1 : [ReadiumPublicationParser.resolveCoverHref]
 * doit forcer l'instanciation de la couverture en premier chapitre, via
 * `Publication.linkWithRel("cover")` (déjà résolu par Readium pour
 * `properties="cover-image"` et `<meta name="cover">`) puis, en repli,
 * [EpubGuideCoverResolver] pour le cas `<guide>` seul.
 */
@RunWith(AndroidJUnit4::class)
class ReadiumPublicationParserCoverFallbackTest {

    private val xhtmlHeader = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><body>"""
    private val xhtmlFooter = "</body></html>"

    @Test
    fun couverture_linear_no_identifiee_uniquement_via_guide_devient_premier_chapitre() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Test Guide Cover</dc:title>
    <dc:identifier id="BookId">urn:uuid:test-guide-cover</dc:identifier>
    <dc:language>fr</dc:language>
  </metadata>
  <manifest>
    <item id="titlepage" href="titlepage.xhtml" media-type="application/xhtml+xml"/>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="titlepage" linear="no"/>
    <itemref idref="chapter1"/>
  </spine>
  <guide>
    <reference type="cover" title="Cover" href="titlepage.xhtml"/>
  </guide>
</package>"""
        val titlepage = "$xhtmlHeader<div><svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 100 150\"><image width=\"100\" height=\"150\" xlink:href=\"cover.jpg\"/></svg></div>$xhtmlFooter"
        val chapter1 = "$xhtmlHeader<p>Bonjour ceci est le premier chapitre.</p>$xhtmlFooter"

        val epubFile = TestEpubBuilder.writeToCache(
            context,
            "fixture-guide-cover-linear-no.epub",
            mapOf(
                "mimetype" to TestEpubBuilder.text(TestEpubBuilder.MIMETYPE),
                "META-INF/container.xml" to TestEpubBuilder.text(TestEpubBuilder.CONTAINER_XML),
                "OEBPS/content.opf" to TestEpubBuilder.text(opf),
                "OEBPS/titlepage.xhtml" to TestEpubBuilder.text(titlepage),
                "OEBPS/chapter1.xhtml" to TestEpubBuilder.text(chapter1),
                "OEBPS/cover.jpg" to TestEpubBuilder.MINIMAL_JPEG_BYTES,
            ),
        )

        val result = ReadiumPublicationParser(context).parse(epubFile.absolutePath)
        assertTrue("le parsing doit reussir", result is ParseResult.Success)
        val success = result as ParseResult.Success

        assertEquals(
            "la couverture (linear=no, guide seul) doit etre ajoutee comme chapitre supplementaire",
            2,
            success.documentModel.chapters.size,
        )
        assertTrue(
            "le premier chapitre doit etre la couverture (titlepage.xhtml), pas chapter1",
            success.documentModel.chapters.first().href.endsWith("titlepage.xhtml"),
        )
        assertEquals(0, success.documentModel.chapters.first().index)
        assertTrue(
            "chapter1 doit etre decale en index 1",
            success.documentModel.chapters[1].href.endsWith("chapter1.xhtml"),
        )
        assertEquals(1, success.documentModel.chapters[1].index)
    }

    @Test
    fun couverture_deja_presente_dans_readingOrder_n_est_pas_dupliquee() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Test Cover Deja Presente</dc:title>
    <dc:identifier id="BookId">urn:uuid:test-cover-present</dc:identifier>
    <dc:language>fr</dc:language>
  </metadata>
  <manifest>
    <item id="titlepage" href="titlepage.xhtml" media-type="application/xhtml+xml"/>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="titlepage"/>
    <itemref idref="chapter1"/>
  </spine>
  <guide>
    <reference type="cover" title="Cover" href="titlepage.xhtml"/>
  </guide>
</package>"""
        val titlepage = "$xhtmlHeader<div><svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 100 150\"><image width=\"100\" height=\"150\" xlink:href=\"cover.jpg\"/></svg></div>$xhtmlFooter"
        val chapter1 = "$xhtmlHeader<p>Bonjour ceci est le premier chapitre.</p>$xhtmlFooter"

        val epubFile = TestEpubBuilder.writeToCache(
            context,
            "fixture-guide-cover-deja-present.epub",
            mapOf(
                "mimetype" to TestEpubBuilder.text(TestEpubBuilder.MIMETYPE),
                "META-INF/container.xml" to TestEpubBuilder.text(TestEpubBuilder.CONTAINER_XML),
                "OEBPS/content.opf" to TestEpubBuilder.text(opf),
                "OEBPS/titlepage.xhtml" to TestEpubBuilder.text(titlepage),
                "OEBPS/chapter1.xhtml" to TestEpubBuilder.text(chapter1),
                "OEBPS/cover.jpg" to TestEpubBuilder.MINIMAL_JPEG_BYTES,
            ),
        )

        val result = ReadiumPublicationParser(context).parse(epubFile.absolutePath)
        assertTrue("le parsing doit reussir", result is ParseResult.Success)
        val success = result as ParseResult.Success

        assertEquals(
            "la couverture est deja dans readingOrder : pas de doublon ajoute",
            2,
            success.documentModel.chapters.size,
        )
    }

    /**
     * Bug réel trouvé sur appareil (rapporté par Issa, verification manuelle) :
     * chaque entrée de la TOC ouvrait le chapitre PRÉCÉDENT le sien, et la
     * dernière entrée ne pointait plus vers rien de valide — `chapterIndex`
     * restait calculé contre `publication.readingOrder` (non décalé) alors
     * que `chapters` (utilisé pour charger le contenu) l'était de +1 par
     * l'ajout de la couverture synthétique.
     */
    @Test
    fun toc_chapterIndex_decale_du_meme_montant_que_la_couverture_ajoutee() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Test TOC Offset</dc:title>
    <dc:identifier id="BookId">urn:uuid:test-toc-offset</dc:identifier>
    <dc:language>fr</dc:language>
  </metadata>
  <manifest>
    <item id="titlepage" href="titlepage.xhtml" media-type="application/xhtml+xml"/>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="chapter2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
  </manifest>
  <spine toc="ncx">
    <itemref idref="titlepage" linear="no"/>
    <itemref idref="chapter1"/>
    <itemref idref="chapter2"/>
  </spine>
  <guide>
    <reference type="cover" title="Cover" href="titlepage.xhtml"/>
  </guide>
</package>"""
        val ncx = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="urn:uuid:test-toc-offset"/></head>
  <docTitle><text>Test TOC Offset</text></docTitle>
  <navMap>
    <navPoint id="np1" playOrder="1">
      <navLabel><text>Chapitre un</text></navLabel>
      <content src="chapter1.xhtml"/>
    </navPoint>
    <navPoint id="np2" playOrder="2">
      <navLabel><text>Chapitre deux</text></navLabel>
      <content src="chapter2.xhtml"/>
    </navPoint>
  </navMap>
</ncx>"""
        val titlepage = "$xhtmlHeader<div><svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 100 150\"><image width=\"100\" height=\"150\" xlink:href=\"cover.jpg\"/></svg></div>$xhtmlFooter"
        val chapter1 = "$xhtmlHeader<p>Contenu du chapitre un.</p>$xhtmlFooter"
        val chapter2 = "$xhtmlHeader<p>Contenu du chapitre deux.</p>$xhtmlFooter"

        val epubFile = TestEpubBuilder.writeToCache(
            context,
            "fixture-toc-offset.epub",
            mapOf(
                "mimetype" to TestEpubBuilder.text(TestEpubBuilder.MIMETYPE),
                "META-INF/container.xml" to TestEpubBuilder.text(TestEpubBuilder.CONTAINER_XML),
                "OEBPS/content.opf" to TestEpubBuilder.text(opf),
                "OEBPS/toc.ncx" to TestEpubBuilder.text(ncx),
                "OEBPS/titlepage.xhtml" to TestEpubBuilder.text(titlepage),
                "OEBPS/chapter1.xhtml" to TestEpubBuilder.text(chapter1),
                "OEBPS/chapter2.xhtml" to TestEpubBuilder.text(chapter2),
                "OEBPS/cover.jpg" to TestEpubBuilder.MINIMAL_JPEG_BYTES,
            ),
        )

        val result = ReadiumPublicationParser(context).parse(epubFile.absolutePath)
        assertTrue("le parsing doit reussir", result is ParseResult.Success)
        val success = result as ParseResult.Success

        assertEquals(3, success.documentModel.chapters.size)
        assertEquals(2, success.documentModel.tableOfContents.size)

        val (tocChapter1, tocChapter2) = success.documentModel.tableOfContents
        assertEquals(
            "l'entree TOC 'Chapitre un' doit pointer vers l'index reel de chapter1.xhtml (decale par la couverture)",
            1,
            tocChapter1.chapterIndex,
        )
        assertEquals(
            "l'entree TOC 'Chapitre deux' (la derniere) doit pointer vers un index valide, pas au-dela de la fin",
            2,
            tocChapter2.chapterIndex,
        )
        assertEquals("chapter1.xhtml", success.documentModel.chapters[tocChapter1.chapterIndex].href.substringAfterLast('/'))
        assertEquals("chapter2.xhtml", success.documentModel.chapters[tocChapter2.chapterIndex].href.substringAfterLast('/'))
    }
}
