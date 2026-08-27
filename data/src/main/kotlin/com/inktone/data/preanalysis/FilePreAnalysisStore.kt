package com.inktone.data.preanalysis

import com.inktone.domain.model.Chapter
import com.inktone.domain.service.PreAnalysisStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation fichier de [PreAnalysisStore] (Lot 22, Palier A).
 *
 * Un fichier JSON par publication, sous `cacheDir/preanalysis/`, portant
 * en en-tête la version du format, la version du parseur et le `fileHash`
 * de la source. `load` retourne `null` dès que l'une de ces trois clés
 * diverge de l'état attendu — le cache n'est **jamais** servi pour une
 * source différente ou un format/parseur évolué (décision 1).
 *
 * Purge explicite ([delete]) : le fichier ne bénéficie d'aucun
 * `ON DELETE CASCADE` de Room (décision 1, critère de sortie testé).
 */
@Singleton
class FilePreAnalysisStore @Inject constructor(
    cacheDir: File,
) : PreAnalysisStore {

    private val json = Json { ignoreUnknownKeys = true }
    private val baseDir = File(cacheDir, "preanalysis")

    override suspend fun save(publicationId: String, fileHash: String, chapters: List<Chapter>) {
        val file = fileFor(publicationId)
        val payload = PreAnalysisFile(
            header = PreAnalysisHeader(FORMAT_VERSION, PARSER_VERSION, fileHash),
            chapters = chapters.map { it.toDto() },
        )
        baseDir.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            tmp.writeText(json.encodeToString(payload))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    override suspend fun load(publicationId: String, fileHash: String): List<Chapter>? {
        val file = fileFor(publicationId)
        if (!file.exists()) return null
        return runCatching {
            val payload = json.decodeFromString(PreAnalysisFile.serializer(), file.readText())
            val header = payload.header
            if (header.formatVersion != FORMAT_VERSION) return null
            if (header.parserVersion != PARSER_VERSION) return null
            if (header.fileHash != fileHash) return null
            payload.chapters.map { it.toDomain() }
        }.getOrNull()
    }

    override suspend fun delete(publicationId: String) {
        fileFor(publicationId).delete()
    }

    private fun fileFor(publicationId: String): File =
        File(baseDir, "$publicationId.prea")

    private companion object {
        /** Version du format de sérialisation — incrémenter à tout changement de schéma. */
        const val FORMAT_VERSION = 1

        /** Version de la sortie du parseur — incrémenter à toute évolution du parsing. */
        const val PARSER_VERSION = 1
    }
}
