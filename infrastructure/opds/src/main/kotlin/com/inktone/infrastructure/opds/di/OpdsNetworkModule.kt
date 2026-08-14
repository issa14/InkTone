package com.inktone.infrastructure.opds.di

import com.inktone.domain.service.OpdsFeedParser
import com.inktone.domain.service.OpdsHttpClient
import com.inktone.infrastructure.opds.DefaultOpdsHttpClient
import com.inktone.infrastructure.opds.XmlOpdsFeedParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OpdsNetworkModule {
    @Binds @Singleton abstract fun bindOpdsHttpClient(impl: DefaultOpdsHttpClient): OpdsHttpClient
    @Binds @Singleton abstract fun bindOpdsFeedParser(impl: XmlOpdsFeedParser): OpdsFeedParser
}
