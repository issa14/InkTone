package com.inktone.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Description des métadonnées affichée dans la fiche d'un livre. Instrumenté :
 * `AnnotatedString.fromHtml` s'appuie sur `android.text.Html`.
 */
class BookDescriptionDisplayTest {

    @Test
    fun les_balises_html_ne_sont_plus_affichees() {
        val shown = descriptionForDisplay(
            "<div><p>Un roman <i>culte</i>.</p><p>Deuxième&nbsp;paragraphe &amp; fin.</p></div>",
        ).text

        assertFalse("balise visible : $shown", shown.contains('<'))
        assertTrue(shown.startsWith("Un roman culte."))
        assertTrue(shown.endsWith("Deuxième paragraphe & fin."))
        assertTrue("paragraphes séparés : $shown", shown.contains('\n'))
    }

    @Test
    fun un_texte_sans_balise_garde_ses_retours_a_la_ligne() {
        assertEquals("Ligne un.\nLigne deux.", descriptionForDisplay("  Ligne un.\nLigne deux.  ").text)
    }

    @Test
    fun un_signe_inferieur_isole_n_est_pas_pris_pour_du_html() {
        assertEquals("3 < 5 et 7 > 2", descriptionForDisplay("3 < 5 et 7 > 2").text)
    }
}
