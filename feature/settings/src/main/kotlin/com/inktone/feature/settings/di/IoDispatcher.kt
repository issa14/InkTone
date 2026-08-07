package com.inktone.feature.settings.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * Lot 6, Palier B — permet à `SettingsViewModelTest` de substituer le
 * `StandardTestDispatcher` du test à `Dispatchers.IO` pour le calcul/
 * vidage du cache. Sans ce point d'injection, `withContext(Dispatchers.IO)`
 * saute sur un vrai pool de threads que `advanceUntilIdle()` (kotlinx-
 * coroutines-test) ne voit pas — la coroutine reprend en temps réel,
 * course avec l'assertion (trouvé par un test qui échouait de façon
 * intermittente, pas en théorie).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
