package com.readflow.di

import android.content.Context
import androidx.room.Room
import com.readflow.data.database.BookDao
import com.readflow.data.database.ProgressDao
import com.readflow.data.database.ReadFlowDatabase
import com.readflow.data.repository.BookRepositoryImpl
import com.readflow.data.repository.TtsRepositoryImpl
import com.readflow.domain.repository.BookRepository
import com.readflow.domain.repository.TtsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReadFlowDatabase {
        return Room.databaseBuilder(
            context,
            ReadFlowDatabase::class.java,
            "readflow.db"
        ).build()
    }

    @Provides
    fun provideBookDao(db: ReadFlowDatabase): BookDao = db.bookDao()

    @Provides
    fun provideProgressDao(db: ReadFlowDatabase): ProgressDao = db.progressDao()

    @Provides
    @Singleton
    fun provideBookRepository(impl: BookRepositoryImpl): BookRepository = impl

    @Provides
    @Singleton
    fun provideTtsRepository(impl: TtsRepositoryImpl): TtsRepository = impl
}
