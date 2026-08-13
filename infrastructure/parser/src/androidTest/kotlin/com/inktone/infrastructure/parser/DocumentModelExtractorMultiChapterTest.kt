package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Ferme le point laissé explicitement ouvert en Phase 3 (Tâche 3.4) :
 * DocumentModelExtractor n'avait jamais été vérifié au-delà d'un fixture
 * à un seul chapitre. Ce test ne modifie pas l'extracteur — il le met à
 * l'épreuve.
 *
 * Plan v3 — le contenu réel par chapitre vient maintenant d'[EpubChapterParser]
 * (Palier 2.1, parsing paresseux D2) ; [ReadiumPublicationParser.parse]
 * ne fournit plus que les coquilles (href/index/titre).
 */
@RunWith(AndroidJUnit4::class)
class DocumentModelExtractorMultiChapterTest {

    @Test
    fun extrait_trois_chapitres_sans_contamination_croisee() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = copyAssetToCache(context, "fixture-multi-chapitre.epub")
        val parser = ReadiumPublicationParser(context)

        val result = parser.parse(fixtureFile.absolutePath)
        check(result is ParseResult.Success)
        val shells = result.documentModel.chapters

        assertEquals("le fixture a 3 chapitres", 3, shells.size)

        val chapterParser = EpubChapterParser(ReadiumPublicationRegistry(context), JsoupChapterParser())
        chapterParser.registerPublication("test-multi-chapitre", fixtureFile.absolutePath)
        val chapters = shells.map { shell -> chapterParser.parseChapter("test-multi-chapitre", shell.href) }

        // Le test critique : le contenu de chaque chapitre doit être
        // DISTINCT et ne pas fuiter dans les chapitres voisins. Si le
        // filtrage par href (Href.resolve(), Tâche 3.4) a une régression
        // sur un cas à plusieurs ressources, ce test doit le révéler —
        // un test sur un seul chapitre ne le pouvait pas par construction.
        val allSentenceTexts = chapters.map { chapter -> chapter.sentences.joinToString(" ") { it.text } }
        assertTrue("chapitre 1 doit contenir son propre texte", allSentenceTexts[0].contains("premier"))
        assertTrue("chapitre 2 doit contenir son propre texte", allSentenceTexts[1].contains("deuxieme"))
        assertTrue("chapitre 3 doit contenir son propre texte", allSentenceTexts[2].contains("troisieme"))
        assertTrue(
            "le chapitre 1 ne doit PAS contenir le texte du chapitre 2",
            !allSentenceTexts[0].contains("deuxieme"),
        )

        // Chaque Sentence.startOffset doit repartir de 0 par chapitre —
        // pas de dérive d'un compteur runningOffset partagé entre
        // chapitres (bug plausible si l'extracteur réutilisait par
        // erreur une variable au mauvais niveau de portée).
        chapters.forEach { chapter ->
            val firstSentence = chapter.sentences.first()
            assertEquals("chaque chapitre commence son offset a 0", 0, firstSentence.startOffset)
        }
    }

    @Test
    fun le_tableau_des_matieres_correspond_aux_trois_chapitres() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = copyAssetToCache(context, "fixture-multi-chapitre.epub")
        val result = ReadiumPublicationParser(context).parse(fixtureFile.absolutePath)
        check(result is ParseResult.Success)

        assertEquals(3, result.documentModel.tableOfContents.size)
    }

    private fun copyAssetToCache(context: Context, assetName: String): File {
        val outFile = File(context.cacheDir, assetName)
        context.assets.open(assetName).use { input -> outFile.outputStream().use { input.copyTo(it) } }
        return outFile
    }
}
