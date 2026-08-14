package com.inktone.infrastructure.tts.di

import javax.inject.Qualifier

/** Adaptateur Palier 2 (Sherpa-ONNX, Tache 5.1) - qualifie pour permettre a FallbackTtsEngine (Tache 5.8) de distinguer les deux moteurs TtsEngine du graphe Hilt. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Palier2

/** Adaptateur Palier 1 (Android natif, Tache 3.1) - voir Palier2. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Palier1

/**
 * Qualifie l'`OkHttpClient` dédié à Edge TTS (WebSocket Bing) — distinct de
 * celui de `SyncNetworkModule` (Lot 11) pour des timeouts propres à la
 * synthèse, et pour éviter un conflit de binding Hilt sur `OkHttpClient`
 * non qualifié (même raison que `@OpdsClient`, Lot 13).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EdgeTts
