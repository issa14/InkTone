package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Lot 9 — thèmes personnalisés créés depuis le Studio. Les thèmes intégrés ne sont jamais persistés ici. */
@Entity(tableName = "custom_themes")
data class CustomThemeEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val backgroundColorHex: String,
    val textColorHex: String,
    val accentColorHex: String,
    val highlightColorHex: String,
    val fontFamily: String,
)
