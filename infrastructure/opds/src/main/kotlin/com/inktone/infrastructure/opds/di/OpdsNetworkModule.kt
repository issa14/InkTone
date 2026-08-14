package com.inktone.infrastructure.opds.di

import com.inktone.domain.service.OpdsCredentialsStore
import com.inktone.domain.service.OpdsFeedParser
import com.inktone.domain.service.OpdsHttpClient
import com.inktone.infrastructure.opds.DefaultOpdsHttpClient
import com.inktone.infrastructure.opds.XmlOpdsFeedParser
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
abstract class OpdsNetworkModule {
    @Binds @Singleton abstract fun bindOpdsHttpClient(impl: DefaultOpdsHttpClient): OpdsHttpClient
    @Binds @Singleton abstract fun bindOpdsFeedParser(impl: XmlOpdsFeedParser): OpdsFeedParser

    companion object {
        @Provides
        @Singleton
        @OpdsClient
        fun provideOpdsOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
