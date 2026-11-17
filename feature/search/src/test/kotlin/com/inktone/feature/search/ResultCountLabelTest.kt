package com.inktone.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

/** Audit UI P1-16 : la liste annonce son nombre de résultats, au bon nombre grammatical. */
class ResultCountLabelTest {

    @Test
    fun le_nombre_de_resultats_s_accorde() {
        assertEquals("1 résultat", resultCountLabel(1))
        assertEquals("40 résultats", resultCountLabel(40))
    }
}
