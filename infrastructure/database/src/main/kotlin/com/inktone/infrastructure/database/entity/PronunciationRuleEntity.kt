package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pronunciation_rules")
data class PronunciationRuleEntity(
    @PrimaryKey val id: String,
    val originalText: String,
    val replacementText: String,
    val isRegex: Boolean,
    val isEnabled: Boolean,
)
