package com.inktone.data.pdfcache

import com.inktone.domain.model.RenderedPage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Lot 22, Palier C, tâche 9 — un round-trip doit restituer exactement les
 * mêmes pixels et dimensions ; la purge par livre doit vider son dossier
 * sans toucher aux autres publications.
 */
class RenderedPageCacheImplTest {

    private fun tempStore(): RenderedPageCacheImpl {
        val dir = File.createTempFile("pdfpages", "test").apply { delete(); mkdirs() }
        return RenderedPageCacheImpl(dir)
    }

    private fun page(width: Int = 2, height: Int = 2) =
        RenderedPage(widthPx = width, heightPx = height, pixelsArgb = IntArray(width * height) { it })

    @Test
    fun `un round-trip restitue la page`() = runTest {
        val store = tempStore()
        store.put("pub-1", pageIndex = 3, targetWidthPx = 800, page())

        val loaded = store.get("pub-1", pageIndex = 3, targetWidthPx = 800)

        assertEquals(page(), loaded)
    }

    @Test
    fun `une page absente retourne null`() = runTest {
        val store = tempStore()
        assertNull(store.get("pub-1", pageIndex = 0, targetWidthPx = 800))
    }

    @Test
    fun `une resolution differente n'est jamais servie`() = runTest {
        val store = tempStore()
        store.put("pub-1", pageIndex = 3, targetWidthPx = 800, page())

        assertNull(store.get("pub-1", pageIndex = 3, targetWidthPx = 1200))
    }

    @Test
    fun `la purge par livre supprime ses pages sans toucher aux autres`() = runTest {
        val store = tempStore()
        store.put("pub-1", pageIndex = 0, targetWidthPx = 800, page())
        store.put("pub-2", pageIndex = 0, targetWidthPx = 800, page())

        store.deletePublication("pub-1")

        assertNull(store.get("pub-1", pageIndex = 0, targetWidthPx = 800))
        assertEquals(page(), store.get("pub-2", pageIndex = 0, targetWidthPx = 800))
    }
}
