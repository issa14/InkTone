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
}
