package com.inktone.data.mapper

import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.ReadingTheme
import com.inktone.infrastructure.database.entity.CustomThemeEntity

/** Lot 9 — un thème persisté en base est toujours personnalisé (isBuiltIn = false, jamais l'inverse). */
fun ReadingTheme.toEntity(): CustomThemeEntity {
    require(!isBuiltIn) { "un thème intégré ne se persiste jamais dans custom_themes : $id" }
    return CustomThemeEntity(
        id = id, displayName = displayName,
        backgroundColorHex = backgroundColorHex, textColorHex = textColorHex,
        accentColorHex = accentColorHex, highlightColorHex = highlightColorHex,
        fontFamily = fontFamily.name,
    )
}

fun CustomThemeEntity.toDomain(): ReadingTheme = ReadingTheme(
    id = id, displayName = displayName, isBuiltIn = false,
    backgroundColorHex = backgroundColorHex, textColorHex = textColorHex,
    accentColorHex = accentColorHex, highlightColorHex = highlightColorHex,
    fontFamily = FontFamily.valueOf(fontFamily),
)
