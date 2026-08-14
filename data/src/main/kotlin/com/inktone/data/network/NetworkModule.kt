package com.inktone.data.network

import com.inktone.infrastructure.opds.di.OpdsClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Client HTTP OPDS (Lot 13) — construit ici, dans `data`, avec les deux
 * intercepteurs réseau : [UserAgentInterceptor] (identité client) et
 * [XmlSanitizerInterceptor] (flux XML/Atom non conformes). Qualifié
 * `@OpdsClient` pour ne pas entrer en conflit avec l'`OkHttpClient` du
 * fournisseur de synchronisation (Lot 11, non qualifié).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    @OpdsClient
    fun provideOpdsOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(UserAgentInterceptor())
        .addInterceptor(XmlSanitizerInterceptor())
        .build()
}
