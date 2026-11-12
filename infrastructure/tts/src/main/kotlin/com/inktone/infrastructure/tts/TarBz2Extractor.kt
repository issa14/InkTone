package com.inktone.infrastructure.tts

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Lot 20 — extraction d'une archive `.tar.bz2` sherpa-onnx (voix
 * upmc-medium, modèle CTC) vers un répertoire cible.
 *
 * Avant ce lot, le téléchargeur ne faisait que déposer l'archive :
 * `SherpaOnnxModelPaths.isReady` ne pouvait jamais passer à vrai et le
 * moteur restait inutilisable malgré un « téléchargement réussi »
 * (AUDIT_CONSOLIDATION_V1.md, B2). L'extraction est le maillon qui
 * rend la chaîne réellement fonctionnelle.
 *
 * **Pur JVM** (aucune dépendance Android) : testable en test unitaire
 * avec une archive construite en mémoire.
 *
 * **Sécurité** : chemin de sortie contrôlé — les entrées absolues ou
 * contenant `..` sont rejetées, jamais écrites hors de [targetDir].
 * `stripRoot` retire le premier segment de chemin (le répertoire racine
 * des archives sherpa-onnx, ex. `vits-piper-fr_FR-upmc-medium/`) pour
 * que les fichiers atterrissent directement dans [targetDir].
 *
 * @param archiveFile l'archive `.tar.bz2` (intégrité SHA-256 déjà
 *   vérifiée en amont par [VoiceModelDownloader]).
 * @param targetDir répertoire de destination (créé si absent).
 * @param stripRoot retire le premier segment de chaque chemin.
 * @return la liste des fichiers extraits.
 * @throws TarBz2ExtractionException en cas de chemin non contrôlé ou
 *   d'archive invalide — jamais d'écriture partielle silencieuse.
 */
object TarBz2Extractor {

    fun extract(archiveFile: File, targetDir: File, stripRoot: Boolean = true): List<File> {
        targetDir.mkdirs()
        val extracted = mutableListOf<File>()
        BZip2CompressorInputStream(FileInputStream(archiveFile)).use { bz2 ->
            TarArchiveInputStream(bz2).use { tar ->
                var entry: TarArchiveEntry? = tar.nextTarEntry
                while (entry != null) {
                    val name = entry.name.removePrefix("/")
                    if (name.isBlank()) {
                        entry = tar.nextTarEntry
                        continue
                    }
                    val segments = name.split('/').filter { it.isNotBlank() && it != "." }
                    if (segments.any { it == ".." }) {
                        throw TarBz2ExtractionException("Entrée hors répertoire cible refusée : ${entry.name}")
                    }
                    // Avec stripRoot, le répertoire racine lui-même (segment
                    // unique, isDirectory) n'est pas recréé : il est la
                    // hiérarchie à supprimer. Un fichier à la racine d'une
                    // archive plate, lui, est conservé.
                    if (stripRoot && segments.size == 1 && entry.isDirectory) {
                        entry = tar.nextTarEntry
                        continue
                    }
                    val relative = if (stripRoot && segments.size > 1) {
                        segments.drop(1).joinToString("/")
                    } else {
                        segments.joinToString("/")
                    }
                    if (relative.isBlank()) {
                        entry = tar.nextTarEntry
                        continue
                    }
                    val target = File(targetDir, relative)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var read = tar.read(buffer)
                            while (read >= 0) {
                                out.write(buffer, 0, read)
                                read = tar.read(buffer)
                            }
                        }
                        extracted += target
                    }
                    entry = tar.nextTarEntry
                }
            }
        }
        return extracted
    }

    /**
     * Extrait vers [targetDir] sans jamais y laisser une installation
     * partielle : extraction dans un répertoire voisin, puis bascule par
     * renommage une fois l'archive entièrement écrite.
     *
     * Les modèles sont réputés installés dès que leurs fichiers EXISTENT
     * (`SherpaOnnxModelPaths.isReady`). Extraire directement dans
     * [targetDir] laissait donc, si l'application était tuée pendant
     * l'extraction, un modèle tronqué tenu pour bon : « Voix neuronale
     * installée » affiché, plus aucun bouton pour la retélécharger, et un
     * fichier incomplet chargé à chaque narration.
     *
     * Un ancien contenu de [targetDir] n'est remplacé qu'après la réussite
     * de l'extraction ; en cas d'échec, il reste en place et le répertoire
     * temporaire est supprimé.
     */
    fun extractAtomically(archiveFile: File, targetDir: File, stripRoot: Boolean = true) {
        val parent = targetDir.absoluteFile.parentFile
            ?: throw TarBz2ExtractionException("Répertoire cible sans parent : $targetDir")
        val staging = File(parent, targetDir.name + STAGING_SUFFIX)
        staging.deleteRecursively()
        try {
            extract(archiveFile, staging, stripRoot)
            targetDir.deleteRecursively()
            if (!staging.renameTo(targetDir)) {
                throw TarBz2ExtractionException("Installation du modèle impossible : $targetDir")
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private const val BUFFER_SIZE = 8192
    private const val STAGING_SUFFIX = ".installation"
}

class TarBz2ExtractionException(message: String) : RuntimeException(message)
