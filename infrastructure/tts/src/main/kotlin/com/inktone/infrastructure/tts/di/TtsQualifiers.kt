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

/**
 * Qualifie l'adaptateur `TtsEngine` Edge TTS (cloud) — pour que
 * `SelectiveTtsEngine` (Lot 14) le distingue de la chaîne offline dans le
 * graphe Hilt (même principe que `@Palier1`/`@Palier2`).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EdgeEngine

/**
 * Qualifie la chaîne offline `FallbackTtsEngine` — pour que
 * `SelectiveTtsEngine` route vers elle quand `VoiceProfile.engine` n'est
 * pas `EDGE_TTS`, ou en repli après une erreur réseau Edge.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OfflineTtsEngine
