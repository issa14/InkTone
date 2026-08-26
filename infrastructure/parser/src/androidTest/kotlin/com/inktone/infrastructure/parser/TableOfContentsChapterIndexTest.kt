package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Bug reel trouve en Tache 4.11 (validation contre un vrai EPUB, Les
 * Miserables Tome I : 6 ressources de spine, 153 entrees de TOC via des
 * ancres #fragment DANS ces memes ressources). L'ancien code utilisait
 * l'index de la TOC elle-meme comme chapterIndex — hors bornes pour la
 * quasi totalite des entrees des qu'une TOC a plus d'entrees que de
 * ressources de spine, silencieusement ignore par
 * ReaderViewModel.navigateToChapter (bornes verifiees par design, K3) :
 * la TOC semblait fonctionner (aucun crash, aucune erreur) mais ne
 * naviguait nulle part pour la plupart des entrees.
 *
 * Ce fixture reproduit la meme structure a petite echelle : 2 chapitres
 * (ressources de spine), 4 entrees de TOC (2 ancres par chapitre).
 */
@RunWith(AndroidJUnit4::class)
class TableOfContentsChapterIndexTest {

    @Test
    fun chaque_entree_toc_pointe_vers_le_bon_chapitre_meme_avec_plus_d_entrees_que_de_chapitres() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = File(context.cacheDir, "fixture-toc-fragments.epub").apply {
            context.assets.open("fixture-toc-fragments.epub").use { i -> outputStream().use { i.copyTo(it) } }
        }
        val result = ReadiumPublicationParser(context, CoverStorage(context)).parse(fixtureFile.absolutePath)
        check(result is ParseResult.Success)

        assertEquals("2 chapitres (ressources de spine)", 2, result.documentModel.chapters.size)
        assertEquals("4 entrees de TOC (2 ancres par chapitre)", 4, result.documentModel.tableOfContents.size)

        val chapterIndices = result.documentModel.tableOfContents.map { it.chapterIndex }
        assertEquals(
            "les 2 premieres entrees TOC pointent vers le chapitre 0, les 2 suivantes vers le chapitre 1 - " +
                "jamais l'index de la TOC elle-meme (qui serait [0, 1, 2, 3], hors bornes des 2 chapitres reels)",
            listOf(0, 0, 1, 1),
            chapterIndices,
        )
    }
}
