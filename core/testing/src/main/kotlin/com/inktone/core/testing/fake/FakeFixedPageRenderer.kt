package com.inktone.core.testing.fake

import com.inktone.domain.model.RenderedPage
import com.inktone.domain.service.FixedPageDocument
import com.inktone.domain.service.FixedPageOpenResult
import com.inktone.domain.service.FixedPageRenderer

/**
 * Renvoie toujours le même [FixedPageOpenResult], configurable par test —
 * pas de vrai rendu PDFium (réservé aux tests `androidTest`
 * d'infrastructure/parser, qui exigent le binding natif). Lot 12, Palier 2.
 */
class FakeFixedPageRenderer(
    private var result: FixedPageOpenResult = FixedPageOpenResult.Failed("non configuré par le test"),
) : FixedPageRenderer {

    fun setNextResult(result: FixedPageOpenResult) {
        this.result = result
    }

    override suspend fun open(fileUri: String): FixedPageOpenResult = result
}

/** Document minimal pour les tests qui ont besoin d'un `Success` exploitable. */
class FakeFixedPageDocument(
    override val pageCount: Int,
    private val page: (Int, Int) -> RenderedPage? = { _, widthPx -> RenderedPage(widthPx, widthPx, IntArray(widthPx * widthPx)) },
) : FixedPageDocument {
    var closed = false
        private set

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): RenderedPage? =
        if (pageIndex in 0 until pageCount) page(pageIndex, targetWidthPx) else null

    override fun close() {
        closed = true
    }
}
