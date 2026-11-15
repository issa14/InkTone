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
    override suspend fun copyToLibrary(uri: String, fileName: String, fileHash: String): String? = uri
    override fun isAppOwned(uri: String): Boolean = false
    override suspend fun deleteOwnedFile(uri: String) = Unit
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

    // Lot 21 (correctif) — le contrat d'offsets absolus de
    // FrenchSentenceSplitter (substring == phrase) doit survivre au
    // passage par ce parseur : c'est la propriété dont dépendent le
    // surlignage mot-à-mot et l'index FTS, et qu'aucun test n'exerçait
    // au point d'intégration (seulement dans `domain`).
    @Test
    fun les_offsets_des_phrases_sont_des_substrings_exacts_du_texte_source() = runTest {
        val text = "Bonjour le monde. Ceci est un test. Il fonctionne !"
        val file = File.createTempFile("test", ".txt").apply {
            writeText(text)
            deleteOnExit()
        }
        val result = parser.parse(file.absolutePath)
        check(result is ParseResult.Success)

        val trimmedText = text.trim()
        val sentences = result.documentModel.chapters.single().sentences
        assertEquals(3, sentences.size)
        sentences.forEach { sentence ->
            assertEquals(
                sentence.text,
                trimmedText.substring(sentence.startOffset, sentence.endOffset),
            )
        }
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

    /**
     * Bug réel corrigé (audit UX D21) : le TXT entier formait un seul bloc ;
     * en défilement, ni la progression ni la reprise ne suivaient la lecture.
     */
    @Test
    fun chaque_paragraphe_forme_un_bloc_et_ses_phrases_y_appartiennent() = runTest {
        val text = "Premier paragraphe. Deux phrases.\n\nSecond paragraphe sans point\n\nTroisième."
        val file = File.createTempFile("test", ".txt").apply {
            writeText(text)
            deleteOnExit()
        }
        val result = parser.parse(file.absolutePath)
        check(result is ParseResult.Success)
        val chapter = result.documentModel.chapters.single()
        val blocks = (chapter.content as com.inktone.domain.model.ChapterContent.Rich).blocks
            .filterIsInstance<com.inktone.domain.model.BookBlock.ParagraphBlock>()

        assertEquals(3, blocks.size)
        assertEquals(listOf(0, 0, 1, 2), chapter.sentences.map { it.blockIndex })
        chapter.sentences.forEach { sentence ->
            assertEquals("offsets inchangés", sentence.text, text.substring(sentence.startOffset, sentence.endOffset))
            val block = blocks[sentence.blockIndex]
            assertEquals(true, sentence.startOffset in block.globalOffsetRange)
        }
    }

    @Test
    fun sans_ligne_vide_chaque_ligne_est_un_paragraphe() {
        assertEquals(listOf(0 until 3, 4 until 8), paragraphRanges("Une\nDeux"))
    }

    // Audit UX D22 : le décodage UTF-8 ne levait jamais d'erreur, un TXT en
    // Windows-1252 s'affichait avec des caractères de remplacement.
    @Test
    fun decode_un_texte_windows_1252() {
        val bytes = "L'été dernier, à Noël, où elle était née.".toByteArray(charset("windows-1252"))
        assertEquals("L'été dernier, à Noël, où elle était née.", decodeText(bytes))
    }

    @Test
    fun garde_un_texte_utf_8_valide_et_ecarte_sa_marque_d_ordre_des_octets() {
        val utf8 = "Élève né à Noël — « oui ».".toByteArray(Charsets.UTF_8)
        assertEquals("Élève né à Noël — « oui ».", decodeText(utf8))
        assertEquals("Élève", decodeText(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Élève".toByteArray()))
    }

    @Test
    fun decode_un_texte_utf_16_par_sa_marque_d_ordre_des_octets() {
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Été".toByteArray(Charsets.UTF_16LE)
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "Été".toByteArray(Charsets.UTF_16BE)
        assertEquals("Été", decodeText(le))
        assertEquals("Été", decodeText(be))
    }

    @Test
    fun un_fichier_windows_1252_s_importe_avec_ses_accents() = runTest {
        val file = File.createTempFile("test", ".txt").apply {
            writeBytes("Il était une fois. Un été brûlant.".toByteArray(charset("windows-1252")))
            deleteOnExit()
        }
        val result = parser.parse(file.absolutePath)
        check(result is ParseResult.Success)

        assertEquals("Il était une fois.", result.documentModel.chapters.single().sentences.first().text)
    }
}
