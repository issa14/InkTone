package com.inktone.infrastructure.database.converter

import androidx.room.TypeConverter

/**
 * Sépare une liste de chaînes courtes (auteurs, sujets) par le caractère
 * de contrôle "Unit Separator" (U+001F), jamais présent dans un texte
 * normal. Suffisant pour ce cas d'usage — si une structure plus riche
 * devient nécessaire, migrer vers un converter JSON avec une migration
 * Room dédiée, pas une modification silencieuse du format existant.
 */
class StringListConverter {
    @TypeConverter
    fun fromList(list: List<String>): String = list.joinToString(separator = "")

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("")
}
