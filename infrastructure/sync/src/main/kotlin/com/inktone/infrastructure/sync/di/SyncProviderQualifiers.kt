package com.inktone.infrastructure.sync.di

import javax.inject.Qualifier

/** Qualifie l'implémentation Google Drive de [com.inktone.domain.service.SyncProvider] pour le routeur. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GoogleDriveProvider

/** Qualifie l'implémentation WebDAV de [com.inktone.domain.service.SyncProvider] pour le routeur. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebDavProvider
