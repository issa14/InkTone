package com.inktone.infrastructure.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.inktone.infrastructure.database.InkToneDatabase
import com.inktone.infrastructure.database.MIGRATION_1_2
import com.inktone.infrastructure.database.MIGRATION_2_3
import com.inktone.infrastructure.database.MIGRATION_3_4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InkToneDatabase =
        Room.databaseBuilder(context, InkToneDatabase::class.java, "inktone.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // K1 — Blueprint §6.5, ADR-016
            // PAS de fallbackToDestructiveMigration ici (K4) : toute migration
            // manquante doit faire planter l'app, jamais effacer les données.
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides fun providePublicationDao(db: InkToneDatabase) = db.publicationDao()
    @Provides fun provideReadingStateDao(db: InkToneDatabase) = db.readingStateDao()
    @Provides fun provideReadingSessionDao(db: InkToneDatabase) = db.readingSessionDao()
    @Provides fun provideBookmarkDao(db: InkToneDatabase) = db.bookmarkDao()
    @Provides fun provideAnnotationDao(db: InkToneDatabase) = db.annotationDao()
    @Provides fun provideVoiceProfileDao(db: InkToneDatabase) = db.voiceProfileDao()
    @Provides fun provideUserPreferencesDao(db: InkToneDatabase) = db.userPreferencesDao()
    @Provides fun provideSentenceFtsDao(db: InkToneDatabase) = db.sentenceFtsDao()
    @Provides fun providePronunciationRuleDao(db: InkToneDatabase) = db.pronunciationRuleDao()
}
