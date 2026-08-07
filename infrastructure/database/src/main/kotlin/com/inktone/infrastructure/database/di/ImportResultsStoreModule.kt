package com.inktone.infrastructure.database.di

import com.inktone.domain.service.ImportResultsStore
import com.inktone.infrastructure.database.RoomImportResultsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportResultsStoreModule {
    @Binds
    @Singleton
    abstract fun bindImportResultsStore(impl: RoomImportResultsStore): ImportResultsStore
}
