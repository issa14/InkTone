package com.inktone.domain.service

import java.io.File

enum class ExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    JSON("json", "application/json"),
}

/**
 * Service d'export des statistiques (Lot Statistiques).
 *
 * Génère un fichier temporaire dans le cache de l'application et
 * retourne le [File] prêt à être partagé via [FileProvider].
 * L'implémentation vit dans `data/` — le domaine ne connaît ni
 * Room ni Android.
 */
interface StatisticsExportService {
    suspend fun exportCsv(): File
    suspend fun exportJson(): File
}
