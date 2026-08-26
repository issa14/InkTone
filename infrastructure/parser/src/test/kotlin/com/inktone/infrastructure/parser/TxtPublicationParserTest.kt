package com.inktone.infrastructure.parser

import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream

/**
 * Fake local backee par de vrais `java.io.File` — le fake partage
 * (`core:testing`) simule le contenu a partir de l'URI elle-meme, pas
 * adapte ici ou le test a besoin d'un vrai contenu texte lisible. `uri`
 * est ici directement `file.absolutePath`, jamais une URI SAF reelle
 * (androidTest de `infrastructure/storage` couvre deja `content://` via
 * un vrai `ContentResolver`).
 */
private class LocalFileStorageService : FileStorageService {
    override suspend fun openInputStream(uri: String): InputStream? =
        File(uri).takeIf { it.exists() }?.inputStream()

    override suspend fun computeSha256(uri: String): String? = null
    override suspend fun getFileSize(uri: String): Long? = File(uri).takeIf { it.exists() }?.length()
    override suspend fun getFileName(uri: String): String? = File(uri).name
    override suspend fun persistReadPermission(uri: String) = Unit
    override suspend fun writeToUri(uri: String, sourceFile: File): Boolean = false
}

class TxtPublicationParserTest {

    private val parser = TxtPublicationParser(LocalFileStorageService())

    @Test
    fun decoupe_un_texte_simple_en_phrases() = runTest {
        val file = File.createTempFile("test", ".txt").apply {
            writeText("Bonjour le monde. Ceci est un test. Il fonctionne !")
            deleteOnExit()
        }
        val result = parser.parse(file.absolutePath)
        check(result is ParseResult.Success)

        val sentences = result.documentModel.chapters.single().sentences
        assertEquals(3, sentences.size)
        assertEquals("Bonjour le monde.", sentences[0].text)
        assertEquals("Il fonctionne !", sentences[2].text)
    }

    // Lot 21 — le parseur passe par FrenchSentenceSplitter (source unique
    // EPUB/PDF/TXT) : une abréviation française ne coupe plus la phrase.
    @Test
    fun ne_decoupe_pas_apres_une_abreviation_francaise() = runTest {
        val file = File.createTempFile("test", ".txt").apply {
            writeText("M. Dupont et Mme Martin sont arrivés. Dr. Petit les suivait. Enfin, il salua.")
            deleteOnExit()
        }
        val result = parser.parse(file.absolutePath)
        check(result is ParseResult.Success)

        val sentences = result.documentModel.chapters.single().sentences
        assertEquals(3, sentences.size)
        assertEquals("M. Dupont et Mme Martin sont arrivés.", sentences[0].text)
        assertEquals("Dr. Petit les suivait.", sentences[1].text)
        assertEquals("Enfin, il salua.", sentences[2].text)
    }

    @Test
    fun fichier_vide_renvoie_corrompu_pas_un_crash() = runTest {
        val file = File.createTempFile("empty", ".txt").apply { deleteOnExit() }
        val result = parser.parse(file.absolutePath)
        assertTrue(result is ParseResult.Corrupted)
    }

    @Test
    fun fichier_inexistant_renvoie_corrompu_pas_une_exception() = runTest {
        val result = parser.parse("/chemin/qui/n/existe/pas.txt")
        assertTrue(result is ParseResult.Corrupted)
    }
}
