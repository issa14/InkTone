package com.inktone.domain.repository

import com.inktone.domain.model.PronunciationRule
import kotlinx.coroutines.flow.Flow

interface PronunciationRuleRepository {
    fun observeAll(): Flow<List<PronunciationRule>>
    suspend fun save(rule: PronunciationRule)
    suspend fun delete(id: String)
}
