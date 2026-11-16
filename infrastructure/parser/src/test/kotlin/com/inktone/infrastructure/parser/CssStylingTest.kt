package com.inktone.infrastructure.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class CssStylingTest {
    private fun effect(css: String, html: String): CssEffect {
        val element = Jsoup.parse(html).body().child(0)
        return StyleSheet.parse(css).effectFor(element)
    }

    @Test
    fun une_classe_en_italique_et_en_gras() {
        val css = ".a { font-style: italic } .b { font-weight: 700 }"
        assertEquals(CssEffect(italic = true), effect(css, "<p class=\"a\">x</p>"))
        assertEquals(CssEffect(bold = true), effect(css, "<p class=\"b\">x</p>"))
    }

    @Test
    fun la_derniere_regle_l_emporte_et_normal_annule() {
        val css = ".a { font-style: italic } p.a { font-style: normal }"
        assertEquals(false, effect(css, "<p class=\"a\">x</p>").italic)
    }

    @Test
    fun le_contenu_masque_est_reconnu() {
        assertEquals(true, effect(".n { display:none }", "<div class=\"n\">x</div>").hidden)
        assertEquals(true, parseDeclarations("visibility: hidden").hidden)
    }

    @Test
    fun les_selecteurs_composes_et_les_regles_arobase_sont_ignores() {
        val css = "div p { font-style: italic } p > em { font-weight: bold } @media print { p { font-style: italic } } p:first-child { font-style: italic }"
        assertEquals(CssEffect(), effect(css, "<p>x</p>"))
    }

    @Test
    fun une_liste_de_selecteurs_s_applique_a_chacun() {
        val css = "em, .k { font-weight: bold }"
        assertEquals(true, effect(css, "<em>x</em>").bold)
        assertEquals(true, effect(css, "<span class=\"k\">x</span>").bold)
    }

    @Test
    fun le_centrage_est_reconnu_et_tout_autre_alignement_le_defait() {
        assertEquals(true, parseDeclarations("text-align: center").centered)
        assertEquals(false, parseDeclarations("text-align: justify").centered)
        assertEquals(true, effect(".c { text-align:center }", "<p class=\"c\">x</p>").centered)
    }

    @Test
    fun l_attribut_style_est_lu() {
        assertEquals(CssEffect(italic = true, bold = true), parseDeclarations("font-style:italic; font-weight:bold; color:red"))
    }
}
