package com.inktone.infrastructure.tts

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * Lot 20 — l'extraction tar+bzip2 est le maillon qui rend le
 * téléchargement des voix réellement exploitable (avant : archive jamais
 * extraite, `isReady` toujours faux — AUDIT_CONSOLIDATION_V1.md B2).
 * Testée en JVM pur avec une archive construite en mémoire.
 */
class TarBz2ExtractorTest {

    private fun writeTarBz2(
        archive: File,
        entries: Map<String, ByteArray>,
        rootDir: String = "vits-piper-fr_FR-upmc-medium",
    ) {
        BZip2CompressorOutputStream(FileOutputStream(archive)).use { bz2 ->
            TarArchiveOutputStream(bz2).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                // Répertoire racine explicite (comme les archives sherpa-onnx).
                tar.putArchiveEntry(TarArchiveEntry("$rootDir/"))
                tar.closeArchiveEntry()
                entries.forEach { (relative, content) ->
                    val entry = TarArchiveEntry("$rootDir/$relative")
                    entry.size = content.size.toLong()
                    tar.putArchiveEntry(entry)
                    tar.write(content)
                    tar.closeArchiveEntry()
                }
            }
        }
    }

    @Test
    fun `extrait les fichiers en retirant le repertoire racine`() {
        val archive = File.createTempFile("model", ".tar.bz2")
        val target = File.createTempFile("target", "")
        target.delete()
        try {
            writeTarBz2(
                archive,
                mapOf(
                    "fr_FR-upmc-medium.onnx" to ByteArray(64) { 1 },
                    "tokens.txt" to "tok".toByteArray(),
                    "espeak-ng-data/phondata" to "pd".toByteArray(),
                ),
            )

            val extracted = TarBz2Extractor.extract(archive, target, stripRoot = true)

            assertEquals(3, extracted.size)
            assertTrue(File(target, "fr_FR-upmc-medium.onnx").isFile)
            assertTrue(File(target, "tokens.txt").isFile)
            assertTrue(File(target, "espeak-ng-data/phondata").isFile)
            assertFalse(File(target, "vits-piper-fr_FR-upmc-medium").exists())
        } finally {
            archive.delete()
            target.deleteRecursively()
        }
    }

    @Test
    fun `sans stripRoot conserve la hierarchie complete`() {
        val archive = File.createTempFile("model", ".tar.bz2")
        val target = File.createTempFile("target", "")
        target.delete()
        try {
            writeTarBz2(archive, mapOf("fr_FR-upmc-medium.onnx" to ByteArray(8)))

            TarBz2Extractor.extract(archive, target, stripRoot = false)

            assertTrue(File(target, "vits-piper-fr_FR-upmc-medium/fr_FR-upmc-medium.onnx").isFile)
        } finally {
            archive.delete()
            target.deleteRecursively()
        }
    }

    @Test
    fun `rejette une entree avec chemin absolu ou parent`() {
        val target = File.createTempFile("target", "")
        target.delete()
        try {
            val archive = File.createTempFile("evil", ".tar.bz2")
            try {
                // Entrée contenant ".." — doit être refusée, jamais écrite hors cible.
                BZip2CompressorOutputStream(FileOutputStream(archive)).use { bz2 ->
                    TarArchiveOutputStream(bz2).use { tar ->
                        val entry = TarArchiveEntry("../../etc/passwd")
                        entry.size = 4
                        tar.putArchiveEntry(entry)
                        tar.write("root".toByteArray())
                        tar.closeArchiveEntry()
                    }
                }
                try {
                    TarBz2Extractor.extract(archive, target, stripRoot = true)
                    fail("Une entrée '..' doit lever une exception")
                } catch (e: TarBz2ExtractionException) {
                    // attendu
                }
            } finally {
                archive.delete()
            }
        } finally {
            target.deleteRecursively()
        }
    }
}
