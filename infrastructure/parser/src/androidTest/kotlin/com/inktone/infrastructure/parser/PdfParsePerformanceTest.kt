package com.inktone.infrastructure.parser

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Mesure le coût réel de [PdfPublicationParser.parse] — appelé non
 * seulement à l'import, mais **à chaque ouverture du lecteur**
 * (`ReaderViewModel.openPublication` reparse `publication.fileUri`).
 *
 * Réserve soulevée à la vérification device du 2026-08-26 : le parse
 * extrayait alors le texte de TOUTES les pages, une page à la fois, sur un
 * seul thread JNI — 7 970 ms pour 994 pages, à chaque ouverture. Depuis, il
 * est paresseux ([PdfChapterParser]) et ce test mesure les DEUX chemins :
 * l'ouverture (paresseuse) et l'import (complet, via `parseAllPages`).
 * Les chiffres sont consignés, jamais assertés comme seuil pass/fail —
 * même discipline que la mesure de rendu dans [PdfPageRendererImplTest]
 * (décision actée 19 du Lot 12).
 *
 * Deux fixtures, deux rôles :
 * - `fixture-large.pdf` (220 pages, 35 Ko) : reproductible partout, mais
 *   synthétique — quasiment sans texte, il mesure surtout le coût
 *   d'ouverture de page, pas celui de l'extraction.
 * - `perf/livre-reel.pdf` : un vrai livre, poussé à la main dans
 *   `getExternalFilesDir("perf")`. Absent en CI → le cas est ignoré
 *   (`assumeTrue`), jamais rouge.
 */
@RunWith(AndroidJUnit4::class)
class PdfParsePerformanceTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun parser() = PdfPublicationParser(LocalFileStorageService(), CoverStorage(context))

    private fun copyFixture(name: String): File =
        File(context.cacheDir, name).apply {
            context.assets.open(name).use { i -> outputStream().use { i.copyTo(it) } }
        }

    @Test
    fun cout_du_parse_complet_sur_fixture_reproductible() = runTest {
        val file = copyFixture("fixture-large.pdf")
        val (result, elapsedMs) = timedParse(file.absolutePath)

        check(result is ParseResult.Success)
        val pages = result.documentModel.chapters.size
        val avecTexte = result.documentModel.chapters.count { it.sentences.isNotEmpty() }
        Log.i(TAG, "fixture-large.pdf : $pages pages, $avecTexte avec texte, parse complet en ${elapsedMs} ms")
    }

    @Test
    fun cout_du_parse_complet_sur_un_vrai_livre() = runTest {
        val file = File(context.getExternalFilesDir("perf"), "livre-reel.pdf")
        assumeTrue(
            "Pousser un vrai PDF dans getExternalFilesDir(\"perf\")/livre-reel.pdf pour activer cette mesure",
            file.exists(),
        )

        // Chemin LECTEUR (paresseux) — paye a chaque ouverture du livre.
        val (result, lazyMs) = timedParse(file.absolutePath)
        check(result is ParseResult.Success)
        val pages = result.documentModel.chapters.size

        // Chemin IMPORT (complet) — paye une seule fois, dans le worker.
        val eagerStart = System.nanoTime()
        val allPages = parser().parseAllPages(file.absolutePath)
        val eagerMs = (System.nanoTime() - eagerStart) / 1_000_000
        val phrases = allPages.sumOf { it.sentences.size }
        val avecTexte = allPages.count { it.sentences.isNotEmpty() }

        Log.i(
            TAG,
            "livre-reel.pdf (${file.length() / 1024} Ko) : $pages pages, $avecTexte avec texte, " +
                "$phrases phrases | OUVERTURE (paresseux) ${lazyMs} ms " +
                "| IMPORT (complet) ${eagerMs} ms",
        )
    }

    private suspend fun timedParse(path: String): Pair<ParseResult, Long> {
        val start = System.nanoTime()
        val result = parser().parse(path)
        return result to (System.nanoTime() - start) / 1_000_000
    }

    private companion object {
        const val TAG = "PdfParsePerf"
    }
}
