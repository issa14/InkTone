package com.inktone.data.mapper

import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.UserPreferences
import com.inktone.infrastructure.database.entity.UserPreferencesEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lot 22, tâche 12 — couleurs de surlignage récentes : un aller-retour
 * doit préserver l'ordre (la plus récente en tête), et une valeur CSV
 * corrompue ou d'une version future (nom d'enum inconnu) ne doit jamais
 * faire planter la restauration des préférences (constat 11).
 */
class UserPreferencesMapperTest {

    @Test
    fun `un aller-retour preserve l'ordre des couleurs recentes`() {
        val original = UserPreferences(
            recentAnnotationColors = listOf(AnnotationColor.BLUE, AnnotationColor.YELLOW),
        )

        val roundTripped = original.toEntity().toDomain()

        assertEquals(listOf(AnnotationColor.BLUE, AnnotationColor.YELLOW), roundTripped.recentAnnotationColors)
    }

    @Test
    fun `aucune couleur recente se traduit en liste vide`() {
        val roundTripped = UserPreferences().toEntity().toDomain()
        assertEquals(emptyList<AnnotationColor>(), roundTripped.recentAnnotationColors)
    }

    @Test
    fun `un nom d'enum inconnu est ignore plutot que de faire planter la lecture`() {
        val entity = UserPreferencesEntity(
            theme = "papier-clair", fontSize = 18, defaultTtsEngine = "SHERPA_ONNX",
            crashReportingEnabled = false, language = "fr",
            recentAnnotationColors = "YELLOW,VALEUR_FUTURE_INCONNUE,BLUE",
        )

        val domain = entity.toDomain()

        assertEquals(listOf(AnnotationColor.YELLOW, AnnotationColor.BLUE), domain.recentAnnotationColors)
    }
}
