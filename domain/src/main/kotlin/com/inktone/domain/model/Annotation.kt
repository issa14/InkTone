package com.inktone.domain.model

import com.inktone.domain.valueobject.Locator

/**
 * Couleur libre d'annotation (Lot 23, décision 6) — un `Int` ARGB, jamais
 * `androidx.compose.ui.graphics.Color` (le domaine ne dépend jamais
 * d'Android/Compose, règle non négociable) ; la conversion vit dans
 * `feature/reader` (`toComposeColor()`). Remplace l'ancien enum fermé à 5
 * valeurs : [PRESETS] reste la palette rapide par défaut, mais toute
 * couleur (y compris personnalisée, Palier D) est désormais représentable.
 */
@JvmInline
value class AnnotationColor(val argb: Int) {

    companion object {
        // Mêmes teintes que l'ancien enum — une migration ne doit changer
        // la couleur visuelle d'aucune annotation existante (décision 6).
        val YELLOW = AnnotationColor(0xFFFFF59D.toInt())
        val GREEN = AnnotationColor(0xFFA5D6A7.toInt())
        val BLUE = AnnotationColor(0xFF90CAF9.toInt())
        val PINK = AnnotationColor(0xFFF48FB1.toInt())
        val ORANGE = AnnotationColor(0xFFFFCC80.toInt())

        /** Palette rapide par défaut du sélecteur (décision 3, Lot 23). */
        val PRESETS = listOf(YELLOW, GREEN, BLUE, PINK, ORANGE)

        private val LEGACY_NAMES = mapOf(
            "YELLOW" to YELLOW, "GREEN" to GREEN, "BLUE" to BLUE, "PINK" to PINK, "ORANGE" to ORANGE,
        )

        /**
         * Décode une représentation persistée : hex `#AARRGGBB` (format
         * courant depuis le Lot 23) ou nom d'enum hérité (`"YELLOW"`...,
         * format d'avant ce Lot) — une base ou une sauvegarde non migrée
         * doit rester lisible (décision 7, jamais de `valueOf` non
         * défensif comme celui de `FontFamily`, constat 11 du Lot 22).
         */
        fun parse(raw: String): AnnotationColor =
            LEGACY_NAMES[raw] ?: AnnotationColor(raw.removePrefix("#").toUInt(16).toInt())
    }
}

/** Représentation persistée d'une [AnnotationColor] — hex `#AARRGGBB`. */
fun AnnotationColor.toHex(): String = "#%08X".format(argb)

/**
 * Type d'annotation (Lot 22, tâche 10) — trois canaux visuels distincts :
 * surlignage, souligné, barré. Le [HIGHLIGHT] couvre l'existant (toute
 * annotation créée avant ce lot est un surlignage) ; les deux autres
 * étendent la parité avec les lecteurs top-tier sans jamais mélanger les
 * canaux visuels : annotation, surlignage TTS (`WordHighlightColor`) et
 * sélection (`SelectionHighlightColor`) restent trois canaux séparés.
 */
enum class AnnotationKind { HIGHLIGHT, UNDERLINE, STRIKETHROUGH }

/**
 * Surlignage, note ou citation liée à une plage de [Locator]s. Un seul
 * modèle d'adressage pour toute la plage — jamais chapter+startOffset
 * d'un côté et Locator de l'autre (revue B2/D7).
 */
data class Annotation(
    val id: String,
    val publicationId: String,
    val startLocator: Locator,
    val endLocator: Locator,
    val color: AnnotationColor,
    val kind: AnnotationKind = AnnotationKind.HIGHLIGHT,
    val content: String? = null,
    val excerpt: String? = null,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        require(publicationId.isNotBlank()) { "publicationId ne peut pas être vide" }
        require(endLocator >= startLocator) { "endLocator doit être postérieur ou égal à startLocator" }
        require(excerpt == null || excerpt.length <= MAX_EXCERPT_LENGTH) {
            "excerpt ne doit pas dépasser $MAX_EXCERPT_LENGTH caractères"
        }
    }

    companion object {
        /** Borne fixée à la création (Lot 4, tâche 4.2) — la carte cible affiche un extrait, pas un passage entier. */
        const val MAX_EXCERPT_LENGTH = 280
    }
}
