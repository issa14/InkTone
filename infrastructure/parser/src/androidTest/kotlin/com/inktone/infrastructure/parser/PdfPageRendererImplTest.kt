package com.inktone.infrastructure.parser

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.FixedPageOpenResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Lot 12, tache 12.7 - verifie le contrat FixedPageRenderer sur
 * appareil reel : cycle de vie ouvert/ferme distinct du parser (decision
 * actee 14), rendu correct, echec type plutot que null silencieux
 * (correction de relecture du contrat). Mesure de performance PDFium sur
 * fixture-large.pdf - remplace les chiffres MuPDF de la recherche
 * initiale (decision actee 19), consignee ici plutot qu'assertee comme
 * seuil pass/fail.
 */
@RunWith(AndroidJUnit4::class)
class PdfPageRendererImplTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun renderer() = PdfPageRendererImpl(LocalFileStorageService())

    private fun copyFixture(name: String): File =
        File(context.cacheDir, name).apply {
            context.assets.open(name).use { i -> outputStream().use { i.copyTo(it) } }
        }

    @Test
    fun ouvre_et_rend_une_page_d_un_pdf_valide() = runTest {
        val file = copyFixture("fixture-valid.pdf")
        val openResult = renderer().open(file.absolutePath)

        check(openResult is FixedPageOpenResult.Success)
        val document = openResult.document
        try {
            assertEquals(1, document.pageCount)
            val page = document.renderPage(0, targetWidthPx = 300)
            assertTrue("le rendu doit produire une page", page != null)
            assertEquals(300, page!!.widthPx)
            assertTrue(page.heightPx > 0)
            assertEquals(page.widthPx * page.heightPx, page.pixelsArgb.size)
        } finally {
            document.close()
        }
    }

    @Test
    fun rendu_hors_bornes_renvoie_null_jamais_une_exception() = runTest {
        val file = copyFixture("fixture-valid.pdf")
        val openResult = renderer().open(file.absolutePath)

        check(openResult is FixedPageOpenResult.Success)
        val document = openResult.document
        try {
            assertEquals(null, document.renderPage(99, targetWidthPx = 300))
        } finally {
            document.close()
        }
    }

    @Test
    fun ouverture_d_un_pdf_protege_renvoie_un_echec_type() = runTest {
        val file = copyFixture("fixture-password.pdf")
        val result = renderer().open(file.absolutePath)
        assertTrue(result is FixedPageOpenResult.Failed)
    }

    @Test
    fun mesure_le_rendu_de_pages_sur_un_pdf_volumineux() = runTest {
        val file = copyFixture("fixture-large.pdf")
        val openResult = renderer().open(file.absolutePath)
        check(openResult is FixedPageOpenResult.Success)
        val document = openResult.document
        try {
            assertEquals(220, document.pageCount)

            val samples = listOf(0, 50, 100, 150, 219)
            val timingsMs = samples.map { pageIndex ->
                val start = System.nanoTime()
                val page = document.renderPage(pageIndex, targetWidthPx = 1080)
                val elapsed = (System.nanoTime() - start) / 1_000_000
                assertTrue("page $pageIndex doit se rendre", page != null)
                elapsed
            }
            Log.i(
                "PdfPageRendererImplTest",
                "rendu 1080px, 5 pages echantillonnees sur 220 (${Build.MODEL}) : $timingsMs ms",
            )
        } finally {
            document.close()
        }
    }
}
