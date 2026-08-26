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

@RunWith(AndroidJUnit4::class)
class ErrorHandlingTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun copyFixture(name: String): File =
        File(context.cacheDir, name).apply {
            context.assets.open(name).use { i -> outputStream().use { i.copyTo(it) } }
        }

    @Test
    fun fichier_corrompu_renvoie_une_erreur_typee_jamais_une_exception() = runTest {
        val fixtureFile = copyFixture("fixture-corrompu.epub")
        // Le test lui-meme echoue si parse() leve une exception non geree
        // — c'est le point : aucun crash, meme sur un fichier invalide.
        val result = ReadiumPublicationParser(context, CoverStorage(context)).parse(fixtureFile.absolutePath)
        assertTrue(result is ParseResult.Corrupted)
    }

    @Test
    fun ressource_manquante_n_empeche_pas_l_ouverture_des_chapitres_valides() = runTest {
        val fixtureFile = copyFixture("fixture-ressource-manquante.epub")
        val result = ReadiumPublicationParser(context, CoverStorage(context)).parse(fixtureFile.absolutePath)
        // Une image manquante ne doit pas empecher l'extraction du TEXTE
        // des chapitres valides — degradation partielle, pas un echec total.
        check(result is ParseResult.Success)
        assertTrue(result.documentModel.chapters.isNotEmpty())
    }

    @Test
    fun uri_totalement_invalide_ne_crash_jamais() = runTest {
        val result = ReadiumPublicationParser(context, CoverStorage(context)).parse("ceci-n-est-pas-une-uri-valide")
        assertTrue(result is ParseResult.Corrupted)
    }
}
