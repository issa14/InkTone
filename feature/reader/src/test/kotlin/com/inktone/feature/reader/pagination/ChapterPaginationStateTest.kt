package com.inktone.feature.reader.pagination

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
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
}
