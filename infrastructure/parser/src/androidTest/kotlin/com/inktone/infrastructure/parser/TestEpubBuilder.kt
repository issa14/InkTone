package com.inktone.infrastructure.parser

import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Construit un EPUB minimal à la volée pour les tests, sans fixture
 * binaire checked-in — utile pour les cas de repli (couverture via
 * `<guide>`, item binaire direct dans le spine, casse d'entrée ZIP
 * différente) trop spécifiques pour justifier un fixture `.epub` dédié
 * de plus dans `androidTest/assets/`.
 */
internal object TestEpubBuilder {

    fun writeToCache(context: Context, fileName: String, entries: Map<String, ByteArray>): File {
        val file = File(context.cacheDir, fileName)
        ZipOutputStream(file.outputStream()).use { zos ->
            entries.forEach { (path, bytes) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return file
    }

    fun text(content: String): ByteArray = content.toByteArray(Charsets.UTF_8)

    /** Octets JPEG minimaux (marqueurs SOI/EOI) — suffisant, le contenu réel n'est jamais décodé par ces tests. */
    val MINIMAL_JPEG_BYTES = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte(),
    )

    const val CONTAINER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

    const val MIMETYPE = "application/epub+zip"
}
