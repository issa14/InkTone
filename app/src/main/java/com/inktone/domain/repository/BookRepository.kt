package com.inktone.domain.repository

import com.inktone.data.database.entity.ReadingProgress
import com.inktone.domain.model.Book
import com.inktone.domain.model.Chapter
import java.io.InputStream

interface BookRepository {
    /**
     * Importe un EPUB depuis un InputStream et retourne le [Book] créé.
     *
     * @param bookId identifiant fourni par l'appelant (plutôt que généré en interne) — permet
     * à l'UI de savoir dès l'appel quel livre est en cours d'import, avant même que le résultat
     * ne soit disponible (voir PLAN import EPUB §2).
     */
    suspend fun importEpub(
        bookId: String,
        inputStream: InputStream,
        fileName: String,
        sourceFolder: String? = null,
        onProgress: (progress: Float, status: String) -> Unit = { _, _ -> }
    ): Book

    /** Récupère un chapitre complet (texte découpé en phrases). */
    suspend fun getChapter(bookId: String, chapterIndex: Int): Chapter

    /** Liste tous les livres importés. */
    suspend fun getAllBooks(): List<Book>

    /** Sauvegarde/charge la progression de lecture (table unifiée `reading_progress`). */
    suspend fun saveProgress(progress: ReadingProgress)
    suspend fun getProgress(bookId: String): ReadingProgress?

    /** Progression de plusieurs livres en une seule requête (voir [getProgress]). */
    suspend fun getProgressForBooks(bookIds: List<String>): Map<String, ReadingProgress>

    /** Ré-extrait la couverture d'un livre depuis son EPUB source. Retourne le nouveau chemin, ou null si aucune couverture trouvée. */
    suspend fun regenerateCover(bookId: String): String?

    /** Retire les couvertures de tous les livres (retour au placeholder dégradé automatique). */
    suspend fun clearAllCovers()

    /** Bascule le statut favori d'un livre. */
    suspend fun setFavorite(bookId: String, isFavorite: Boolean)

    /** Liste dédupliquée de tous les tags (subjects EPUB) présents dans la bibliothèque. */
    suspend fun getAllTags(): List<String>

    /**
     * Marque `FAILED` tout livre resté bloqué en `IMPORTING` (import interrompu par un arrêt
     * du process — aucun worker actif ne le reprendra tant que la Phase WorkManager n'existe
     * pas, voir PLAN import EPUB §3/§4). Retourne les titres des livres concernés, pour
     * affichage d'un message à l'utilisateur avec retry manuel.
     */
    suspend fun recoverOrphanedImports(): List<String>
}
