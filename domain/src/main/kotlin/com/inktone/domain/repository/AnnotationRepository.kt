package com.inktone.domain.repository

import com.inktone.domain.model.Annotation
import kotlinx.coroutines.flow.Flow

interface AnnotationRepository {
    fun observeForPublication(publicationId: String): Flow<List<Annotation>>
    suspend fun insert(annotation: Annotation)
    suspend fun update(annotation: Annotation)
    suspend fun delete(id: String)

    /** Lot 4, tâche 4.3 — même patron que Publication.isPinned. */
    suspend fun setPinned(id: String, isPinned: Boolean)

    /** Lot 11, tâche 11.1 — toutes les annotations, toutes publications confondues, pour l'export de sauvegarde. */
    suspend fun getAll(): List<Annotation>
}
