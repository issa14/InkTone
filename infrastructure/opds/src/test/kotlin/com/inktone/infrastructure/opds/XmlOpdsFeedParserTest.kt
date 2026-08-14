package com.inktone.infrastructure.opds

import com.inktone.domain.model.OpdsItem
import com.inktone.domain.service.OpdsFailureReason
import com.inktone.domain.service.OpdsParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Lot 13, tâche 13.2.8 — fixtures XML réelles, cas malformé, sans lien d'acquisition, hrefs relatifs. */
@RunWith(RobolectricTestRunner::class)
class OpdsFeedParserTest {

    private val parser = XmlOpdsFeedParser()

    private val gutenbergFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
          <title>Gutenberg Catalog</title>
          <link rel="search" type="application/opensearchdescription+xml" href="https://www.gutenberg.org/ebooks/opensearch?q={searchTerms}"/>
          <link rel="next" href="/ebooks/page2.opds"/>
          <entry>
            <title>Science Fiction</title>
            <link rel="subsection" type="application/atom+xml" href="/ebooks/sf.atom"/>
          </entry>
          <entry>
            <title>The Time Machine</title>
            <author><name>H. G. Wells</name></author>
            <link rel="http://opds-spec.org/image/thumbnail" href="/covers/timemachine.jpg"/>
            <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/ebooks/timemachine.epub"/>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun parse_un_flux_atom_reel_en_navigation_et_livre() {
        val result = parser.parse(gutenbergFeed, "https://www.gutenberg.org/ebooks.opds")

        val feed = (result as OpdsParseResult.Success).feed
        assertEquals("Gutenberg Catalog", feed.title)
        assertEquals("https://www.gutenberg.org/ebooks/page2.opds", feed.nextPageUrl)
        assertEquals("https://www.gutenberg.org/ebooks/opensearch?q={searchTerms}", feed.searchTemplateUrl)
        assertEquals(2, feed.items.size)

        val nav = feed.items[0] as OpdsItem.Navigation
        assertEquals("Science Fiction", nav.title)
        // href relatif résolu contre l'URL du flux consulté.
        assertEquals("https://www.gutenberg.org/ebooks/sf.atom", nav.href)

        val book = feed.items[1] as OpdsItem.Book
        assertEquals("The Time Machine", book.title)
        assertEquals(listOf("H. G. Wells"), book.authors)
        assertEquals("https://www.gutenberg.org/covers/timemachine.jpg", book.coverUrl)
        assertEquals("https://www.gutenberg.org/ebooks/timemachine.epub", book.acquisitionHref)
        assertEquals("application/epub+zip", book.mimeType)
    }

    @Test
    fun un_flux_malforme_renvoie_malformed_feed_jamais_un_feed_vide() {
        val result = parser.parse("<feed><title>Incomplet</feed>", "https://example.com/opds")

        assertTrue(result is OpdsParseResult.Failure)
        assertEquals(OpdsFailureReason.MALFORMED_FEED, (result as OpdsParseResult.Failure).reason)
    }

    @Test
    fun une_entree_sans_lien_d_acquisition_ni_de_navigation_est_ignoree() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>T</title>
              <entry><title>Entrée orpheline</title></entry>
              <entry>
                <title>Livre</title>
                <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/book.epub"/>
              </entry>
            </feed>
        """.trimIndent()

        val feed = (parser.parse(xml, "https://example.com/opds") as OpdsParseResult.Success).feed

        assertEquals(1, feed.items.size)
        assertEquals("Livre", (feed.items[0] as OpdsItem.Book).title)
    }

    @Test
    fun un_href_deja_absolu_est_conserve_tel_quel() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>T</title>
              <entry>
                <title>Livre</title>
                <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="https://other.example.org/a/b.epub"/>
              </entry>
            </feed>
        """.trimIndent()

        val feed = (parser.parse(xml, "https://example.com/opds") as OpdsParseResult.Success).feed

        assertEquals("https://other.example.org/a/b.epub", (feed.items[0] as OpdsItem.Book).acquisitionHref)
    }

    @Test
    fun un_rel_search_pointant_vers_un_document_sans_template_ne_donne_pas_de_recherche() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>T</title>
              <link rel="search" type="application/opensearchdescription+xml" href="/opensearch.xml"/>
            </feed>
        """.trimIndent()

        val feed = (parser.parse(xml, "https://example.com/opds") as OpdsParseResult.Success).feed

        assertEquals(null, feed.searchTemplateUrl)
    }
}
