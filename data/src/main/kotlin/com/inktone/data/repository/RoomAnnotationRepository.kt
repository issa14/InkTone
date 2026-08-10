package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.Annotation
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.infrastructure.database.dao.AnnotationDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomAnnotationRepository @Inject constructor(
    private val dao: AnnotationDao,
) : AnnotationRepository {
    override fun observeForPublication(publicationId: String): Flow<List<Annotation>> =
        dao.observeForPublication(publicationId).map { list -> list.map { it.toDomain() } }
    override suspend fun insert(annotation: Annotation) = dao.insert(annotation.toEntity())
    override suspend fun update(annotation: Annotation) = dao.update(annotation.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun setPinned(id: String, isPinned: Boolean) = dao.setPinned(id, isPinned)
    override suspend fun getAll(): List<Annotation> = dao.getAll().map { it.toDomain() }
}
