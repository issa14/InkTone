package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.FileStorageService
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream

/**
 * Meme patron que `LocalFileStorageService` de `TxtPublicationParserTest`
 * (src/test) - source set distinct, pas partageable telle quelle. `uri`
 * est ici `file.absolutePath`, jamais une URI SAF reelle.
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

/**
 * Lot 12, tache 12.3 - verifie que le DocumentModel est construit
 * honnetement (texte reellement extrait, pas un objet vide de facade,
 * decision actee 4 du plan) et que la couverture est sauvegardee. Le
 * catalogue complet des cas d'erreur (page scannee, corrompu, mot de
 * passe usurpe...) est couvert par la tache 12.6.
 */
@RunWith(AndroidJUnit4::class)
class PdfPublicationParserTest {

    private fun fixture(name: String): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return File(context.cacheDir, name).apply {
            context.assets.open(name).use { input -> outputStream().use { input.copyTo(it) } }
        }
    }

    private fun parser(): PdfPublicationParser {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return PdfPublicationParser(LocalFileStorageService(), context)
    }

    @Test
    fun extrait_le_texte_reel_d_un_pdf_vectoriel() = runTest {
        val file = fixture("fixture-valid.pdf")
        val result = parser().parse(file.absolutePath)

        check(result is ParseResult.Success)
        assertEquals(1, result.documentModel.chapters.size)
        val chapter = result.documentModel.chapters.single()
        assertEquals("page-0", chapter.href)
        assertTrue("le texte de la page doit etre extrait", chapter.paragraphs.isNotEmpty())
        val text = chapter.paragraphs.flatMap { it.sentences }.joinToString(" ") { it.text }
        assertTrue(text.contains("InkTone"))
    }

    @Test
    fun sauvegarde_une_couverture_pour_un_pdf_valide() = runTest {
        val file = fixture("fixture-valid.pdf")
        val result = parser().parse(file.absolutePath)

        check(result is ParseResult.Success)
        val coverUri = result.metadata.coverUri
        assertFalse("une couverture doit etre sauvegardee", coverUri.isNullOrBlank())
        assertTrue(File(coverUri!!).exists())
    }
}
