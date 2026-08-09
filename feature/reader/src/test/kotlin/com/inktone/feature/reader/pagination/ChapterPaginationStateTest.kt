package com.inktone.feature.reader.pagination

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.inktone.domain.model.ReadingTheme
import com.inktone.feature.reader.ThemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tâche 3b.7, test 7 — la clé d'invalidation doit dériver
 * `lineHeightSp`/`fontFamilyKey` du `TextStyle` réellement appliqué au
 * rendu, pas d'une constante déconnectée. Avant 3b.2, ces deux champs
 * étaient alimentés en dur (`lineHeightSp = fontSizeSp`,
 * `fontFamilyKey = "default"`) : ce test échouait alors, puisque
 * `paginationStyleKeyFrom` ignorait complètement les valeurs de
 * `baseTextStyle` qui lui étaient passées.
 */
class ChapterPaginationStateTest {

    @Test
    fun `deux cles ne differant que par l interligne ne sont pas egales`() {
        val keyA = paginationStyleKeyFrom(
            baseTextStyle = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
            fontSizeSp = 18,
            viewportWidthPx = 1000,
            viewportHeightPx = 2000,
            paddingPx = 16,
        )
        val keyB = paginationStyleKeyFrom(
            baseTextStyle = TextStyle(fontSize = 18.sp, lineHeight = 30.sp),
            fontSizeSp = 18,
            viewportWidthPx = 1000,
            viewportHeightPx = 2000,
            paddingPx = 16,
        )
        assertNotEquals(keyA, keyB)
        assertEquals(24, keyA.lineHeightSp)
        assertEquals(30, keyB.lineHeightSp)
    }

    @Test
    fun `deux cles ne differant que par la famille de police ne sont pas egales`() {
        val keyA = paginationStyleKeyFrom(
            baseTextStyle = TextStyle(fontSize = 18.sp, fontFamily = FontFamily.Serif),
            fontSizeSp = 18,
            viewportWidthPx = 1000,
            viewportHeightPx = 2000,
            paddingPx = 16,
        )
        val keyB = paginationStyleKeyFrom(
            baseTextStyle = TextStyle(fontSize = 18.sp, fontFamily = FontFamily.Monospace),
            fontSizeSp = 18,
            viewportWidthPx = 1000,
            viewportHeightPx = 2000,
            paddingPx = 16,
        )
        assertNotEquals(keyA, keyB)
        assertNotEquals(keyA.fontFamilyKey, keyB.fontFamilyKey)
    }

    @Test
    fun `sans lineHeight explicite, retombe sur fontSizeSp comme avant`() {
        val key = paginationStyleKeyFrom(
            baseTextStyle = TextStyle(fontSize = 18.sp),
            fontSizeSp = 18,
            viewportWidthPx = 1000,
            viewportHeightPx = 2000,
            paddingPx = 16,
        )
        assertEquals(18, key.lineHeightSp)
    }

    // ───── Lot 9, Tâche 9.3 point 3 — « les couleurs n'invalident pas la
    // pagination, la police oui ». Le garde-fou du lot 3d (thème absent de
    // la clé) reste vrai pour les couleurs mais cesse de l'être pour la
    // police depuis que ReadingTheme en porte une : ces deux tests
    // couvrent explicitement les deux moitiés de la règle avec de vrais
    // ReadingTheme, pas un TextStyle construit à la main. ─────

    @Test
    fun `deux themes aux couleurs differentes mais a la meme police produisent la meme cle`() {
        // Sépia Vintage et Papier Clair ont des couleurs très différentes
        // mais partagent la même famille (SERIF) — aucune couleur n'entre
        // jamais dans TextStyle (3a.1), donc dans la clé.
        val fontFamily = ThemeColors.toComposeFontFamily(ReadingTheme.PAPIER_CLAIR.fontFamily)
        assertEquals(ReadingTheme.PAPIER_CLAIR.fontFamily, ReadingTheme.SEPIA_VINTAGE.fontFamily)

        val keyA = paginationStyleKeyFrom(
            baseTextStyle = TextStyle(fontSize = 18.sp, fontFamily = fontFamily),
            fontSizeSp = 18, viewportWidthPx = 1000, viewportHeightPx = 2000, paddingPx = 16,
        )
        val keyB = paginationStyleKeyFrom(
            baseTextStyle = TextStyle(fontSize = 18.sp, fontFamily = fontFamily),
            fontSizeSp = 18, viewportWidthPx = 1000, viewportHeightPx = 2000, paddingPx = 16,
        )
        assertEquals(keyA, keyB)
    }

    @Test
    fun `changer d ambiance vers une police differente invalide la cle`() {
        // Papier Clair (SERIF) vs Obsidienne (SANS_SERIF) — la police
        // diffère réellement entre ces deux ambiances (Tâche 9.1).
        assertNotEquals(ReadingTheme.PAPIER_CLAIR.fontFamily, ReadingTheme.OBSIDIENNE.fontFamily)

        val keyA = paginationStyleKeyFrom(
            baseTextStyle = TextStyle(fontSize = 18.sp, fontFamily = ThemeColors.toComposeFontFamily(ReadingTheme.PAPIER_CLAIR.fontFamily)),
            fontSizeSp = 18, viewportWidthPx = 1000, viewportHeightPx = 2000, paddingPx = 16,
        )
        val keyB = paginationStyleKeyFrom(
            baseTextStyle = TextStyle(fontSize = 18.sp, fontFamily = ThemeColors.toComposeFontFamily(ReadingTheme.OBSIDIENNE.fontFamily)),
            fontSizeSp = 18, viewportWidthPx = 1000, viewportHeightPx = 2000, paddingPx = 16,
        )
        assertNotEquals(keyA, keyB)
    }
}
