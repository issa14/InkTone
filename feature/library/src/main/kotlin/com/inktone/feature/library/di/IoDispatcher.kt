package com.inktone.feature.library.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * Audit v1.0.0 (AUDIT_CONSOLIDATION_V1.md, P5) — même pattern que
 * `feature/settings/.../di/IoDispatcher` : permet aux tests
 * (LibraryViewModelTest, RecentsViewModelTest, LibraryDetailViewModelTest)
 * de substituer leur `StandardTestDispatcher` à `Dispatchers.Default`
 * pour le calcul de progression (computeProgressMap) qui ne doit pas
 * s'exécuter sur Main à chaque émission du Flow Room. Sans ce point
 * d'injection, `withContext(Dispatchers.Default)` saute sur un vrai pool
 * de threads que `advanceUntilIdle()` (kotlinx-coroutines-test) ne voit
 * pas — course avec l'assertion.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @IoDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
