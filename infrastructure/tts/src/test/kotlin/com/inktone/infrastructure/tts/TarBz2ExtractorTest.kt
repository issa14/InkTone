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

    @Test
    fun `l installation atomique remplace un ancien contenu et ne laisse aucun repertoire temporaire`() {
        val archive = File.createTempFile("model", ".tar.bz2")
        val parent = File.createTempFile("parent", "").also { it.delete(); it.mkdirs() }
        val target = File(parent, "vits-piper-fr_FR-upmc-medium")
        try {
            // Reste d'une installation précédente interrompue.
            target.mkdirs()
            File(target, "fr_FR-upmc-medium.onnx").writeBytes(ByteArray(3))
            File(target, "obsolete.txt").writeText("x")
            writeTarBz2(archive, mapOf("fr_FR-upmc-medium.onnx" to ByteArray(64) { 1 }, "tokens.txt" to "tok".toByteArray()))

            TarBz2Extractor.extractAtomically(archive, target)

            assertEquals(64L, File(target, "fr_FR-upmc-medium.onnx").length())
            assertTrue(File(target, "tokens.txt").exists())
            assertFalse("l'ancien contenu ne survit pas à la nouvelle installation", File(target, "obsolete.txt").exists())
            assertEquals(listOf(target.name), parent.list()!!.toList())
        } finally {
            archive.delete()
            parent.deleteRecursively()
        }
    }

    @Test
    fun `une archive tronquee ne laisse aucun modele partiel dans le repertoire cible`() {
        // Équivalent testable d'une extraction interrompue : avant, les
        // fichiers déjà écrits restaient dans le répertoire cible, et le
        // modèle passait pour installé (seule leur existence est vérifiée).
        val archive = File.createTempFile("model", ".tar.bz2")
        val parent = File.createTempFile("parent", "").also { it.delete(); it.mkdirs() }
        val target = File(parent, "vits-piper-fr_FR-upmc-medium")
        try {
            writeTarBz2(
                archive,
                mapOf(
                    "tokens.txt" to "tok".toByteArray(),
                    "fr_FR-upmc-medium.onnx" to ByteArray(200_000) { (it % 251).toByte() },
                ),
            )
            val bytes = archive.readBytes()
            archive.writeBytes(bytes.copyOf(bytes.size / 2))

            try {
                TarBz2Extractor.extractAtomically(archive, target)
                fail("une archive tronquée doit échouer")
            } catch (expected: Exception) {
                // Échec attendu : seul compte l'état du disque ensuite.
            }

            assertFalse(target.exists())
            assertTrue("aucun répertoire temporaire ne doit subsister", parent.list()!!.isEmpty())
        } finally {
            archive.delete()
            parent.deleteRecursively()
        }
    }
}
