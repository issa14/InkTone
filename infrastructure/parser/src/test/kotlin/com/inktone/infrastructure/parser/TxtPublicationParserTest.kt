package com.inktone.infrastructure.parser

import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TxtPublicationParserTest {

    @Test
    fun decoupe_un_texte_simple_en_phrases() = runTest {
        val file = File.createTempFile("test", ".txt").apply {
            writeText("Bonjour le monde. Ceci est un test. Il fonctionne !")
            deleteOnExit()
        }
        val result = TxtPublicationParser().parse(file.absolutePath)
        check(result is ParseResult.Success)

        val sentences = result.documentModel.chapters.single().paragraphs.single().sentences
        assertEquals(3, sentences.size)
        assertEquals("Bonjour le monde.", sentences[0].text)
        assertEquals("Il fonctionne !", sentences[2].text)
    }

    @Test
    fun fichier_vide_renvoie_corrompu_pas_un_crash() = runTest {
        val file = File.createTempFile("empty", ".txt").apply { deleteOnExit() }
        val result = TxtPublicationParser().parse(file.absolutePath)
        assertTrue(result is ParseResult.Corrupted)
    }

    @Test
    fun fichier_inexistant_renvoie_corrompu_pas_une_exception() = runTest {
        val result = TxtPublicationParser().parse("/chemin/qui/n/existe/pas.txt")
        assertTrue(result is ParseResult.Corrupted)
    }
}
