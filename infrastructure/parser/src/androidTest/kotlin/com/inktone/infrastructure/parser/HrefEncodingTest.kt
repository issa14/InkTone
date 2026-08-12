package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Test-first, K6 : vérifie si le pipeline existant (Href.resolve(),
 * Tâche 3.4) gère déjà correctement le percent-encoding mixte, avant de
 * supposer qu'un correctif custom est nécessaire.
 *
 * Plan v3 — `ReadiumPublicationParser.parse()` ne fait plus qu'extraire
 * des coquilles de chapitre (D2, parsing paresseux) : le contenu réel
 * (et donc l'exercice du href encodé) vient maintenant d'[EpubChapterParser]
 * (Palier 2.1), qui doit résoudre le href de chapitre exactement comme
 * l'ancien [DocumentModelExtractor] le faisait.
 */
@RunWith(AndroidJUnit4::class)
class HrefEncodingTest {

    @Test
    fun extrait_le_contenu_malgre_des_hrefs_encodes_differemment() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = File(context.cacheDir, "fixture-hrefs-encodes.epub").apply {
            context.assets.open("fixture-hrefs-encodes.epub").use { i -> outputStream().use { i.copyTo(it) } }
        }
        val result = ReadiumPublicationParser(context).parse(fixtureFile.absolutePath)
        check(result is ParseResult.Success)

        val chapterParser = EpubChapterParser(ReadiumPublicationRegistry(context), JsoupChapterParser())
        chapterParser.registerPublication("test-hrefs-encodes", fixtureFile.absolutePath)
        val allSentences = result.documentModel.chapters.flatMap { shell ->
            chapterParser.parseChapter("test-hrefs-encodes", shell.href).sentences
        }
        assertTrue(
            "le contenu doit etre extrait malgre le href encode differemment entre le spine et le fichier interne",
            allSentences.isNotEmpty(),
        )
    }
}
