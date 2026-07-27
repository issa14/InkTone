package com.inktone.infrastructure.parser.di

import com.inktone.domain.service.PublicationParser
import com.inktone.infrastructure.parser.CompositePublicationParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Lie le contrat de domaine PublicationParser (Tâche 1.7) à
 * CompositePublicationParser, qui sélectionne le bon parser par format
 * (Readium pour EPUB, TxtPublicationParser pour TXT — Tâche 4.2). Jamais
 * lié directement à ReadiumPublicationParser, contrairement à ce que la
 * Phase 3 faisait implicitement (un seul format alors supporté).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {
    @Binds
    @Singleton
    abstract fun bindPublicationParser(impl: CompositePublicationParser): PublicationParser
}
