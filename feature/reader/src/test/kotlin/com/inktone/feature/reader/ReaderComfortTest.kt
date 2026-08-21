package com.inktone.feature.reader

import androidx.compose.ui.unit.dp
import com.inktone.domain.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fous des réglages de confort P4 qui se prouvent sans écran.
 *
 * Le cas le plus important est le premier : le cran par défaut doit rendre
 * EXACTEMENT comme avant l'introduction du réglage. C'est ce qui garantit
 * qu'une bibliothèque existante ne change pas d'apparence après mise à jour,
 * promesse que la valeur par défaut de la migration fait de son côté.
 */
class ReaderComfortTest {

    @Test
    fun leCranParDefautReproduitLancienneMargeEnDur() {
        assertEquals(16.dp, readerMarginFor(UserPreferences.MARGIN_STEP_DEFAULT))
    }

    @Test
    fun lesCransSontStrictementCroissants() {
        val etroite = readerMarginFor(0)
        val normale = readerMarginFor(1)
        val large = readerMarginFor(2)
        assertTrue("étroite < normale", etroite < normale)
        assertTrue("normale < large", normale < large)
    }

    @Test
    fun unCranHorsBornesRetombeSurUneValeurUtilisable() {
        // Une préférence corrompue ou un appelant fautif ne doit jamais
        // produire une marge négative (page de largeur négative) ni faire
        // planter le rendu : on borne, on ne lève pas.
        assertEquals(readerMarginFor(0), readerMarginFor(-5))
        assertEquals(readerMarginFor(2), readerMarginFor(99))
    }

    @Test
    fun chaqueCranAUnLibelleDistinct() {
        val labels = UserPreferences.MARGIN_STEP_RANGE.map { readerMarginLabel(it) }
        assertEquals(labels.size, labels.distinct().size)
    }
}
