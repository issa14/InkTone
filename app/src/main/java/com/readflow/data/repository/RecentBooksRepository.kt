package com.readflow.data.repository

import com.readflow.data.database.RecentBookDao
import com.readflow.data.database.entity.RecentBookEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestionnaire LRU thread-safe de l'historique des livres récemment ouverts.
 *
 * Garantit un maximum de 30 entrées. Quand un livre est ouvert :
 * - S'il existe déjà → mis à jour et replacé en tête (lastOpened rafraîchi).
 * - S'il n'existe pas → inséré en tête.
 * - Si la limite est dépassée → le plus ancien est supprimé.
 */
@Singleton
class RecentBooksRepository @Inject constructor(
    private val dao: RecentBookDao
) {
    companion object {
        const val MAX_RECENT_BOOKS = 30
    }

    private val mutex = Mutex()

    /** Flux réactif de la liste triée (plus récent en premier). */
    val recentBooks: Flow<List<RecentBookEntity>> = dao.getRecentBooks()

    /**
     * Enregistre l'ouverture d'un livre. Thread-safe via [Mutex].
     */
    suspend fun openBook(book: RecentBookEntity) {
        mutex.withLock {
            // Mise à jour du timestamp + métadonnées (upsert = REPLACE)
            dao.upsert(book.copy(lastOpened = System.currentTimeMillis()))
            // Nettoyer si la limite est dépassée
            dao.trimToLimit()
        }
    }

    /** Supprime un livre de l'historique. */
    suspend fun removeBook(bookId: String) {
        mutex.withLock {
            dao.deleteByBookId(bookId)
        }
    }

    /** Vide intégralement l'historique. */
    suspend fun clearAll() {
        mutex.withLock {
            dao.clearAll()
        }
    }
}
