package com.inktone.infrastructure.parser

import android.util.Log
import com.inktone.domain.repository.PublicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Déplace les couvertures déjà extraites de l'ancien `cacheDir/covers`
 * vers `filesDir/covers` ([CoverStorage]) et réécrit les `coverUri`
 * correspondants en base.
 *
 * Sans cette migration, le seul changement de répertoire ferait perdre
 * sa vignette à toute bibliothèque déjà constituée au moment de la mise
 * à jour : `coverUri` stocke un chemin absolu, et `BookCover` retombe
 * silencieusement sur le placeholder quand le fichier n'existe pas
 * (`File(coverUri).takeIf { it.exists() }`). Le correctif aurait
 * reproduit, à la mise à jour, exactement le symptôme qu'il corrige.
 *
 * Idempotente et sans état persisté : l'ancien répertoire est supprimé
 * en fin de parcours, donc [start] ne fait plus rien aux lancements
 * suivants. Un échec partiel (fichier verrouillé, écriture refusée)
 * laisse le répertoire en place et sera repris au lancement suivant —
 * jamais un `coverUri` réécrit vers un fichier qui n'existe pas.
 *
 * Opération ponctuelle de démarrage, hors chemin critique : lancée dans
 * un scope fourni par l'appelant, jamais bloquante pour `onCreate`.
 */
@Singleton
class CoverStorageMigration @Inject constructor(
    private val coverStorage: CoverStorage,
    private val publicationRepository: PublicationRepository,
) {

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            runCatching { migrate() }
                .onFailure { Log.w(TAG, "migration des couvertures interrompue", it) }
        }
    }

    private suspend fun migrate() {
        val legacyDir = coverStorage.legacyCacheDirectory()
        if (!legacyDir.isDirectory) return

        val targetDir = coverStorage.directory()
        val legacyFiles = legacyDir.listFiles()?.filter { it.isFile }.orEmpty()

        var moved = 0
        val relocated = mutableMapOf<String, String>()
        for (source in legacyFiles) {
            val destination = File(targetDir, source.name)
            if (moveFile(source, destination)) {
                relocated[source.absolutePath] = destination.absolutePath
                moved++
            }
        }

        // Réécriture ciblée : seules les publications dont la couverture
        // pointe vers un fichier réellement déplacé sont touchées. Une
        // couverture `content://` (import OPDS) ou déjà dans `filesDir`
        // n'est jamais réécrite.
        var rewritten = 0
        for (publication in publicationRepository.observeAll().first()) {
            val newPath = publication.coverUri?.let { relocated[it] } ?: continue
            publicationRepository.setCoverUri(publication.id, newPath)
            rewritten++
        }

        // Supprimé seulement si tout est parti : un reliquat signifie
        // qu'il reste du travail pour le prochain lancement.
        val remaining = legacyDir.listFiles()?.any { it.isFile } ?: false
        if (!remaining) legacyDir.deleteRecursively()

        if (moved > 0 || rewritten > 0) {
            Log.i(TAG, "couvertures migrees vers filesDir : $moved fichier(s), $rewritten coverUri reecrit(s)")
        }
    }

    /**
     * `renameTo` échoue en retournant `false` (jamais une exception) dès
     * que source et destination sont sur des volumes différents — le cas
     * réel sur les appareils où le cache est monté séparément des données.
     * La copie explicite est donc le chemin nominal, pas un cas d'exception.
     */
    private fun moveFile(source: File, destination: File): Boolean {
        if (source.renameTo(destination)) return true
        return runCatching {
            source.copyTo(destination, overwrite = true)
            source.delete()
            true
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "CoverStorageMigration"
    }
}
