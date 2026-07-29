package com.inktone.infrastructure.database.di

import com.inktone.domain.service.SearchService
import com.inktone.infrastructure.database.search.RoomSearchService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {
    @Binds
    @Singleton
    abstract fun bindSearchService(impl: RoomSearchService): SearchService
}
