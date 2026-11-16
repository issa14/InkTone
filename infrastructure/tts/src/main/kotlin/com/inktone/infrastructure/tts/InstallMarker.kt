package com.inktone.infrastructure.tts

import java.io.File

/**
 * Marqueur d'installation complète d'un modèle : un fichier posé APRÈS l'extraction, qui porte
 * l'empreinte de l'archive installée (audit UX D71). Un modèle dont les fichiers existent mais sans
 * marqueur, ou avec celui d'une autre archive, n'est pas tenu pour installé : une extraction
 * interrompue, ou un modèle mis à jour, se réinstalle au lieu de planter le moteur.
 */
internal object InstallMarker {
    private const val NAME = ".install-complete"

    fun write(dir: File, archiveSha256: String) {
        File(dir, NAME).writeText(archiveSha256)
    }

    fun matches(dir: File, archiveSha256: String): Boolean =
        runCatching { File(dir, NAME).readText().trim() == archiveSha256 }.getOrDefault(false)
}
