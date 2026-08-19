package com.inktone.infrastructure.sync.di

import com.inktone.domain.service.SyncProvider
import com.inktone.domain.service.WebDavSyncService
import com.inktone.infrastructure.sync.SyncProviderRouter
import com.inktone.infrastructure.sync.webdav.WebDavCredentialsStore
import com.inktone.infrastructure.sync.webdav.WebDavCredentialsStoreContract
import com.inktone.infrastructure.sync.webdav.WebDavSyncManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncNetworkModule {
    /**
     * Lot 19 — sélection par [com.inktone.domain.model.SyncProviderId]
     * plutôt qu'un binding direct sur Google Drive : WebDAV est désormais
     * implémenté, l'aiguillage lit le compte persisté.
     */
    @Binds @Singleton abstract fun bindSyncProvider(impl: SyncProviderRouter): SyncProvider

    /** Connexion WebDAV (test/connect/déconnecter), consommée par `data` (`WebDavSyncLinker`). */
    @Binds @Singleton abstract fun bindWebDavSyncService(impl: WebDavSyncManager): WebDavSyncService

    /** Stockage chiffré des identifiants WebDAV — interface extraite pour les tests JVM sans Keystore. */
    @Binds @Singleton abstract fun bindWebDavCredentialsStore(impl: WebDavCredentialsStore): WebDavCredentialsStoreContract

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS) // téléversement du fichier de sauvegarde, potentiellement volumineux
            .build()
    }
}
