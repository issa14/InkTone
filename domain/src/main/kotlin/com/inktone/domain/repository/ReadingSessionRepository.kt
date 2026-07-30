package com.inktone.domain.repository

import com.inktone.domain.model.ReadingSession

interface ReadingSessionRepository {
    suspend fun insert(session: ReadingSession)
    suspend fun getAllForPublication(publicationId: String): List<ReadingSession>
    suspend fun getAll(): List<ReadingSession>

    /**
     * Somme des durées de session pour une date donnée (format
     * "yyyy-MM-dd") — nécessaire pour la jauge quotidienne (Tâche 1.4,
     * Partie 1), absente jusqu'ici (seul [getAll] existait).
     */
    suspend fun getTotalDurationForDate(date: String): Long
}
