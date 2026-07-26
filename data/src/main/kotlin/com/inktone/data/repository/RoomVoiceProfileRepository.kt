package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.repository.VoiceProfileRepository
import com.inktone.infrastructure.database.dao.VoiceProfileDao
import javax.inject.Inject

class RoomVoiceProfileRepository @Inject constructor(
    private val dao: VoiceProfileDao,
) : VoiceProfileRepository {
    override suspend fun getById(id: String): VoiceProfile? = dao.getById(id)?.toDomain()
    override suspend fun getAll(): List<VoiceProfile> = dao.getAll().map { it.toDomain() }
    override suspend fun save(profile: VoiceProfile) = dao.save(profile.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}
