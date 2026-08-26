package com.inktone.data.backup

import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.AnnotationKind
import com.inktone.domain.valueobject.Locator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lot 23, décision 7 — une sauvegarde produite AVANT ce Lot porte des noms
 * d'enum (`"YELLOW"`), une sauvegarde produite APRÈS porte du hex
 * (`"#FFFFF59D"`) : les deux doivent rester restaurables sans erreur.
 */
class BackupModelsAnnotationTest {

    private fun locator(offset: Int) = Locator(resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = offset)

    @Test
    fun `un aller-retour preserve la couleur`() {
        val original = Annotation(
            id = "a1", publicationId = "pub-1",
            startLocator = locator(0), endLocator = locator(10),
            color = AnnotationColor.BLUE, kind = AnnotationKind.UNDERLINE,
            createdAt = 0L, updatedAt = 0L,
        )
        assertEquals(original, original.toBackup().toDomain())
    }

    @Test
    fun `une sauvegarde anterieure au Lot 23 (nom d'enum) reste restaurable`() {
        val legacyBackup = AnnotationBackup(
            id = "a1", publicationId = "pub-1",
            startLocator = locator(0).toBackup(), endLocator = locator(10).toBackup(),
            color = "YELLOW", createdAt = 0L, updatedAt = 0L,
        )
        assertEquals(AnnotationColor.YELLOW, legacyBackup.toDomain().color)
    }
}
