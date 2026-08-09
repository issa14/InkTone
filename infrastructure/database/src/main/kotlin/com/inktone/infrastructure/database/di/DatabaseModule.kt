package com.inktone.infrastructure.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.inktone.infrastructure.database.InkToneDatabase
import com.inktone.infrastructure.database.MIGRATION_1_2
import com.inktone.infrastructure.database.MIGRATION_2_3
import com.inktone.infrastructure.database.MIGRATION_3_4
import com.inktone.infrastructure.database.MIGRATION_4_5
import com.inktone.infrastructure.database.MIGRATION_5_6
import com.inktone.infrastructure.database.MIGRATION_6_7
import com.inktone.infrastructure.database.MIGRATION_7_8
import com.inktone.infrastructure.database.MIGRATION_8_9
import com.inktone.infrastructure.database.MIGRATION_9_10
import com.inktone.infrastructure.database.MIGRATION_10_11
import com.inktone.infrastructure.database.MIGRATION_11_12
import com.inktone.infrastructure.database.MIGRATION_12_13
import com.inktone.infrastructure.database.MIGRATION_13_14
import com.inktone.infrastructure.database.MIGRATION_14_15
import com.inktone.infrastructure.database.MIGRATION_15_16
import com.inktone.infrastructure.database.MIGRATION_16_17
import com.inktone.infrastructure.database.MIGRATION_17_18
import com.inktone.infrastructure.database.MIGRATION_18_19
import com.inktone.infrastructure.database.MIGRATION_19_20
import com.inktone.infrastructure.database.MIGRATION_20_21
import com.inktone.infrastructure.database.MIGRATION_21_22
import com.inktone.infrastructure.database.MIGRATION_22_23
import com.inktone.infrastructure.database.MIGRATION_23_24
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24)
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
    @Provides fun provideLibraryItemDao(db: InkToneDatabase) = db.libraryItemDao()
    @Provides fun provideImportResultDao(db: InkToneDatabase) = db.importResultDao()
    @Provides fun provideCustomThemeDao(db: InkToneDatabase) = db.customThemeDao()
    @Provides fun providePendingConflictDao(db: InkToneDatabase) = db.pendingConflictDao()
}
