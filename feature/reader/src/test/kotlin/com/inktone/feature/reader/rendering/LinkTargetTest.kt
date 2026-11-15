package com.inktone.feature.reader.rendering

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkTargetTest {
    private val chapters = listOf("OEBPS/Text/cover.xhtml", "OEBPS/Text/ch1.xhtml", "OEBPS/Text/notes.xhtml", "OEBPS/Text/Chapitre 2.xhtml")
    private fun resolve(href: String, from: String = "OEBPS/Text/ch1.xhtml") = resolveLinkTarget(href, from, chapters)

    @Test
    fun une_note_de_bas_de_page_mene_au_chapitre_des_notes() {
        assertEquals(LinkTarget.Chapter(2, "n3"), resolve("notes.xhtml#n3"))
    }

    @Test
    fun un_chemin_relatif_avec_point_point_est_resolu() {
        assertEquals(LinkTarget.Chapter(0), resolve("../Text/cover.xhtml", from = "OEBPS/Text/ch1.xhtml"))
    }

    @Test
    fun le_pourcentage_est_decode_des_deux_cotes() {
        assertEquals(LinkTarget.Chapter(3), resolve("Chapitre%202.xhtml"))
    }

    @Test
    fun une_adresse_exterieure_est_reconnue() {
        assertEquals(LinkTarget.External("https://exemple.org/a"), resolve("https://exemple.org/a"))
        assertEquals(LinkTarget.External("mailto:a@b.fr"), resolve("mailto:a@b.fr"))
    }

    @Test
    fun un_fragment_seul_ou_un_chapitre_inconnu_ne_mene_nulle_part() {
        assertEquals(LinkTarget.None, resolve("absent.xhtml"))
        assertEquals(LinkTarget.None, resolve("ftp://x/y"))
        assertEquals(LinkTarget.None, resolve(""))
    }

    @Test
    fun une_note_hors_des_chapitres_est_une_ressource_a_afficher_en_fenetre() {
        assertEquals(
            LinkTarget.Resource("OEBPS/Text/footnotes.xml", "footnote_1"),
            resolve("footnotes.xml#footnote_1"),
        )
    }

    @Test
    fun un_fragment_seul_vise_une_ancre_du_chapitre_courant() {
        assertEquals(LinkTarget.Chapter(1, "haut"), resolve("#haut"))
        assertEquals(LinkTarget.None, resolve("#"))
    }

    @Test
    fun un_lien_sans_fragment_n_a_pas_d_ancre() {
        assertEquals(LinkTarget.Chapter(2, null), resolve("notes.xhtml"))
    }
}
