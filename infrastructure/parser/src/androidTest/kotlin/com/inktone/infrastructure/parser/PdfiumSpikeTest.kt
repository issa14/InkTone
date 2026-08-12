package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legere.pdfiumandroid.PdfPasswordException
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Lot 12, tache 12.1 - spike jetable, pas le parser final (tache 12.2).
 * Verifie sur appareil reel le mecanisme d'echec de PdfiumCore face a un
 * PDF protege par mot de passe, et confirme l'ouverture d'un PDF valide -
 * les deux inconnues de la decision actee 3 du plan.
 */
@RunWith(AndroidJUnit4::class)
class PdfiumSpikeTest {

    private fun fixture(name: String): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return File(context.cacheDir, name).apply {
            context.assets.open(name).use { input -> outputStream().use { input.copyTo(it) } }
        }
    }

    @Test
    fun ouvre_un_pdf_valide_et_lit_le_nombre_de_pages() = runTest {
        val file = fixture("fixture-valid.pdf")
        val core = PdfiumCore()
        val document = core.newDocument(file.readBytes())
        try {
            assertEquals(1, document.getPageCount())
        } finally {
            document.close()
        }
    }

    @Test
    fun leve_PdfPasswordException_sur_un_pdf_protege_sans_mot_de_passe() = runTest {
        val file = fixture("fixture-password.pdf")
        val core = PdfiumCore()
        assertThrows(PdfPasswordException::class.java) {
            core.newDocument(file.readBytes())
        }
    }

    @Test
    fun ouvre_un_pdf_protege_avec_le_bon_mot_de_passe() = runTest {
        val file = fixture("fixture-password.pdf")
        val core = PdfiumCore()
        val document = core.newDocument(file.readBytes(), password = "inktone-test")
        try {
            assertEquals(1, document.getPageCount())
        } finally {
            document.close()
        }
    }
}
