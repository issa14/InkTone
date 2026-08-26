package com.inktone.data.pdfcache

import com.inktone.domain.model.RenderedPage
import com.inktone.domain.service.RenderedPageCache
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation fichier de [RenderedPageCache] (Lot 22, Palier C, tâche 9).
 *
 * Un fichier par page rendue : `pdfpages/<publicationId>/<pageIndex>_<targetWidthPx>.page`,
 * pixels ARGB bruts précédés de leurs dimensions — même principe de
 * structuration par publication que `TtsSegmentCacheImpl`, pour que la
 * purge à la suppression du livre soit triviale.
 */
@Singleton
class RenderedPageCacheImpl @Inject constructor(cacheDir: File) : RenderedPageCache {

    private val baseDir = File(cacheDir, "pdfpages")

    override suspend fun get(publicationId: String, pageIndex: Int, targetWidthPx: Int): RenderedPage? {
        val file = pageFile(publicationId, pageIndex, targetWidthPx)
        if (!file.exists()) return null
        return runCatching { readPage(file) }.getOrNull()
    }

    override suspend fun put(publicationId: String, pageIndex: Int, targetWidthPx: Int, page: RenderedPage) {
        val dir = publicationDir(publicationId)
        dir.mkdirs()
        val file = pageFile(publicationId, pageIndex, targetWidthPx)
        val tmp = File(dir, "${pageIndex}_${targetWidthPx}.page.tmp")
        try {
            writePage(tmp, page)
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    override suspend fun deletePublication(publicationId: String) {
        publicationDir(publicationId).deleteRecursively()
    }

    private fun publicationDir(publicationId: String): File = File(baseDir, publicationId)

    private fun pageFile(publicationId: String, pageIndex: Int, targetWidthPx: Int): File =
        File(publicationDir(publicationId), "${pageIndex}_${targetWidthPx}.page")

    private fun writePage(file: File, page: RenderedPage) {
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.writeInt(page.widthPx)
            out.writeInt(page.heightPx)
            page.pixelsArgb.forEach { out.writeInt(it) }
        }
    }

    private fun readPage(file: File): RenderedPage {
        DataInputStream(file.inputStream().buffered()).use { input ->
            val width = input.readInt()
            val height = input.readInt()
            val pixels = IntArray(width * height) { input.readInt() }
            return RenderedPage(widthPx = width, heightPx = height, pixelsArgb = pixels)
        }
    }
}
