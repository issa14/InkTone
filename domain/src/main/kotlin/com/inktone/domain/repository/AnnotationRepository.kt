package com.inktone.domain.repository

import com.inktone.domain.model.Annotation
import kotlinx.coroutines.flow.Flow

interface AnnotationRepository {
    fun observeForPublication(publicationId: String): Flow<List<Annotation>>
    suspend fun insert(annotation: Annotation)
    suspend fun update(annotation: Annotation)
    suspend fun delete(id: String)
}
