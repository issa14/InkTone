package com.inktone.domain.model

/**
 * Page à pagination fixe (PDF) rendue en pixels bruts (Lot 12, Palier 2,
 * décision actée 12). Jamais `android.graphics.Bitmap` ici — le domaine
 * ne dépend jamais d'Android (règle non négociable, CLAUDE.md).
 * `feature/reader` reconstruit un `Bitmap` via
 * `Bitmap.createBitmap(pixelsArgb, widthPx, heightPx, Config.ARGB_8888)`,
 * seul point où cette conversion a lieu.
 */
data class RenderedPage(
    val widthPx: Int,
    val heightPx: Int,
    val pixelsArgb: IntArray,
) {
    init {
        require(widthPx > 0) { "widthPx doit être strictement positif" }
        require(heightPx > 0) { "heightPx doit être strictement positif" }
        require(pixelsArgb.size == widthPx * heightPx) {
            "pixelsArgb doit contenir exactement widthPx * heightPx éléments"
        }
    }

    // IntArray n'a pas d'égalité structurelle par défaut (identité de
    // référence) - une data class qui l'embarque doit la redéfinir
    // explicitement, sinon deux rendus identiques comparent inégaux
    // dans les tests.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RenderedPage) return false
        return widthPx == other.widthPx && heightPx == other.heightPx && pixelsArgb.contentEquals(other.pixelsArgb)
    }

    override fun hashCode(): Int {
        var result = widthPx
        result = 31 * result + heightPx
        result = 31 * result + pixelsArgb.contentHashCode()
        return result
    }
}
