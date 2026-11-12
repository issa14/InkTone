package com.inktone.data.mapper

import com.inktone.domain.model.ChapterLengths
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Colonne `publications.chapterLengths` (CSV d'entiers, MIGRATION_32_33) :
 * une valeur illisible ou décalée est traitée comme ABSENTE — progression
 * inconnue, jamais un crash de la Bibliothèque ni une progression fausse.
 */
class PublicationMapperTest {

    private fun publication(chapterLengths: ChapterLengths?) = Publication(
        id = "pub", title = "Titre", format = PublicationFormat.EPUB, fileUri = "content://pub",
        fileHash = "hash", fileSize = 1L, chapterCount = 3, chapterLengths = chapterLengths, importDate = 0L,
    )

    @Test
    fun `aller-retour domaine - entite conserve les longueurs`() {
        val original = publication(ChapterLengths(listOf(0, 1520, 8840)))
        val entity = original.toEntity()

        assertEquals("0,1520,8840", entity.chapterLengths)
        assertEquals(original, entity.toDomain())
    }

    @Test
    fun `longueurs absentes restent absentes`() {
        assertNull(publication(null).toEntity().toDomain().chapterLengths)
    }

    @Test
    fun `une colonne illisible est traitee comme absente`() {
        assertNull(chapterLengthsFromColumn("0,abc,12", chapterCount = 3))
        assertNull(chapterLengthsFromColumn("0,-4,12", chapterCount = 3))
    }

    @Test
    fun `un nombre de chapitres divergent est traite comme absent`() {
        assertNull(chapterLengthsFromColumn("10,20", chapterCount = 3))
    }

    @Test
    fun `un livre sans chapitre a une liste vide, pas une valeur absente`() {
        assertEquals(ChapterLengths(emptyList()), chapterLengthsFromColumn("", chapterCount = 0))
    }
}
