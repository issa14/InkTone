package com.inktone.data.mapper

import com.inktone.domain.model.PronunciationRule
import com.inktone.infrastructure.database.entity.PronunciationRuleEntity

fun PronunciationRule.toEntity(): PronunciationRuleEntity = PronunciationRuleEntity(
    id = id, originalText = originalText, replacementText = replacementText,
    isRegex = isRegex, isEnabled = isEnabled,
)

fun PronunciationRuleEntity.toDomain(): PronunciationRule = PronunciationRule(
    id = id, originalText = originalText, replacementText = replacementText,
    isRegex = isRegex, isEnabled = isEnabled,
)
