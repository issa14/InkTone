package com.inktone.infrastructure.parser

import android.graphics.Bitmap
import io.legere.pdfiumandroid.PdfPage

/**
 * Primitive de rendu bas niveau partagée (Lot 12, décision actée 13 du
 * plan) — un seul point d'appel à l'API bitmap de PDFium, réutilisé par
 * `PdfPublicationParser` (couverture, tâche 12.3) et `PdfPageRendererImpl`
 * (Palier 2, tâche 12.7). Rendu « ajusté à la largeur » : la hauteur se
 * déduit du ratio réel de la page, jamais une valeur fixe qui la
 * déformerait.
 */
internal fun PdfPage.renderToBitmap(targetWidthPx: Int): Bitmap? {
    val widthPt = getPageWidthPoint()
    val heightPt = getPageHeightPoint()
    if (widthPt <= 0 || heightPt <= 0 || targetWidthPx <= 0) return null

    val targetHeightPx = (targetWidthPx.toFloat() * heightPt / widthPt).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
    renderPageBitmap(bitmap, 0, 0, targetWidthPx, targetHeightPx, false, false)
    return bitmap
}
