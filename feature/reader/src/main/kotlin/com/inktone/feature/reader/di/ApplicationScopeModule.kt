package com.inktone.feature.reader.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Scope de coroutines à la durée de vie du PROCESSUS, pour les écritures qui
 * ne doivent jamais être annulées par la destruction d'un écran.
 *
 * Cas d'origine : la sauvegarde finale du Lecteur à sa fermeture (position
 * de reprise, K3, et dernier fragment de statistiques). Depuis lifecycle 2.8,
 * `ViewModel.clear()` ferme `viewModelScope` AVANT d'appeler `onCleared()` :
 * un `viewModelScope.launch` posé dans `onCleared()` ne s'exécute jamais, et
 * la dernière position défilée était silencieusement perdue (test
 * `ReaderViewModelScrollPositionTest.fermer_le_livre_pendant_le_debounce_…`).
 *
 * `SupervisorJob` : l'échec d'une écriture n'annule pas les suivantes.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
