package com.inktone.infrastructure.sync.di

import android.content.Context
import com.inktone.domain.service.TokenProvider
import com.inktone.infrastructure.sync.auth.AppAuthGoogleAuthRepository
import com.inktone.infrastructure.sync.auth.AuthStateStore
import com.inktone.infrastructure.sync.auth.GoogleAuthRepository
import com.inktone.infrastructure.sync.auth.SecureAuthStateStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.openid.appauth.AuthorizationService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncAuthModule {
    @Binds @Singleton abstract fun bindGoogleAuthRepository(impl: AppAuthGoogleAuthRepository): GoogleAuthRepository
    @Binds @Singleton abstract fun bindTokenProvider(impl: AppAuthGoogleAuthRepository): TokenProvider
    @Binds @Singleton abstract fun bindAuthStateStore(impl: SecureAuthStateStore): AuthStateStore

    companion object {
        @Provides
        @Singleton
        fun provideAuthorizationService(@ApplicationContext context: Context): AuthorizationService =
            AuthorizationService(context)
    }
}
