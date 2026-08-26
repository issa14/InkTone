package com.inktone.infrastructure.parser

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Propriétaire unique du répertoire des couvertures extraites, partagé
 * par [ReadiumPublicationParser] et [PdfPublicationParser] — auparavant
 * chacun recalculait `File(context.cacheDir, "covers")` de son côté.
 *
 * **Pourquoi `filesDir` et non `cacheDir`** (bug réel remonté par les
 * premiers bêta-testeurs, 2026-08-26) : les couvertures vivaient dans
 * `cacheDir`, qu'Android purge librement sous pression de stockage, et
 * que l'app elle-même effaçait intégralement via « Vider le cache »
 * (`SettingsViewModel.clearCache`). Toute la bibliothèque perdait ses
 * vignettes d'un coup, sans action visible de l'utilisateur, et seul un
 * « Reconstruire les couvertures » manuel (Lot 19) les ramenait.
 *
 * Une couverture n'est pas du cache : elle est dérivée, mais coûteuse à
 * reconstruire (réouverture ZIP ou rastérisation PDFium par livre) et
 * directement visible. Elle appartient donc aux données de l'app.
 * `filesDir` reste interne et privé — aucune permission de stockage
 * n'est impliquée (K5/ADR-015 intact).
 *
 * Le nom de fichier reste `hashCode().toUInt() + ".jpg"`, dérivé de
 * l'URI source : deux extractions du même livre écrasent le même
 * fichier plutôt que d'en accumuler.
 */
@Singleton
class CoverStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Répertoire courant des couvertures, créé à la demande. */
    fun directory(): File = File(context.filesDir, COVERS_DIR_NAME).apply { mkdirs() }

    /** Emplacement de la couverture d'une publication, par URI source. */
    fun coverFileFor(fileUri: String): File = File(directory(), "${fileUri.hashCode().toUInt()}.jpg")

    /**
     * Ancien emplacement (`cacheDir/covers`), conservé uniquement pour la
     * migration ponctuelle — voir [CoverStorageMigration]. Jamais créé
     * ici : `mkdirs()` ressusciterait un répertoire qu'on cherche
     * justement à faire disparaître.
     */
    internal fun legacyCacheDirectory(): File = File(context.cacheDir, COVERS_DIR_NAME)

    private companion object {
        const val COVERS_DIR_NAME = "covers"
    }
}
