package com.inktone.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lot 21, tâche 7 (correctif) — `buildShareContext` était auparavant
 * écrite en ligne dans `ReaderScreen`, donc invérifiable sans
 * instrumentation. Couvre notamment le repli chapitre/page : un PDF
 * n'a pas de chapitres, `chapterIndex` y est un index de PAGE (même
 * distinction que le titre des signets PDF dans `ReaderViewModel`).
 */
class ShareContextTest {

    @Test
    fun titre_auteur_et_chapitre_sont_joints_dans_l_ordre() {
        assertEquals(
            "Les Misérables — Victor Hugo — Fantine",
            buildShareContext(
                title = "Les Misérables",
                author = "Victor Hugo",
                chapterTitle = "Fantine",
                chapterIndex = 0,
                isPdf = false,
            ),
        )
    }

    @Test
    fun sans_titre_de_chapitre_le_repli_epub_est_chapitre_n() {
        assertEquals(
            "Chapitre 3",
            buildShareContext(title = null, author = null, chapterTitle = null, chapterIndex = 2, isPdf = false),
        )
    }

    @Test
    fun sans_titre_de_chapitre_le_repli_pdf_est_page_n_jamais_chapitre_n() {
        assertEquals(
            "Page 3",
            buildShareContext(title = null, author = null, chapterTitle = null, chapterIndex = 2, isPdf = true),
        )
    }

    @Test
    fun titre_et_auteur_blancs_sont_ignores() {
        assertEquals(
            "Chapitre 1",
            buildShareContext(title = "  ", author = "", chapterTitle = null, chapterIndex = 0, isPdf = false),
        )
    }

    @Test
    fun le_contexte_n_est_jamais_vide() {
        val context = buildShareContext(title = null, author = null, chapterTitle = null, chapterIndex = 0, isPdf = false)
        assert(context.isNotBlank())
    }
}
