package com.inktone.domain.usecase

import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Règle du livre de reprise — SEULE définition partagée par la carte
 * « Reprendre la lecture » et par le mini-lecteur, qui doit savoir s'il
 * ferait doublon avec elle.
 */
class ResumePublicationTest {

    private fun publication(id: String, lastOpened: Long?) = Publication(
        id = id, title = "Titre $id", format = PublicationFormat.EPUB,
        fileUri = "content://$id", fileHash = "hash-$id", fileSize = 1L,
        chapterCount = 1, importDate = 0L, lastOpened = lastOpened,
    )

    @Test
    fun retourne_le_livre_ouvert_le_plus_recemment() {
        val livres = listOf(
            publication("ancien", 1_000L),
            publication("recent", 3_000L),
            publication("moyen", 2_000L),
        )
        assertEquals("recent", livres.resumePublication()?.id)
    }

    @Test
    fun ignore_les_livres_jamais_ouverts() {
        // Un livre importé mais jamais ouvert n'a rien à reprendre : sans ce
        // filtre, `maxByOrNull` sur un `lastOpened` nul le ferait remonter.
        val livres = listOf(publication("jamais-ouvert", null), publication("ouvert", 500L))
        assertEquals("ouvert", livres.resumePublication()?.id)
    }

    @Test
    fun nul_quand_aucun_livre_n_a_jamais_ete_ouvert() {
        assertNull(listOf(publication("a", null), publication("b", null)).resumePublication())
    }

    @Test
    fun nul_sur_une_bibliotheque_vide() {
        assertNull(emptyList<Publication>().resumePublication())
    }
}
