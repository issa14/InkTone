package com.inktone.feature.library

import androidx.compose.ui.graphics.Color
import com.inktone.core.designsystem.annotationMarkerColor
import com.inktone.domain.model.AnnotationColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** La table des variantes (core:designsystem) suit les préréglages du domaine (audit UI P2-3). */
class AnnotationMarkerColorTest {

    @Test
    fun chaque_prereglage_a_sa_variante_saturee() {
        AnnotationColor.PRESETS.forEach { preset ->
            assertNotEquals("préréglage sans variante : ${preset.argb}", Color(preset.argb), annotationMarkerColor(preset.argb))
        }
    }

    @Test
    fun une_couleur_personnalisee_est_rendue_telle_quelle() {
        val custom = 0xFF123456.toInt()
        assertEquals(Color(custom), annotationMarkerColor(custom))
    }
}
