package com.inktone.data.di

import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.usecase.UpdateReadingStateUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Fournit les Use Cases du domaine a constructeur simple (Tache 1.8 :
 * pas de @Inject dans domain/, qui reste pur Kotlin sans annotation de
 * framework de DI). Un seul Use Case pour l'instant (Tache 3.5) ; les
 * autres seront ajoutes au fil des phases qui les consomment reellement,
 * pas par anticipation.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    fun provideUpdateReadingStateUseCase(
        readingStateRepository: ReadingStateRepository,
    ): UpdateReadingStateUseCase = UpdateReadingStateUseCase(readingStateRepository)
}
