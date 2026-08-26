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

    /**
     * Bug reel trouve sur appareil (2026-08-26), remonte par les premiers
     * beta-testeurs sous la forme « la lecture de PDF ne marche pas » :
     * PDF importe correctement (titre, auteur, couverture), puis page
     * NOIRE et muette a l'ouverture dans le lecteur.
     *
     * Cause : `newDocument(ByteArray)` fait un `FPDF_LoadMemDocument`, le
     * natif ne garde qu'un POINTEUR vers le tampon. `open()` laissait son
     * `ByteArray` local devenir injoignable en retournant — le ramasse-
     * miettes le collectait, et tout `renderPage` ulterieur lisait de la
     * memoire liberee. L'echec etait avale par le `catch` de renderPage :
     * aucun message, aucun log, juste du noir.
     *
     * Ce test reproduit la condition manquante : rendre APRES que `open`
     * ait rendu la main ET apres une vraie pression memoire. Sans la
     * retention du tampon par le document, il echoue.
     */
    @Test
    fun rend_encore_apres_le_retour_de_open_et_sous_pression_memoire() = runTest {
        val file = copyFixture("fixture-valid.pdf")
        val openResult = renderer().open(file.absolutePath)

        check(openResult is FixedPageOpenResult.Success)
        val document = openResult.document
        try {
            // `open` a rendu la main : son ByteArray local n'est plus
            // reference que par le document lui-meme, si celui-ci le retient.
            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()
            // Allocation reelle : un System.gc() seul reste une suggestion,
            // la pression memoire force un vrai passage.
            repeat(40) { ByteArray(1 shl 20) }
            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()

            val page = document.renderPage(0, targetWidthPx = 300)
            assertTrue(
                "le rendu doit encore fonctionner apres GC : le document doit retenir le tampon source",
                page != null,
            )
            assertEquals(300, page!!.widthPx)
            assertTrue("une page rendue ne peut pas etre vide", page.pixelsArgb.any { it != 0 })
        } finally {
            document.close()
        }
    }
}
