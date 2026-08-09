package com.inktone.domain.model

/**
 * Ambiance de lecture — Lot 9, remplace l'ancien enum fermé (`LIGHT`,
 * `DARK`, `SEPIA`, `SYSTEM`). Un thème personnalisé créé par
 * l'utilisateur est structurellement identique à un thème intégré, seul
 * [isBuiltIn] les distingue — c'est ce qui permet à la Galerie et au
 * Studio de les traiter avec le même code de rendu.
 *
 * Couleurs en hex `#RRGGBB` — jamais `androidx.compose.ui.graphics.Color`
 * ici, le domaine ne dépend jamais de Compose (Blueprint §12.4). La
 * conversion vers `Color` vit côté UI (`feature/reader` `ThemeColors`,
 * `feature/settings` Studio/Galerie).
 */
data class ReadingTheme(
    val id: String,
    val displayName: String,
    val isBuiltIn: Boolean,
    val backgroundColorHex: String,
    val textColorHex: String,
    val accentColorHex: String,
    val highlightColorHex: String,
    val fontFamily: FontFamily,
) {
    init {
        require(id.isNotBlank()) { "id ne peut pas être vide" }
        require(displayName.isNotBlank()) { "displayName ne peut pas être vide" }
        listOf(backgroundColorHex, textColorHex, accentColorHex, highlightColorHex).forEach {
            require(HEX_REGEX.matches(it)) { "couleur invalide : $it (attendu #RRGGBB)" }
        }
    }

    companion object {
        private val HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

        // ───── Section 1 — Ambiances de lecture (UX_FLOW_DESIGN.md §Galerie de thèmes) ─────

        val PAPIER_CLAIR = ReadingTheme(
            id = "papier_clair", displayName = "Papier Clair", isBuiltIn = true,
            backgroundColorHex = "#FFFFFF", textColorHex = "#000000",
            accentColorHex = "#1976D2", highlightColorHex = "#FFEB3B",
            fontFamily = FontFamily.SERIF,
        )
        val OBSIDIENNE = ReadingTheme(
            id = "obsidienne", displayName = "Obsidienne", isBuiltIn = true,
            backgroundColorHex = "#000000", textColorHex = "#FFFFFF",
            accentColorHex = "#90CAF9", highlightColorHex = "#FFD54F",
            fontFamily = FontFamily.SANS_SERIF,
        )
        val SEPIA_VINTAGE = ReadingTheme(
            id = "sepia_vintage", displayName = "Sépia Vintage", isBuiltIn = true,
            backgroundColorHex = "#F4ECD8", textColorHex = "#3B2F22",
            accentColorHex = "#8D6E63", highlightColorHex = "#FFCC80",
            fontFamily = FontFamily.SERIF,
        )

        // Doc cible : « sans-serif pour les thèmes sombres type
        // Obsidienne/Sauge » — groupées ensemble malgré un fond non noir,
        // c'est le parti pris explicite de la cible, pas une erreur.
        val SAUGE_OLIVE = ReadingTheme(
            id = "sauge_olive", displayName = "Sauge & Olive", isBuiltIn = true,
            backgroundColorHex = "#E8ECD7", textColorHex = "#33402D",
            accentColorHex = "#7C9070", highlightColorHex = "#D9C36A",
            fontFamily = FontFamily.SANS_SERIF,
        )

        val AMBIANCES: List<ReadingTheme> = listOf(PAPIER_CLAIR, OBSIDIENNE, SEPIA_VINTAGE, SAUGE_OLIVE)

        // ───── Section 2 — Confort & Accessibilité ─────

        val OPEN_DYSLEXIC_ESPACEMENT = ReadingTheme(
            id = "open_dyslexic_espacement", displayName = "OpenDyslexic & Espacement", isBuiltIn = true,
            backgroundColorHex = "#FFFFFF", textColorHex = "#000000",
            accentColorHex = "#1976D2", highlightColorHex = "#FFEB3B",
            fontFamily = FontFamily.OPEN_DYSLEXIC,
        )
        val NOIR_ABSOLU_AMOLED = ReadingTheme(
            id = "noir_absolu_amoled", displayName = "Noir Absolu AMOLED", isBuiltIn = true,
            backgroundColorHex = "#000000", textColorHex = "#E0E0E0",
            accentColorHex = "#64B5F6", highlightColorHex = "#FFD54F",
            fontFamily = FontFamily.SANS_SERIF,
        )

        val ACCESSIBILITY: List<ReadingTheme> = listOf(OPEN_DYSLEXIC_ESPACEMENT, NOIR_ABSOLU_AMOLED)

        /** Tous les thèmes intégrés, ambiances + accessibilité — catalogue de résolution/migration. */
        val BUILT_IN: List<ReadingTheme> = AMBIANCES + ACCESSIBILITY

        /**
         * Repli par défaut — thème initial d'un compte neuf (`UserPreferences.theme`)
         * et cible de la transaction de repli quand un thème personnalisé
         * actif est supprimé (Tâche 9.5).
         */
        val DEFAULT = PAPIER_CLAIR

        /**
         * Bascule cyclique du lecteur (lot 3b, tranchée Tâche 9.2) : trois
         * ambiances de référence seulement — un cycle sur l'ensemble ouvert
         * (thèmes personnalisés compris) serait impraticable au geste
         * rapide. Reprend exactement le mapping de l'ancien enum
         * LIGHT→DARK→SEPIA→LIGHT, aucune surprise pour l'utilisateur.
         */
        val CYCLE: List<ReadingTheme> = listOf(PAPIER_CLAIR, OBSIDIENNE, SEPIA_VINTAGE)
    }
}
