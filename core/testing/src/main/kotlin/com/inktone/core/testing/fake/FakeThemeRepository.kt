package com.inktone.core.testing.fake

import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeThemeRepository : ThemeRepository {
    private val custom = MutableStateFlow<List<ReadingTheme>>(emptyList())

    override fun observeAll(): Flow<List<ReadingTheme>> =
        custom.map { ReadingTheme.BUILT_IN + it }

    override suspend fun getById(id: String): ReadingTheme? =
        ReadingTheme.BUILT_IN.firstOrNull { it.id == id } ?: custom.value.firstOrNull { it.id == id }

    override suspend fun saveCustom(theme: ReadingTheme) {
        custom.value = custom.value.filterNot { it.id == theme.id } + theme
    }

    override suspend fun deleteCustom(id: String) {
        custom.value = custom.value.filterNot { it.id == id }
    }
}
