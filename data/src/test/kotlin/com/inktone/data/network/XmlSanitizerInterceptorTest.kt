package com.inktone.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class XmlSanitizerInterceptorTest {

    private val server = MockWebServer()

    @Before fun setUp() { server.start() }

    @After fun tearDown() { server.shutdown() }

    private val client = OkHttpClient.Builder().addInterceptor(XmlSanitizerInterceptor()).build()

    private fun body(contentType: String, raw: String): String {
        server.enqueue(MockResponse().setHeader("Content-Type", contentType).setBody(raw))
        return client.newCall(Request.Builder().url(server.url("/")).build()).execute().use {
            it.body!!.string()
        }
    }

    @Test
    fun echappe_les_esperluettes_non_echappees_dans_un_flux_atom() {
        assertEquals("Tom &amp; Jerry", body("application/atom+xml", "Tom & Jerry"))
    }

    @Test
    fun preserve_les_entites_xml_deja_valides() {
        assertEquals("Tom &amp; Jerry", body("application/atom+xml", "Tom &amp; Jerry"))
    }

    @Test
    fun preserve_les_references_numeriques() {
        assertEquals("&#233;", body("application/atom+xml", "&#233;"))
        assertEquals("&#xE9;", body("application/atom+xml", "&#xE9;"))
    }

    @Test
    fun echappe_les_entites_html_nommees_qui_ne_sont_pas_xml() {
        // Comportement attendu : l'entité HTML `&eacute;` n'est pas une
        // entité XML, son `&` est échappé — le parseur ne plante plus.
        assertEquals("Caf&amp;eacute;", body("application/atom+xml", "Caf&eacute;"))
    }

    @Test
    fun ne_touche_pas_les_reponses_non_xml() {
        assertEquals("Tom & Jerry", body("application/json", "Tom & Jerry"))
    }

    @Test
    fun ne_touche_pas_un_corps_binaire_epub() {
        assertEquals("raw&bytes", body("application/epub+zip", "raw&bytes"))
    }
}
