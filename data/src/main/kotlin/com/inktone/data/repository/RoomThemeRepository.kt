package com.inktone.data.repository

import com.inktone.data.mapper.toDomain
import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.repository.ThemeRepository
import com.inktone.infrastructure.database.dao.CustomThemeDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Union des thèmes intégrés ([ReadingTheme.BUILT_IN], constantes en
 * mémoire, jamais en base) et des thèmes personnalisés (`custom_themes`,
 * Lot 9). [getById] cherche d'abord dans les intégrés (accès direct, pas
 * de requête) avant d'interroger la base — les intégrés sont consultés
 * bien plus souvent (bascule cyclique, résolution à chaque ouverture de
 * livre).
 */
class RoomThemeRepository @Inject constructor(
    private val dao: CustomThemeDao,
) : ThemeRepository {
    override fun observeAll(): Flow<List<ReadingTheme>> =
        dao.observeAll().map { custom -> ReadingTheme.BUILT_IN + custom.map { it.toDomain() } }

    override suspend fun getById(id: String): ReadingTheme? =
        ReadingTheme.BUILT_IN.firstOrNull { it.id == id } ?: dao.getById(id)?.toDomain()

    override suspend fun saveCustom(theme: ReadingTheme) = dao.save(theme.toEntity())

    override suspend fun deleteCustom(id: String) = dao.delete(id)
}
