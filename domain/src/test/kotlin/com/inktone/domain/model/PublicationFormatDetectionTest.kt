package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PublicationFormatDetectionTest {

    private fun bytes(text: String) = text.toByteArray(Charsets.ISO_8859_1)

    @Test
    fun `l'extension connue prime sur le contenu`() {
        assertEquals(PublicationFormat.TXT, detectPublicationFormat("notes.TXT", bytes("PK\u0003")))
        assertEquals(PublicationFormat.PDF, detectPublicationFormat("livre.pdf", bytes("texte")))
        assertEquals(PublicationFormat.EPUB, detectPublicationFormat("livre.epub", bytes("%PDF-1.7")))
    }

    /** Audit UX D23 : un fichier texte sans extension `.txt` était rejeté comme corrompu. */
    @Test
    fun `sans extension, le contenu decide`() {
        assertEquals(PublicationFormat.TXT, detectPublicationFormat("Mon texte", bytes("Il était une fois, dans un pays lointain.")))
        assertEquals(PublicationFormat.PDF, detectPublicationFormat("document", bytes("%PDF-1.4\n")))
        assertEquals(PublicationFormat.EPUB, detectPublicationFormat("livre", bytes("PK\u0003\u0004mimetype")))
    }

    @Test
    fun `dans le doute, un EPUB`() {
        assertEquals(PublicationFormat.EPUB, detectPublicationFormat("inconnu", byteArrayOf(0, 1, 2, 3)))
        assertEquals(PublicationFormat.EPUB, detectPublicationFormat("inconnu", ByteArray(0)))
    }

    @Test
    fun `le format a son extension`() {
        assertEquals("epub", PublicationFormat.EPUB.fileExtension)
        assertEquals("txt", PublicationFormat.TXT.fileExtension)
        assertEquals("pdf", PublicationFormat.PDF.fileExtension)
    }
}
