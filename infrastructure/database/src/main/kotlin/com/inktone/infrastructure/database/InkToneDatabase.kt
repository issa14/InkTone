package com.inktone.infrastructure.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inktone.infrastructure.database.converter.StringListConverter
import com.inktone.infrastructure.database.dao.AnnotationDao
import com.inktone.infrastructure.database.dao.BookmarkDao
import com.inktone.infrastructure.database.dao.PronunciationRuleDao
import com.inktone.infrastructure.database.dao.PublicationDao
import com.inktone.infrastructure.database.dao.ReadingSessionDao
import com.inktone.infrastructure.database.dao.ReadingStateDao
import com.inktone.infrastructure.database.dao.SentenceFtsDao
import com.inktone.infrastructure.database.dao.UserPreferencesDao
import com.inktone.infrastructure.database.dao.VoiceProfileDao
import com.inktone.infrastructure.database.entity.AnnotationEntity
import com.inktone.infrastructure.database.entity.BookmarkEntity
import com.inktone.infrastructure.database.entity.PronunciationRuleEntity
import com.inktone.infrastructure.database.entity.PublicationEntity
import com.inktone.infrastructure.database.entity.ReadingSessionEntity
import com.inktone.infrastructure.database.entity.ReadingStateEntity
import com.inktone.infrastructure.database.entity.SentenceFtsEntity
import com.inktone.infrastructure.database.entity.UserPreferencesEntity
import com.inktone.infrastructure.database.entity.VoiceProfileEntity

@Database(
    entities = [
        PublicationEntity::class, ReadingStateEntity::class, ReadingSessionEntity::class,
        BookmarkEntity::class, AnnotationEntity::class, VoiceProfileEntity::class,
        UserPreferencesEntity::class, SentenceFtsEntity::class, PronunciationRuleEntity::class,
    ],
    version = 6, // Tache 1.4 : dailyGoalMinutes (MIGRATION_5_6)
    exportSchema = true, // condition du harnais de migration — Tâche 2.4
)
@TypeConverters(StringListConverter::class)
abstract class InkToneDatabase : RoomDatabase() {
    abstract fun publicationDao(): PublicationDao
    abstract fun readingStateDao(): ReadingStateDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun sentenceFtsDao(): SentenceFtsDao
    abstract fun pronunciationRuleDao(): PronunciationRuleDao
}
