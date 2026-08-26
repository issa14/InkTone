package com.inktone.infrastructure.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.service.ParseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verification contre un vrai EPUB a hierarchie NCX imbriquee (Gutenberg
 * #17489, Les Miserables Tome I — meme livre que
 * `docs/execution/VALIDATION_EPUB_REEL_LES_MISERABLES.md`, Tache 4.11).
 *
 * Cette note-la concluait a un `toc.ncx` PLAT pour ce livre (« aucun
 * navPoint imbrique — verifie directement dans le XML source ») et
 * reportait donc la verification de `TableOfContentsEntry.children`.
 * **Cette conclusion etait erronee** (ou le fichier source a change
 * depuis) : le `toc.ncx` reel de ce livre EST imbrique sur 2 niveaux
 * (verifie ici par inspection XML directe avant d'ecrire ce test, pas
 * suppose) — chaque « Chapitre N » a un navPoint enfant portant le titre
 * reel du chapitre (ex. "Chapitre I" > "Monsieur Myriel"), et "Tome
 * I—FANTINE" a deux enfants ("(1862)", "TABLE DES MATIERES").
 *
 * Bug reel trouve en ecrivant ce test :
 * `DocumentModelExtractor.extract()` ne lisait jamais `Link.children`
 * (Readium) — `TableOfContentsEntry.children` restait donc toujours
 * vide, quelle que soit la hierarchie NCX source, corrige dans le meme
 * commit (`toTocEntry`, recursif).
 */
@RunWith(AndroidJUnit4::class)
class TableOfContentsChildrenTest {

    @Test
    fun la_toc_reelle_du_livre_est_hierarchique_et_children_est_peuple() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = File(context.cacheDir, "fixture-les-miserables-tome1.epub").apply {
            context.assets.open("fixture-les-miserables-tome1.epub").use { i -> outputStream().use { i.copyTo(it) } }
        }
        val result = ReadiumPublicationParser(context, CoverStorage(context)).parse(fixtureFile.absolutePath)
        check(result is ParseResult.Success) { "echec de parsing : $result" }

        val toc = result.documentModel.tableOfContents

        assertEquals("81 entrees de premier niveau (verifie par inspection XML directe du toc.ncx source)", 81, toc.size)

        // "Tome I—FANTINE" (2e entree) a 2 enfants : "(1862)" et "TABLE DES MATIERES".
        val tomeEntry = toc[1]
        assertEquals("Tome I—FANTINE", tomeEntry.title)
        assertEquals(listOf("(1862)", "TABLE DES MATIÈRES"), tomeEntry.children.map { it.title })

        // "Chapitre I" (4e entree) a 1 enfant : le titre reel du chapitre.
        val chapitreUn = toc[3]
        assertEquals("Chapitre I", chapitreUn.title)
        assertEquals(1, chapitreUn.children.size)
        assertEquals("Monsieur Myriel", chapitreUn.children.first().title)

        // L'enfant partage la meme ressource de spine que son parent (ancre
        // #fragment dans la meme page) - meme chapterIndex des deux cotes,
        // pas une regression du bug deja corrige en Tache 4.11
        // (chapterIndex resolu par href sans fragment, pas par position
        // dans la TOC).
        assertEquals(chapitreUn.chapterIndex, chapitreUn.children.first().chapterIndex)

        // Entrees sans hierarchie source (ex. "Livre premier—Un juste",
        // 3e entree) : children reste une liste vide, pas null ni une
        // erreur - comportement par defaut de TableOfContentsEntry.
        assertTrue("Livre premier—Un juste".let { toc[2].title == it && toc[2].children.isEmpty() })
    }
}
