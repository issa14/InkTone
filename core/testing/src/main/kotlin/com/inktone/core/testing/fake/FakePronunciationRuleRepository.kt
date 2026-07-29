package com.inktone.core.testing.fake

import com.inktone.domain.model.PronunciationRule
import com.inktone.domain.repository.PronunciationRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePronunciationRuleRepository : PronunciationRuleRepository {
    private val state = MutableStateFlow<List<PronunciationRule>>(emptyList())

    override fun observeAll(): Flow<List<PronunciationRule>> = state

    override suspend fun save(rule: PronunciationRule) {
        state.value = state.value.filterNot { it.id == rule.id } + rule
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}
