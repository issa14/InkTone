package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.PronunciationRule
import com.inktone.domain.repository.PronunciationRuleRepository
import com.inktone.infrastructure.database.dao.PronunciationRuleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomPronunciationRuleRepository @Inject constructor(
    private val dao: PronunciationRuleDao,
) : PronunciationRuleRepository {
    override fun observeAll(): Flow<List<PronunciationRule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun save(rule: PronunciationRule) = dao.save(rule.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}
