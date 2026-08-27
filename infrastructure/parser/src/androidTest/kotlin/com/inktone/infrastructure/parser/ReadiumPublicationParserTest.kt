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
 *
 * Ce test affirmait auparavant que `parse()` renvoyait directement les
 * phrases du chapitre. Ce contrat n'existe plus : depuis le passage au
 * parsing paresseux (D2, plan v3), `parse()` délègue à `parseLazy`, qui ne
 * produit que des COQUILLES de chapitre — `blocks` et `sentences` vides,
 * volontairement — et le contenu réel vient d'[EpubChapterParser] au moment
 * où il est demandé. L'assertion périmée échouait donc sur une architecture
 * saine ; elle n'avait jamais été vue, ces tests ne tournant pas en
 * intégration continue.
 *
 * L'intention d'origine est conservée : le texte réel du fixture doit bien
 * finir par être extrait. Elle est simplement vérifiée là où l'extraction a
 * désormais lieu — même démarche que [HrefEncodingTest].
 */
@RunWith(AndroidJUnit4::class)
class ReadiumPublicationParserTest {

    @Test
    fun ouvre_un_epub_de_test_sans_erreur() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = copyAssetToCache(context, "fixture-minimal.epub")
        val parser = ReadiumPublicationParser(context, CoverStorage(context))

        val result = parser.parse(fixtureFile.absolutePath)

        assertTrue("le parsing doit reussir sur un EPUB valide", result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertFalse("le fixture de test n'est pas protege par DRM", success.isDrmProtected)
        assertEquals("le fixture n'a qu'un chapitre", 1, success.documentModel.chapters.size)
    }

    @Test
    fun ne_renvoie_que_des_coquilles_de_chapitre_le_contenu_venant_du_chapitre() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = copyAssetToCache(context, "fixture-minimal.epub")

        val result = ReadiumPublicationParser(context, CoverStorage(context)).parse(fixtureFile.absolutePath)
        check(result is ParseResult.Success)
        val shell = result.documentModel.chapters.first()

        // Le parsing paresseux est un choix, pas un manque : ouvrir un livre
        // ne doit pas coûter l'extraction de tous ses chapitres.
        assertTrue(
            "parse() ne doit extraire aucune phrase — c'est le role d'EpubChapterParser",
            shell.sentences.isEmpty(),
        )

        val chapterParser = EpubChapterParser(ReadiumPublicationRegistry(context), JsoupChapterParser())
        chapterParser.registerPublication("test-minimal", fixtureFile.absolutePath)
        val sentences = chapterParser.parseChapter("test-minimal", shell.href).sentences

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
