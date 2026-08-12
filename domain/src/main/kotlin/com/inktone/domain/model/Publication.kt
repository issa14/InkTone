package com.inktone.domain.model

enum class PublicationFormat { EPUB, TXT, PDF }

/**
 * Œuvre importée dans la bibliothèque (Blueprint §3.3).
 *
 * `seriesName`/`seriesIndex`/`isFavorite`/`subjects` font partie du
 * modèle dès la v1 (acquis K11 — extraction via `belongsTo` Readium et
 * `subjects` peuplés à l'import, pas une évolution future).
 *
 * `pageCount` (Lot 12, tâche 12.4) — la définition précise annoncée ici
 * depuis la revue B5 : `null` pour un format reflowable (EPUB/TXT, pas de
 * pagination fixe), un entier strictement positif pour un format paginé
 * (PDF). Distinct de `chapterCount`, qui reste la structure de lecture/
 * recherche générique — les deux coïncident numériquement pour un PDF
 * (page = chapitre, `PdfPublicationParser`) mais portent des intentions
 * différentes, jamais fusionnés en un champ générique ambigu.
 */
data class Publication(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val language: String? = null,
    val description: String? = null,
    val coverUri: String? = null,
    val format: PublicationFormat,
    val fileUri: String,
    val fileHash: String,
    val fileSize: Long,
    val chapterCount: Int,
    val pageCount: Int? = null,
    val seriesName: String? = null,
    val seriesIndex: Float? = null,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val subjects: List<String> = emptyList(),
    val isDrmProtected: Boolean = false,
    val importDate: Long,
    val lastOpened: Long? = null,
) {
    init {
        require(title.isNotBlank()) { "title ne peut pas être vide" }
        require(chapterCount >= 0) { "chapterCount doit être positif ou nul" }
        require(pageCount == null || pageCount > 0) { "pageCount doit être strictement positif si renseigné" }
        require(fileSize >= 0) { "fileSize doit être positif ou nul" }
        require(fileHash.isNotBlank()) { "fileHash ne peut pas être vide (détection de doublons K-issue)" }
    }
}
