package com.inktone.infrastructure.opds.di

import com.inktone.domain.service.OpdsCredentialsStore
import com.inktone.infrastructure.opds.SecureOpdsCredentialsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OpdsModule {
    @Binds @Singleton abstract fun bindOpdsCredentialsStore(impl: SecureOpdsCredentialsStore): OpdsCredentialsStore
}
