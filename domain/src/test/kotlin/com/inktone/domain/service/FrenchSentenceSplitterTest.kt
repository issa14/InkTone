package com.inktone.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lot 21 — contrat du découpeur de phrases unifié (source unique EPUB /
 * PDF / TXT). Les tests portent sur les cas que la regex naïve
 * `(?<=[.!?])\s+` cassait : abréviations françaises, ellipses, guillemets,
 * et sur le contrat de stabilité des offsets (substring == phrase).
 */
class FrenchSentenceSplitterTest {

    // ───── Abréviations françaises ─────

    @Test
    fun ne_decoupe_pas_apres_m_dot() {
        val sentences = FrenchSentenceSplitter.split("M. Dupont arriva. Il salua.")
        assertEquals(listOf("M. Dupont arriva.", "Il salua."), sentences.map { it.first })
    }

    @Test
    fun ne_decoupe_pas_apres_mme_ni_dr() {
        val sentences = FrenchSentenceSplitter.split("Mme Martin et Dr Petit sont venus. Ensuite ils partirent.")
        assertEquals(
            listOf("Mme Martin et Dr Petit sont venus.", "Ensuite ils partirent."),
            sentences.map { it.first },
        )
    }

    @Test
    fun ne_decoupe_pas_apres_etc() {
        val sentences = FrenchSentenceSplitter.split("Il apporta livres, cahiers, etc. Puis il sortit.")
        assertEquals(listOf("Il apporta livres, cahiers, etc. Puis il sortit."), sentences.map { it.first })
    }

    @Test
    fun ne_decoupe_pas_apres_p_ex() {
        // "p. ex." est une abréviation composée : aucun point ne termine la phrase.
        val sentences = FrenchSentenceSplitter.split("Voir p. ex. le chapitre trois. Il est clair.")
        assertEquals(listOf("Voir p. ex. le chapitre trois.", "Il est clair."), sentences.map { it.first })
    }

    // Lot 21 (correctif) — le filtre d'abréviations doit reconnaître le
    // dernier mot d'un segment même quand il est précédé d'un saut de
    // ligne plutôt que d'une espace ASCII : c'est la forme que produisent
    // PDFium (une ligne visuelle par `\r\n`) et un TXT dur-wrappé, les
    // deux formats visés par l'unification du découpage.

    @Test
    fun ne_decoupe_pas_apres_une_abreviation_precedee_d_un_saut_de_ligne() {
        val sentences = FrenchSentenceSplitter.split(
            "La liste continue\nM. Dupont a confirmé. Il partit ensuite.",
        )
        assertEquals(
            listOf("La liste continue\nM. Dupont a confirmé.", "Il partit ensuite."),
            sentences.map { it.first },
        )
    }

    @Test
    fun ne_decoupe_pas_apres_une_abreviation_precedee_d_un_saut_de_ligne_crlf() {
        val sentences = FrenchSentenceSplitter.split(
            "La liste continue\r\nM. Dupont a confirmé. Il partit ensuite.",
        )
        assertEquals(
            listOf("La liste continue\r\nM. Dupont a confirmé.", "Il partit ensuite."),
            sentences.map { it.first },
        )
    }

    // ───── Ponctuation forte simple ─────

    @Test
    fun decoupe_sur_point_et_exclamation() {
        val sentences = FrenchSentenceSplitter.split("Première phrase. Seconde ! Troisième ?")
        assertEquals(
            listOf("Première phrase.", "Seconde !", "Troisième ?"),
            sentences.map { it.first },
        )
    }

    // ───── Ellipses ─────

    @Test
    fun ne_crashe_pas_sur_une_ellipse_et_decoupe_autour() {
        // ICU FR traite l'ellipse comme une frontière ; ce qui compte est
        // qu'aucune exception ne surgisse et que les offsets restent exacts.
        val text = "Attendez… Voilà la suite."
        val sentences = FrenchSentenceSplitter.split(text)
        assertTrue(sentences.isNotEmpty())
        sentences.forEach { (sentence, start, end) ->
            assertEquals(sentence, text.substring(start, end))
        }
    }

    // ───── Guillemets français ─────

    @Test
    fun ne_decoupe_pas_au_milieu_d_un_dialogue_guillemete() {
        val text = "« Bonjour », dit-il."
        val sentences = FrenchSentenceSplitter.split(text)
        assertEquals(listOf("« Bonjour », dit-il."), sentences.map { it.first })
    }

    // ───── Contrat de stabilité des offsets ─────

    @Test
    fun les_offsets_sont_exacts_dans_l_espace_du_texte_source() {
        val text = "Bonjour le monde. Ceci est un test."
        val sentences = FrenchSentenceSplitter.split(text)

        assertEquals(2, sentences.size)
        val (premiere, start0, end0) = sentences[0]
        assertEquals("Bonjour le monde.", premiere)
        assertEquals(0, start0)
        assertEquals(17, end0)
        assertEquals(premiere, text.substring(start0, end0))

        val (seconde, start1, end1) = sentences[1]
        assertEquals("Ceci est un test.", seconde)
        assertEquals(18, start1)
        assertEquals(35, end1) // "Ceci est un test." = 17 caractères (18+17)
        assertEquals(seconde, text.substring(start1, end1))
    }

    @Test
    fun texte_vide_ou_blanc_renvoie_aucune_phrase() {
        assertTrue(FrenchSentenceSplitter.split("").isEmpty())
        assertTrue(FrenchSentenceSplitter.split("   \n  ").isEmpty())
    }
}
