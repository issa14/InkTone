package com.inktone.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Police OpenDyslexic — conçue pour les lecteurs dyslexiques.
 * Source : https://opendyslexic.org/ — Licence SIL Open Font License 1.1.
 */
val OpenDyslexicFamily: FontFamily = FontFamily(Font(R.font.opendyslexic_regular))

/** Typographie du chrome de l'app (Tâche 9bis.1.1, porté du legacy tel quel). */
val InkToneTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 28.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp),
)

/**
 * Amélioration Tâche 9bis.1.3 — échelle séparée du chrome UI, appliquée
 * uniquement au texte du livre dans `ReaderScreen`. `lineHeight`/
 * `letterSpacing` plus généreux que `InkToneTypography.bodyLarge` :
 * confort de lecture continue de plusieurs heures, pas juste esthétique.
 * La famille de police reste celle choisie par l'utilisateur
 * (`FontFamily`/`OPEN_DYSLEXIC`, Tâche 4.7) — non fixée ici.
 */
val ReadingTypography = TextStyle(
    fontSize = 18.sp,
    lineHeight = 1.6.em,
    letterSpacing = 0.01.em,
)
