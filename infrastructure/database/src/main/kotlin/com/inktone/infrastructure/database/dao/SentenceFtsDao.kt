package com.inktone.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.inktone.infrastructure.database.entity.SentenceFtsEntity

@Dao
interface SentenceFtsDao {
    @Insert
    suspend fun insertAll(entities: List<SentenceFtsEntity>)

    @Query("SELECT * FROM sentence_fts WHERE sentence_fts MATCH :query")
    suspend fun searchAll(query: String): List<SentenceFtsEntity>

    @Query("SELECT * FROM sentence_fts WHERE sentence_fts MATCH :query AND publicationId = :publicationId")
    suspend fun searchInPublication(query: String, publicationId: String): List<SentenceFtsEntity>
}
