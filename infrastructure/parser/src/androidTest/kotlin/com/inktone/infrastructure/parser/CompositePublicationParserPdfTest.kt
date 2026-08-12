package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Lot 12, tache 12.6 - verifie que .pdf est bien route vers
 * PdfPublicationParser, jamais vers readiumParser par defaut (le
 * commentaire de CompositePublicationParser annoncait ce point
 * d'extension depuis la Phase 1). Un mauvais routage ferait echouer ce
 * test : Readium ne sait pas lire un PDF brut.
 */
@RunWith(AndroidJUnit4::class)
class CompositePublicationParserPdfTest {

    @Test
    fun route_un_pdf_vers_le_parser_pdf_par_extension_de_fichier_resolue() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileStorageService = LocalFileStorageService()
        val composite = CompositePublicationParser(
            readiumParser = ReadiumPublicationParser(context),
            txtParser = TxtPublicationParser(fileStorageService),
            pdfParser = PdfPublicationParser(fileStorageService, context),
            fileStorageService = fileStorageService,
        )

        assertTrue(PublicationFormat.PDF in composite.supportedFormats)

        val file = File(context.cacheDir, "fixture-valid.pdf").apply {
            context.assets.open("fixture-valid.pdf").use { i -> outputStream().use { i.copyTo(it) } }
        }
        val result = composite.parse(file.absolutePath)

        check(result is ParseResult.Success)
        assertTrue(result.documentModel.chapters.single().paragraphs.isNotEmpty())
    }
}
