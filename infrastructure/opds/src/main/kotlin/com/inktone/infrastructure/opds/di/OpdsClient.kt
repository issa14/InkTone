package com.inktone.infrastructure.opds.di

import javax.inject.Qualifier

/**
 * Qualifie l'`OkHttpClient` dédié à OPDS — distinct de celui de
 * `SyncNetworkModule` (Lot 11) pour ne pas coupler la configuration
 * réseau de deux fournisseurs différents, et pour éviter un conflit de
 * binding Hilt sur `OkHttpClient` non qualifié.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpdsClient
