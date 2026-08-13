package com.inktone.domain.model

/**
 * Bloc de contenu dans un chapitre — unité de rendu atomique.
 *
 * Chaque bloc est rendu par un composable dédié dans [BookBlockItem].
 * L'ordre des blocs dans [ChapterContent.Rich.blocks] est l'ordre d'affichage
 * dans le flux de lecture.
 *
 * ## Pont TTS ↔ UI
 *
 * Les blocs de texte ([ParagraphBlock], [HeadingBlock]) exposent
 * [globalOffsetRange] : l'intervalle d'offsets dans le texte concaténé du
 * chapitre. Ceci permet une recherche dichotomique O(log n) pour trouver le
 * bloc contenant un offset TTS donné (voir [BookBlockItem]).
 *
 * Les blocs non-texte ([ImageBlock], [SeparatorBlock]) ont
 * `globalOffsetRange = null` — ils ne participent pas au flux TTS.
 *
 * @property approxByteSize Taille mémoire approximative en octets, utilisée
 *   par le cache LRU pour l'éviction.
 * @property globalOffsetRange Intervalle [début, fin[ dans le texte concaténé
 *   du chapitre, ou null pour les blocs non-texte.
 */
sealed class BookBlock {
    abstract val approxByteSize: Int
    abstract val globalOffsetRange: IntRange?

    /**
     * Paragraphe de texte avec styles inline.
     *
     * @param richText Texte enrichi du paragraphe.
     * @param globalOffsetRange Offsets [début, fin[ dans le texte concaténé
     *   du chapitre. Jamais null pour un bloc de texte.
     * @param isBlockquote `true` pour un `<blockquote>` — seule distinction
     *   sémantique de bloc conservée face à un `<p>`/`<div>` ordinaire
     *   (mise en italique par [com.inktone.feature.reader.rendering.BookBlockStyleMapper]).
     */
    data class ParagraphBlock(
        val richText: StyledText,
        override val globalOffsetRange: IntRange,
        val isBlockquote: Boolean = false,
    ) : BookBlock() {
        override val approxByteSize: Int
            get() = richText.approxByteSize + 16

        init {
            require(!globalOffsetRange.isEmpty()) {
                "globalOffsetRange ne peut pas être vide pour un ParagraphBlock"
            }
        }
    }

    /**
     * Titre de section.
     *
     * @param level Niveau hiérarchique (1 = titre principal, 2 = sous-titre, etc.)
     * @param richText Texte enrichi du titre.
     * @param globalOffsetRange Offsets dans le texte concaténé du chapitre.
     */
    data class HeadingBlock(
        val level: Int,
        val richText: StyledText,
        override val globalOffsetRange: IntRange,
    ) : BookBlock() {
        override val approxByteSize: Int
            get() = richText.approxByteSize + 20

        init {
            require(level in 1..6) { "Heading level doit être entre 1 et 6, reçu: $level" }
            require(!globalOffsetRange.isEmpty()) {
                "globalOffsetRange ne peut pas être vide pour un HeadingBlock"
            }
        }
    }

    /**
     * Image inline dans le flux de lecture.
     *
     * @param href Chemin de la ressource image dans l'archive EPUB.
     * @param alt Texte alternatif pour l'accessibilité.
     * @param intrinsicWidth Largeur intrinsèque en px (attribut HTML width), ou null.
     * @param intrinsicHeight Hauteur intrinsèque en px (attribut HTML height), ou null.
     */
    data class ImageBlock(
        val href: String,
        val alt: String? = null,
        val intrinsicWidth: Int? = null,
        val intrinsicHeight: Int? = null,
    ) : BookBlock() {
        override val approxByteSize: Int = (href.length * 2) + (alt?.length?.times(2) ?: 0) + 32
        override val globalOffsetRange: IntRange? = null
    }

    /**
     * Séparateur horizontal (ligne, `* * *`, etc.).
     */
    data object SeparatorBlock : BookBlock() {
        override val approxByteSize: Int = 8
        override val globalOffsetRange: IntRange? = null
    }
}
