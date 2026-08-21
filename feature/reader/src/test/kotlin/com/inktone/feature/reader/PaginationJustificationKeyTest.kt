package com.inktone.feature.reader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.inktone.feature.reader.pagination.paginationStyleKeyFrom
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Garde-fou de la seule vraie chausse-trappe de P4 : la justification embarque
 * la césure, qui déplace les points de coupure de ligne — donc change la
 * pagination.
 *
 * Si la clé d'invalidation ne varie pas avec ce réglage, la pagination reste
 * calculée sur l'ancien style sans aucun signal : les pages continuent d'être
 * découpées comme avant, et le texte réellement dessiné déborde ou saute. Le
 * même piège avait déjà été documenté pour `lineHeightSp`/`fontFamilyKey`
 * (voir le KDoc de `paginationStyleKeyFrom`) — ce test le ferme pour de bon.
 */
class PaginationJustificationKeyTest {

    private val style = TextStyle(fontSize = 18.sp, lineHeight = 25.sp)

    private fun key(justified: Boolean) = paginationStyleKeyFrom(
        baseTextStyle = style,
        fontSizeSp = 18,
        viewportWidthPx = 1080,
        viewportHeightPx = 1920,
        paddingPx = 48,
        justified = justified,
    )

    @Test
    fun activerLaJustificationChangeLaCleDeRepagination() {
        assertNotEquals(key(justified = false), key(justified = true))
    }

    @Test
    fun changerDeMargeChangeLaCleDeRepagination() {
        // La marge entre dans la clé par `paddingPx` : un cran de marge
        // différent doit forcer une remesure, sinon la largeur de page mesurée
        // ne correspond plus à celle dessinée.
        val etroite = paginationStyleKeyFrom(
            baseTextStyle = style,
            fontSizeSp = 18,
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            paddingPx = 24,
        )
        val large = paginationStyleKeyFrom(
            baseTextStyle = style,
            fontSizeSp = 18,
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            paddingPx = 96,
        )
        assertNotEquals(etroite, large)
    }
}
