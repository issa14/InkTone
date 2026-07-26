package com.inktone.core.testing.fake

import com.inktone.domain.model.VoiceProfile
import com.inktone.domain.repository.VoiceProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVoiceProfileRepository : VoiceProfileRepository {
    private val state = MutableStateFlow<List<VoiceProfile>>(emptyList())

    override suspend fun getById(id: String): VoiceProfile? =
        state.value.firstOrNull { it.id == id }

    override suspend fun getAll(): List<VoiceProfile> = state.value

    override suspend fun save(profile: VoiceProfile) {
        state.value = state.value.filterNot { it.id == profile.id } + profile
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}
