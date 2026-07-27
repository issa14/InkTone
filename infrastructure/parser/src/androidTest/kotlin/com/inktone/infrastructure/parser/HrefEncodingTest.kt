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
        val allSentences = result.documentModel.chapters.flatMap { it.paragraphs }.flatMap { it.sentences }
        assertTrue(
            "le contenu doit etre extrait malgre le href encode differemment entre le spine et le fichier interne",
            allSentences.isNotEmpty(),
        )
    }
}
