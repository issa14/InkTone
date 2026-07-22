package com.inktone.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.work.WorkManager
import com.inktone.data.database.AnnotationDao
import com.inktone.data.database.BookDao
import com.inktone.data.database.BookmarkDao
import com.inktone.data.database.HighlightDao
import com.inktone.data.database.MIGRATION_1_2
import com.inktone.data.database.MIGRATION_2_3
import com.inktone.data.database.MIGRATION_3_4
import com.inktone.data.database.MIGRATION_4_5
import com.inktone.data.database.MIGRATION_5_6
import com.inktone.data.database.MIGRATION_13_14
import com.inktone.data.database.MIGRATION_14_15
import com.inktone.data.database.MIGRATION_15_16
import com.inktone.data.database.MIGRATION_16_17
import com.inktone.data.database.PronunciationRuleDao
import com.inktone.data.database.InkToneDatabase
import com.inktone.data.database.ReadingProgressDao
import com.inktone.data.database.ReadingSessionDao
import com.inktone.data.database.RichBlockCacheDao
import com.inktone.data.database.SearchDao
import com.inktone.data.database.SentenceCacheDao
import com.inktone.data.repository.BookRepositoryImpl
import com.inktone.data.repository.TtsRepositoryImpl
import com.inktone.data.settings.SettingsRepository
import com.inktone.domain.provider.EdgeTtsProvider
import com.inktone.domain.provider.PiperTtsProvider
import com.inktone.domain.provider.TtsProvider
import com.inktone.domain.repository.BookRepository
import com.inktone.domain.repository.TtsRepository
import com.inktone.domain.service.AudioServiceLauncher
import com.inktone.service.audio.AudioServiceLauncherImpl
import com.inktone.service.edge.EdgeTtsClient
import com.inktone.service.edge.Mp3Decoder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.multibindings.IntoSet
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InkToneDatabase {
        return Room.databaseBuilder(
            context,
            InkToneDatabase::class.java,
            "inktone.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
         // WAL plutôt que TRUNCATE : le coût de commit en TRUNCATE croît avec la taille du
         // fichier .db, ce qui devenait perceptible lors d'un import par lot de centaines de
         // livres (voir PLAN_ACTION_TOP_TIER_CLAUDECODE.md §2.0). Un seul processus accède à
         // cette base, donc pas de contrainte multi-process qui aurait justifié TRUNCATE.
         .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
         // Le chemin 6→13 n'a jamais eu de migration explicite (versions consommées pendant
         // une période de développement pré-beta sans base installée réelle à reconstituer
         // fidèlement — voir architecture.md §11.1 et PLAN_ACTION_TOP_TIER_CLAUDECODE.md 1.2bis).
         // Room refuse qu'une version soit à la fois couverte (comme version de départ OU
         // d'arrivée) par une Migration explicite et listée ici (IllegalArgumentException à
         // build()) — la version 6 est donc exclue malgré l'absence de MIGRATION_6_7, car
         // MIGRATION_5_6 s'y termine déjà. Seules 7 à 12 sont réellement libres. Toute future
         // migration non couverte au-delà de la version 13 fera planter l'app au lieu d'effacer
         // silencieusement la base d'un testeur.
         .fallbackToDestructiveMigrationFrom(7, 8, 9, 10, 11, 12)
         .build()
    }

    // Injecté plutôt qu'appelé statiquement (WorkManager.getInstance(context)) pour rester
    // testable — un mock JVM ne peut pas intercepter un appel statique Android (voir
    // PLAN import EPUB §4, LibraryViewModel).
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    fun provideBookDao(db: InkToneDatabase): BookDao = db.bookDao()

    @Provides
    fun provideBookmarkDao(db: InkToneDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideSearchDao(db: InkToneDatabase): SearchDao = db.searchDao()

    @Provides
    fun provideReadingProgressDao(db: InkToneDatabase): ReadingProgressDao = db.readingProgressDao()

    @Provides
    fun provideSentenceCacheDao(db: InkToneDatabase): SentenceCacheDao = db.sentenceCacheDao()

    @Provides
    fun providePronunciationRuleDao(db: InkToneDatabase): PronunciationRuleDao = db.pronunciationRuleDao()

    @Provides
    fun provideAnnotationDao(db: InkToneDatabase): AnnotationDao = db.annotationDao()

    @Provides
    fun provideHighlightDao(db: InkToneDatabase): HighlightDao = db.highlightDao()

    @Provides
    fun provideReadingSessionDao(db: InkToneDatabase): ReadingSessionDao = db.readingSessionDao()

    @Provides
    fun provideRichBlockCacheDao(db: InkToneDatabase): RichBlockCacheDao = db.richBlockCacheDao()

    @Provides
    @Singleton
    fun provideBookRepository(impl: BookRepositoryImpl): BookRepository = impl

    // ── TTS Providers ──────────────────────────────────────

    @Provides
    @Singleton
    @IntoSet
    fun providePiperTtsProvider(impl: PiperTtsProvider): TtsProvider = impl

    @Provides
    @Singleton
    @IntoSet
    fun provideEdgeTtsProvider(impl: EdgeTtsProvider): TtsProvider = impl

    @Provides
    @Singleton
    fun provideTtsRepository(
        impl: TtsRepositoryImpl
    ): TtsRepository = impl

    @Provides
    @Singleton
    fun provideAudioServiceLauncher(impl: AudioServiceLauncherImpl): AudioServiceLauncher = impl
}
