package com.inktone.data.preanalysis

import com.inktone.domain.model.Chapter
import com.inktone.domain.service.PreAnalysisStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation fichier de [PreAnalysisStore] (Lot 22, Palier A).
 *
 * ## Disposition (format 3)
 *
 * Un RÉPERTOIRE par publication, sous `cacheDir/preanalysis/<id>/` :
 * - `header.json` : version du format, version du parseur, `fileHash` de
 *   la source, et la liste ordonnée des hrefs de chapitres ;
 * - `<n>.json` : le chapitre de rang `n` dans cette liste.
 *
 * [loadChapter] ne lit que l'en-tête et le chapitre demandé. Le format 1
 * (un seul objet JSON) puis le format 2 (en-tête puis chapitres, dans un
 * même fichier) obligeaient à désérialiser le livre ENTIER — 25 Mo pour un
 * gros livre, ~10 s sur V2206 — pour afficher un seul chapitre.
 *
 * Toute pré-analyse dont l'en-tête diverge (format, parseur, `fileHash`)
 * n'est **jamais** servie (décision 1) ; elle est régénérée en tâche de
 * fond (voir « Régénération » dans [PreAnalysisStore]).
 *
 * Purge explicite ([delete]) : aucun `ON DELETE CASCADE` de Room
 * (décision 1, critère de sortie testé). Les fichiers plats des formats 1
 * et 2 (`<id>.prea`) sont supprimés à la réécriture et à la purge.
 *
 * **Sûr pour le thread principal** : toute lecture/écriture de fichier et
 * toute (dé)sérialisation passent par [Dispatchers.IO].
 */
@Singleton
class FilePreAnalysisStore @Inject constructor(
    cacheDir: File,
) : PreAnalysisStore {

    private val json = Json { ignoreUnknownKeys = true }
    private val baseDir = File(cacheDir, "preanalysis")

    override suspend fun save(publicationId: String, fileHash: String, chapters: List<Chapter>) {
        withContext(Dispatchers.IO) { writeAtomically(publicationId, fileHash, chapters) }
    }

    /**
     * Écrit dans un répertoire temporaire, puis le substitue d'un seul
     * renommage : un lecteur ne voit jamais un en-tête annonçant des
     * chapitres pas encore écrits.
     */
    private fun writeAtomically(publicationId: String, fileHash: String, chapters: List<Chapter>) {
        baseDir.mkdirs()
        val target = dirFor(publicationId)
        val tmp = File(baseDir, "$publicationId.tmp").apply { deleteRecursively(); mkdirs() }
        try {
            chapters.forEachIndexed { index, chapter ->
                File(tmp, chapterFileName(index)).writeText(json.encodeToString(ChapterDto.serializer(), chapter.toDto()))
            }
            val header = PreAnalysisHeader(FORMAT_VERSION, PARSER_VERSION, fileHash, chapters.map { it.href })
            File(tmp, HEADER_FILE).writeText(json.encodeToString(PreAnalysisHeader.serializer(), header))
            target.deleteRecursively()
            check(tmp.renameTo(target)) { "Remplacement de la pré-analyse impossible : $target" }
        } finally {
            if (tmp.exists()) tmp.deleteRecursively()
        }
        legacyFileFor(publicationId).delete()
    }

    override suspend fun loadChapter(publicationId: String, fileHash: String, chapterHref: String): Chapter? =
        withContext(Dispatchers.IO) {
            runCatching {
                val header = readHeader(publicationId)?.takeIf { it.isValidFor(fileHash) } ?: return@runCatching null
                val index = header.chapterHrefs.indexOf(chapterHref).takeIf { it >= 0 } ?: return@runCatching null
                val file = File(dirFor(publicationId), chapterFileName(index))
                json.decodeFromString(ChapterDto.serializer(), file.readText()).toDomain()
            }.getOrNull()
        }

    override suspend fun isFresh(publicationId: String, fileHash: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { readHeader(publicationId)?.isValidFor(fileHash) == true }.getOrDefault(false)
    }

    override suspend fun delete(publicationId: String) {
        withContext(Dispatchers.IO) {
            dirFor(publicationId).deleteRecursively()
            legacyFileFor(publicationId).delete()
        }
    }

    /** En-tête, ou `null` s'il est absent ou illisible (pré-analyse d'un ancien format). */
    private fun readHeader(publicationId: String): PreAnalysisHeader? {
        val file = File(dirFor(publicationId), HEADER_FILE)
        if (!file.exists()) return null
        return json.decodeFromString(PreAnalysisHeader.serializer(), file.readText())
    }

    private fun PreAnalysisHeader.isValidFor(fileHash: String): Boolean =
        formatVersion == FORMAT_VERSION && parserVersion == PARSER_VERSION && this.fileHash == fileHash

    private fun dirFor(publicationId: String): File = File(baseDir, publicationId)

    /** Fichier plat des formats 1 et 2, à supprimer dès qu'il est remplacé. */
    private fun legacyFileFor(publicationId: String): File = File(baseDir, "$publicationId.prea")

    private fun chapterFileName(index: Int): String = "$index.json"

    private companion object {
        const val HEADER_FILE = "header.json"

        /** Version du format de sérialisation — incrémenter à tout changement de schéma. */
        const val FORMAT_VERSION = 3

        /** Version de la sortie du parseur — incrémenter à toute évolution du parsing. */
        const val PARSER_VERSION = 2
    }
}
