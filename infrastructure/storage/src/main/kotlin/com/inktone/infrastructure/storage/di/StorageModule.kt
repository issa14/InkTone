package com.inktone.infrastructure.storage.di

import com.inktone.domain.service.FileStorageService
import com.inktone.infrastructure.storage.SafFileStorageService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {
    @Binds
    @Singleton
    abstract fun bindFileStorageService(impl: SafFileStorageService): FileStorageService
}
