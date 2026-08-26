package com.inktone.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Police OpenDyslexic — conçue pour les lecteurs dyslexiques.
 * Source : https://opendyslexic.org/ — Licence SIL Open Font License 1.1.
 */
val OpenDyslexicFamily: FontFamily = FontFamily(Font(R.font.opendyslexic_regular))

/**
 * Source Serif 4 variable (OFL 1.1, github.com/adobe-fonts/source-serif) —
 * police de lecture française à empattements (Lot 21, tâche 10). Axes
 * `wght` + `opsz`, 4 poids fonctionnels : 400/500/600/700.
 */
@OptIn(ExperimentalTextApi::class)
private fun sourceSerif(w: Int) = Font(
    R.font.source_serif4_variable,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

/** Police de lecture à empattements dédiée au français (Lot 21). */
val SourceSerifFamily: FontFamily = FontFamily(sourceSerif(400), sourceSerif(500), sourceSerif(600), sourceSerif(700))

// --- Sous-lot 2b — typographie de marque ---

/** Work Sans variable (OFL 1.1, github.com/weiweihuanghuang/Work-Sans).
 *  Axe `wght` 100–900, 4 poids fonctionnels : 400/500/600/700. */
@OptIn(ExperimentalTextApi::class)
private fun workSans(w: Int) = Font(
    R.font.work_sans_variable,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

/** Chrome fonctionnel intégral — Work Sans (D-typo-3, Sous-lot 2b). */
val WorkSansFamily = FontFamily(workSans(400), workSans(500), workSans(600), workSans(700))

/** Literata variable (OFL 1.1, github.com/googlefonts/literata).
 *  Axes `wght` + `opsz`, usage narratif ponctuel — jamais dans la scale. */
@OptIn(ExperimentalTextApi::class)
val NarrativeAccentFamily = FontFamily(
    Font(R.font.literata_variable, FontWeight.Normal,
         variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.literata_variable, FontWeight.SemiBold,
         variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

// --- Typographie chrome ---

/** Typographie du chrome de l'app (Sous-lot 2b — Work Sans intégral). */
val InkToneTypography = Typography(
    displayLarge = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    displayMedium = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 28.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = WorkSansFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp),
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
