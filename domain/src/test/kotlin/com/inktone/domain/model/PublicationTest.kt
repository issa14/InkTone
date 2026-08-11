package com.inktone.domain.model

import org.junit.Assert.assertThrows
import org.junit.Test

class PublicationTest {

    private fun validPublication(fileHash: String = "hash1") = Publication(
        id = "pub-1",
        title = "Les Misérables",
        format = PublicationFormat.EPUB,
        fileUri = "content://fake/1",
        fileHash = fileHash,
        fileSize = 1000L,
        chapterCount = 10,
        importDate = 0L,
    )

    @Test
    fun `fileHash vide est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            validPublication(fileHash = "")
        }
    }

    @Test
    fun `title vide est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            validPublication().copy(title = "")
        }
    }

    @Test
    fun `pageCount nul est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            validPublication().copy(pageCount = 0)
        }
    }

    @Test
    fun `pageCount negatif est rejete`() {
        assertThrows(IllegalArgumentException::class.java) {
            validPublication().copy(pageCount = -5)
        }
    }

    @Test
    fun `pageCount absent est valide pour un format reflowable`() {
        validPublication().copy(pageCount = null)
    }
}
