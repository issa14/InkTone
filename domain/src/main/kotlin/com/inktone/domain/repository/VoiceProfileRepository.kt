package com.inktone.domain.repository

import com.inktone.domain.model.VoiceProfile

interface VoiceProfileRepository {
    suspend fun getById(id: String): VoiceProfile?
    suspend fun getAll(): List<VoiceProfile>
    suspend fun save(profile: VoiceProfile)
    suspend fun delete(id: String)
}
