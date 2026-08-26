package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.core.testing.fake.FakePreAnalysisStore
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.domain.model.BookBlock
import com.inktone.domain.model.ChapterContent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bug réel trouvé sur appareil : certains générateurs d'EPUB placent
 * directement l'item binaire (`image/jpeg`) dans le `<spine>`, sans
 * fichier XHTML pour l'envelopper. Tenter un parsing Jsoup sur des octets
 * JPEG produit un chapitre sans texte extractible — écran noir.
 *
 * Directive de correction 2 : [EpubChapterParser.imageChapterOrNull] doit
 * détecter ce cas via le type MIME du [org.readium.r2.shared.publication.Link]
 * résolu et construire directement un [BookBlock.ImageBlock], sans passer
 * par Jsoup.
 */
@RunWith(AndroidJUnit4::class)
class EpubChapterParserBinaryImageTest {

    @Test
    fun item_image_directement_dans_le_spine_produit_un_seul_ImageBlock() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Test Image Directe Spine</dc:title>
    <dc:identifier id="BookId">urn:uuid:test-image-spine</dc:identifier>
    <dc:language>fr</dc:language>
  </metadata>
  <manifest>
    <item id="cover-img" href="cover.jpg" media-type="image/jpeg"/>
    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="cover-img"/>
    <itemref idref="chapter1"/>
  </spine>
</package>"""
        val chapter1 = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><body><p>Bonjour ceci est le premier chapitre.</p></body></html>"""

        val epubFile = TestEpubBuilder.writeToCache(
            context,
            "fixture-image-directe-spine.epub",
            mapOf(
                "mimetype" to TestEpubBuilder.text(TestEpubBuilder.MIMETYPE),
                "META-INF/container.xml" to TestEpubBuilder.text(TestEpubBuilder.CONTAINER_XML),
                "OEBPS/content.opf" to TestEpubBuilder.text(opf),
                "OEBPS/chapter1.xhtml" to TestEpubBuilder.text(chapter1),
                "OEBPS/cover.jpg" to TestEpubBuilder.MINIMAL_JPEG_BYTES,
            ),
        )

        val chapterParser = EpubChapterParser(ReadiumPublicationRegistry(context), JsoupChapterParser(), FakePreAnalysisStore(), FakePublicationRepository())
        chapterParser.registerPublication("test-image-spine", epubFile.absolutePath)

        val imageChapter = chapterParser.parseChapter("test-image-spine", "OEBPS/cover.jpg")

        val content = imageChapter.content
        assertTrue("le contenu doit etre ChapterContent.Rich", content is ChapterContent.Rich)
        val blocks = (content as ChapterContent.Rich).blocks
        assertEquals("un seul bloc, l'image elle-meme", 1, blocks.size)
        val imageBlock = blocks.first()
        assertTrue("le bloc doit etre un ImageBlock", imageBlock is BookBlock.ImageBlock)
        assertEquals("OEBPS/cover.jpg", (imageBlock as BookBlock.ImageBlock).href)
        assertTrue("aucune phrase pour un chapitre image pure", imageChapter.sentences.isEmpty())

        // Le chapitre XHTML normal qui suit doit rester intact (pas de
        // regression sur le chemin Jsoup habituel).
        val textChapter = chapterParser.parseChapter("test-image-spine", "OEBPS/chapter1.xhtml")
        assertTrue(textChapter.sentences.any { it.text.contains("Bonjour") })
    }
}
