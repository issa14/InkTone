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
 * Lot 12, tache 12.6 - catalogue des cas d'erreur du parser PDF, miroir
 * de [ErrorHandlingTest] (EPUB) et [DrmDetectionTest]. Chaque cas doit
 * produire un [ParseResult] type, jamais une exception ni un crash natif
 * (safeNativeCall, tache 12.2).
 */
@RunWith(AndroidJUnit4::class)
class PdfErrorHandlingTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val parser get() = PdfPublicationParser(LocalFileStorageService(), CoverStorage(context))

    private fun copyFixture(name: String): File =
        File(context.cacheDir, name).apply {
            context.assets.open(name).use { i -> outputStream().use { i.copyTo(it) } }
        }

    @Test
    fun page_scannee_sans_texte_ne_produit_pas_de_corrompu() = runTest {
        val file = copyFixture("fixture-scanned.pdf")
        val result = parser.parse(file.absolutePath)

        check(result is ParseResult.Success)
        val chapter = result.documentModel.chapters.single()
        assertTrue("une page sans texte extractible reste un chapitre valide", chapter.sentences.isEmpty())
    }

    @Test
    fun pdf_protege_par_mot_de_passe_renvoie_drm_protected() = runTest {
        val file = copyFixture("fixture-password.pdf")
        val result = parser.parse(file.absolutePath)
        assertTrue(result is ParseResult.DrmProtected)
    }

    @Test
    fun pdf_tronque_renvoie_corrompu_jamais_un_crash() = runTest {
        val file = copyFixture("fixture-corrupted.pdf")
        // Le test lui-meme echoue si parse() leve une exception non geree.
        val result = parser.parse(file.absolutePath)
        assertTrue(result is ParseResult.Corrupted)
    }

    @Test
    fun extension_pdf_usurpee_renvoie_unsupported_format() = runTest {
        val file = copyFixture("fixture-fake.pdf")
        val result = parser.parse(file.absolutePath)
        assertTrue(result is ParseResult.UnsupportedFormat)
    }

    @Test
    fun fichier_inexistant_ne_crash_jamais() = runTest {
        val result = parser.parse("/chemin/qui/n/existe/pas.pdf")
        assertTrue(result is ParseResult.Corrupted)
    }
}
