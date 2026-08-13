package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Nécessite le fixture EPUB minimal dans
 * infrastructure/parser/src/androidTest/assets/fixture-minimal.epub
 * (livre de test à 1 chapitre, sans DRM, construit pour ce test — pas de
 * contenu tiers).
 */
@RunWith(AndroidJUnit4::class)
class ReadiumPublicationParserTest {

    @Test
    fun ouvre_un_epub_de_test_sans_erreur() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = copyAssetToCache(context, "fixture-minimal.epub")
        val parser = ReadiumPublicationParser(context)

        val result = parser.parse(fixtureFile.absolutePath)

        assertTrue("le parsing doit reussir sur un EPUB valide", result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertFalse("le fixture de test n'est pas protege par DRM", success.isDrmProtected)

        // Tache 3.4 : le DocumentModel doit contenir le texte reel du
        // fixture (extrait via publication.content()), pas le placeholder
        // vide de la Tache 3.2.
        assertEquals("le fixture n'a qu'un chapitre", 1, success.documentModel.chapters.size)
        val chapter = success.documentModel.chapters.first()
        val sentences = chapter.sentences
        assertTrue("au moins une phrase attendue dans le chapitre", sentences.isNotEmpty())
        assertTrue(
            "le texte extrait doit correspondre au contenu du fixture",
            sentences.any { it.text.contains("Bonjour") },
        )
    }

    private fun copyAssetToCache(context: Context, assetName: String): File {
        val outFile = File(context.cacheDir, assetName)
        context.assets.open(assetName).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outFile
    }
}
