package com.inktone.feature.reader

import androidx.compose.ui.graphics.Color
import com.inktone.domain.model.ReadingTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeColorTest {

    @Test
    fun theme_sombre_utilise_un_fond_noir_et_un_texte_blanc() {
        assertEquals(Color.Black, ThemeColors.background(ReadingTheme.DARK))
        assertEquals(Color.White, ThemeColors.text(ReadingTheme.DARK))
    }

    @Test
    fun theme_sepia_utilise_un_fond_beige_et_un_texte_noir() {
        assertEquals(Color(0xFFF4ECD8), ThemeColors.background(ReadingTheme.SEPIA))
        assertEquals(Color.Black, ThemeColors.text(ReadingTheme.SEPIA))
    }

    @Test
    fun theme_clair_et_systeme_utilisent_le_meme_rendu() {
        assertEquals(ThemeColors.background(ReadingTheme.LIGHT), ThemeColors.background(ReadingTheme.SYSTEM))
        assertEquals(ThemeColors.text(ReadingTheme.LIGHT), ThemeColors.text(ReadingTheme.SYSTEM))
    }
}
