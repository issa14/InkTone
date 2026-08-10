package com.inktone.infrastructure.sync.di

import com.inktone.domain.service.SyncProvider
import com.inktone.infrastructure.sync.drive.GoogleDriveSyncProvider
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
    /** Seule implémentation à ce jour (tâche 11.5) — WebDAV (hors périmètre de ce lot) exigera une sélection par [com.inktone.domain.model.SyncProviderId] plutôt qu'un binding direct. */
    @Binds @Singleton abstract fun bindSyncProvider(impl: GoogleDriveSyncProvider): SyncProvider

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
