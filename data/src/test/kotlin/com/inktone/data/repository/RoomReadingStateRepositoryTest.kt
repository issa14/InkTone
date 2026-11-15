package com.inktone.data.repository

import com.inktone.data.mapper.toEntity
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingState
import com.inktone.domain.valueobject.Locator
import com.inktone.infrastructure.database.dao.ReadingStateDao
import com.inktone.infrastructure.database.entity.ReadingStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Audit UX D20 : la narration (position) et le Lecteur (surcharges) lisaient puis
 * réécrivaient la même ligne ; l'une pouvait écraser le changement de l'autre.
 */
class RoomReadingStateRepositoryTest {

    private class SlowDao : ReadingStateDao {
        private val rows = MutableStateFlow<Map<String, ReadingStateEntity>>(emptyMap())
        override suspend fun get(publicationId: String): ReadingStateEntity? {
            val read = rows.value[publicationId]
            delay(5)
            return read
        }
        override fun observe(publicationId: String): Flow<ReadingStateEntity?> = rows.map { it[publicationId] }
        override suspend fun getAll(): List<ReadingStateEntity> = rows.value.values.toList()
        override fun observeAll(): Flow<List<ReadingStateEntity>> = rows.map { it.values.toList() }
        override suspend fun save(entity: ReadingStateEntity) {
            rows.value = rows.value + (entity.publicationId to entity)
        }
        override suspend fun delete(publicationId: String) {
            rows.value = rows.value - publicationId
        }
    }

    private fun state(chapter: Int, overrides: ReadingOverrides? = null) = ReadingState(
        publicationId = "pub",
        locator = Locator(resourceHref = "ch$chapter.xhtml", chapterIndex = chapter, charOffset = 0),
        lastReadAt = 0L,
        overrides = overrides,
    )

    @Test
    fun `une position et une surcharge ecrites en meme temps se conservent toutes les deux`() = runBlocking {
        val dao = SlowDao()
        dao.save(state(0).toEntity())
        val repository = RoomReadingStateRepository(dao)

        listOf(
            async(Dispatchers.Default) { repository.update("pub") { it!!.copy(overrides = ReadingOverrides(theme = "sepia_vintage")) } },
            async(Dispatchers.Default) { repository.update("pub") { it!!.copy(locator = state(7).locator) } },
        ).awaitAll()

        val result = repository.get("pub")!!
        assertEquals("sepia_vintage", result.overrides?.theme)
        assertEquals(7, result.locator.chapterIndex)
    }
}
